package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MeleeRhythmTest {
  private final MeleeRhythm rhythm = new MeleeRhythm();

  @Test
  void startsReadyToRepathAndStrike() {
    assertThat(rhythm.shouldRepath()).isTrue();
    assertThat(rhythm.canStrike(1.5)).isTrue();
  }

  @Test
  void repathsEveryTenTicks() {
    rhythm.markRepath();

    advance(9);
    assertThat(rhythm.shouldRepath()).isFalse();

    rhythm.advance();
    assertThat(rhythm.shouldRepath()).isTrue();
  }

  @Test
  void strikeWaitsTheAttackInterval() {
    rhythm.markStrike();

    advance(19);
    assertThat(rhythm.canStrike(1.0)).isFalse();

    rhythm.advance();
    assertThat(rhythm.canStrike(1.0)).isTrue();
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

    MeleeRhythm other = new MeleeRhythm();
    other.markRepath();
    assertThat(other.canStrike(1.0)).isTrue();
  }

  private void advance(int ticks) {
    for (int tick = 0; tick < ticks; tick++) {
      rhythm.advance();
    }
  }
}
