package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;

/** When a melee goal repaths and when it may strike again, counted in its own ticks. */
public final class MeleeRhythm {
  // Repathing every tick costs CPU and changes nothing at a zombie's walking speed.
  static final int REPATH_INTERVAL_TICKS = 10;

  private int ticksSinceRepath = REPATH_INTERVAL_TICKS;
  private int ticksSinceStrike = MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;

  public void advance() {
    ticksSinceRepath++;
    ticksSinceStrike++;
  }

  public boolean shouldRepath() {
    return ticksSinceRepath >= REPATH_INTERVAL_TICKS;
  }

  public void markRepath() {
    ticksSinceRepath = 0;
  }

  public boolean canStrike(double distanceBlocks) {
    return distanceBlocks <= MinecraftConstants.MELEE_REACH_BLOCKS
        && ticksSinceStrike >= MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;
  }

  public void markStrike() {
    ticksSinceStrike = 0;
  }
}
