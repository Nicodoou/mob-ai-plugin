package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.domain.shared.GroupId;

/** Spreads group decisions over the decision window so they do not all land on one tick. */
public final class DecisionCadence {
  private DecisionCadence() {}

  public static boolean isDue(GroupId group, long tick, int intervalTicks) {
    return Math.floorMod(tick + offsetOf(group, intervalTicks), intervalTicks) == 0;
  }

  public static boolean isWindowStart(long tick, int intervalTicks) {
    return Math.floorMod(tick, intervalTicks) == 0;
  }

  static int offsetOf(GroupId group, int intervalTicks) {
    return Math.floorMod(group.value().hashCode(), intervalTicks);
  }
}
