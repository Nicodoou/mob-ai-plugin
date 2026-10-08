package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.settings.LearningSettings;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** What each player tends to do, as a time-decayed average of what the groups saw (CT-30). */
public final class TraitLedger {
  private static final PlayerTraits UNKNOWN = new PlayerTraits(0, 0, 0);
  private static final double HALF = 0.5;
  private static final double OBSERVATION_WEIGHT = 1.0;

  private final Supplier<LearningSettings> settings;
  private final Map<PlayerId, TraitSums> sums = new HashMap<>();

  public TraitLedger(Supplier<LearningSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "TraitLedger.settings");
  }

  public void observe(GroupSnapshot snapshot) {
    Objects.requireNonNull(snapshot, "TraitLedger.snapshot");
    for (PlayerSnapshot player : snapshot.players()) {
      if (player.health() > 0) {
        observePlayer(player, snapshot.tick());
      }
    }
  }

  public PlayerTraits traitsOf(PlayerId player) {
    Objects.requireNonNull(player, "TraitLedger.player");
    TraitSums known = sums.get(player);
    return known == null ? UNKNOWN : known.traits();
  }

  public Map<PlayerId, TraitSums> capture() {
    return Collections.unmodifiableMap(new HashMap<>(sums));
  }

  public void restore(Map<PlayerId, TraitSums> captured) {
    Objects.requireNonNull(captured, "TraitLedger.captured");
    sums.clear();
    sums.putAll(captured);
  }

  private void observePlayer(PlayerSnapshot player, long tick) {
    TraitSums previous = sums.get(player.id());
    if (previous != null && previous.lastTick() == tick) {
      return;
    }
    double keep = previous == null ? 0 : keepFactor(tick - previous.lastTick());
    sums.put(player.id(), decayedWith(previous, keep, player, tick));
  }

  private double keepFactor(long elapsedTicks) {
    long halfLifeTicks = settings.get().traitsHalfLifeTicks();
    return Math.pow(HALF, Math.max(0, elapsedTicks) / (double) halfLifeTicks);
  }

  private static TraitSums decayedWith(
      TraitSums previous, double keep, PlayerSnapshot player, long tick) {
    double shield = previous == null ? 0 : previous.shield();
    double ranged = previous == null ? 0 : previous.ranged();
    double armor = previous == null ? 0 : previous.armor();
    double weight = previous == null ? 0 : previous.weight();
    return new TraitSums(
        shield * keep + (player.blocking() ? 1 : 0),
        ranged * keep + (player.holdingRanged() ? 1 : 0),
        armor * keep + armorObservation(player),
        weight * keep + OBSERVATION_WEIGHT,
        tick);
  }

  private static double armorObservation(PlayerSnapshot player) {
    return Math.min(1.0, player.armorPoints() / MinecraftConstants.ARMOR_MAX_POINTS);
  }
}
