package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.memory.DangerObservation;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import java.util.Objects;
import java.util.Optional;

public final class ClosePlan {
  // The group's own plan counts in full; observers (phase 2) will count less.
  private static final double OWN_PLAN_WEIGHT = 1.0;

  private final ActiveGroups activeGroups;

  public ClosePlan(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "ClosePlan.activeGroups");
  }

  public Optional<RecordChange> execute(PlanClosed event) {
    return activeGroups.group(event.groupId()).map(group -> record(group, event.plan()));
  }

  private static RecordChange record(Group group, ClosedPlan plan) {
    RecordChange change =
        group
            .memory()
            .recordStrategy(
                new StrategyObservation(
                    plan.target(),
                    plan.strategy(),
                    plan.success(),
                    OWN_PLAN_WEIGHT,
                    plan.endTick()));
    group
        .memory()
        .recordDanger(
            new DangerObservation(
                plan.target(), plan.groupHealthLost(), plan.damageDealt(), plan.endTick()));
    return change;
  }
}
