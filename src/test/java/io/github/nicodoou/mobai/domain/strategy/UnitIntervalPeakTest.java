package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class UnitIntervalPeakTest {

  @Test
  void findsThePeakOrTheBetterEnd() {
    double interior = UnitIntervalPeak.of(1, -1);
    double vertexBeyondOne = UnitIntervalPeak.of(3, -1);
    double rising = UnitIntervalPeak.of(1, 1);
    double falling = UnitIntervalPeak.of(-1, 0);
    double flat = UnitIntervalPeak.of(0, 0);

    assertThat(interior).isEqualTo(0.5, within(1e-9));
    assertThat(vertexBeyondOne).isEqualTo(1, within(1e-9));
    assertThat(rising).isEqualTo(1, within(1e-9));
    assertThat(falling).isEqualTo(0, within(1e-9));
    assertThat(flat).isEqualTo(0, within(1e-9));
  }

  @Test
  void nonFiniteCoefficientsAreRejected() {
    assertThatThrownBy(() -> UnitIntervalPeak.of(Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("UnitIntervalPeak coefficients must be finite, got NaN, 0.0");
  }
}
