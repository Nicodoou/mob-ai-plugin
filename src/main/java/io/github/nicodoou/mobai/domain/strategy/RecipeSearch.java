package io.github.nicodoou.mobai.domain.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.ToDoubleFunction;

/** The playable recipe that scores best under one set of sampled weights (CT-30). */
public final class RecipeSearch {
  private static final int SPIDER_RESERVE = 0;
  private final Supplier<RecipeBounds> bounds;

  public RecipeSearch(Supplier<RecipeBounds> bounds) {
    this.bounds = Objects.requireNonNull(bounds, "RecipeSearch.bounds");
  }

  public PlanRecipe best(double[] weights, GroupComposition composition) {
    validate(weights, composition);
    RecipeBounds current = bounds.get();
    Tuning tuning = tuningFor(weights, current);
    ToDoubleFunction<PlanRecipe> score =
        recipe -> scoreOf(weights, RecipeFeatures.of(recipe, composition.skeletons(), current));
    return highestScoring(candidatesFor(composition, tuning), score);
  }

  private static void validate(double[] weights, GroupComposition composition) {
    if (weights.length != RecipeFeatures.DIMENSION) {
      throw new IllegalArgumentException(
          "RecipeSearch.weights must have "
              + RecipeFeatures.DIMENSION
              + " values, got "
              + weights.length);
    }
    if (composition.melee() + composition.skeletons() == 0) {
      throw new IllegalArgumentException("RecipeSearch needs at least one mob");
    }
  }

  private static Tuning tuningFor(double[] weights, RecipeBounds bounds) {
    double retreatPosition =
        UnitIntervalPeak.of(
            weights[RecipeFeatures.RETREAT_LINEAR], weights[RecipeFeatures.RETREAT_SQUARE]);
    double delayPosition =
        UnitIntervalPeak.of(
            weights[RecipeFeatures.DELAY_LINEAR], weights[RecipeFeatures.DELAY_SQUARE]);
    return new Tuning(
        reserveDelayAt(delayPosition, bounds),
        bounds.minReserveDelayTicks(),
        retreatPosition * bounds.maxRetreatHealthFraction());
  }

  // The inverse of the logarithmic delay feature, so the search lands on the delay the model chose.
  private static long reserveDelayAt(double position, RecipeBounds bounds) {
    double minimum = bounds.minReserveDelayTicks();
    return Math.round(minimum * Math.pow(bounds.maxReserveDelayTicks() / minimum, position));
  }

  private static List<PlanRecipe> candidatesFor(GroupComposition composition, Tuning tuning) {
    List<PlanRecipe> candidates = new ArrayList<>();
    for (RoleSplit zombies : zombieSplits(composition.zombies())) {
      for (RoleSplit spiders : spiderSplits(composition.spiders())) {
        for (boolean volley : volleyOptions(composition)) {
          candidates.add(
              new PlanRecipe(zombies, spiders, volley, tuning.delayFor(zombies), tuning.retreat()));
        }
      }
    }
    return candidates;
  }

  private static List<RoleSplit> zombieSplits(int zombies) {
    List<RoleSplit> splits = new ArrayList<>();
    for (int flank = 0; flank <= zombies; flank++) {
      for (int reserve = 0; reserve <= zombies - flank; reserve++) {
        splits.add(new RoleSplit(zombies - flank - reserve, flank, reserve));
      }
    }
    return splits;
  }

  private static List<RoleSplit> spiderSplits(int spiders) {
    List<RoleSplit> splits = new ArrayList<>();
    for (int flank = 0; flank <= spiders; flank++) {
      splits.add(new RoleSplit(spiders - flank, flank, SPIDER_RESERVE));
    }
    return splits;
  }

  private static List<Boolean> volleyOptions(GroupComposition composition) {
    if (VolleyStrategy.isViableFor(composition)) {
      return List.of(false, true);
    }
    return List.of(false);
  }

  private static PlanRecipe highestScoring(
      List<PlanRecipe> candidates, ToDoubleFunction<PlanRecipe> score) {
    PlanRecipe best = candidates.getFirst();
    double bestScore = score.applyAsDouble(best);
    for (PlanRecipe candidate : candidates) {
      double candidateScore = score.applyAsDouble(candidate);
      if (candidateScore > bestScore) {
        best = candidate;
        bestScore = candidateScore;
      }
    }
    return best;
  }

  private static double scoreOf(double[] weights, double[] features) {
    double score = 0;
    for (int index = 0; index < weights.length; index++) {
      score += weights[index] * features[index];
    }
    return score;
  }

  private record Tuning(long delayWithReserve, long delayWithoutReserve, double retreat) {
    long delayFor(RoleSplit zombies) {
      if (zombies.reserve() > 0) {
        return delayWithReserve;
      }
      return delayWithoutReserve;
    }
  }
}
