package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RangeSituationTest {
  private static final double MIN_BLOCKS = 8;
  private static final double MAX_BLOCKS = 15;

  @Test
  void targetOutOfSightIsApproached() {
    assertThat(move(10, false)).isEqualTo(RangeMove.APPROACH);
  }

  @Test
  void farTargetIsApproached() {
    assertThat(move(15.1, true)).isEqualTo(RangeMove.APPROACH);
  }

  @Test
  void closeTargetMakesItBackOff() {
    assertThat(move(7.9, true)).isEqualTo(RangeMove.BACK_OFF);
  }

  @Test
  void bothEndsOfTheRangeHold() {
    assertThat(move(8, true)).isEqualTo(RangeMove.HOLD);
    assertThat(move(15, true)).isEqualTo(RangeMove.HOLD);
  }

  @Test
  void closeButHiddenTargetIsApproached() {
    assertThat(move(5, false)).isEqualTo(RangeMove.APPROACH);
  }

  private static RangeMove move(double distanceBlocks, boolean inSight) {
    return new RangeSituation(distanceBlocks, inSight).nextMove(MIN_BLOCKS, MAX_BLOCKS);
  }
}
