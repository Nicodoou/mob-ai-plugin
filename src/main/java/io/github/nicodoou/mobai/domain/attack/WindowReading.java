package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

/**
 * The target's post-hit protection as read this tick: how long until a hit counts again, and how
 * long the protection lasts after a hit.
 */
public record WindowReading(PlayerId target, long tick, long protectedTicks, long windowTicks) {
  public WindowReading {
    Objects.requireNonNull(target, "WindowReading.target");
    if (protectedTicks < 0) {
      throw new IllegalArgumentException(
          "WindowReading.protectedTicks must be zero or positive, got " + protectedTicks);
    }
    if (windowTicks < 1) {
      throw new IllegalArgumentException(
          "WindowReading.windowTicks must be at least 1, got " + windowTicks);
    }
  }

  long closingTick() {
    return tick + protectedTicks;
  }

  /** Until when a hit landed now would protect the target. */
  long windowEndIfHitNow() {
    return tick + windowTicks;
  }
}
