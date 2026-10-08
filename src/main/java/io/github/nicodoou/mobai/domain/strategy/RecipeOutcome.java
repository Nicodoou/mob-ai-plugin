package io.github.nicodoou.mobai.domain.strategy;

import java.util.Objects;

/** How a recipe plan went, for the model. */
public record RecipeOutcome(RecipePlay play, double success, long tick) {
  public RecipeOutcome {
    Objects.requireNonNull(play, "RecipeOutcome.play");
    if (!(success >= 0 && success <= 1)) {
      throw new IllegalArgumentException("RecipeOutcome.success must be in [0, 1], got " + success);
    }
    if (tick < 0) {
      throw new IllegalArgumentException(
          "RecipeOutcome.tick must be zero or positive, got " + tick);
    }
  }
}
