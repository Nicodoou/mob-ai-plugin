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

  static void requireBetween(NamedSetting setting, double minimum, double maximum) {
    if (!(setting.value() >= minimum && setting.value() <= maximum)) {
      throw new IllegalArgumentException(
          setting.field()
              + " must be between "
              + minimum
              + " and "
              + maximum
              + ", got "
              + setting.value());
    }
  }

  static void requireNotAbove(NamedSetting lower, NamedSetting upper) {
    if (lower.value() > upper.value()) {
      throw new IllegalArgumentException(
          lower.field()
              + " must not exceed "
              + upper.field()
              + ", got "
              + lower.value()
              + " > "
              + upper.value());
    }
  }
}
