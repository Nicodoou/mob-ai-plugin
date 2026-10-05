package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.decision.DecisionTrace;
import io.github.nicodoou.mobai.domain.decision.StrategyCheck;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.target.TargetSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The decision trace while the brain fills it in, step by step. */
final class TraceDraft {
  private final GroupId group;
  private final long tick;
  private final GroupState stateBefore;
  private Optional<PlanId> plan = Optional.empty();
  private Optional<TargetSelection> targetSelection = Optional.empty();
  private List<StrategyCheck> strategyChecks = List.of();
  private Optional<SelectionResult<StrategyId>> strategySelection = Optional.empty();
  private final List<MobId> newlyRetreating = new ArrayList<>();
  private final List<MobId> returningFromRetreat = new ArrayList<>();
  private Optional<PlanEndReason> endReason = Optional.empty();
  private Optional<RegroupEndReason> regroupEnd = Optional.empty();
  private final List<AttackChoice> attackChoices = new ArrayList<>();

  TraceDraft(GroupId group, long tick, GroupState stateBefore) {
    this.group = group;
    this.tick = tick;
    this.stateBefore = stateBefore;
  }

  void plan(PlanId value) {
    plan = Optional.of(value);
  }

  void targetSelection(TargetSelection value) {
    targetSelection = Optional.of(value);
  }

  void strategyChecks(List<StrategyCheck> value) {
    strategyChecks = List.copyOf(value);
  }

  void strategySelection(SelectionResult<StrategyId> value) {
    strategySelection = Optional.of(value);
  }

  void endReason(PlanEndReason value) {
    endReason = Optional.of(value);
  }

  void regroupEnd(RegroupEndReason value) {
    regroupEnd = Optional.of(value);
  }

  void addNewlyRetreating(MobId mob) {
    newlyRetreating.add(mob);
  }

  void addReturningFromRetreat(MobId mob) {
    returningFromRetreat.add(mob);
  }

  void addAttackChoice(AttackChoice choice) {
    attackChoices.add(choice);
  }

  DecisionTrace build(GroupState stateAfter) {
    return new DecisionTrace(
        group,
        tick,
        stateBefore,
        stateAfter,
        plan,
        targetSelection,
        strategyChecks,
        strategySelection,
        newlyRetreating,
        returningFromRetreat,
        endReason,
        regroupEnd,
        attackChoices);
  }
}
