package io.github.nicodoou.mobai.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class DangerRecordTest {
  private static final long HALF_LIFE = 12_000;

  @Test
  void decayHalvesBothAfterAHalfLife() {
    DangerRecord record = new DangerRecord(40, 10, 1_000);

    DangerRecord decayed = record.decayedTo(13_000, HALF_LIFE);

    assertThat(decayed.healthLost()).isCloseTo(20, within(1e-9));
    assertThat(decayed.damageDealt()).isCloseTo(5, within(1e-9));
    assertThat(decayed.lastUpdateTick()).isEqualTo(13_000);
  }

  @Test
  void plansAddUp() {
    DangerRecord record = DangerRecord.empty(0).withPlan(10, 4).withPlan(6, 2);

    assertThat(record.healthLost()).isCloseTo(16, within(1e-9));
    assertThat(record.damageDealt()).isCloseTo(6, within(1e-9));
  }

  @Test
  void rejectsNegativeValues() {
    assertThatThrownBy(() -> new DangerRecord(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("DangerRecord.healthLost must be zero or positive, got -1.0");
  }
}
