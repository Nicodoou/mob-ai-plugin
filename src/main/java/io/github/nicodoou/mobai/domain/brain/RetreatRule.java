package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.group.GroupStrength;
import io.github.nicodoou.mobai.domain.settings.PlanSettings;
import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Objects;
import java.util.function.Supplier;

public final class RetreatRule {
  private final Supplier<PlanSettings> plan;
  private final Supplier<RetreatSettings> retreat;

  public RetreatRule(Supplier<PlanSettings> plan, Supplier<RetreatSettings> retreat) {
    this.plan = Objects.requireNonNull(plan, "RetreatRule.plan");
    this.retreat = Objects.requireNonNull(retreat, "RetreatRule.retreat");
  }

  public boolean shouldRetreat(MobSnapshot mob) {
    return shouldRetreatAt(mob, plan.get().retreatHealthFraction());
  }

  public boolean shouldRetreatAt(MobSnapshot mob, double healthFraction) {
    return mob.health() <= healthFraction * mob.maxHealth();
  }

  // CT-13, CT-31: the last plan's dead still count, so a lone healthy survivor stays retreated
  // until reinforcements make up for them.
  public boolean isGroupRetreated(GroupSnapshot snapshot, double lastPlanLostHealth) {
    return GroupStrength.ofSnapshot(snapshot, lastPlanLostHealth)
        < plan.get().groupRetreatStrength();
  }

  public boolean shouldReturn(MobSnapshot mob) {
    return mob.health() >= retreat.get().recoveryHealthFraction() * mob.maxHealth();
  }

  public boolean canRecover(MobSnapshot mob, GroupSnapshot snapshot) {
    double safe = retreat.get().healSafeDistanceBlocks();
    return snapshot.players().stream()
        .filter(player -> player.health() > 0)
        .noneMatch(player -> player.pose().position().distanceTo(mob.position()) < safe);
  }
}
