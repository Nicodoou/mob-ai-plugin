package io.github.nicodoou.mobai.adapter.runtime;

import io.github.nicodoou.mobai.domain.port.ServerClock;

/** Ticks counted by the plugin; unlike Bukkit's counter, it survives restarts. */
public final class ServerTickCounter implements ServerClock {
  private long tick;

  @Override
  public long currentTick() {
    return tick;
  }

  public void advance() {
    tick++;
  }

  public void restore(long savedTick) {
    if (savedTick < tick) {
      throw new IllegalArgumentException(
          "ServerTickCounter cannot go back from " + tick + " to " + savedTick);
    }
    tick = savedTick;
  }
}
