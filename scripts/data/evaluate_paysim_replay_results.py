#!/usr/bin/env python3
"""Evaluate PaySim replay detection results against the label sidecar."""

from __future__ import annotations

import argparse
import json
import re
import sys
from collections import Counter
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from paysim_evaluation_policy import (
    DEFAULT_THRESHOLD_VERSION,
    EVALUATION_CONTRACT_VERSION,
    EVALUATION_POLICY_VERSION,
    RULE_VERSION,
    threshold_policy_for,
)
from paysim_native_type_mapping import mapping_for


SCRIPT_VERSION = "paysim-evaluation-v1"
REPORT_SCHEMA_VERSION = "2026-06-v2-phase9"
DEFAULT_LABELS = Path("data/samples/paysim-labels-sample.jsonl")
DEFAULT_RESULTS = Path("data/processed/paysim-detection-results.jsonl")
DEFAULT_OUTPUT = Path("data/processed/paysim-evaluation-report.json")
RISK_LEVELS = ("LOW", "MEDIUM", "HIGH", "CRITICAL")
RISK_RANK = {risk: index for index, risk in enumerate(RISK_LEVELS)}
MAX_SAMPLE_EVENT_IDS = 10
RAW_FIELDS = {"nameOrig", "nameDest"}
LABEL_FIELDS_FORBIDDEN_IN_RESULTS = {"isFraud", "isFlaggedFraud", "sourceFlaggedFraud"}
FORBIDDEN_REPORT_FIELDS = {"requestBody", "responseBody", "token", "authToken", "authorization"}
RAW_IDENTIFIER_PATTERN = re.compile(r"\b[CM]\d{3,}\b")
REPLAY_PRE_HTTP_REJECT_PREFIXES = (
    "UNSUPPORTED_EVENT_TYPE_FOR_CURRENT_API",
    "LABEL_FIELD",
    "RAW_IDENTIFIER_FIELD",
    "RAW_IDENTIFIER_VALUE",
    "RECEIVED_AT_PRESENT",
    "MISSING_FIELD",
    "INVALID_HASH_ID",
    "INVALID_EVENT_ID",
    "INVALID_TRACE_ID",
    "INVALID_EVENT_TYPE",
    "INVALID_NATIVE_EVENT_TYPE",
    "UNSUPPORTED_NATIVE_TYPE",
    "UNSUPPORTED_MAPPING_POLICY_VERSION",
    "NATIVE_MAPPING_MISMATCH",
    "NORMALIZED_EVENT_TYPE_MISMATCH",
    "TYPE_SUPPORT_LEVEL_MISMATCH",
    "INVALID_CURRENCY",
    "INVALID_AMOUNT",
    "INVALID_EVENT_TIME",
)


class EvaluationError(Exception):
    pass


@dataclass
class LabelRow:
    event_id: str
    is_fraud: bool
    source_type: str | None = None


@dataclass
class DetectionResult:
    event_id: str
    normalized_event_id: str
    risk_level: str
    risk_score: float | None = None
    rule_codes: list[str] = field(default_factory=list)


