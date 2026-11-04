package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlankStrikeTest {
  @Test
  void outsideTheShieldArcItStrikesFromTheSideEvenWithTheShieldUp() {
    assertThat(FlankStrike.of(false, true)).isEqualTo(FlankStrike.FROM_THE_SIDE);
  }

  @Test
  void insideTheShieldArcWithTheShieldDownItStrikesHeadOn() {
    assertThat(FlankStrike.of(true, false)).isEqualTo(FlankStrike.HEAD_ON);
  }

  @Test
  void insideTheShieldArcWithTheShieldUpItHoldsFire() {
    assertThat(FlankStrike.of(true, true)).isEqualTo(FlankStrike.HOLD_FIRE);
  }
}
