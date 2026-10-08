package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;

/** How long a mob walking on the ground takes to cover a distance, starting from standing still. */
final class EscapeTiming {
  // Longer than any weapon's recharge: a mob this slow never makes it in time.
  static final long MAX_TICKS = 200;

  private EscapeTiming() {}

  static long ticksToCover(double distanceBlocks, double speed) {
    if (distanceBlocks <= 0) {
      return 0;
    }
    double acceleration = MinecraftConstants.MOB_MOVE_INPUT_SCALE * speed * speed;
    double velocity = 0;
    double covered = 0;
    for (long tick = 1; tick <= MAX_TICKS; tick++) {
      velocity += acceleration;
      covered += velocity;
      if (covered >= distanceBlocks) {
        return tick;
      }
      velocity *= MinecraftConstants.GROUND_DRAG_PER_TICK;
    }
    return MAX_TICKS;
  }
}
