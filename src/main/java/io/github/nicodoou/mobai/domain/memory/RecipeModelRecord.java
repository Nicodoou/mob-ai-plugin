package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import java.util.Objects;

/** One player's recipe model as last learned, and when (CT-30). */
public record RecipeModelRecord(LinearPosterior model, long lastTick) {
  public RecipeModelRecord {
    Objects.requireNonNull(model, "RecipeModelRecord.model");
    if (lastTick < 0) {
      throw new IllegalArgumentException(
          "RecipeModelRecord.lastTick must be zero or positive, got " + lastTick);
    }
  }
}
