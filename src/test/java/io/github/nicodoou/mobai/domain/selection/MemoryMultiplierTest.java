package io.github.nicodoou.mobai.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import org.junit.jupiter.api.Test;

class MemoryMultiplierTest {
  private final SelectionSettings defaults = TestSettings.defaults().selection();

  @Test
  void defaultRangeIsHalfToOneAndAHalf() {
    assertThat(MemoryMultiplier.of(0, defaults)).isCloseTo(0.5, within(1e-9));
    assertThat(MemoryMultiplier.of(0.5, defaults)).isCloseTo(1.0, within(1e-9));
    assertThat(MemoryMultiplier.of(1, defaults)).isCloseTo(1.5, within(1e-9));
  }

  @Test
  void customRangeIsInterpolated() {
    SelectionSettings custom = new SelectionSettings(defaults.defaultPolicy(), 0.8, 1.2, 0.1, 10);

    assertThat(MemoryMultiplier.of(0.25, custom)).isCloseTo(0.9, within(1e-9));
  }

  @Test
  void rejectsRateOutOfRange() {
    assertThatThrownBy(() -> MemoryMultiplier.of(1.1, defaults))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("rate must be between 0.0 and 1.0, got 1.1");
  }
}
