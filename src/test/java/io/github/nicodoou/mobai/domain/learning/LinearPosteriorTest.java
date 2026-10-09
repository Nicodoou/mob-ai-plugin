package io.github.nicodoou.mobai.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import org.junit.jupiter.api.Test;

class LinearPosteriorTest {
  private static final double[] TRUE_WEIGHTS = {0.2, -0.5, 0.8};
  private static final double[][] TRAINING_FEATURES = {
    {1, 0, 0}, {0, 1, 0}, {0, 0, 1}, {1, 1, 0}, {0, 1, 1}, {1, 0.5, 0.25}
  };
  private static final double DEVIATION_FIRST = -0.6963106238227916;
  private static final double DEVIATION_SECOND = 0.5222329678670936;

  private static LinearPosterior oneObservation() {
    return LinearPosterior.prior(new double[] {0, 0}, 1)
        .withObservation(new double[] {1, 2}, 1, 0.5);
  }

  @Test
  void modelsWithTheSameContentAreEqual() {
    LinearPosterior first = oneObservation();
    LinearPosterior second = oneObservation();

    assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
  }

  @Test
  void aDifferentObservationMakesADifferentModel() {
    LinearPosterior prior = LinearPosterior.prior(new double[] {0, 0}, 1);

    LinearPosterior high = prior.withObservation(new double[] {1, 2}, 0.7, 0.5);
    LinearPosterior low = prior.withObservation(new double[] {1, 2}, 0.2, 0.5);

    assertThat(high).isNotEqualTo(low);
  }

  @Test
  void priorMeanIsTheGivenMean() {
    LinearPosterior prior = LinearPosterior.prior(new double[] {0.5, 0, -1}, 4);

    double[] mean = prior.mean();

    assertThat(mean).containsExactly(new double[] {0.5, 0, -1}, within(1e-9));
    assertThat(prior.observations()).isZero();
  }

  @Test
  void oneObservationGivesTheKnownPosteriorMean() {
    LinearPosterior prior = LinearPosterior.prior(new double[] {0, 0}, 1);

    LinearPosterior posterior = prior.withObservation(new double[] {1, 2}, 1, 0.5);

    double[][] precision = posterior.precision();
    assertThat(precision[0]).containsExactly(new double[] {3, 4}, within(1e-9));
    assertThat(precision[1]).containsExactly(new double[] {4, 9}, within(1e-9));
    assertThat(posterior.information()).containsExactly(new double[] {2, 4}, within(1e-9));
    assertThat(posterior.mean()).containsExactly(new double[] {2.0 / 11, 4.0 / 11}, within(1e-9));
    assertThat(posterior.observations()).isCloseTo(1, within(1e-9));
  }

  @Test
  void aNonZeroPriorMeanIsWeighedByItsPrecision() {
    LinearPosterior prior = LinearPosterior.prior(new double[] {1, 0}, 2);

    LinearPosterior posterior = prior.withObservation(new double[] {1, 0}, 3, 1);

    assertThat(posterior.mean()).containsExactly(new double[] {7.0 / 3, 0}, within(1e-9));
  }

  @Test
  void manyObservationsRecoverTheTrueWeights() {
    LinearPosterior posterior = LinearPosterior.prior(new double[] {0, 0, 0}, 10);

    for (int round = 0; round < 50; round++) {
      for (double[] features : TRAINING_FEATURES) {
        posterior = posterior.withObservation(features, dot(TRUE_WEIGHTS, features), 0.01);
      }
    }

    assertThat(posterior.mean()).containsExactly(TRUE_WEIGHTS, within(1e-3));
  }

  @Test
  void predictIsTheMeanTimesTheFeatures() {
    LinearPosterior posterior = oneObservation();

    double prediction = posterior.predict(new double[] {1, 3});

    assertThat(prediction).isCloseTo(14.0 / 11, within(1e-9));
  }

