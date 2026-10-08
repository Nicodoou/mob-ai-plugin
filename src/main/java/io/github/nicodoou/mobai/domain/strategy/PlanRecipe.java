package io.github.nicodoou.mobai.domain.strategy;

import java.util.Objects;

/**
 * A plan as played (CT-30): roles by kind, volley, when the reserve joins and when mobs retreat.
 */
public record PlanRecipe(
    RoleSplit zombies,
    RoleSplit spiders,
    boolean volley,
    long reserveDelayTicks,
    double retreatHealthFraction) {
  public PlanRecipe {
    Objects.requireNonNull(zombies, "PlanRecipe.zombies");
    Objects.requireNonNull(spiders, "PlanRecipe.spiders");
    if (spiders.reserve() != 0) {
      throw new IllegalArgumentException(
          "PlanRecipe.spiders cannot keep a reserve, got " + spiders.reserve());
    }
    if (reserveDelayTicks < 1) {
      throw new IllegalArgumentException(
          "PlanRecipe.reserveDelayTicks must be at least 1, got " + reserveDelayTicks);
    }
    if (!(retreatHealthFraction >= 0 && retreatHealthFraction < 1)) {
      throw new IllegalArgumentException(
          "PlanRecipe.retreatHealthFraction must be in [0, 1), got " + retreatHealthFraction);
    }
  }
}
