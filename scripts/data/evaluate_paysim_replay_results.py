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


SCRIPT_VERSION = "v2-phase-6"
DEFAULT_LABELS = Path("data/samples/paysim-labels-sample.jsonl")
DEFAULT_RESULTS = Path("data/processed/paysim-detection-results.jsonl")
DEFAULT_REPLAY_REPORT = Path("data/processed/paysim-replay-report.json")
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
    true_positive: int = 0
    false_positive: int = 0
    true_negative: int = 0
    false_negative: int = 0
    risk_level_counts: Counter[str] = field(default_factory=Counter)
    fraud_by_risk_level: Counter[str] = field(default_factory=Counter)
    non_fraud_by_risk_level: Counter[str] = field(default_factory=Counter)
    rule_code_counts: Counter[str] = field(default_factory=Counter)
    sample_event_ids: list[str] = field(default_factory=list)

    def add_sample_event_id(self, event_id: str) -> None:
        if len(self.sample_event_ids) < MAX_SAMPLE_EVENT_IDS:
            self.sample_event_ids.append(event_id)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Evaluate PaySim replay results against label sidecar.")
    parser.add_argument("--labels", type=Path, default=DEFAULT_LABELS)
    parser.add_argument("--results", type=Path, default=DEFAULT_RESULTS)
    parser.add_argument("--replay-report", type=Path, default=DEFAULT_REPLAY_REPORT)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--positive-risk-level", choices=RISK_LEVELS, default="MEDIUM")
    parser.add_argument("--event-id-prefix")
    parser.add_argument("--exclude-replay-rejected", dest="exclude_replay_rejected", action="store_true", default=True)
    parser.add_argument("--include-replay-rejected", dest="exclude_replay_rejected", action="store_false")
    parser.add_argument("--include-missing-results", dest="include_missing_results", action="store_true", default=True)
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
            rule_codes=extract_rule_codes(row),
        )
    return results, warnings


def load_replay_report(path: Path | None) -> dict[str, Any] | None:
    if path is None or not path.exists():
        return None
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


def is_predicted_positive(risk_level: str, positive_risk_level: str) -> bool:
    return RISK_RANK[risk_level] >= RISK_RANK[positive_risk_level]


def safe_ratio(numerator: int, denominator: int) -> float | None:
    if denominator == 0:
        return None
    return numerator / denominator


def sorted_counter(counter: Counter[str]) -> dict[str, int]:
    values = {key: counter.get(key, 0) for key in RISK_LEVELS}
    values.update({key: counter[key] for key in sorted(counter) if key not in RISK_RANK})
    return values


def evaluate_rows(
    labels: dict[str, LabelRow],
    results: dict[str, DetectionResult],
    rejected_event_ids: set[str],
    positive_risk_level: str,
    include_missing_results: bool,
) -> EvaluationStats:
    label_ids = set(labels)
    result_ids = set(results)
    stats = EvaluationStats(
        total_labels=len(labels),
        total_results=len(results),
        matched_results=len(label_ids & result_ids),
        unmatched_results=len(result_ids - label_ids),
    )
    for event_id in sorted(labels):
        label = labels[event_id]
        if event_id in rejected_event_ids:
            stats.excluded_replay_rejected += 1
            continue
        result = results.get(event_id)
        if result is None:
            stats.missing_results += 1
            if not include_missing_results:
                continue
            stats.evaluated_events += 1
            stats.add_sample_event_id(event_id)
            if label.is_fraud:
                stats.false_negative += 1
            else:
                stats.true_negative += 1
            continue

        predicted_positive = is_predicted_positive(result.risk_level, positive_risk_level)
        stats.evaluated_events += 1
        stats.add_sample_event_id(event_id)
        stats.risk_level_counts[result.risk_level] += 1
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
    if stats.missing_results > 0:
        warnings.append(
            "Missing detection results are included in metrics; "
            "non-fraud missing results are counted as true negatives by policy."
        )
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
    warnings: list[str],
    started_at: str,
    finished_at: str,
) -> dict[str, Any]:
    tp = stats.true_positive
    fp = stats.false_positive
    tn = stats.true_negative
    fn = stats.false_negative
    return {
        "scriptVersion": SCRIPT_VERSION,
        "labelsPath": str(labels_path),
        "resultsPath": str(results_path),
        "replayReportPath": str(replay_report_path) if replay_report_path else None,
        "startedAt": started_at,
        "finishedAt": finished_at,
        "positiveRiskLevel": args.positive_risk_level,
        "eventIdPrefix": event_id_prefix,
        "excludeReplayRejected": args.exclude_replay_rejected,
        "includeMissingResults": args.include_missing_results,
        "missingResultTreatment": "fraud_missing_as_FN_non_fraud_missing_as_TN"
        if args.include_missing_results
        else "missing_results_excluded_from_denominator",
        "totalLabels": stats.total_labels,
        "totalResults": stats.total_results,
        "matchedResults": stats.matched_results,
        "unmatchedResults": stats.unmatched_results,
        "evaluatedEvents": stats.evaluated_events,
        "excludedReplayRejected": stats.excluded_replay_rejected,
        "replayPayloadRejected": replay_payload_rejected,
        "replayRejectedEventIdsAvailable": len(rejected_event_ids),
        "replayRejectedExclusionComplete": None
        if replay_payload_rejected is None or not args.exclude_replay_rejected
        else replay_payload_rejected <= len(rejected_event_ids),
        "missingResults": stats.missing_results,
        "confusionMatrix": {
            "truePositive": tp,
            "falsePositive": fp,
            "trueNegative": tn,
            "falseNegative": fn,
        },
        "metrics": {
            "precision": safe_ratio(tp, tp + fp),
            "recall": safe_ratio(tp, tp + fn),
            "falsePositiveRate": safe_ratio(fp, fp + tn),
            "falseNegativeRate": safe_ratio(fn, fn + tp),
            "accuracy": safe_ratio(tp + tn, stats.evaluated_events),
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
    stats = evaluate_rows(labels, results, rejected_event_ids, args.positive_risk_level, args.include_missing_results)
    warnings = build_warnings(label_warnings + result_warnings, stats, replay_payload_rejected, rejected_event_ids)
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