@dataclass
class EvaluationStats:
    total_labels: int = 0
    total_results: int = 0
    matched_results: int = 0
    unmatched_results: int = 0
    evaluated_events: int = 0
    excluded_replay_rejected: int = 0
    missing_results: int = 0
    total_fraud_labels: int = 0
    total_non_fraud_labels: int = 0
    missing_fraud_labels: int = 0
    missing_non_fraud_labels: int = 0
    true_positive: int = 0
    false_positive: int = 0
    true_negative: int = 0
    false_negative: int = 0
    risk_level_counts: Counter[str] = field(default_factory=Counter)
    fraud_by_risk_level: Counter[str] = field(default_factory=Counter)
    non_fraud_by_risk_level: Counter[str] = field(default_factory=Counter)
    rule_code_counts: Counter[str] = field(default_factory=Counter)
    action_decision_distribution: Counter[str] = field(default_factory=Counter)
    review_candidate_events: int = 0
    blocked_candidate_events: int = 0
    evaluated_native_type_distribution: Counter[str] = field(default_factory=Counter)
    evaluated_normalized_type_distribution: Counter[str] = field(default_factory=Counter)
    denominator_excluded_native_type_distribution: Counter[str] = field(default_factory=Counter)
    sample_event_ids: list[str] = field(default_factory=list)

    def add_sample_event_id(self, event_id: str) -> None:
        if len(self.sample_event_ids) < MAX_SAMPLE_EVENT_IDS:
            self.sample_event_ids.append(event_id)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate PaySim replay results against label sidecar.")
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--replay-report", type=Path, default=None)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--positive-risk-level", choices=RISK_LEVELS, default="MEDIUM")
    parser.add_argument("--threshold-version", default=DEFAULT_THRESHOLD_VERSION)
    parser.add_argument("--rule-version", default=RULE_VERSION)
    parser.add_argument("--evaluation-policy-version", default=EVALUATION_POLICY_VERSION)
    parser.add_argument("--event-id-prefix")
    parser.add_argument("--exclude-replay-rejected", dest="exclude_replay_rejected", action="store_true", default=True)
    parser.add_argument("--include-replay-rejected", dest="exclude_replay_rejected", action="store_false")
    parser.add_argument("--include-missing-results", dest="include_missing_results", action="store_true", default=False)
    parser.add_argument("--exclude-missing-results", dest="include_missing_results", action="store_false")
    parser.add_argument("--force", action="store_true")
    parser.add_argument("--strict", action="store_true")
    return parser.parse_args()


def iso_now() -> str:
    return datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")


def read_jsonl(path: Path):
    with path.open("r", encoding="utf-8") as file:
        for line_no, line in enumerate(file, start=1):
            stripped = line.strip()
            if not stripped:
                continue
            try:
                value = json.loads(stripped)
            except json.JSONDecodeError as exc:
                raise EvaluationError(f"{path}:{line_no}: invalid JSON") from exc
            if not isinstance(value, dict):
                raise EvaluationError(f"{path}:{line_no}: JSON row must be an object")
            yield line_no, value


def scan_forbidden(value: Any, source: str, forbidden_fields: set[str]) -> None:
    if isinstance(value, dict):
        for key, child in value.items():
            if key in RAW_FIELDS or key in forbidden_fields or key in FORBIDDEN_REPORT_FIELDS:
                raise EvaluationError(f"{source} contains forbidden field: {key}")
            scan_forbidden(child, source, forbidden_fields)
    elif isinstance(value, list):
        for child in value:
            scan_forbidden(child, source, forbidden_fields)
    elif isinstance(value, str) and RAW_IDENTIFIER_PATTERN.search(value):
        raise EvaluationError(f"{source} contains raw PaySim identifier pattern")


def normalize_event_id(event_id: str, event_id_prefix: str | None) -> str:
    if event_id_prefix:
        prefix = f"{event_id_prefix}-"
        if event_id.startswith(prefix):
            return event_id[len(prefix) :]
    return event_id


def extract_rule_codes(row: dict[str, Any]) -> list[str]:
    value = row.get("ruleCodes", row.get("matchedRules", row.get("matchedRuleCodes", [])))
    if value is None:
        return []
    if isinstance(value, list):
        return [str(item) for item in value if str(item)]
    if isinstance(value, str):
        return [item.strip() for item in value.split(",") if item.strip()]
    raise EvaluationError("detection result ruleCodes/matchedRules must be a list or comma-separated string")


def extract_risk_score(row: dict[str, Any]) -> float | None:
    if "riskScore" not in row or row["riskScore"] is None:
        return None
    try:
        value = float(row["riskScore"])
    except (TypeError, ValueError) as exc:
        raise EvaluationError("detection result riskScore must be numeric when present") from exc
    if value < 0 or value > 100:
        raise EvaluationError("detection result riskScore must be between 0 and 100")
    return value


