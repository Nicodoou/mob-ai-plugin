package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeEstimate;
import java.util.List;
import java.util.Objects;

public record RecipeAdvice(PlayerTraits traits, double observations, List<RecipeEstimate> best) {
  public RecipeAdvice {
    Objects.requireNonNull(traits, "RecipeAdvice.traits");
    best = List.copyOf(best);
  }
}
