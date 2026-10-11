package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Objects;

/** How much of its fighting health a group keeps, counting the dead as lost (CT-31). */
public final class GroupStrength {
  // A plan that never saw its mobs' health has no evidence of losses.
  private static final double FULL_STRENGTH = 1;
  private static final double NO_STRENGTH = 0;

  private GroupStrength() {}

  public static double ofPlan(Plan plan) {
    Objects.requireNonNull(plan, "GroupStrength.plan");
    double starting = startingHealthOf(plan);
    if (starting <= 0) {
      return FULL_STRENGTH;
    }
    return currentHealthOf(plan) / starting;
  }

  public static double ofSnapshot(GroupSnapshot snapshot, double lastPlanLostHealth) {
    Objects.requireNonNull(snapshot, "GroupStrength.snapshot");
    requireLostHealth(lastPlanLostHealth);
    if (snapshot.mobs().isEmpty()) {
      return NO_STRENGTH;
    }
    return livingHealthOf(snapshot) / (livingMaxHealthOf(snapshot) + lastPlanLostHealth);
  }

  /** The starting health of the mobs the plan started with and no longer has. */
  public static double lostHealthOf(Plan plan) {
    Objects.requireNonNull(plan, "GroupStrength.plan");
    return plan.startingRoles().keySet().stream()
        .filter(mob -> !plan.roles().containsKey(mob))
        .mapToDouble(mob -> plan.startingHealth().getOrDefault(mob, 0.0))
        .sum();
  }

  static double startingHealthOf(Plan plan) {
    return plan.startingHealth().values().stream().mapToDouble(Double::doubleValue).sum();
  }

  // A mob that left the plan (died or vanished) counts with no health.
  static double currentHealthOf(Plan plan) {
    return plan.startingRoles().keySet().stream().mapToDouble(mob -> healthInPlan(plan, mob)).sum();
  }

  private static double healthInPlan(Plan plan, MobId mob) {
    if (!plan.roles().containsKey(mob)) {
      return 0;
    }
    return plan.lastSeenHealth().getOrDefault(mob, 0.0);
  }

  private static double livingHealthOf(GroupSnapshot snapshot) {
    return snapshot.mobs().stream().mapToDouble(MobSnapshot::health).sum();
  }

  private static double livingMaxHealthOf(GroupSnapshot snapshot) {
    return snapshot.mobs().stream().mapToDouble(MobSnapshot::maxHealth).sum();
  }

  private static void requireLostHealth(double lostHealth) {
    if (!(lostHealth >= 0) || !Double.isFinite(lostHealth)) {
      throw new IllegalArgumentException(
          "GroupStrength.lastPlanLostHealth must be zero or positive, got " + lostHealth);
    }
  }
}
