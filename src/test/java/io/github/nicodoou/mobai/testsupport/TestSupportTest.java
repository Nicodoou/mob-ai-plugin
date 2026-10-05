package io.github.nicodoou.mobai.testsupport;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestSupportTest {

  @Test
  void seededRandomRepeatsTheSequenceForTheSameSeed() {
    SeededRandomSource first = new SeededRandomSource(42);
    SeededRandomSource second = new SeededRandomSource(42);

    for (int i = 0; i < 5; i++) {
      assertThat(first.nextUnit()).isEqualTo(second.nextUnit());
    }

    for (int i = 0; i < 5; i++) {
      assertThat(first.nextGaussian()).isEqualTo(second.nextGaussian());
    }

    for (int i = 0; i < 5; i++) {
      assertThat(first.nextIndex(10)).isEqualTo(second.nextIndex(10));
    }
  }

  @Test
  void seededRandomIndexStaysWithinBound() {
    SeededRandomSource random = new SeededRandomSource(7);
    boolean[] seen = new boolean[7];

    for (int i = 0; i < 10_000; i++) {
      int index = random.nextIndex(7);
      assertThat(index).isBetween(0, 6);
      seen[index] = true;
    }

    for (boolean s : seen) {
      assertThat(s).isTrue();
    }
  }

  @Test
  void seededRandomRejectsNonPositiveBound() {
    SeededRandomSource random = new SeededRandomSource(42);
    assertThatThrownBy(() -> random.nextIndex(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bound must be positive, got 0");
  }

  @Test
  void scriptedRandomReturnsValuesInOrder() {
    ScriptedRandomSource random =
        new ScriptedRandomSource().withUnits(0.1, 0.2).withGaussians(-1.0).withIndexes(2);

    assertThat(random.nextUnit()).isEqualTo(0.1);
    assertThat(random.nextUnit()).isEqualTo(0.2);
    assertThat(random.nextGaussian()).isEqualTo(-1.0);
    assertThat(random.nextIndex(3)).isEqualTo(2);
    assertThat(random.isExhausted()).isTrue();
  }

  @Test
  void scriptedRandomFailsWhenExhausted() {
    ScriptedRandomSource random = new ScriptedRandomSource();
    assertThatThrownBy(random::nextUnit)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("no scripted unit value left");
  }

  @Test
  void scriptedRandomRejectsIndexOutsideBound() {
    ScriptedRandomSource random = new ScriptedRandomSource().withIndexes(5);
    assertThatThrownBy(() -> random.nextIndex(3))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("scripted index 5 is outside [0, 3)");
  }

  @Test
  void fakeClockAdvances() {
    FakeServerClock clock = new FakeServerClock(100);
    clock.advance(50);
    assertThat(clock.currentTick()).isEqualTo(150);
  }

  @Test
  void fakeClockRejectsNegativeAdvance() {
    FakeServerClock clock = new FakeServerClock(100);
    assertThatThrownBy(() -> clock.advance(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ticks must be zero or positive, got -1");
  }
}
