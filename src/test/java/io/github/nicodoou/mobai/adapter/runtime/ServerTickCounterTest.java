package io.github.nicodoou.mobai.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ServerTickCounterTest {
  @Test
  void startsAtZeroAndAdvancesOneTickAtATime() {
    ServerTickCounter counter = new ServerTickCounter();
    long initial = counter.currentTick();

    counter.advance();
    counter.advance();
    counter.advance();

    assertThat(initial).isZero();
    assertThat(counter.currentTick()).isEqualTo(3);
  }

  @Test
  void restoreJumpsToTheSavedTick() {
    ServerTickCounter counter = new ServerTickCounter();

    counter.restore(123_000);
    counter.advance();

    assertThat(counter.currentTick()).isEqualTo(123_001);
  }

  @Test
  void restoreCannotGoBack() {
    ServerTickCounter counter = new ServerTickCounter();
    counter.restore(50);

    assertThatThrownBy(() -> counter.restore(10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ServerTickCounter cannot go back from 50 to 10");
  }
}
