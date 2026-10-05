package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.brain.RegroupEndReason;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.target.TargetSelection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Why the brain decided what it decided; returned as data, never logged by the domain. */
public record DecisionTrace(
    GroupId group,
    long tick,
    GroupState stateBefore,
    GroupState stateAfter,
    Optional<PlanId> plan,
    Optional<TargetSelection> targetSelection,
    List<StrategyCheck> strategyChecks,
    Optional<SelectionResult<StrategyId>> strategySelection,
    List<MobId> newlyRetreating,
    List<MobId> returningFromRetreat,
    Optional<PlanEndReason> endReason,
    Optional<RegroupEndReason> regroupEnd,
    List<AttackChoice> attackChoices) {
  public DecisionTrace {
    Objects.requireNonNull(group, "DecisionTrace.group");
    Objects.requireNonNull(stateBefore, "DecisionTrace.stateBefore");
    Objects.requireNonNull(stateAfter, "DecisionTrace.stateAfter");
    Objects.requireNonNull(plan, "DecisionTrace.plan");
    Objects.requireNonNull(targetSelection, "DecisionTrace.targetSelection");
    Objects.requireNonNull(strategyChecks, "DecisionTrace.strategyChecks");
    Objects.requireNonNull(strategySelection, "DecisionTrace.strategySelection");
    Objects.requireNonNull(newlyRetreating, "DecisionTrace.newlyRetreating");
    Objects.requireNonNull(returningFromRetreat, "DecisionTrace.returningFromRetreat");
    Objects.requireNonNull(endReason, "DecisionTrace.endReason");
    Objects.requireNonNull(regroupEnd, "DecisionTrace.regroupEnd");
    Objects.requireNonNull(attackChoices, "DecisionTrace.attackChoices");
    if (tick < 0) {
      throw new IllegalArgumentException(
          "DecisionTrace.tick must be zero or positive, got " + tick);
    }
    strategyChecks = List.copyOf(strategyChecks);
    newlyRetreating = List.copyOf(newlyRetreating);
    returningFromRetreat = List.copyOf(returningFromRetreat);
    attackChoices = List.copyOf(attackChoices);
  }
}
