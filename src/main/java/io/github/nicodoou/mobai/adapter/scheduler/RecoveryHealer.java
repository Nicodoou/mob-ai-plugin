package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;

/** Heals recovering members at the Regeneration I rhythm, without the potion (CT-07). */
public final class RecoveryHealer {
  private final RoleRegistry roles;
  private final HealSchedule schedule;

  public RecoveryHealer(RoleRegistry roles, HealSchedule schedule) {
    this.roles = Objects.requireNonNull(roles, "RecoveryHealer.roles");
    this.schedule = Objects.requireNonNull(schedule, "RecoveryHealer.schedule");
  }

  public void tick(long now) {
    for (MobId mob : schedule.due(roles.recoveringMobs(), now)) {
      heal(mob);
    }
  }

  // heal() never goes past the maximum health and fires EntityRegainHealthEvent.
  private void heal(MobId mob) {
    if (Bukkit.getEntity(mob.value()) instanceof Mob entity && entity.isValid()) {
      entity.heal(MinecraftConstants.REGENERATION_HEAL_POINTS);
    }
  }
}
