package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShotHoldTest {
  private final ShotHold hold = new ShotHold();

  @Test
  void aShooterNotHoldingHasNotRunOut() {
    assertThat(hold.hasRunOut(1_000, 1)).isFalse();
    assertThat(hold.heldTicks(1_000)).isZero();
  }

  @Test
  void theHoldRunsOutAfterItsLimit() {
    hold.begin(1_000);

    assertThat(hold.hasRunOut(1_019, 20)).isFalse();
    assertThat(hold.hasRunOut(1_020, 20)).isTrue();
  }

  @Test
  void beginningAgainKeepsTheFirstTick() {
    hold.begin(1_000);

    hold.begin(1_005);

    assertThat(hold.heldTicks(1_010)).isEqualTo(10);
  }

  @Test
  void resetStartsOver() {
    hold.begin(1_000);

    hold.reset();

    assertThat(hold.heldTicks(1_050)).isZero();
  }
}
