package io.github.nicodoou.mobai.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class LearningPriorTest {
  @Test
  void virtualAttemptsFollowTheLearningSpeedFormula() {
    assertThat(LearningPrior.fromLearningSpeed(1).virtualAttempts()).isCloseTo(2, within(1e-9));
    assertThat(LearningPrior.fromLearningSpeed(0).virtualAttempts()).isCloseTo(50, within(1e-9));
    assertThat(LearningPrior.fromLearningSpeed(0.7).virtualAttempts())
        .isCloseTo(16.4, within(1e-9));
  }

  @Test
  void oneHitOfOneGivesTwoThirdsAtFullLearningSpeed() {
    LearningPrior prior = LearningPrior.fromLearningSpeed(1);

    SuccessEstimate estimate = prior.estimate(new AttackRecord(1, 1, 0));

    assertThat(estimate.mean()).isCloseTo(2.0 / 3.0, within(1e-9));
  }

  @Test
  void tenHitsOfTenAtFullLearningSpeed() {
    LearningPrior prior = LearningPrior.fromLearningSpeed(1);

    SuccessEstimate estimate = prior.estimate(new AttackRecord(10, 10, 0));

    assertThat(estimate.mean()).isCloseTo(11.0 / 12.0, within(1e-9));
  }

  @Test
  void tenHitsOfTenAtSlowestLearningSpeed() {
    LearningPrior prior = LearningPrior.fromLearningSpeed(0);

    SuccessEstimate estimate = prior.estimate(new AttackRecord(10, 10, 0));

    assertThat(estimate.mean()).isCloseTo(35.0 / 60.0, within(1e-9));
  }

  @Test
  void emptyRecordGivesFiftyPercent() {
    LearningPrior prior = LearningPrior.fromLearningSpeed(0.7);

    SuccessEstimate estimate = prior.estimate(AttackRecord.empty(0));

    assertThat(estimate.alpha()).isCloseTo(8.2, within(1e-9));
    assertThat(estimate.beta()).isCloseTo(8.2, within(1e-9));
    assertThat(estimate.mean()).isCloseTo(0.5, within(1e-9));
    assertThat(estimate.observedAttempts()).isZero();
  }

  @Test
  void rejectsLearningSpeedOutOfRange() {
    assertThatThrownBy(() -> LearningPrior.fromLearningSpeed(1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("learningSpeed must be between 0.0 and 1.0, got 1.5");
  }
}
