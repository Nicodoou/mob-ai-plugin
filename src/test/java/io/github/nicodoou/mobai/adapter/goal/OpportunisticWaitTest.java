package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OpportunisticWaitTest {
  private static final long MAX_WAIT_TICKS = 60;

  private final OpportunisticWait wait = new OpportunisticWait();

  @Test
  void shootsWhenTheTargetIsBusy() {
    assertThat(next(TargetFocus.ELSEWHERE, 1000)).isEqualTo(OpportunisticMove.SHOOT_OPPORTUNISTIC);
  }

  @Test
  void waitsWhileTheTargetWatches() {
    assertThat(next(TargetFocus.ON_SHOOTER, 1000)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1030)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1059)).isEqualTo(OpportunisticMove.WAIT);
  }

  @Test
  void givesUpWhenTheWaitRunsOut() {
    assertThat(next(TargetFocus.ON_SHOOTER, 1000)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1060)).isEqualTo(OpportunisticMove.GIVE_UP);
    assertThat(next(TargetFocus.ON_SHOOTER, 1062)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1122)).isEqualTo(OpportunisticMove.GIVE_UP);
  }

  @Test
  void resetStartsTheWaitAgain() {
    assertThat(next(TargetFocus.ON_SHOOTER, 1000)).isEqualTo(OpportunisticMove.WAIT);

    wait.reset();

    assertThat(next(TargetFocus.ON_SHOOTER, 1050)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1100)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1110)).isEqualTo(OpportunisticMove.GIVE_UP);
  }

  @Test
  void aBusyTargetEndsTheWait() {
    assertThat(next(TargetFocus.ON_SHOOTER, 1000)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ELSEWHERE, 1030)).isEqualTo(OpportunisticMove.SHOOT_OPPORTUNISTIC);
    assertThat(next(TargetFocus.ON_SHOOTER, 1080)).isEqualTo(OpportunisticMove.WAIT);
    assertThat(next(TargetFocus.ON_SHOOTER, 1139)).isEqualTo(OpportunisticMove.WAIT);
  }

  private OpportunisticMove next(TargetFocus focus, long now) {
    return wait.next(focus, now, MAX_WAIT_TICKS);
  }
}
