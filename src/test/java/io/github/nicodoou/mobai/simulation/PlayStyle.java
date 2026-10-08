package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeBounds;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import java.util.ArrayList;
import java.util.List;

/** A synthetic opponent: its traits and the true worth of each recipe against it. */
enum PlayStyle {
  SHIELD_BLOCKER(
      new PlayerTraits(1, 0, 0.5),
      new Coefficients(
          0.75,
          new Penalty(1.2, 0.5),
          new Penalty(0.8, 0.25),
          new Penalty(0.4, 0.5),
          new Penalty(0.5, 0.5),
          0.06,
          new Penalty(0.3, 0.5))),
  BERSERKER(
      new PlayerTraits(0, 0, 0.5),
      new Coefficients(
          0.70,
          new Penalty(0.9, 0),
          new Penalty(1.0, 0),
          new Penalty(0.3, 0.25),
          new Penalty(0.6, 0.15),
          -0.05,
          Penalty.NONE)),
  ARCHER(
      new PlayerTraits(0, 1, 0.5),
      new Coefficients(
          0.70,
          new Penalty(0.6, 0.25),
          new Penalty(0.6, 0),
          new Penalty(1.0, 1),
          new Penalty(0.4, 0.7),
          0.08,
          Penalty.NONE));

  private static final int RETREAT_STEPS = 20;
  private static final int DELAY_STEPS = 10;
  private static final double LEGACY_RETREAT_HEALTH_FRACTION = 0.3;
  private static final RoleSplit ALL_ZOMBIES_PRESS = new RoleSplit(4, 0, 0);
  private static final RoleSplit HALF_ZOMBIES_FLANK = new RoleSplit(2, 2, 0);
  private static final RoleSplit ALL_SPIDERS_PRESS = new RoleSplit(2, 0, 0);
  private static final RoleSplit HALF_SPIDERS_FLANK = new RoleSplit(1, 1, 0);
  private static final RoleSplit ALL_SPIDERS_FLANK = new RoleSplit(0, 2, 0);
  private static final int SPIDER_RESERVE = 0;
  private static final double PRESENT = 1;
  private static final double ABSENT = 0;
  private static final double UNIT_MIN = 0;
  private static final double UNIT_MAX = 1;

  private final PlayerTraits traits;
  private final Coefficients coefficients;
  private final double optimum;

  PlayStyle(PlayerTraits traits, Coefficients coefficients) {
    this.traits = traits;
    this.coefficients = coefficients;
    this.optimum = bestTrueScoreOf(gridRecipes());
  }

  PlayerTraits traits() {
    return traits;
  }

  double trueScore(PlanRecipe recipe) {
    return coefficients.scoreOf(Knobs.of(recipe));
  }

  double optimum() {
    return optimum;
  }

  double legacyRegret() {
    return optimum - bestTrueScoreOf(legacyRecipes());
  }

  PlayerTraits measuredTraits(RandomSource random, double deviation) {
    return perturbed(traits, random, deviation);
  }

  static PlayerTraits perturbed(PlayerTraits traits, RandomSource random, double deviation) {
    double shield = clampToUnit(traits.shield() + deviation * random.nextGaussian());
    double ranged = clampToUnit(traits.ranged() + deviation * random.nextGaussian());
    double armor = clampToUnit(traits.armor() + deviation * random.nextGaussian());
    return new PlayerTraits(shield, ranged, armor);
  }

  static double clampToUnit(double value) {
    return Math.clamp(value, UNIT_MIN, UNIT_MAX);
  }

  private double bestTrueScoreOf(List<PlanRecipe> recipes) {
    return recipes.stream().mapToDouble(this::trueScore).max().orElseThrow();
  }

  private static List<PlanRecipe> gridRecipes() {
    List<PlanRecipe> recipes = new ArrayList<>();
    for (RoleSplit zombies : zombieSplits(RecipeLearner.GROUP.zombies())) {
      for (RoleSplit spiders : spiderSplits(RecipeLearner.GROUP.spiders())) {
        recipes.addAll(tunedRecipes(zombies, spiders));
      }
    }
    return recipes;
  }

