package io.github.nicodoou.mobai.domain.strategy;

/**
 * Where the recipe model starts (CT-30, CT-33): the prior success in the bias and, with strength k,
 * a preference for the middle of every continuous knob, so sampled peaks seldom fall on an edge
 * before the model knows better (D-02).
 */
public final class RecipePrior {
  private static final int BIAS_INDEX = 0;
  private static final int[][] CONTINUOUS_KNOBS = {
    {RecipeFeatures.DELAY_LINEAR, RecipeFeatures.DELAY_SQUARE},
    {RecipeFeatures.RETREAT_LINEAR, RecipeFeatures.RETREAT_SQUARE},
    {RecipeFeatures.GROUP_RETREAT_LINEAR, RecipeFeatures.GROUP_RETREAT_SQUARE},
    {RecipeFeatures.CHASE_LINEAR, RecipeFeatures.CHASE_SQUARE}
  };
  private static final int LINEAR = 0;
  private static final int SQUARE = 1;
  private static final double NO_PREFERENCE = 0;

  private RecipePrior() {}

  /** The mean for the contextual model: the preference lives in the base block only. */
  public static double[] contextualMean(double priorSuccess, double midKnobPrior) {
    double[] mean = new double[ContextualFeatures.DIMENSION];
    fillBaseBlock(mean, priorSuccess, midKnobPrior);
    return mean;
  }

  /** The mean for a model over the recipe features alone (the simulation's flat model). */
  public static double[] recipeMean(double priorSuccess, double midKnobPrior) {
    double[] mean = new double[RecipeFeatures.DIMENSION];
    fillBaseBlock(mean, priorSuccess, midKnobPrior);
    return mean;
  }

  // k·x − k·x² peaks at x = 0.5, the middle of every knob.
  private static void fillBaseBlock(double[] mean, double priorSuccess, double midKnobPrior) {
    validate(priorSuccess, midKnobPrior);
    mean[BIAS_INDEX] = priorSuccess;
    for (int[] knob : CONTINUOUS_KNOBS) {
      mean[knob[LINEAR]] = midKnobPrior;
      // Subtracted from zero so that without a preference the mean stays +0 and not -0.
      mean[knob[SQUARE]] = NO_PREFERENCE - midKnobPrior;
    }
  }

  private static void validate(double priorSuccess, double midKnobPrior) {
    if (!(midKnobPrior >= 0) || !Double.isFinite(midKnobPrior)) {
      throw new IllegalArgumentException(
          "RecipePrior.midKnobPrior must be zero or positive, got " + midKnobPrior);
    }
    if (!(priorSuccess >= 0 && priorSuccess <= 1)) {
      throw new IllegalArgumentException(
          "RecipePrior.priorSuccess must be between 0 and 1, got " + priorSuccess);
    }
  }
}
