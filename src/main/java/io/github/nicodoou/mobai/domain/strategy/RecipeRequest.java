package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.Objects;

/**
 * What the planner needs for one plan: the model, the target's traits, the group and the target.
 */
public record RecipeRequest(
    LinearPosterior model, PlayerTraits traits, GroupSnapshot snapshot, PlayerId target) {
  public RecipeRequest {
    Objects.requireNonNull(model, "RecipeRequest.model");
    Objects.requireNonNull(traits, "RecipeRequest.traits");
    Objects.requireNonNull(snapshot, "RecipeRequest.snapshot");
    Objects.requireNonNull(target, "RecipeRequest.target");
  }
}
