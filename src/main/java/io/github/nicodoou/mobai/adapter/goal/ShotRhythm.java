package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import java.util.Objects;

/** When a shooter may loose its next arrow, measured in game ticks on the plugin's clock. */
public final class ShotRhythm {
  private final ServerClock clock;
  private long nextShotTick = Long.MIN_VALUE;

  public ShotRhythm(ServerClock clock) {
    this.clock = Objects.requireNonNull(clock, "ShotRhythm.clock");
  }

  public boolean canShoot() {
    return clock.currentTick() >= nextShotTick;
  }

  /** The draw starts early enough for the arrow to leave on the rhythm, not 20 ticks late. */
  public boolean canDraw() {
    return clock.currentTick() + MinecraftConstants.BOW_FULL_DRAW_TICKS >= nextShotTick;
  }

  public void markShot() {
    nextShotTick = clock.currentTick() + MinecraftConstants.SKELETON_ATTACK_INTERVAL_TICKS;
  }
}
