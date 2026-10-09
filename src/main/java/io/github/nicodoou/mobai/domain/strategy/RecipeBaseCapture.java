package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** A copy of the server base: its model and who is in training. */
public record RecipeBaseCapture(Optional<LinearPosterior> model, List<PlayerId> trainers) {
  public RecipeBaseCapture {
    Objects.requireNonNull(model, "RecipeBaseCapture.model");
    trainers = List.copyOf(trainers);
  }
}
