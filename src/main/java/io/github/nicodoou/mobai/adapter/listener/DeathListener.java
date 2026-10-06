package io.github.nicodoou.mobai.adapter.listener;

import io.github.nicodoou.mobai.application.RecordPlayerDeath;
import io.github.nicodoou.mobai.application.RemovalCause;
import io.github.nicodoou.mobai.application.RemoveMember;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityRemoveEvent;

/** Members leave their group when they die or are removed; a dying target closes its plans. */
public final class DeathListener implements Listener {
  private final RemoveMember removeMember;
  private final RecordPlayerDeath recordPlayerDeath;
  private final ServerClock clock;

  public DeathListener(
      RemoveMember removeMember, RecordPlayerDeath recordPlayerDeath, ServerClock clock) {
    this.removeMember = Objects.requireNonNull(removeMember, "DeathListener.removeMember");
    this.recordPlayerDeath =
        Objects.requireNonNull(recordPlayerDeath, "DeathListener.recordPlayerDeath");
    this.clock = Objects.requireNonNull(clock, "DeathListener.clock");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    long tick = clock.currentTick();
    if (event.getEntity() instanceof Player player) {
      recordPlayerDeath.execute(new PlayerId(player.getUniqueId()), tick);
      return;
    }
    removeMember.execute(new MobId(event.getEntity().getUniqueId()), RemovalCause.DIED, tick);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemove(EntityRemoveEvent event) {
    if (isMemberExit(event.getCause())) {
      removeMember.execute(
          new MobId(event.getEntity().getUniqueId()), RemovalCause.DESPAWNED, clock.currentTick());
    }
  }

  // DEATH is handled by onDeath; UNLOAD keeps the mob in its group.
  private static boolean isMemberExit(EntityRemoveEvent.Cause cause) {
    return cause != EntityRemoveEvent.Cause.DEATH && cause != EntityRemoveEvent.Cause.UNLOAD;
  }
}
