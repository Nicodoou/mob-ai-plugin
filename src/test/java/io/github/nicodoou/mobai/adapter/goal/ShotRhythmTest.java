package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import org.junit.jupiter.api.Test;

class ShotRhythmTest {
  // Paper ticks a running goal only every other game tick.
  private static final int GAME_TICKS_PER_GOAL_TICK = 2;

  private final FakeServerClock clock = new FakeServerClock(1_000);
  private final ShotRhythm rhythm = new ShotRhythm(clock);

  @Test
  void startsReadyToShoot() {
    assertThat(rhythm.canShoot()).isTrue();
  }

  @Test
  void waitsTheSkeletonAttackInterval() {
    rhythm.markShot();

    clock.advance(39);
    assertThat(rhythm.canShoot()).isFalse();

    clock.advance(1);
    assertThat(rhythm.canShoot()).isTrue();
  }

  @Test
  void ticksEveryOtherGameTickStillShootEveryFortyTicks() {
    rhythm.markShot();

    for (int goalTick = 0; goalTick < 19; goalTick++) {
      clock.advance(GAME_TICKS_PER_GOAL_TICK);
      assertThat(rhythm.canShoot()).isFalse();
    }
    clock.advance(GAME_TICKS_PER_GOAL_TICK);

    assertThat(rhythm.canShoot()).isTrue();
  }
}
