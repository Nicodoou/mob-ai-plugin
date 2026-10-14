package io.github.nicodoou.mobai.domain.strategy;

import java.util.Objects;

/**
 * A plan as played (CT-30, CT-33): roles by kind, volley, when the reserve joins, when mobs retreat
 * alone and together, and how far they chase.
 */
public record PlanRecipe(
    RoleSplit zombies,
    RoleSplit spiders,
    boolean volley,
    long reserveDelayTicks,
    double retreatHealthFraction,
    double groupRetreatStrength,
    double chaseCommitment) {
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
    if (!(groupRetreatStrength >= 0 && groupRetreatStrength < 1)) {
      throw new IllegalArgumentException(
          "PlanRecipe.groupRetreatStrength must be in [0, 1), got " + groupRetreatStrength);
    }
    if (!(chaseCommitment >= 0 && chaseCommitment <= 1)) {
      throw new IllegalArgumentException(
          "PlanRecipe.chaseCommitment must be in [0, 1], got " + chaseCommitment);
    }
  }
}
