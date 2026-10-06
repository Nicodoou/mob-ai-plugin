package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.application.DamageTaken;
import io.github.nicodoou.mobai.application.RecordDamageTaken;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/** Turns damage that players deal to our mobs into threat. */
public final class ThreatListener implements Listener {
  private final RecordDamageTaken recordDamageTaken;
  private final ServerClock clock;

  public ThreatListener(RecordDamageTaken recordDamageTaken, ServerClock clock) {
    this.recordDamageTaken =
        Objects.requireNonNull(recordDamageTaken, "ThreatListener.recordDamageTaken");
    this.clock = Objects.requireNonNull(clock, "ThreatListener.clock");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Mob mob) || event.getFinalDamage() <= 0) {
      return;
    }
    attackingPlayer(event.getDamager())
        .ifPresent(player -> recordDamageTaken.execute(damageTaken(event, mob, player)));
  }

  private static Optional<Player> attackingPlayer(Entity damager) {
    if (damager instanceof Player player) {
      return Optional.of(player);
    }
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Player shooter) {
      return Optional.of(shooter);
    }
    return Optional.empty();
  }

  private DamageTaken damageTaken(EntityDamageByEntityEvent event, Mob mob, Player player) {
    return new DamageTaken(
        new MobId(mob.getUniqueId()),
        new PlayerId(player.getUniqueId()),
        event.getFinalDamage(),
        clock.currentTick());
  }
}
