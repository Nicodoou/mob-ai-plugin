package io.github.nicodoou.mobai.domain.strategy;

/**
 * Recipe features crossed with the player's traits, and the sampled weights folded back (CT-30).
 */
public final class ContextualFeatures {
  public static final int BLOCKS = 4;
  public static final int DIMENSION = RecipeFeatures.DIMENSION * BLOCKS;
  private static final double BASE_SCALE = 1;

  private ContextualFeatures() {}

  public static double[] of(double[] recipeFeatures, PlayerTraits traits) {
    requireLength("recipeFeatures", recipeFeatures, RecipeFeatures.DIMENSION);
    double[] scales = blockScales(traits);
    double[] contextual = new double[DIMENSION];
    for (int block = 0; block < BLOCKS; block++) {
      for (int i = 0; i < RecipeFeatures.DIMENSION; i++) {
        contextual[block * RecipeFeatures.DIMENSION + i] = recipeFeatures[i] * scales[block];
      }
    }
    return contextual;
  }

  public static double[] weightsFor(double[] weights, PlayerTraits traits) {
    requireLength("weights", weights, DIMENSION);
    double[] scales = blockScales(traits);
    double[] folded = new double[RecipeFeatures.DIMENSION];
    for (int block = 0; block < BLOCKS; block++) {
      for (int i = 0; i < RecipeFeatures.DIMENSION; i++) {
        folded[i] += weights[block * RecipeFeatures.DIMENSION + i] * scales[block];
      }
    }
    return folded;
  }

  private static double[] blockScales(PlayerTraits traits) {
    return new double[] {BASE_SCALE, traits.shield(), traits.ranged(), traits.armor()};
  }

  private static void requireLength(String name, double[] values, int expected) {
    if (values.length != expected) {
      throw new IllegalArgumentException(
          "ContextualFeatures."
              + name
              + " must have "
              + expected
              + " values, got "
              + values.length);
    }
  }
}
