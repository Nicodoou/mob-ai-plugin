package io.github.nicodoou.mobai.domain.brain;

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
    return mob.health() <= plan.get().retreatHealthFraction() * mob.maxHealth();
  }

  // CT-13: a group in this state would close any new plan at once with GROUP_RETREATED.
  public boolean isGroupRetreated(GroupSnapshot snapshot) {
    long retreating = snapshot.mobs().stream().filter(this::shouldRetreat).count();
    return 2 * retreating > snapshot.mobs().size();
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
