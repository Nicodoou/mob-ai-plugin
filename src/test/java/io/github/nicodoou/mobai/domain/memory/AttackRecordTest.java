package io.github.nicodoou.mobai.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class AttackRecordTest {
  private static final long HALF_LIFE = 12_000;

  @Test
  void emptyRecordHasNoData() {
    AttackRecord record = AttackRecord.empty(5);

    assertThat(record).isEqualTo(new AttackRecord(0, 0, 5));
    assertThat(record.failures()).isZero();
  }

  @Test
  void oneHalfLifeHalvesSuccessesAndAttempts() {
    AttackRecord decayed = new AttackRecord(8, 10, 0).decayedTo(12_000, HALF_LIFE);

    assertThat(decayed.successes()).isCloseTo(4, within(1e-9));
    assertThat(decayed.attempts()).isCloseTo(5, within(1e-9));
    assertThat(decayed.lastUpdateTick()).isEqualTo(12_000);
  }

  @Test
  void twoHalfLivesQuarterTheCounts() {
    AttackRecord decayed = new AttackRecord(8, 10, 0).decayedTo(24_000, HALF_LIFE);

    assertThat(decayed.successes()).isCloseTo(2, within(1e-9));
    assertThat(decayed.attempts()).isCloseTo(2.5, within(1e-9));
    assertThat(decayed.lastUpdateTick()).isEqualTo(24_000);
  }

  @Test
  void decayingToTheSameTickChangesNothing() {
    AttackRecord record = new AttackRecord(8, 10, 100);

    assertThat(record.decayedTo(100, HALF_LIFE)).isEqualTo(record);
  }

  @Test
  void decayingBackwardsFails() {
    AttackRecord record = new AttackRecord(8, 10, 100);

    assertThatThrownBy(() -> record.decayedTo(99, HALF_LIFE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot decay backwards: record at tick 100, requested 99");
  }

  @Test
  void observationAddsWeightedCredit() {
    AttackRecord full = new AttackRecord(1, 2, 0).withObservation(0.5, 1.0);
    AttackRecord weighted = new AttackRecord(0, 0, 0).withObservation(0.73, 0.5);

    assertThat(full).isEqualTo(new AttackRecord(1.5, 3, 0));
    assertThat(weighted.successes()).isCloseTo(0.365, within(1e-9));
    assertThat(weighted.attempts()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void failuresAreAttemptsMinusSuccesses() {
    assertThat(new AttackRecord(3, 10, 0).failures()).isCloseTo(7, within(1e-9));
  }

  @Test
  void rejectsMoreSuccessesThanAttempts() {
    assertThatThrownBy(() -> new AttackRecord(3, 2, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackRecord.successes must not exceed attempts, got 3.0 > 2.0");
  }

  @Test
  void rejectsNegativeOrNonFiniteCounts() {
    assertThatThrownBy(() -> new AttackRecord(-1, 2, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackRecord.successes must be zero or positive, got -1.0");
    assertThatThrownBy(() -> new AttackRecord(0, Double.NaN, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
