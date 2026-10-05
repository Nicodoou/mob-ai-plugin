package io.github.nicodoou.mobai.domain.memory;

public final class LearningPrior {
  // RF-06: 50 virtual attempts at learning speed 0 and 2 (a Beta(1, 1)) at learning speed 1.
  private static final double MAX_VIRTUAL_ATTEMPTS = 50;
  private static final double MIN_VIRTUAL_ATTEMPTS = 2;

  private final double virtualAttempts;

  private LearningPrior(double virtualAttempts) {
    this.virtualAttempts = virtualAttempts;
  }

  public static LearningPrior fromLearningSpeed(double learningSpeed) {
    if (!(learningSpeed >= 0 && learningSpeed <= 1)) {
      throw new IllegalArgumentException(
          "learningSpeed must be between 0.0 and 1.0, got " + learningSpeed);
    }
    return new LearningPrior(
        MAX_VIRTUAL_ATTEMPTS - (MAX_VIRTUAL_ATTEMPTS - MIN_VIRTUAL_ATTEMPTS) * learningSpeed);
  }

  public double virtualAttempts() {
    return virtualAttempts;
  }

  public SuccessEstimate estimate(AttackRecord record) {
    double half = virtualAttempts / 2;
    return new SuccessEstimate(
        record.successes() + half, record.failures() + half, record.attempts());
  }
}
