package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscapeTimingTest {
  private static final double ZOMBIE_SPEED = 0.23;
  private static final double ZOMBIE_SPEED_II = 0.322;

  @Test
  void zombieCoversOneBlockInTenTicks() {
    assertThat(EscapeTiming.ticksToCover(1.0, ZOMBIE_SPEED)).isEqualTo(10);
    assertThat(EscapeTiming.ticksToCover(2.0, ZOMBIE_SPEED)).isEqualTo(19);
  }

  @Test
  void fasterMobGetsOutSooner() {
    assertThat(EscapeTiming.ticksToCover(1.0, ZOMBIE_SPEED_II)).isEqualTo(6);
  }

  @Test
  void nothingToCoverTakesNoTime() {
    assertThat(EscapeTiming.ticksToCover(0, ZOMBIE_SPEED)).isZero();
    assertThat(EscapeTiming.ticksToCover(-1, ZOMBIE_SPEED)).isZero();
  }

  @Test
  void standingMobNeverGetsThere() {
    assertThat(EscapeTiming.ticksToCover(1.0, 0)).isEqualTo(EscapeTiming.MAX_TICKS);
  }
}
