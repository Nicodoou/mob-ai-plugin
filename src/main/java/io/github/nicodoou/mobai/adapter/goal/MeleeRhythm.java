package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import java.util.Objects;

/**
 * When a melee goal repaths and when it may strike again, measured in game ticks on the plugin's
 * clock. Paper calls a running goal only every other game tick, so counting calls would halve the
 * rhythm.
 */
public final class MeleeRhythm {
  // Repathing every tick costs CPU and changes nothing at a zombie's walking speed.
  static final int REPATH_INTERVAL_TICKS = 10;

  private final ServerClock clock;
  private long nextRepathTick = Long.MIN_VALUE;
  private long nextStrikeTick = Long.MIN_VALUE;

  public MeleeRhythm(ServerClock clock) {
    this.clock = Objects.requireNonNull(clock, "MeleeRhythm.clock");
  }

  public boolean shouldRepath() {
    return clock.currentTick() >= nextRepathTick;
  }

  public void markRepath() {
    nextRepathTick = clock.currentTick() + REPATH_INTERVAL_TICKS;
  }

  public boolean canStrike(double distanceBlocks) {
    return distanceBlocks <= MinecraftConstants.MELEE_REACH_BLOCKS
        && clock.currentTick() >= nextStrikeTick;
  }

  public void markStrike() {
    nextStrikeTick = clock.currentTick() + MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;
  }
}
