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
  private final double danger;

  public PlanScoring(PlanSettings plan, SuccessSettings success, double danger) {
    this.plan = Objects.requireNonNull(plan, "PlanScoring.plan");
    this.success = Objects.requireNonNull(success, "PlanScoring.success");
    if (!(danger >= 0 && danger <= 1)) {
      throw new IllegalArgumentException(
          "PlanScoring.danger must be between 0.0 and 1.0, got " + danger);
    }
    this.danger = danger;
  }

  public PlanScores scoresOf(Plan plan, PlanEndReason reason, long endTick) {
    Objects.requireNonNull(plan, "PlanScoring.plan");
    Objects.requireNonNull(reason, "PlanScoring.reason");
    return new PlanScores(
        damageScore(plan, reason), speedScore(plan, reason, endTick), survivalScore(plan));
  }

  // CT-27, D-01: survival scales the attack instead of adding to it, so a plan that never fights
  // scores nothing however safe it kept the group.
  public double successOf(PlanScores scores) {
    SuccessWeights weights = SuccessWeights.forDanger(success, danger);
    double kept = 1 - weights.survival() + weights.survival() * scores.survival();
    return Math.min(1, attackOf(scores, weights) * kept);
  }

  private static double attackOf(PlanScores scores, SuccessWeights weights) {
    double attackWeight = weights.damage() + weights.speed();
    if (attackWeight == 0) {
      return 1;
    }
    return (weights.damage() * scores.damage() + weights.speed() * scores.speed()) / attackWeight;
  }

  public double danger() {
    return danger;
  }

  // Healing during the plan offsets the loss, but never below zero.
  public double healthLostOf(Plan closing) {
    Objects.requireNonNull(closing, "PlanScoring.plan");
    double lost = 0;
    for (Map.Entry<MobId, Double> start : closing.startingHealth().entrySet()) {
      lost += start.getValue() - healthAtClose(closing, start.getKey());
    }
    return Math.max(0, lost);
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

  private double survivalScore(Plan closing) {
    return SURVIVAL_PART_WEIGHT * aliveShare(closing)
        + SURVIVAL_PART_WEIGHT * healthKeptShare(closing);
  }

  private static double aliveShare(Plan closing) {
    // No plan opens without mobs today; a plan with none lost nobody.
    if (closing.startingMembers() == 0) {
      return 1;
    }
    long alive =
        closing.startingRoles().keySet().stream().filter(closing.roles()::containsKey).count();
    return (double) alive / closing.startingMembers();
  }

  private double healthKeptShare(Plan closing) {
    double initial = startingGroupHealth(closing);
    if (initial <= 0) {
      return 1;
    }
    return 1 - Math.min(1, healthLostOf(closing) / initial);
  }

  private static double startingGroupHealth(Plan closing) {
    return closing.startingHealth().values().stream().mapToDouble(Double::doubleValue).sum();
  }

  // A mob that left the group (died or vanished) lost all its health.
  private static double healthAtClose(Plan closing, MobId mob) {
    if (!closing.roles().containsKey(mob)) {
      return 0;
    }
    return closing.lastSeenHealth().getOrDefault(mob, 0.0);
  }
}
