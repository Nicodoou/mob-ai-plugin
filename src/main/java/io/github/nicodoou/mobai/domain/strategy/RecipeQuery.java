package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import java.util.Objects;

public record RecipeQuery(
    LinearPosterior model, PlayerTraits traits, GroupComposition composition) {
  public RecipeQuery {
    Objects.requireNonNull(model, "RecipeQuery.model");
    Objects.requireNonNull(traits, "RecipeQuery.traits");
    Objects.requireNonNull(composition, "RecipeQuery.composition");
  }
}
