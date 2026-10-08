package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class ContextualFeaturesTest {
  private static final double WEIGHT_STEP = 0.01;

  @Test
  void blocksAreTheFeaturesScaledByEachTrait() {
    double[] features = new double[RecipeFeatures.DIMENSION];
    for (int i = 0; i < features.length; i++) {
      features[i] = i + 1;
    }

    double[] contextual = ContextualFeatures.of(features, new PlayerTraits(0.5, 0.25, 1));

    assertThat(contextual).hasSize(ContextualFeatures.DIMENSION);
    assertThat(contextual[0]).isEqualTo(1, within(1e-9));
    assertThat(contextual[15]).isEqualTo(0.5, within(1e-9));
    assertThat(contextual[30]).isEqualTo(0.25, within(1e-9));
    assertThat(contextual[45]).isEqualTo(1, within(1e-9));
    assertThat(contextual[59]).isEqualTo(15, within(1e-9));
  }

  @Test
  void foldedWeightsGiveTheSameScore() {
    double[] weights = new double[ContextualFeatures.DIMENSION];
    for (int i = 0; i < weights.length; i++) {
      weights[i] = WEIGHT_STEP * (i + 1);
    }
    double[] features = new double[RecipeFeatures.DIMENSION];
    for (int i = 0; i < features.length; i++) {
      features[i] = 1.0 / (i + 1);
    }
    var traits = new PlayerTraits(0.3, 0.7, 0.2);

    double contextualScore = dot(weights, ContextualFeatures.of(features, traits));
    double foldedScore = dot(ContextualFeatures.weightsFor(weights, traits), features);

    assertThat(foldedScore).isEqualTo(contextualScore, within(1e-12));
  }

  @Test
  void wrongLengthsAreRejected() {
    var traits = new PlayerTraits(0, 0, 0);

    assertThatThrownBy(() -> ContextualFeatures.of(new double[14], traits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ContextualFeatures.recipeFeatures must have 15 values, got 14");
    assertThatThrownBy(() -> ContextualFeatures.weightsFor(new double[59], traits))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ContextualFeatures.weights must have 60 values, got 59");
  }

  private static double dot(double[] left, double[] right) {
    double sum = 0;
    for (int i = 0; i < left.length; i++) {
      sum += left[i] * right[i];
    }
    return sum;
  }
}
