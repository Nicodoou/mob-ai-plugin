package io.github.nicodoou.mobai.domain.snapshot;

final class SnapshotChecks {
  private SnapshotChecks() {}

  static void requirePositive(String field, double value) {
    if (!(value > 0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be a positive number, got " + value);
    }
  }

  static void requireNonNegative(String field, double value) {
    if (!(value >= 0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(field + " must be zero or positive, got " + value);
    }
  }

  static void requireBetween(String field, double value, double maximum) {
    if (!(value >= 0 && value <= maximum)) {
      throw new IllegalArgumentException(
          field + " must be between 0.0 and " + maximum + ", got " + value);
    }
  }

  static void requireAtLeast(String field, long value, long minimum) {
    if (value < minimum) {
      throw new IllegalArgumentException(field + " must be at least " + minimum + ", got " + value);
    }
  }
}