  @Test
  void shrinkingHalfwayTowardThePrior() {
    LinearPosterior posterior = oneObservation();

    LinearPosterior shrunk =
        posterior.shrunkToward(LinearPosterior.prior(new double[] {0, 0}, 1), 0.5);

    double[][] precision = shrunk.precision();
    assertThat(precision[0]).containsExactly(new double[] {2, 2}, within(1e-9));
    assertThat(precision[1]).containsExactly(new double[] {2, 5}, within(1e-9));
    assertThat(shrunk.mean()).containsExactly(new double[] {1.0 / 6, 1.0 / 3}, within(1e-9));
    assertThat(shrunk.observations()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void keepOneChangesNothingAndKeepZeroIsTheAnchor() {
    LinearPosterior posterior = oneObservation();
    LinearPosterior anchor = LinearPosterior.prior(new double[] {1, -1}, 2);

    LinearPosterior kept = posterior.shrunkToward(anchor, 1);
    LinearPosterior forgotten = posterior.shrunkToward(anchor, 0);

    assertThat(kept.mean()).containsExactly(posterior.mean(), within(1e-9));
    assertThat(forgotten.mean()).containsExactly(anchor.mean(), within(1e-9));
  }

  @Test
  void sampleWithZeroDrawsIsTheMean() {
    LinearPosterior posterior = oneObservation();
    ScriptedRandomSource random = new ScriptedRandomSource().withGaussians(0, 0);

    double[] sample = posterior.sample(random, 1);

    assertThat(sample).containsExactly(posterior.mean(), within(1e-9));
    assertThat(random.isExhausted()).isTrue();
  }

  @Test
  void sampleUsesTheInverseTransposedCholeskyFactor() {
    LinearPosterior posterior = oneObservation();
    ScriptedRandomSource random = new ScriptedRandomSource().withGaussians(0, 1, 0, 1);

    double[] unitScale = posterior.sample(random, 1);
    double[] fourfoldScale = posterior.sample(random, 4);

    assertThat(unitScale)
        .containsExactly(
            new double[] {2.0 / 11 + DEVIATION_FIRST, 4.0 / 11 + DEVIATION_SECOND}, within(1e-9));
    assertThat(fourfoldScale)
        .containsExactly(
            new double[] {2.0 / 11 + 2 * DEVIATION_FIRST, 4.0 / 11 + 2 * DEVIATION_SECOND},
            within(1e-9));
  }

  @Test
  void sampleCovarianceIsTheInversePrecision() {
    LinearPosterior posterior = oneObservation();
    SeededRandomSource random = new SeededRandomSource(33);
    int sampleCount = 20_000;
    double[][] samples = new double[sampleCount][];

    for (int i = 0; i < sampleCount; i++) {
      samples[i] = posterior.sample(random, 1);
    }

    double[][] covariance = empiricalCovariance(samples);
    assertThat(covariance[0]).containsExactly(new double[] {9.0 / 11, -4.0 / 11}, within(0.02));
    assertThat(covariance[1]).containsExactly(new double[] {-4.0 / 11, 3.0 / 11}, within(0.02));
  }

  @Test
  void restoredModelHasTheSameMean() {
    LinearPosterior posterior = oneObservation();

    LinearPosterior restored =
        LinearPosterior.of(
            posterior.precision(), posterior.information(), posterior.observations());
    posterior.precision()[0][0] = 100;
    posterior.information()[0] = 100;

    assertThat(restored.mean()).containsExactly(posterior.mean(), within(1e-9));
    assertThat(restored.observations()).isCloseTo(posterior.observations(), within(1e-9));
    assertThat(posterior.precision()[0]).containsExactly(new double[] {3, 4}, within(1e-9));
    assertThat(posterior.information()).containsExactly(new double[] {2, 4}, within(1e-9));
  }

  @Test
  void invalidInputsAreRejected() {
    LinearPosterior posterior = oneObservation();
    double[] features = {1, 1};
    double[][] identity = {{1, 0}, {0, 1}};
    ScriptedRandomSource random = new ScriptedRandomSource();

    assertThatThrownBy(() -> posterior.withObservation(new double[] {1, 1, 1}, 1, 1))
        .hasMessage("LinearPosterior.features must have 2 values, got 3");
    assertThatThrownBy(() -> posterior.withObservation(new double[] {1, Double.NaN}, 1, 1))
        .hasMessage("LinearPosterior.features must be finite");
    assertThatThrownBy(() -> posterior.withObservation(features, Double.POSITIVE_INFINITY, 1))
        .hasMessage("LinearPosterior.reward must be finite, got Infinity");
    assertThatThrownBy(() -> posterior.withObservation(features, 1, 0))
        .hasMessage("LinearPosterior.noiseVariance must be a positive number, got 0.0");
    assertThatThrownBy(() -> posterior.shrunkToward(posterior, 1.5))
        .hasMessage("LinearPosterior.keep must be between 0 and 1, got 1.5");
    assertThatThrownBy(
            () -> posterior.shrunkToward(LinearPosterior.prior(new double[] {0, 0, 0}, 1), 0.5))
        .hasMessage("LinearPosterior.anchor must have 2 dimensions, got 3");
    assertThatThrownBy(() -> posterior.sample(random, 0))
        .hasMessage("LinearPosterior.explorationScale must be a positive number, got 0.0");
    assertThatThrownBy(() -> LinearPosterior.prior(new double[] {0, 0}, 0))
        .hasMessage("LinearPosterior.variance must be a positive number, got 0.0");
    assertThatThrownBy(() -> LinearPosterior.prior(new double[] {}, 1))
        .hasMessage("LinearPosterior.mean must not be empty");
    assertThatThrownBy(() -> LinearPosterior.of(identity, new double[] {0, 0}, -1))
        .hasMessage("LinearPosterior.observations must be zero or positive, got -1.0");
    assertThatThrownBy(() -> LinearPosterior.of(identity, new double[] {0, 0, 0}, 0))
        .hasMessage("LinearPosterior.information must have 2 values, got 3");
  }

  private static double dot(double[] left, double[] right) {
    double sum = 0;
    for (int i = 0; i < left.length; i++) {
      sum += left[i] * right[i];
    }
    return sum;
  }

  private static double[][] empiricalCovariance(double[][] samples) {
    int dimension = samples[0].length;
    double[] average = new double[dimension];
    for (double[] sample : samples) {
      for (int i = 0; i < dimension; i++) {
        average[i] += sample[i] / samples.length;
      }
    }
    double[][] covariance = new double[dimension][dimension];
    for (double[] sample : samples) {
      for (int i = 0; i < dimension; i++) {
        for (int j = 0; j < dimension; j++) {
          covariance[i][j] += (sample[i] - average[i]) * (sample[j] - average[j]) / samples.length;
        }
      }
    }
    return covariance;
  }
}
