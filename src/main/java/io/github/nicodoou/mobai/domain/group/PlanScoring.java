package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.settings.PlanSettings;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Map;
import java.util.Objects;

/** Scores a plan at its close (CT-27). */
public final class PlanScoring {
  // Allies still alive and health kept weigh the same in survival.
  private static final double SURVIVAL_PART_WEIGHT = 0.5;
  // A plan closed in the tick it opened still took time; this avoids dividing by zero.
  private static final long MIN_DURATION_TICKS = 1;

  private final PlanSettings plan;
  private final SuccessSettings success;

  public PlanScoring(PlanSettings plan, SuccessSettings success) {
    this.plan = Objects.requireNonNull(plan, "PlanScoring.plan");
    this.success = Objects.requireNonNull(success, "PlanScoring.success");
  }

  public PlanScores scoresOf(Plan plan, PlanEndReason reason, long endTick) {
    Objects.requireNonNull(plan, "PlanScoring.plan");
    Objects.requireNonNull(reason, "PlanScoring.reason");
    return new PlanScores(
        damageScore(plan, reason), speedScore(plan, reason, endTick), survivalScore(plan));
  }

  public double successOf(PlanScores scores) {
    double weighted =
        success.damageWeight() * scores.damage()
            + success.speedWeight() * scores.speed()
            + success.survivalWeight() * scores.survival();
    return Math.min(1, weighted);
  }

  private double damageScore(Plan closing, PlanEndReason reason) {
    if (reason == PlanEndReason.TARGET_DIED) {
      return 1;
    }
    double fullSuccessDamage = this.plan.fullSuccessDamageFraction() * closing.targetMaxHealth();
    return Math.min(1, closing.damageDealt() / fullSuccessDamage);
  }

  private double speedScore(Plan closing, PlanEndReason reason, long endTick) {
    double share = healthShareTaken(closing, reason);
    long durationTicks = Math.max(MIN_DURATION_TICKS, endTick - closing.startTick());
    return Math.min(1, share * success.referenceKillTicks() / durationTicks);
  }

  private static double healthShareTaken(Plan closing, PlanEndReason reason) {
    if (reason == PlanEndReason.TARGET_DIED) {
      return 1;
    }
    return Math.min(1, closing.damageDealt() / closing.targetMaxHealth());
  }

  private static double survivalScore(Plan closing) {
    return SURVIVAL_PART_WEIGHT * aliveShare(closing)
        + SURVIVAL_PART_WEIGHT * healthKeptShare(closing);
  }

  private static double aliveShare(Plan closing) {
    long alive =
        closing.startingRoles().keySet().stream().filter(closing.roles()::containsKey).count();
    return (double) alive / closing.startingMembers();
  }

  private static double healthKeptShare(Plan closing) {
    double initial = startingGroupHealth(closing);
    if (initial <= 0) {
      return 1;
    }
    return 1 - Math.min(1, netHealthLost(closing) / initial);
  }

  private static double startingGroupHealth(Plan closing) {
    return closing.startingHealth().values().stream().mapToDouble(Double::doubleValue).sum();
  }

  // Healing during the plan offsets the loss, but never below zero.
  private static double netHealthLost(Plan closing) {
    double lost = 0;
    for (Map.Entry<MobId, Double> start : closing.startingHealth().entrySet()) {
      lost += start.getValue() - healthAtClose(closing, start.getKey());
    }
    return Math.max(0, lost);
  }

  // A mob that left the group (died or vanished) lost all its health.
  private static double healthAtClose(Plan closing, MobId mob) {
    if (!closing.roles().containsKey(mob)) {
      return 0;
    }
    return closing.lastSeenHealth().getOrDefault(mob, 0.0);
  }
}
