package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlayerTraitsTest {
  @Test
  void valuesOutsideZeroToOneAreRejected() {
    assertThatThrownBy(() -> new PlayerTraits(1.1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerTraits values must be in [0, 1], got 1.1/0.0/0.0");
    assertThatThrownBy(() -> new PlayerTraits(0, Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerTraits values must be in [0, 1], got 0.0/NaN/0.0");
  }
}
