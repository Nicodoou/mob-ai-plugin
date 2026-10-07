package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.snapshot.MovementTracker;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Feeds the movement tracker every tick and forgets players who leave. */
public final class MovementSampler implements Listener {
  private final MovementTracker movement;
  private final ServerClock clock;

  public MovementSampler(MovementTracker movement, ServerClock clock) {
    this.movement = Objects.requireNonNull(movement, "MovementSampler.movement");
    this.clock = Objects.requireNonNull(clock, "MovementSampler.clock");
  }

  public void sampleOnlinePlayers() {
    long tick = clock.currentTick();
    for (Player player : Bukkit.getOnlinePlayers()) {
      Location feet = player.getLocation();
      movement.sample(
          new PlayerId(player.getUniqueId()),
          new Vec3(feet.getX(), feet.getY(), feet.getZ()),
          tick);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    movement.forget(new PlayerId(event.getPlayer().getUniqueId()));
  }
}