def load_labels(path: Path, strict: bool) -> tuple[dict[str, LabelRow], list[str]]:
    labels: dict[str, LabelRow] = {}
    warnings: list[str] = []
    for line_no, row in read_jsonl(path):
        scan_forbidden(row, "label file", set())
        event_id = row.get("eventId")
        if not isinstance(event_id, str) or not event_id:
            raise EvaluationError(f"{path}:{line_no}: label eventId is required")
        if not isinstance(row.get("isFraud"), bool):
            raise EvaluationError(f"{path}:{line_no}: label isFraud boolean is required")
        if strict:
            if not isinstance(row.get("sourceFlaggedFraud"), bool):
                raise EvaluationError(f"{path}:{line_no}: label sourceFlaggedFraud boolean is required")
            if isinstance(row.get("sourceStep"), bool) or not isinstance(row.get("sourceStep"), int) or row["sourceStep"] < 0:
                raise EvaluationError(f"{path}:{line_no}: label sourceStep non-negative integer is required")
            if not isinstance(row.get("sourceType"), str) or not row["sourceType"]:
                raise EvaluationError(f"{path}:{line_no}: label sourceType string is required")
        if event_id in labels:
            raise EvaluationError(f"duplicate label eventId: {event_id}")
        source_type = row.get("sourceType") if isinstance(row.get("sourceType"), str) else None
        labels[event_id] = LabelRow(event_id=event_id, is_fraud=row["isFraud"], source_type=source_type)
    return labels, warnings


def load_results(path: Path, event_id_prefix: str | None, strict: bool) -> tuple[dict[str, DetectionResult], list[str]]:
    results: dict[str, DetectionResult] = {}
    warnings: list[str] = []
    for line_no, row in read_jsonl(path):
        scan_forbidden(row, "detection result file", LABEL_FIELDS_FORBIDDEN_IN_RESULTS)
        event_id = row.get("eventId")
        risk_level = row.get("riskLevel")
        if not isinstance(event_id, str) or not event_id:
            raise EvaluationError(f"{path}:{line_no}: result eventId is required")
        if risk_level not in RISK_RANK:
            raise EvaluationError(f"{path}:{line_no}: unsupported riskLevel: {risk_level}")
        normalized_event_id = normalize_event_id(event_id, event_id_prefix)
        if normalized_event_id in results:
            raise EvaluationError(f"duplicate result eventId after prefix normalization: {normalized_event_id}")
        results[normalized_event_id] = DetectionResult(
            event_id=event_id,
            normalized_event_id=normalized_event_id,
            risk_level=risk_level,
            risk_score=extract_risk_score(row),
            rule_codes=extract_rule_codes(row),
        )
    return results, warnings


def load_replay_report(path: Path | None) -> dict[str, Any] | None:
    if path is None:
        return None
    if not path.exists():
        raise FileNotFoundError(str(path))
    try:
        value = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise EvaluationError(f"invalid replay report JSON: {path}") from exc
    if not isinstance(value, dict):
        raise EvaluationError("replay report must be a JSON object")
    scan_forbidden(value, "replay report", set())
    return value


def event_id_prefix_from(args: argparse.Namespace, replay_report: dict[str, Any] | None) -> str | None:
    if args.event_id_prefix:
        return args.event_id_prefix
    if replay_report and isinstance(replay_report.get("eventIdPrefix"), str) and replay_report["eventIdPrefix"]:
        return replay_report["eventIdPrefix"]
    return None


def replay_rejected_event_ids(replay_report: dict[str, Any] | None, event_id_prefix: str | None) -> set[str]:
    if not replay_report:
        return set()
    rejected: set[str] = set()
    failures = replay_report.get("failures", [])
    if not isinstance(failures, list):
        return rejected
    for failure in failures:
        if not isinstance(failure, dict):
            continue
        event_id = failure.get("eventId")
        reason = failure.get("reason")
        if not isinstance(event_id, str) or not isinstance(reason, str):
            continue
        if reason.startswith(REPLAY_PRE_HTTP_REJECT_PREFIXES):
            rejected.add(normalize_event_id(event_id, event_id_prefix))
    return rejected


def replay_payload_rejected_count(replay_report: dict[str, Any] | None) -> int | None:
    if not replay_report:
        return None
    value = replay_report.get("payloadRejected", 0)
    if value is None:
        return 0
    try:
        count = int(value)
    except (TypeError, ValueError) as exc:
        raise EvaluationError("replay report payloadRejected must be an integer") from exc
    if count < 0:
        raise EvaluationError("replay report payloadRejected must be >= 0")
    return count


def replay_mapping_value(replay_report: dict[str, Any] | None, key: str, default: Any) -> Any:
    if not replay_report:
        return default
    value = replay_report.get(key, default)
    if isinstance(default, dict) and not isinstance(value, dict):
        return default
    return value


