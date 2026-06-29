"""PaySim native transaction type mapping policy for replay/evaluation."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any


MAPPING_POLICY_VERSION = "paysim-native-mapping-v1"


@dataclass(frozen=True)
class TypeMapping:
    native_type: str
    normalized_type: str | None
    support_level: str
    rule_interpretation: str
    evaluation_denominator: str
    excluded_reason: str | None
    semantic_loss_risk: str
    next_action: str

    def as_dict(self) -> dict[str, Any]:
        return {
            "nativeType": self.native_type,
            "normalizedType": self.normalized_type,
            "supportLevel": self.support_level,
            "ruleInterpretation": self.rule_interpretation,
            "evaluationDenominator": self.evaluation_denominator,
            "excludedReason": self.excluded_reason,
            "semanticLossRisk": self.semantic_loss_risk,
            "nextAction": self.next_action,
        }


TYPE_MAPPINGS: dict[str, TypeMapping] = {
    "PAYMENT": TypeMapping(
        native_type="PAYMENT",
        normalized_type="PAYMENT",
        support_level="production-supported",
        rule_interpretation="normal payment behavior",
        evaluation_denominator="included",
        excluded_reason=None,
        semantic_loss_risk="low",
        next_action="keep",
    ),
    "TRANSFER": TypeMapping(
        native_type="TRANSFER",
        normalized_type="TRANSFER",
        support_level="production-supported",
        rule_interpretation="transfer risk behavior",
        evaluation_denominator="included",
        excluded_reason=None,
        semantic_loss_risk="low",
        next_action="keep",
    ),
    "CASH_OUT": TypeMapping(
        native_type="CASH_OUT",
        normalized_type="WITHDRAWAL",
        support_level="replay-supported",
        rule_interpretation="cash-out mapped to withdrawal-like outflow for replay",
        evaluation_denominator="included",
        excluded_reason=None,
        semantic_loss_risk="high",
        next_action="review Rule V2 semantics before production use",
    ),
    "CASH_IN": TypeMapping(
        native_type="CASH_IN",
        normalized_type="DEPOSIT",
        support_level="replay-supported",
        rule_interpretation="cash-in mapped to deposit-like inflow for replay",
        evaluation_denominator="included",
        excluded_reason=None,
        semantic_loss_risk="medium",
        next_action="review false positive impact before production use",
    ),
    "DEBIT": TypeMapping(
        native_type="DEBIT",
        normalized_type=None,
        support_level="unsupported",
        rule_interpretation="not interpreted by current transaction contract",
        evaluation_denominator="excluded",
        excluded_reason="UNSUPPORTED_NATIVE_TYPE:DEBIT",
        semantic_loss_risk="high",
        next_action="define separate debit semantics before inclusion",
    ),
}


def mapping_for(native_type: str) -> TypeMapping:
    mapping = TYPE_MAPPINGS.get(native_type)
    if mapping is None:
        return TypeMapping(
            native_type=native_type,
            normalized_type=None,
            support_level="unsupported",
            rule_interpretation="unknown PaySim native type",
            evaluation_denominator="excluded",
            excluded_reason=f"UNSUPPORTED_NATIVE_TYPE:{native_type}",
            semantic_loss_risk="unknown",
            next_action="add explicit mapping before replay inclusion",
        )
    return mapping


def mapping_policy_table() -> list[dict[str, Any]]:
    return [TYPE_MAPPINGS[key].as_dict() for key in sorted(TYPE_MAPPINGS)]
