package io.github.nicodoou.mobai.domain.strategy;

import java.util.Objects;

public record RecipeEstimate(PlanRecipe recipe, double predictedSuccess) {
  public RecipeEstimate {
    Objects.requireNonNull(recipe, "RecipeEstimate.recipe");
  }
}