def is_predicted_positive(result: DetectionResult, positive_risk_level: str, threshold_policy) -> bool:
    if result.risk_score is not None:
        return result.risk_score >= threshold_policy.medium_risk_threshold
    return RISK_RANK[result.risk_level] >= RISK_RANK[positive_risk_level]


def action_decision_for(result: DetectionResult, threshold_policy) -> str:
    if result.risk_score is not None:
        if result.risk_score >= threshold_policy.high_risk_threshold:
            return "BLOCK"
        if result.risk_score >= threshold_policy.medium_risk_threshold:
            return "REVIEW"
        return "ALLOW"
    if result.risk_level in ("HIGH", "CRITICAL"):
        return "BLOCK"
    if result.risk_level == "MEDIUM":
        return "REVIEW"
    return "ALLOW"


def safe_ratio(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return numerator / denominator


def f1_score(precision: float | None, recall: float | None) -> float | None:
    if precision is None or recall is None or precision + recall == 0:
        return None
    return 2 * precision * recall / (precision + recall)


def sorted_counter(counter: Counter[str]) -> dict[str, int]:
    values = {key: counter.get(key, 0) for key in RISK_LEVELS}
    values.update({key: counter[key] for key in sorted(counter) if key not in RISK_RANK})
    return values


def sorted_distribution(counter: Counter[str]) -> dict[str, int]:
    return dict(sorted(counter.items()))


def normalized_type_for_source(source_type: str | None) -> str | None:
    if not source_type:
        return None
    mapping = mapping_for(source_type)
    return mapping.normalized_type


def evaluate_rows(
    labels: dict[str, LabelRow],
    results: dict[str, DetectionResult],
    rejected_event_ids: set[str],
    positive_risk_level: str,
    threshold_policy,
    include_missing_results: bool,
) -> EvaluationStats:
    label_ids = set(labels)
    result_ids = set(results)
    stats = EvaluationStats(
        total_labels=len(labels),
        total_results=len(results),
        matched_results=len(label_ids & result_ids),
        unmatched_results=len(result_ids - label_ids),
        total_fraud_labels=sum(1 for label in labels.values() if label.is_fraud),
        total_non_fraud_labels=sum(1 for label in labels.values() if not label.is_fraud),
    )
    for event_id in sorted(labels):
        label = labels[event_id]
        if event_id in rejected_event_ids:
            stats.excluded_replay_rejected += 1
            if label.source_type:
                stats.denominator_excluded_native_type_distribution[label.source_type] += 1
            continue
        result = results.get(event_id)
        if result is None:
            stats.missing_results += 1
            if label.is_fraud:
                stats.missing_fraud_labels += 1
            else:
                stats.missing_non_fraud_labels += 1
            if not include_missing_results:
                continue
            stats.evaluated_events += 1
            stats.add_sample_event_id(event_id)
            if label.source_type:
                stats.evaluated_native_type_distribution[label.source_type] += 1
                normalized_type = normalized_type_for_source(label.source_type)
                if normalized_type:
                    stats.evaluated_normalized_type_distribution[normalized_type] += 1
            if label.is_fraud:
                stats.false_negative += 1
            else:
                stats.true_negative += 1
            continue

        predicted_positive = is_predicted_positive(result, positive_risk_level, threshold_policy)
        action_decision = action_decision_for(result, threshold_policy)
        stats.evaluated_events += 1
        stats.add_sample_event_id(event_id)
        if label.source_type:
            stats.evaluated_native_type_distribution[label.source_type] += 1
            normalized_type = normalized_type_for_source(label.source_type)
            if normalized_type:
                stats.evaluated_normalized_type_distribution[normalized_type] += 1
        stats.risk_level_counts[result.risk_level] += 1
        stats.action_decision_distribution[action_decision] += 1
        if action_decision in ("REVIEW", "BLOCK"):
            stats.review_candidate_events += 1
        if action_decision == "BLOCK":
            stats.blocked_candidate_events += 1
        if label.is_fraud:
            stats.fraud_by_risk_level[result.risk_level] += 1
            if predicted_positive:
                stats.true_positive += 1
            else:
                stats.false_negative += 1
        else:
            stats.non_fraud_by_risk_level[result.risk_level] += 1
            if predicted_positive:
                stats.false_positive += 1
            else:
                stats.true_negative += 1
        for rule_code in result.rule_codes:
            stats.rule_code_counts[rule_code] += 1
    return stats


def build_warnings(
    base_warnings: list[str],
    stats: EvaluationStats,
    replay_payload_rejected: int | None,
    rejected_event_ids: set[str],
    include_missing_results: bool,
) -> list[str]:
    warnings = list(base_warnings)
    if stats.true_positive + stats.false_positive == 0:
        warnings.append("No positive prediction was produced; precision is null.")
    if stats.evaluated_events == 0:
        warnings.append("No events were evaluated; denominator-based metrics are null.")
    if replay_payload_rejected is not None and replay_payload_rejected > len(rejected_event_ids):
        warnings.append(
            "Replay report payloadRejected is greater than available rejected eventIds; "
            "evaluation denominator may include replay-rejected events."
        )
    if stats.missing_results > 0 and include_missing_results:
        warnings.append(
            "Missing detection results are included in metrics; "
            "non-fraud missing results are counted as true negatives by policy."
        )
    elif stats.missing_results > 0:
        warnings.append("Missing detection results are excluded from denominator metrics by default.")
    if stats.total_results > 0 and stats.matched_results == 0:
        warnings.append("Detection results exist but do not match any label eventId. Check --event-id-prefix or result export source.")
    elif stats.unmatched_results > 0:
        warnings.append("Some detection results do not match any label eventId and were not used in metrics.")
    return warnings


def build_report(
    args: argparse.Namespace,
    labels_path: Path,
    results_path: Path,
    replay_report_path: Path | None,
    event_id_prefix: str | None,
    stats: EvaluationStats,
    replay_payload_rejected: int | None,
    rejected_event_ids: set[str],
    replay_report: dict[str, Any] | None,
    warnings: list[str],
    started_at: str,
    finished_at: str,
) -> dict[str, Any]:
    tp = stats.true_positive
    fp = stats.false_positive
    tn = stats.true_negative
    fn = stats.false_negative
    precision = safe_ratio(tp, tp + fp)
    recall = safe_ratio(tp, tp + fn)
    false_positive_rate = safe_ratio(fp, fp + tn)
    false_negative_rate = safe_ratio(fn, fn + tp)
    accuracy = safe_ratio(tp + tn, stats.evaluated_events)
    f1 = f1_score(precision, recall)
    misclassified_events = fp + fn
    unmatched_result_events = stats.unmatched_results
    failed_records = 0
    invalid_records = 0
    replay_native_distribution = replay_mapping_value(
        replay_report,
        "inputNativeTypeDistribution",
        replay_mapping_value(replay_report, "nativeTypeDistribution", {}),
    )
    replay_normalized_distribution = replay_mapping_value(
        replay_report,
        "inputNormalizedTypeDistribution",
        replay_mapping_value(replay_report, "normalizedTypeDistribution", {}),
    )
    replay_support_distribution = replay_mapping_value(
        replay_report,
        "inputTypeSupportLevelDistribution",
        replay_mapping_value(replay_report, "typeSupportLevelDistribution", {}),
    )
    replay_excluded_by_type = replay_mapping_value(replay_report, "excludedByType", {})
    threshold_policy = threshold_policy_for(args.threshold_version)
    review_candidate_rate = safe_ratio(stats.review_candidate_events, stats.evaluated_events)
    blocked_candidate_rate = safe_ratio(stats.blocked_candidate_events, stats.evaluated_events)
    return {
        "scriptVersion": SCRIPT_VERSION,
        "reportSchemaVersion": REPORT_SCHEMA_VERSION,
        "evaluationContractVersion": EVALUATION_CONTRACT_VERSION,
        "evaluationPolicyVersion": args.evaluation_policy_version,
        "ruleVersion": args.rule_version,
        "thresholdVersion": args.threshold_version,
        "thresholdPolicy": threshold_policy.as_dict(),
        "highRiskThreshold": threshold_policy.high_risk_threshold,
        "mediumRiskThreshold": threshold_policy.medium_risk_threshold,
        "decisionPolicy": {
            "reviewCandidate": threshold_policy.review_candidate_policy,
            "blockedCandidate": threshold_policy.blocked_candidate_policy,
        },
        "labelsPath": str(labels_path),
        "resultsPath": str(results_path),
        "replayReportPath": str(replay_report_path) if replay_report_path else None,
        "replayReportUsed": replay_report_path is not None,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "positiveRiskLevel": args.positive_risk_level,
        "eventIdPrefix": event_id_prefix,
        "excludeReplayRejected": args.exclude_replay_rejected,
        "includeMissingResults": args.include_missing_results,
        "missingResultTreatment": "fraud_missing_as_FN_non_fraud_missing_as_TN"
        if args.include_missing_results
        else "missing_results_excluded_from_denominator",
        "recordFailurePolicy": "fail_fast_before_report_generation",
        "pipelineFailureCountingPolicy": "fail_fast_before_report_generation",
        "invalidRecordCountingPolicy": "fail_fast_before_report_generation",
        "totalLabels": stats.total_labels,
        "totalFraudLabels": stats.total_fraud_labels,
        "totalNonFraudLabels": stats.total_non_fraud_labels,
        "totalResults": stats.total_results,
        "matchedResults": stats.matched_results,
        "unmatchedResults": stats.unmatched_results,
        "evaluatedEvents": stats.evaluated_events,
        "excludedReplayRejected": stats.excluded_replay_rejected,
        "replayPayloadRejected": replay_payload_rejected,
        "mappingPolicyVersion": replay_mapping_value(replay_report, "mappingPolicyVersion", None),
        "mappingPolicyVersions": replay_mapping_value(replay_report, "mappingPolicyVersions", {}),
        "mappingMetadataPolicy": replay_mapping_value(replay_report, "mappingMetadataPolicy", None),
        "replayMissingMappingMetadata": replay_mapping_value(replay_report, "missingMappingMetadata", 0),
        "typeDistributionScope": "split_replay_input_and_evaluation_denominator",
        "nativeTypeDistributionSource": "replay_report_input_scope",
        "normalizedTypeDistributionSource": "replay_report_input_scope",
        "typeSupportLevelDistributionSource": "replay_report_input_scope",
        "evaluationDenominatorTypeDistributionAvailable": True,
        "nativeTypeDistribution": replay_native_distribution,
        "normalizedTypeDistribution": replay_normalized_distribution,
        "typeSupportLevelDistribution": replay_support_distribution,
        "excludedByType": replay_excluded_by_type,
        "replayNativeTypeDistribution": replay_native_distribution,
        "replayNormalizedTypeDistribution": replay_normalized_distribution,
        "replayTypeSupportLevelDistribution": replay_support_distribution,
        "evaluatedNativeTypeDistribution": sorted_distribution(stats.evaluated_native_type_distribution),
        "evaluatedNormalizedTypeDistribution": sorted_distribution(stats.evaluated_normalized_type_distribution),
        "excludedNativeTypeDistribution": replay_excluded_by_type,
        "denominatorExcludedNativeTypeDistribution": sorted_distribution(stats.denominator_excluded_native_type_distribution),
        "replayRejectedEventIdsAvailable": len(rejected_event_ids),
        "replayRejectedExclusionComplete": None
        if replay_payload_rejected is None or not args.exclude_replay_rejected
        else replay_payload_rejected <= len(rejected_event_ids),
        "missingResults": stats.missing_results,
        "missingFraudLabels": stats.missing_fraud_labels,
        "missingNonFraudLabels": stats.missing_non_fraud_labels,
        "totalEvents": stats.evaluated_events,
        "fraudLabeledEvents": tp + fn,
        "evaluatedFraudLabeledEvents": tp + fn,
        "detectedFraudEvents": tp + fp,
        "missedFraudEvents": fn,
        "evaluatedMissedFraudEvents": fn,
        "falsePositiveEvents": fp,
        "truePositiveEvents": tp,
        "trueNegativeEvents": tn,
        "misclassifiedEvents": misclassified_events,
        "unmatchedResultEvents": unmatched_result_events,
        "reviewCandidateEvents": stats.review_candidate_events,
        "reviewCandidateRate": review_candidate_rate,
        "blockedCandidateEvents": stats.blocked_candidate_events,
        "blockedCandidateRate": blocked_candidate_rate,
        "actionDecisionDistribution": dict(sorted(stats.action_decision_distribution.items())),
        "operatorWorkloadSummary": {
            "reviewCandidateEvents": stats.review_candidate_events,
            "reviewCandidateRate": review_candidate_rate,
            "blockedCandidateEvents": stats.blocked_candidate_events,
            "blockedCandidateRate": blocked_candidate_rate,
            "workloadBudget": threshold_policy.operator_workload_budget,
            "interpretation": "candidate workload summary for evaluation evidence; not a production staffing guarantee",
        },
        "evaluationExcludedRecords": stats.excluded_replay_rejected,
        "failedRecords": failed_records,
        "invalidRecords": invalid_records,
        "confusionMatrix": {
            "truePositive": tp,
            "falsePositive": fp,
            "trueNegative": tn,
            "falseNegative": fn,
        },
        "metrics": {
            "precision": precision,
            "recall": recall,
            "f1Score": f1,
            "falsePositiveRate": false_positive_rate,
            "falseNegativeRate": false_negative_rate,
            "accuracy": accuracy,
        },
        "riskLevelCounts": sorted_counter(stats.risk_level_counts),
        "fraudByRiskLevel": sorted_counter(stats.fraud_by_risk_level),
        "nonFraudByRiskLevel": sorted_counter(stats.non_fraud_by_risk_level),
        "ruleCodeCounts": dict(sorted(stats.rule_code_counts.items())),
        "warnings": warnings,
        "sampleEventIds": stats.sample_event_ids,
    }


def validate_args(args: argparse.Namespace) -> None:
    if not args.labels.exists():
        raise FileNotFoundError(str(args.labels))
    if not args.results.exists():
        raise FileNotFoundError(str(args.results))
    if args.output.exists() and not args.force:
        raise EvaluationError(f"output file already exists. Use --force: {args.output}")
    try:
        threshold_policy_for(args.threshold_version)
    except ValueError as exc:
        raise EvaluationError(str(exc)) from exc
    if args.evaluation_policy_version != EVALUATION_POLICY_VERSION:
        raise EvaluationError(f"unsupported evaluationPolicyVersion: {args.evaluation_policy_version}")
    if not args.rule_version:
        raise EvaluationError("ruleVersion must not be blank")


def write_report(path: Path, report: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2, sort_keys=True) + "\n", encoding="utf-8")


