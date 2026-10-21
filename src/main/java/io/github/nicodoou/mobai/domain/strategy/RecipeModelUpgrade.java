package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import java.util.Objects;

/**
 * Brings a recipe model stored before CT-33 (15 features per block) up to the current features,
 * keeping everything it learned: the new parameters start as in an empty model.
 */
public final class RecipeModelUpgrade {
  public static final int LEGACY_FEATURES = 15;
  public static final int LEGACY_DIMENSION = LEGACY_FEATURES * ContextualFeatures.BLOCKS;

  private RecipeModelUpgrade() {}

  public static LinearPosterior toCurrent(LinearPosterior model, LinearPosterior prior) {
    Objects.requireNonNull(model, "RecipeModelUpgrade.model");
    Objects.requireNonNull(prior, "RecipeModelUpgrade.prior");
    if (prior.dimension() != ContextualFeatures.DIMENSION) {
      throw new IllegalArgumentException(
          "RecipeModelUpgrade.prior must have "
              + ContextualFeatures.DIMENSION
              + " parameters, got "
              + prior.dimension());
    }
    if (model.dimension() == ContextualFeatures.DIMENSION) {
      return model;
    }
    if (model.dimension() != LEGACY_DIMENSION) {
      throw new IllegalArgumentException(
          "RecipeModelUpgrade: a recipe model of "
              + model.dimension()
              + " parameters is neither the current "
              + ContextualFeatures.DIMENSION
              + " nor the legacy "
              + LEGACY_DIMENSION);
    }
    // What was learned before saw the new knobs at 0, so it knows nothing about them or their
    // crossings: the new parameters can start as in an empty model without changing any prediction.
    return LinearPosterior.of(
        enlargedPrecision(model.precision(), prior.precision()),
        enlargedInformation(model.information(), prior.information()),
        model.observations());
  }

  private static int newIndexOf(int legacyIndex) {
    int block = legacyIndex / LEGACY_FEATURES;
    return block * RecipeFeatures.DIMENSION + legacyIndex % LEGACY_FEATURES;
  }

  private static boolean isNew(int index) {
    return index % RecipeFeatures.DIMENSION >= LEGACY_FEATURES;
  }

  private static double[][] enlargedPrecision(double[][] legacy, double[][] prior) {
    int size = ContextualFeatures.DIMENSION;
    double[][] enlarged = new double[size][size];
    for (int a = 0; a < legacy.length; a++) {
      for (int c = 0; c < legacy.length; c++) {
        enlarged[newIndexOf(a)][newIndexOf(c)] = legacy[a][c];
      }
    }
    for (int index = 0; index < size; index++) {
      if (isNew(index)) {
        enlarged[index][index] = prior[index][index];
      }
    }
    return enlarged;
  }

  private static double[] enlargedInformation(double[] legacy, double[] prior) {
    double[] enlarged = new double[ContextualFeatures.DIMENSION];
    for (int index = 0; index < enlarged.length; index++) {
      if (isNew(index)) {
        enlarged[index] = prior[index];
      }
    }
    for (int a = 0; a < legacy.length; a++) {
      enlarged[newIndexOf(a)] = legacy[a];
    }
    return enlarged;
  }
}
