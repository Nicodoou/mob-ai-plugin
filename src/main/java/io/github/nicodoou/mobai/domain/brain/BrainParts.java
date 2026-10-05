package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.selection.SelectionPolicyFactory;
import io.github.nicodoou.mobai.domain.strategy.StrategyCatalog;
import io.github.nicodoou.mobai.domain.target.SpiderTargetRule;
import io.github.nicodoou.mobai.domain.target.TargetSelector;
import java.util.Objects;

/** The rule objects the brain coordinates; built once at startup. */
public record BrainParts(
    TargetSelector targetSelector,
    SpiderTargetRule spiderTargetRule,
    StrategyCatalog strategies,
    SelectionPolicyFactory policies,
    AttackSuggester attackSuggester,
    PlanEndDetector planEndDetector,
    RetreatRule retreatRule,
    RegroupRule regroupRule,
    RegroupWindow regroupWindow) {
  public BrainParts {
    Objects.requireNonNull(targetSelector, "BrainParts.targetSelector");
    Objects.requireNonNull(spiderTargetRule, "BrainParts.spiderTargetRule");
    Objects.requireNonNull(strategies, "BrainParts.strategies");
    Objects.requireNonNull(policies, "BrainParts.policies");
    Objects.requireNonNull(attackSuggester, "BrainParts.attackSuggester");
    Objects.requireNonNull(planEndDetector, "BrainParts.planEndDetector");
    Objects.requireNonNull(retreatRule, "BrainParts.retreatRule");
    Objects.requireNonNull(regroupRule, "BrainParts.regroupRule");
    Objects.requireNonNull(regroupWindow, "BrainParts.regroupWindow");
  }
}
