package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PlanStart(
    StrategyId strategy,
    PlayerId target,
    Map<MobId, Role> roles,
    double targetMaxHealth,
    long tick,
    Optional<RecipePlay> recipe) {
  public PlanStart {
    Objects.requireNonNull(strategy, "PlanStart.strategy");
    Objects.requireNonNull(target, "PlanStart.target");
    Objects.requireNonNull(roles, "PlanStart.roles");
    Objects.requireNonNull(recipe, "PlanStart.recipe");
    if (!(targetMaxHealth > 0) || !Double.isFinite(targetMaxHealth)) {
      throw new IllegalArgumentException(
          "PlanStart.targetMaxHealth must be a positive number, got " + targetMaxHealth);
    }
    if (tick < 0) {
      throw new IllegalArgumentException("PlanStart.tick must be zero or positive, got " + tick);
    }
    roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
  }
}
