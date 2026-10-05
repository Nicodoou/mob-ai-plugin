package io.github.nicodoou.mobai.domain.shared;

import java.util.Objects;
import java.util.regex.Pattern;

public record StrategyId(String value) {
  private static final Pattern UPPER_SNAKE_CASE = Pattern.compile("[A-Z][A-Z_]*");

  public StrategyId {
    Objects.requireNonNull(value, "StrategyId.value");
    if (!UPPER_SNAKE_CASE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "StrategyId must be UPPER_SNAKE_CASE, got '" + value + "'");
    }
  }
}
