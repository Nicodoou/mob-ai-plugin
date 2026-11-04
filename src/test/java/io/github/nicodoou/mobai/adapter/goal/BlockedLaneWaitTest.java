package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BlockedLaneWaitTest {
  private static final long MAX_WAIT_TICKS = 15;

  private final BlockedLaneWait wait = new BlockedLaneWait();

  @Test
  void waitsUntilTheTimeRunsOut() {
    assertThat(wait.isStillWaiting(1000, MAX_WAIT_TICKS)).isTrue();
    assertThat(wait.isStillWaiting(1014, MAX_WAIT_TICKS)).isTrue();
    assertThat(wait.isStillWaiting(1015, MAX_WAIT_TICKS)).isFalse();
  }

  @Test
  void runningOutStartsTheNextWaitAfresh() {
    wait.isStillWaiting(1000, MAX_WAIT_TICKS);
    wait.isStillWaiting(1015, MAX_WAIT_TICKS);

    assertThat(wait.isStillWaiting(1100, MAX_WAIT_TICKS)).isTrue();
  }

  @Test
  void resetStartsTheWaitAgain() {
    wait.isStillWaiting(1000, MAX_WAIT_TICKS);

    wait.reset();

    assertThat(wait.isStillWaiting(1020, MAX_WAIT_TICKS)).isTrue();
  }

  @Test
  void noPatienceMeansNoWait() {
    assertThat(wait.isStillWaiting(1000, 0)).isFalse();
  }
}
