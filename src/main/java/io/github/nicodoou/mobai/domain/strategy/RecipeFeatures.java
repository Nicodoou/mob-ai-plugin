package io.github.nicodoou.mobai.domain.strategy;

/** The numbers the recipe model weighs: each knob with its square, plus chosen interactions. */
public final class RecipeFeatures {
  public static final int DIMENSION = 15;
  public static final int DELAY_LINEAR = 7;
  public static final int DELAY_SQUARE = 8;
  public static final int RETREAT_LINEAR = 9;
  public static final int RETREAT_SQUARE = 10;
  private static final double BIAS = 1;
  private static final double PRESENT = 1;
  private static final double ABSENT = 0;

  private RecipeFeatures() {}

  public static double[] of(PlanRecipe recipe, int skeletons, RecipeBounds bounds) {
    validateCounts(recipe, skeletons);
    validateDelay(recipe, bounds);
    validateRetreat(recipe, bounds);
    return vectorOf(knobsOf(recipe, skeletons, bounds));
  }

  private static void validateCounts(PlanRecipe recipe, int skeletons) {
    if (skeletons < 0) {
      throw new IllegalArgumentException(
          "RecipeFeatures.skeletons must be zero or positive, got " + skeletons);
    }
    if (meleeOf(recipe) + skeletons == 0) {
      throw new IllegalArgumentException("RecipeFeatures needs at least one mob");
    }
  }

  private static void validateDelay(PlanRecipe recipe, RecipeBounds bounds) {
    long delay = recipe.reserveDelayTicks();
    boolean isDelayOutside =
        delay < bounds.minReserveDelayTicks() || delay > bounds.maxReserveDelayTicks();
    if (hasReserve(recipe) && isDelayOutside) {
      throw new IllegalArgumentException(
          "RecipeFeatures.reserveDelayTicks must be between "
              + bounds.minReserveDelayTicks()
              + " and "
              + bounds.maxReserveDelayTicks()
              + ", got "
              + delay);
    }
  }

  private static void validateRetreat(PlanRecipe recipe, RecipeBounds bounds) {
    if (recipe.retreatHealthFraction() > bounds.maxRetreatHealthFraction()) {
      throw new IllegalArgumentException(
          "RecipeFeatures.retreatHealthFraction must not exceed "
              + bounds.maxRetreatHealthFraction()
              + ", got "
              + recipe.retreatHealthFraction());
    }
  }

  private static Knobs knobsOf(PlanRecipe recipe, int skeletons, RecipeBounds bounds) {
    RoleSplit zombies = recipe.zombies();
    RoleSplit spiders = recipe.spiders();
    return new Knobs(
        fraction(zombies.flank(), zombies.total()),
        fraction(zombies.reserve(), zombies.total()),
        fraction(spiders.flank(), spiders.total()),
        delayPositionOf(recipe, bounds),
        recipe.retreatHealthFraction() / bounds.maxRetreatHealthFraction(),
        recipe.volley() ? PRESENT : ABSENT,
        fraction(skeletons, meleeOf(recipe) + skeletons),
        fraction(zombies.flank() + spiders.flank(), meleeOf(recipe)));
  }

  private static double[] vectorOf(Knobs knobs) {
    return new double[] {
      BIAS,
      knobs.zombieFlank(),
      square(knobs.zombieFlank()),
      knobs.zombieReserve(),
      square(knobs.zombieReserve()),
      knobs.spiderFlank(),
      square(knobs.spiderFlank()),
      knobs.delayPosition(),
      square(knobs.delayPosition()),
      knobs.retreat(),
      square(knobs.retreat()),
      knobs.volley(),
      knobs.zombieFlank() * knobs.zombieReserve(),
      knobs.volley() * knobs.skeletonShare(),
      knobs.meleeFlank() * knobs.skeletonShare()
    };
  }

  // Logarithmic so that doubling the delay moves the knob the same amount anywhere in the range.
  private static double delayPositionOf(PlanRecipe recipe, RecipeBounds bounds) {
    if (!hasReserve(recipe)) {
      return ABSENT;
    }
    double minimum = bounds.minReserveDelayTicks();
    double range = Math.log(bounds.maxReserveDelayTicks() / minimum);
    return Math.log(recipe.reserveDelayTicks() / minimum) / range;
  }

  private static boolean hasReserve(PlanRecipe recipe) {
    return recipe.zombies().reserve() > 0;
  }

  private static int meleeOf(PlanRecipe recipe) {
    return recipe.zombies().total() + recipe.spiders().total();
  }

  private static double fraction(int part, int whole) {
    if (whole == 0) {
      return ABSENT;
    }
    return (double) part / whole;
  }

  private static double square(double value) {
    return value * value;
  }

  private record Knobs(
      double zombieFlank,
      double zombieReserve,
      double spiderFlank,
      double delayPosition,
      double retreat,
      double volley,
      double skeletonShare,
      double meleeFlank) {}
}
