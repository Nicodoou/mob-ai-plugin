package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.settings.RelaySettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One target's relay: the melee mobs ready to hit, in order of arrival, and the arrows flying. */
final class RelayLane {
  // The value is the last tick the mob asked; re-inserting a key keeps its place in the line.
  private final Map<MobId, Long> readyMobs = new LinkedHashMap<>();
  private final List<Long> landingTicks = new ArrayList<>();

  void prune(long tick, RelaySettings settings) {
    readyMobs.values().removeIf(lastAsked -> tick - lastAsked > settings.readyStaleTicks());
    landingTicks.removeIf(landing -> tick > landing + settings.landingGraceTicks());
  }

  boolean isEmpty() {
    return readyMobs.isEmpty() && landingTicks.isEmpty();
  }

  void markReady(MobId mob, long tick) {
    readyMobs.put(mob, tick);
  }

  void forgetReady(MobId mob) {
    readyMobs.remove(mob);
  }

  boolean hasReadyMob() {
    return !readyMobs.isEmpty();
  }

  boolean isNextToStrike(MobId mob) {
    return readyMobs.keySet().stream().findFirst().filter(mob::equals).isPresent();
  }

  void addLanding(long landingTick) {
    landingTicks.add(landingTick);
  }

  boolean hasLandingBefore(long tick) {
    return landingTicks.stream().anyMatch(landing -> landing < tick);
  }

  boolean hasLandingNear(long tick, long windowTicks) {
    return landingTicks.stream().anyMatch(landing -> Math.abs(landing - tick) < windowTicks);
  }
}
