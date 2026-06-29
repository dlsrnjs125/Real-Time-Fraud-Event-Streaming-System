"""PaySim replay evaluation policy/version metadata for Phase 9+ contracts."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


EVALUATION_CONTRACT_VERSION = "v2-phase9-evaluation-contract-v1"
EVALUATION_POLICY_VERSION = "evaluation-policy-v1"
RULE_VERSION = "rule-v2-baseline-v1"
RULE_VERSIONS = {RULE_VERSION}
DEFAULT_THRESHOLD_VERSION = "threshold-v1"


@dataclass(frozen=True)
class ThresholdPolicy:
    threshold_version: str
    medium_risk_threshold: int
    high_risk_threshold: int
    positive_risk_level_fallback: str
    review_risk_levels_fallback: tuple[str, ...]
    block_risk_levels_fallback: tuple[str, ...]
    fraud_prediction_policy: str
    review_candidate_policy: str
    blocked_candidate_policy: str
    operator_workload_budget: dict[str, float]

    def as_dict(self) -> dict[str, Any]:
        return {
            "thresholdVersion": self.threshold_version,
            "mediumRiskThreshold": self.medium_risk_threshold,
            "highRiskThreshold": self.high_risk_threshold,
            "positiveRiskLevelFallback": self.positive_risk_level_fallback,
            "reviewRiskLevelsFallback": list(self.review_risk_levels_fallback),
            "blockRiskLevelsFallback": list(self.block_risk_levels_fallback),
            "fraudPredictionPolicy": self.fraud_prediction_policy,
            "reviewCandidatePolicy": self.review_candidate_policy,
            "blockedCandidatePolicy": self.blocked_candidate_policy,
            "operatorWorkloadBudget": self.operator_workload_budget,
        }


THRESHOLD_POLICIES: dict[str, ThresholdPolicy] = {
    "threshold-v1": ThresholdPolicy(
        threshold_version="threshold-v1",
        medium_risk_threshold=50,
        high_risk_threshold=80,
        positive_risk_level_fallback="MEDIUM",
        review_risk_levels_fallback=("MEDIUM", "HIGH", "CRITICAL"),
        block_risk_levels_fallback=("HIGH", "CRITICAL"),
        fraud_prediction_policy="riskScore >= mediumRiskThreshold when riskScore is present; otherwise riskLevel >= MEDIUM",
        review_candidate_policy="riskScore >= mediumRiskThreshold or riskLevel in MEDIUM/HIGH/CRITICAL",
        blocked_candidate_policy="riskScore >= highRiskThreshold or riskLevel in HIGH/CRITICAL",
        operator_workload_budget={
            "maxReviewCandidateRateCandidate": 0.30,
            "maxBlockedCandidateRateCandidate": 0.10,
        },
    ),
    "threshold-strict-v1": ThresholdPolicy(
        threshold_version="threshold-strict-v1",
        medium_risk_threshold=70,
        high_risk_threshold=90,
        positive_risk_level_fallback="HIGH",
        review_risk_levels_fallback=("HIGH", "CRITICAL"),
        block_risk_levels_fallback=("CRITICAL",),
        fraud_prediction_policy="riskScore >= mediumRiskThreshold when riskScore is present; otherwise riskLevel >= HIGH",
        review_candidate_policy="riskScore >= mediumRiskThreshold or riskLevel in HIGH/CRITICAL",
        blocked_candidate_policy="riskScore >= highRiskThreshold or riskLevel in CRITICAL",
        operator_workload_budget={
            "maxReviewCandidateRateCandidate": 0.20,
            "maxBlockedCandidateRateCandidate": 0.05,
        },
    ),
}


def threshold_policy_for(threshold_version: str) -> ThresholdPolicy:
    try:
        return THRESHOLD_POLICIES[threshold_version]
    except KeyError as exc:
        raise ValueError(f"unsupported thresholdVersion: {threshold_version}") from exc


def validate_rule_version(rule_version: str) -> None:
    if rule_version not in RULE_VERSIONS:
        raise ValueError(f"unsupported ruleVersion: {rule_version}")
