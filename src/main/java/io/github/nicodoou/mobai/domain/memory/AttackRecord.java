package io.github.nicodoou.mobai.domain.memory;

public record AttackRecord(double successes, double attempts, long lastUpdateTick) {
  // Absorbs floating-point rounding after repeated decay.
  private static final double TOLERANCE = 1e-9;

  public AttackRecord {
    if (!(successes >= 0) || !Double.isFinite(successes)) {
      throw new IllegalArgumentException(
          "AttackRecord.successes must be zero or positive, got " + successes);
    }
    if (!(attempts >= 0) || !Double.isFinite(attempts)) {
      throw new IllegalArgumentException(
          "AttackRecord.attempts must be zero or positive, got " + attempts);
    }
    if (successes > attempts + TOLERANCE) {
      throw new IllegalArgumentException(
          "AttackRecord.successes must not exceed attempts, got " + successes + " > " + attempts);
    }
    if (lastUpdateTick < 0) {
      throw new IllegalArgumentException(
          "AttackRecord.lastUpdateTick must be zero or positive, got " + lastUpdateTick);
    }
  }

  public static AttackRecord empty(long tick) {
    return new AttackRecord(0, 0, tick);
  }

  public double failures() {
    return Math.max(0, attempts - successes);
  }

  public AttackRecord decayedTo(long tick, long halfLifeTicks) {
    if (tick < lastUpdateTick) {
      throw new IllegalArgumentException(
          "cannot decay backwards: record at tick " + lastUpdateTick + ", requested " + tick);
    }
    double factor = Math.pow(0.5, (tick - lastUpdateTick) / (double) halfLifeTicks);
    return new AttackRecord(successes * factor, attempts * factor, tick);
  }

  public AttackRecord withObservation(double credit, double weight) {
    return new AttackRecord(successes + credit * weight, attempts + weight, lastUpdateTick);
  }
}
