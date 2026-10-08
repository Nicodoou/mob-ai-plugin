package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Objects;

public record ClosedPlan(
    PlanId id,
    StrategyId strategy,
    PlayerId target,
    PlanEndReason reason,
    double success,
    PlanScores scores,
    double danger,
    double groupHealthLost,
    double damageDealt,
    long startTick,
    long endTick) {

  public ClosedPlan {
    Objects.requireNonNull(id, "ClosedPlan.id");
    Objects.requireNonNull(strategy, "ClosedPlan.strategy");
    Objects.requireNonNull(target, "ClosedPlan.target");
    Objects.requireNonNull(reason, "ClosedPlan.reason");
    Objects.requireNonNull(scores, "ClosedPlan.scores");
    if (!(success >= 0 && success <= 1)) {
      throw new IllegalArgumentException(
          "ClosedPlan.success must be between 0.0 and 1.0, got " + success);
    }
    if (!(danger >= 0 && danger <= 1)) {
      throw new IllegalArgumentException(
          "ClosedPlan.danger must be between 0.0 and 1.0, got " + danger);
    }
    if (!(groupHealthLost >= 0) || !Double.isFinite(groupHealthLost)) {
      throw new IllegalArgumentException(
          "ClosedPlan.groupHealthLost must be zero or positive, got " + groupHealthLost);
    }
    if (endTick < startTick) {
      throw new IllegalArgumentException(
          "ClosedPlan.endTick must not be before startTick, got " + endTick + " < " + startTick);
    }
  }
}
