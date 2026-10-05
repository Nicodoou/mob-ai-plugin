package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.port.ServerClock;

public final class FakeServerClock implements ServerClock {
  private long currentTick;

  public FakeServerClock(long startTick) {
    if (startTick < 0) {
      throw new IllegalArgumentException("startTick must be zero or positive, got " + startTick);
    }
    this.currentTick = startTick;
  }

  @Override
  public long currentTick() {
    return currentTick;
  }

  public void advance(long ticks) {
    if (ticks < 0) {
      throw new IllegalArgumentException("ticks must be zero or positive, got " + ticks);
    }
    this.currentTick += ticks;
  }
}
