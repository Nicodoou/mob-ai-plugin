package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A recipe as handed out: what was chosen, against whom, what the model will learn from, and who
 * does what.
 */
public record RecipePlay(
    PlanRecipe recipe,
    PlayerTraits traits,
    List<Double> features,
    Map<MobId, Role> roles,
    Set<MobId> reserve) {
  public RecipePlay {
    Objects.requireNonNull(recipe, "RecipePlay.recipe");
    Objects.requireNonNull(traits, "RecipePlay.traits");
    Objects.requireNonNull(features, "RecipePlay.features");
    Objects.requireNonNull(roles, "RecipePlay.roles");
    Objects.requireNonNull(reserve, "RecipePlay.reserve");
    if (features.size() != ContextualFeatures.DIMENSION) {
      throw new IllegalArgumentException(
          "RecipePlay.features must have "
              + ContextualFeatures.DIMENSION
              + " values, got "
              + features.size());
    }
    features = List.copyOf(features);
    roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    reserve = Set.copyOf(reserve);
  }
}
