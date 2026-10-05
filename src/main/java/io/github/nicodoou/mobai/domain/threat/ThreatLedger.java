package io.github.nicodoou.mobai.domain.threat;

import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class ThreatLedger {
  private final Supplier<TargetSettings> settings;
  private final Map<PlayerId, ArrayDeque<ThreatEntry>> entries = new LinkedHashMap<>();
  private long lastTick = 0;

  public ThreatLedger(Supplier<TargetSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "ThreatLedger.settings");
  }

  public void recordDamage(PlayerId player, double damage, long tick) {
    Objects.requireNonNull(player, "ThreatLedger.player");
    requireValidDamage(damage);
    requireValidTick(tick);
    lastTick = tick;
    ArrayDeque<ThreatEntry> queue = entries.computeIfAbsent(player, ignored -> new ArrayDeque<>());
    queue.addLast(new ThreatEntry(tick, damage));
    dropExpired(queue, tick);
  }

  public double threatOf(PlayerId player, long currentTick) {
    ArrayDeque<ThreatEntry> queue = entries.get(player);
    if (queue == null) {
      return 0;
    }
    return queue.stream()
        .filter(entry -> isActive(entry, currentTick))
        .mapToDouble(ThreatEntry::damage)
        .sum();
  }

  public void prune(long currentTick) {
    entries.values().forEach(queue -> dropExpired(queue, currentTick));
    entries.values().removeIf(ArrayDeque::isEmpty);
  }

  public void forget(PlayerId player) {
    entries.remove(player);
  }

  public List<PlayerId> trackedPlayers() {
    return List.copyOf(entries.keySet());
  }

  int entryCount() {
    return entries.values().stream().mapToInt(ArrayDeque::size).sum();
  }

  private long windowTicks() {
    return settings.get().threatWindowTicks();
  }

  private boolean isActive(ThreatEntry entry, long currentTick) {
    return currentTick - entry.tick() < windowTicks();
  }

  private void dropExpired(ArrayDeque<ThreatEntry> queue, long currentTick) {
    queue.removeIf(entry -> !isActive(entry, currentTick));
  }

  private static void requireValidDamage(double damage) {
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException(
          "ThreatLedger.damage must be a positive number, got " + damage);
    }
  }

  private void requireValidTick(long tick) {
    if (tick < 0) {
      throw new IllegalArgumentException("ThreatLedger.tick must be zero or positive, got " + tick);
    }
    if (tick < lastTick) {
      throw new IllegalArgumentException(
          "ThreatLedger.tick must not go back, got " + tick + " after " + lastTick);
    }
  }

  private record ThreatEntry(long tick, double damage) {}
}
