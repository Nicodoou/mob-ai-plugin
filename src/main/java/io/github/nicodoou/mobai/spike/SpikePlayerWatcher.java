package io.github.nicodoou.mobai.spike;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

/** Polls every online player each tick: shield transitions and real per-tick movement. */
final class SpikePlayerWatcher implements Runnable {
  private final SpikeRecorder recorder;
  private final Map<UUID, Boolean> blocking = new HashMap<>();
  private final Map<UUID, Boolean> handRaised = new HashMap<>();
  private final Map<UUID, Location> lastLocation = new HashMap<>();
  private final Map<UUID, Vector> movementPerTick = new HashMap<>();

  SpikePlayerWatcher(SpikeRecorder recorder) {
    this.recorder = recorder;
  }

  @Override
  public void run() {
    for (Player player : Bukkit.getOnlinePlayers()) {
      UUID id = player.getUniqueId();
      trackTransition(player, "isBlocking", blocking, player.isBlocking());
      trackTransition(player, "isHandRaised", handRaised, player.isHandRaised());
      Location now = player.getLocation();
      Location before = lastLocation.put(id, now.clone());
      if (before != null && before.getWorld() == now.getWorld()) {
        movementPerTick.put(id, now.toVector().subtract(before.toVector()));
      }
    }
  }

  Vector movementPerTick(Player player) {
    return movementPerTick.getOrDefault(player.getUniqueId(), new Vector()).clone();
  }

  private void trackTransition(
      Player player, String name, Map<UUID, Boolean> states, boolean current) {
    Boolean previous = states.put(player.getUniqueId(), current);
    if (previous != null && previous != current) {
      recorder.record(
          "shield",
          player.getName()
              + " "
              + name
              + " "
              + previous
              + " -> "
              + current
              + " | attackCooldown="
              + player.getAttackCooldown());
    }
  }
}
