package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import java.util.Objects;

/** How long a shooter has been drawing its bow, on the plugin's clock. */
final class BowDraw {
  private static final long NOT_DRAWING = Long.MIN_VALUE;

  private final ServerClock clock;
  private long drawStartTick = NOT_DRAWING;

  BowDraw(ServerClock clock) {
    this.clock = Objects.requireNonNull(clock, "BowDraw.clock");
  }

  boolean isDrawing() {
    return drawStartTick != NOT_DRAWING;
  }

  void start() {
    drawStartTick = clock.currentTick();
  }

  boolean isFull() {
    return isDrawing()
        && clock.currentTick() - drawStartTick >= MinecraftConstants.BOW_FULL_DRAW_TICKS;
  }

  void release() {
    drawStartTick = NOT_DRAWING;
  }
}
