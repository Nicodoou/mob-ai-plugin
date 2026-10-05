package io.github.nicodoou.mobai.domain.settings;

final class SettingsChecks {
  private SettingsChecks() {}

  static void requireAtLeast(String field, long value, long minimum) {
    if (value < minimum) {
      throw new IllegalArgumentException(field + " must be at least " + minimum + ", got " + value);
    }
  }

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

  static void requireBetween(String field, double value, double minimum, double maximum) {
    if (!(value >= minimum && value <= maximum)) {
      throw new IllegalArgumentException(
          field + " must be between " + minimum + " and " + maximum + ", got " + value);
    }
  }

  static void requireNotAbove(String lowerField, double lower, String upperField, double upper) {
    if (lower > upper) {
      throw new IllegalArgumentException(
          lowerField + " must not exceed " + upperField + ", got " + lower + " > " + upper);
    }
  }
}
