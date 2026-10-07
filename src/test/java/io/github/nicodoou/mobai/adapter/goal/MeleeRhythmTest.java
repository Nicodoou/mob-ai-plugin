package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import org.junit.jupiter.api.Test;

class MeleeRhythmTest {
  // Paper ticks a running goal only every other game tick.
  private static final int GAME_TICKS_PER_GOAL_TICK = 2;

  private final FakeServerClock clock = new FakeServerClock(1_000);
  private final MeleeRhythm rhythm = new MeleeRhythm(clock);

  @Test
  void startsReadyToRepathAndStrike() {
    assertThat(rhythm.shouldRepath()).isTrue();
    assertThat(rhythm.canStrike(1.5)).isTrue();
  }

  @Test
  void repathsEveryTenGameTicks() {
    rhythm.markRepath();

    clock.advance(9);
    assertThat(rhythm.shouldRepath()).isFalse();

    clock.advance(1);
    assertThat(rhythm.shouldRepath()).isTrue();
  }

  @Test
  void strikeWaitsTheAttackIntervalInGameTicks() {
    rhythm.markStrike();

    clock.advance(19);
    assertThat(rhythm.canStrike(1.0)).isFalse();

    clock.advance(1);
    assertThat(rhythm.canStrike(1.0)).isTrue();
  }

  @Test
  void strikesOncePerAttackIntervalOfGameTicksWhenTickedEveryOtherTick() {
    rhythm.markStrike();

    goalTicks(9);
    assertThat(rhythm.canStrike(1.0)).isFalse();

    goalTicks(1);
    assertThat(rhythm.canStrike(1.0)).isTrue();
  }

  @Test
  void repathsEveryTenGameTicksWhenTickedEveryOtherTick() {
    rhythm.markRepath();

    goalTicks(4);
    assertThat(rhythm.shouldRepath()).isFalse();

    goalTicks(1);
    assertThat(rhythm.shouldRepath()).isTrue();
  }

  @Test
  void outOfReachNeverStrikes() {
    assertThat(rhythm.canStrike(2.01)).isFalse();
  }

  @Test
  void reachIsInclusive() {
    assertThat(rhythm.canStrike(2.0)).isTrue();
  }

  @Test
  void repathAndStrikeAreIndependent() {
    rhythm.markStrike();
    assertThat(rhythm.shouldRepath()).isTrue();

    MeleeRhythm other = new MeleeRhythm(clock);
    other.markRepath();
    assertThat(other.canStrike(1.0)).isTrue();
  }

  private void goalTicks(int count) {
    for (int tick = 0; tick < count; tick++) {
      clock.advance(GAME_TICKS_PER_GOAL_TICK);
    }
  }
}
