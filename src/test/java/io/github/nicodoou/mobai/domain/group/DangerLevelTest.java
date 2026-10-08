package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import org.junit.jupiter.api.Test;

class DangerLevelTest {
  private final SuccessSettings settings = TestSettings.defaults().success();

  @Test
  void noFightingMeansOrdinary() {
    double level = DangerLevel.of(DangerRecord.empty(0), settings);

    assertThat(level).isCloseTo(0, within(1e-9));
  }

  @Test
  void halfwayRatioIsHalfDanger() {
    double level = DangerLevel.of(new DangerRecord(80, 10, 0), settings);

    assertThat(level).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void highRatioIsFullDanger() {
    double level = DangerLevel.of(new DangerRecord(140, 10, 0), settings);

    assertThat(level).isCloseTo(1, within(1e-9));
  }

  @Test
  void dangerIsCapped() {
    double level = DangerLevel.of(new DangerRecord(1_000, 10, 0), settings);

    assertThat(level).isCloseTo(1, within(1e-9));
  }
}
