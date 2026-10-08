package io.github.nicodoou.mobai.domain.strategy;

/** The ranges the recipe search explores (CT-30). */
public record RecipeBounds(
    long minReserveDelayTicks, long maxReserveDelayTicks, double maxRetreatHealthFraction) {
  public RecipeBounds {
    if (minReserveDelayTicks < 1) {
      throw new IllegalArgumentException(
          "RecipeBounds.minReserveDelayTicks must be at least 1, got " + minReserveDelayTicks);
    }
    if (maxReserveDelayTicks <= minReserveDelayTicks) {
      throw new IllegalArgumentException(
          "RecipeBounds.maxReserveDelayTicks must exceed the minimum, got "
              + maxReserveDelayTicks
              + " <= "
              + minReserveDelayTicks);
    }
    if (!(maxRetreatHealthFraction > 0 && maxRetreatHealthFraction < 1)) {
      throw new IllegalArgumentException(
          "RecipeBounds.maxRetreatHealthFraction must be in (0, 1), got "
              + maxRetreatHealthFraction);
    }
  }
}