def evaluate(args: argparse.Namespace) -> dict[str, Any]:
    validate_args(args)
    started_at = iso_now()
    replay_report = load_replay_report(args.replay_report)
    event_id_prefix = event_id_prefix_from(args, replay_report)
    labels, label_warnings = load_labels(args.labels, args.strict)
    results, result_warnings = load_results(args.results, event_id_prefix, args.strict)
    rejected_event_ids = replay_rejected_event_ids(replay_report, event_id_prefix) if args.exclude_replay_rejected else set()
    replay_payload_rejected = replay_payload_rejected_count(replay_report)
    threshold_policy = threshold_policy_for(args.threshold_version)
    stats = evaluate_rows(labels, results, rejected_event_ids, args.positive_risk_level, threshold_policy, args.include_missing_results)
    warnings = build_warnings(
        label_warnings + result_warnings,
        stats,
        replay_payload_rejected,
        rejected_event_ids,
        args.include_missing_results,
    )
    finished_at = iso_now()
    report = build_report(
        args,
        args.labels,
        args.results,
        args.replay_report if replay_report else None,
        event_id_prefix,
        stats,
        replay_payload_rejected,
        rejected_event_ids,
        replay_report,
        warnings,
        started_at,
        finished_at,
    )
    write_report(args.output, report)
    return report


def main() -> int:
    args = parse_args()
    try:
        report = evaluate(args)
    except FileNotFoundError as exc:
        print(f"FAIL: input file missing: {exc}", file=sys.stderr)
        return 2
    except EvaluationError as exc:
        print(f"FAIL: {exc}", file=sys.stderr)
        return 2
    matrix = report["confusionMatrix"]
    print("PASS: PaySim replay evaluation completed")
    print(
        f"labels={report['totalLabels']} results={report['totalResults']} evaluated={report['evaluatedEvents']} "
        f"excludedReplayRejected={report['excludedReplayRejected']} missingResults={report['missingResults']} "
        f"TP={matrix['truePositive']} FP={matrix['falsePositive']} TN={matrix['trueNegative']} FN={matrix['falseNegative']}"
    )
    print(f"report={args.output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
