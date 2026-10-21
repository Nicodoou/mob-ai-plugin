package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import org.junit.jupiter.api.Test;

class RecipeModelUpgradeTest {
  private static final double TOLERANCE = 1e-9;
  private static final double NOISE_VARIANCE = 0.01;
  private static final int LEGACY = RecipeModelUpgrade.LEGACY_DIMENSION;
  private static final int LEGACY_FEATURES = RecipeModelUpgrade.LEGACY_FEATURES;
  private static final LinearPosterior PRIOR =
      LinearPosterior.prior(RecipePrior.contextualMean(0.5, 2.0), 1.0);

  @Test
  void currentModelsPassUnchanged() {
    assertThat(RecipeModelUpgrade.toCurrent(PRIOR, PRIOR)).isEqualTo(PRIOR);
  }

  @Test
  void legacyLearningKeepsItsPlace() {
    LinearPosterior legacy = legacyModel();

    LinearPosterior upgraded = RecipeModelUpgrade.toCurrent(legacy, PRIOR);

    assertThat(upgraded.dimension()).isEqualTo(ContextualFeatures.DIMENSION);
    assertThat(upgraded.observations()).isCloseTo(2, within(TOLERANCE));
    double[][] legacyPrecision = legacy.precision();
    double[][] upgradedPrecision = upgraded.precision();
    for (int a = 0; a < LEGACY; a++) {
      assertThat(upgraded.information()[newIndexOf(a)])
          .isCloseTo(legacy.information()[a], within(TOLERANCE));
      for (int c = 0; c < LEGACY; c++) {
        assertThat(upgradedPrecision[newIndexOf(a)][newIndexOf(c)])
            .isCloseTo(legacyPrecision[a][c], within(TOLERANCE));
      }
    }
  }

  @Test
  void newParametersStartAsAnEmptyModel() {
    LinearPosterior upgraded = RecipeModelUpgrade.toCurrent(legacyModel(), PRIOR);
    double[][] precision = upgraded.precision();
    double[] information = upgraded.information();
    int[] newIndices = {15, 16, 17, 18, 34, 75};
    double[] expectedInformation = {2, -2, 2, -2, 0, 0};

    for (int position = 0; position < newIndices.length; position++) {
      int index = newIndices[position];
      assertThat(precision[index][index]).isCloseTo(1.0, within(TOLERANCE));
      assertThat(information[index]).isCloseTo(expectedInformation[position], within(TOLERANCE));
      assertThat(precision[index][0]).isCloseTo(0, within(TOLERANCE));
      assertThat(precision[0][index]).isCloseTo(0, within(TOLERANCE));
    }
  }

  @Test
  void predictionsWithTheNewKnobsAtZeroDoNotChange() {
    LinearPosterior legacy = legacyModel();
    double[] legacyFeatures = firstFeatures();
    double[] upgradedFeatures = new double[ContextualFeatures.DIMENSION];
    for (int a = 0; a < LEGACY; a++) {
      upgradedFeatures[newIndexOf(a)] = legacyFeatures[a];
    }

    LinearPosterior upgraded = RecipeModelUpgrade.toCurrent(legacy, PRIOR);

    assertThat(upgraded.predict(upgradedFeatures))
        .isCloseTo(legacy.predict(legacyFeatures), within(TOLERANCE));
  }

  @Test
  void unknownSizesAreRejected() {
    LinearPosterior tiny = LinearPosterior.prior(new double[10], 1.0);

    assertThatThrownBy(() -> RecipeModelUpgrade.toCurrent(tiny, PRIOR))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "RecipeModelUpgrade: a recipe model of 10 parameters is neither the current 76 nor"
                + " the legacy 60");
  }

  @Test
  void aPriorOfTheWrongSizeIsRejected() {
    LinearPosterior legacy = legacyModel();

    assertThatThrownBy(() -> RecipeModelUpgrade.toCurrent(PRIOR, legacy))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeModelUpgrade.prior must have 76 parameters, got 60");
  }

  static LinearPosterior legacyModel() {
    double[] mean = new double[LEGACY];
    mean[0] = 0.5;
    return LinearPosterior.prior(mean, 1.0)
        .withObservation(firstFeatures(), 0.8, NOISE_VARIANCE)
        .withObservation(secondFeatures(), 0.3, NOISE_VARIANCE);
  }

  private static double[] firstFeatures() {
    double[] features = new double[LEGACY];
    for (int i = 0; i < LEGACY; i++) {
      features[i] = (i + 1) / (double) LEGACY;
    }
    return features;
  }

  private static double[] secondFeatures() {
    double[] features = new double[LEGACY];
    for (int i = 0; i < LEGACY; i++) {
      features[i] = 1 - i / (double) LEGACY;
    }
    return features;
  }

  private static int newIndexOf(int legacyIndex) {
    return (legacyIndex / LEGACY_FEATURES) * RecipeFeatures.DIMENSION
        + legacyIndex % LEGACY_FEATURES;
  }
}
