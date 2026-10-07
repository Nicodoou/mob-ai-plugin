package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.debug.TraceHub;
import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.tracker.ProjectileClosure;
import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Closes the arrows that landed or ran out of time, once per tick, and traces each one. */
public final class ProjectileResolver {
  private final AttackTracker tracker;
  private final TraceHub hub;
  private final Supplier<AttackSettings> settings;

  public ProjectileResolver(
      AttackTracker tracker, TraceHub hub, Supplier<AttackSettings> settings) {
    this.tracker = Objects.requireNonNull(tracker, "ProjectileResolver.tracker");
    this.hub = Objects.requireNonNull(hub, "ProjectileResolver.hub");
    this.settings = Objects.requireNonNull(settings, "ProjectileResolver.settings");
  }

  public void tick(long now) {
    for (ProjectileClosure closure :
        tracker.closeProjectiles(now, settings.get().projectileTimeoutTicks(), this::isValid)) {
      hub.attacked(closure.mob(), now, closure.classification());
    }
  }

  // The shooter may have died while its arrow flew: then only the player is checked.
  private boolean isValid(MobId mob, PlayerId target) {
    Player player = Bukkit.getPlayer(target.value());
    if (player == null) {
      return false;
    }
    if (Bukkit.getEntity(mob.value()) instanceof Mob shooter) {
      return TargetChecks.isValidTarget(player, shooter);
    }
    return player.isOnline() && !player.isDead();
  }
}