  private static List<PlanRecipe> tunedRecipes(RoleSplit zombies, RoleSplit spiders) {
    List<PlanRecipe> recipes = new ArrayList<>();
    for (boolean volley : List.of(false, true)) {
      for (long delayTicks : delaysFor(zombies)) {
        for (int step = 0; step <= RETREAT_STEPS; step++) {
          double retreat = retreatAt((double) step / RETREAT_STEPS);
          recipes.add(new PlanRecipe(zombies, spiders, volley, delayTicks, retreat));
        }
      }
    }
    return recipes;
  }

  private static List<Long> delaysFor(RoleSplit zombies) {
    RecipeBounds bounds = RecipeLearner.BOUNDS;
    if (zombies.reserve() == 0) {
      return List.of(bounds.minReserveDelayTicks());
    }
    List<Long> delays = new ArrayList<>();
    for (int step = 0; step <= DELAY_STEPS; step++) {
      delays.add(delayAt((double) step / DELAY_STEPS));
    }
    return delays;
  }

  private static long delayAt(double position) {
    RecipeBounds bounds = RecipeLearner.BOUNDS;
    double minimum = bounds.minReserveDelayTicks();
    return Math.round(minimum * Math.pow(bounds.maxReserveDelayTicks() / minimum, position));
  }

  private static double retreatAt(double position) {
    return position * RecipeLearner.BOUNDS.maxRetreatHealthFraction();
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

  private static List<PlanRecipe> legacyRecipes() {
    return List.of(
        legacyRecipe(ALL_ZOMBIES_PRESS, ALL_SPIDERS_PRESS, false),
        legacyRecipe(HALF_ZOMBIES_FLANK, HALF_SPIDERS_FLANK, false),
        legacyRecipe(ALL_ZOMBIES_PRESS, ALL_SPIDERS_FLANK, false),
        legacyRecipe(ALL_ZOMBIES_PRESS, ALL_SPIDERS_PRESS, true));
  }

  // The four strategies of today never hold a reserve, so the delay is the shortest one.
  private static PlanRecipe legacyRecipe(RoleSplit zombies, RoleSplit spiders, boolean volley) {
    return new PlanRecipe(
        zombies,
        spiders,
        volley,
        RecipeLearner.BOUNDS.minReserveDelayTicks(),
        LEGACY_RETREAT_HEALTH_FRACTION);
  }

  private record Penalty(double weight, double target) {
    static final Penalty NONE = new Penalty(0, 0);

    double of(double value) {
      double distance = value - target;
      return weight * distance * distance;
    }
  }

  private record Coefficients(
      double base,
      Penalty zombieFlank,
      Penalty zombieReserve,
      Penalty spiderFlank,
      Penalty retreat,
      double volleyBonus,
      Penalty delay) {
    double scoreOf(Knobs knobs) {
      return base
          - zombieFlank.of(knobs.zombieFlank())
          - zombieReserve.of(knobs.zombieReserve())
          - spiderFlank.of(knobs.spiderFlank())
          - retreat.of(knobs.retreat())
          + volleyBonus * knobs.volley()
          - delayPenaltyOf(knobs);
    }

    private double delayPenaltyOf(Knobs knobs) {
      if (!knobs.hasReserve()) {
        return 0;
      }
      return delay.of(knobs.delayPosition());
    }
  }

  private record Knobs(
      double zombieFlank,
      double zombieReserve,
      double spiderFlank,
      double retreat,
      double volley,
      boolean hasReserve,
      double delayPosition) {
    static Knobs of(PlanRecipe recipe) {
      RoleSplit zombies = recipe.zombies();
      RoleSplit spiders = recipe.spiders();
      return new Knobs(
          fraction(zombies.flank(), zombies.total()),
          fraction(zombies.reserve(), zombies.total()),
          fraction(spiders.flank(), spiders.total()),
          recipe.retreatHealthFraction() / RecipeLearner.BOUNDS.maxRetreatHealthFraction(),
          recipe.volley() ? PRESENT : ABSENT,
          zombies.reserve() > 0,
          delayPositionOf(recipe.reserveDelayTicks()));
    }

    private static double delayPositionOf(long delayTicks) {
      RecipeBounds bounds = RecipeLearner.BOUNDS;
      double minimum = bounds.minReserveDelayTicks();
      return Math.log(delayTicks / minimum) / Math.log(bounds.maxReserveDelayTicks() / minimum);
    }

    private static double fraction(int part, int whole) {
      if (whole == 0) {
        return ABSENT;
      }
      return (double) part / whole;
    }
  }
}
