package io.github.nicodoou.mobai.domain.learning;

import io.github.nicodoou.mobai.domain.port.RandomSource;

/**
 * Bayesian linear regression with known noise, kept in information form: precision and precision
 * times mean (CT-30).
 */
public final class LinearPosterior {
  private final double[][] precision;
  private final double[] information;
  private final double observations;

  private LinearPosterior(double[][] precision, double[] information, double observations) {
    this.precision = precision;
    this.information = information;
    this.observations = observations;
  }

  public static LinearPosterior prior(double[] mean, double variance) {
    requireMean(mean);
    requirePositiveVariance(variance);
    double[][] precision = new double[mean.length][mean.length];
    double[] information = new double[mean.length];
    for (int i = 0; i < mean.length; i++) {
      precision[i][i] = 1 / variance;
      information[i] = mean[i] / variance;
    }
    return new LinearPosterior(precision, information, 0);
  }

  public static LinearPosterior of(
      double[][] precision, double[] information, double observations) {
    requireInformationLength(information, precision.length);
    requireObservations(observations);
    Cholesky.of(precision);
    return new LinearPosterior(copyOf(precision), information.clone(), observations);
  }

  public int dimension() {
    return information.length;
  }

  public double observations() {
    return observations;
  }

  public double[] mean() {
    return Cholesky.of(precision).solve(information);
  }

  public double predict(double[] features) {
    requireFeatures(features);
    return dot(mean(), features);
  }

  public double[][] precision() {
    return copyOf(precision);
  }

  public double[] information() {
    return information.clone();
  }

  public LinearPosterior withObservation(double[] features, double reward, double noiseVariance) {
    requireFeatures(features);
    requireFiniteReward(reward);
    requirePositiveNoiseVariance(noiseVariance);
    return new LinearPosterior(
        plusOuterProduct(features, 1 / noiseVariance),
        plusScaled(information, features, reward / noiseVariance),
        observations + 1);
  }

  public LinearPosterior shrunkToward(LinearPosterior anchor, double keep) {
    requireSameDimension(anchor);
    requireKeep(keep);
    return new LinearPosterior(
        blendedMatrix(anchor.precision, precision, keep),
        blendedVector(anchor.information, information, keep),
        observations * keep);
  }

  public double[] sample(RandomSource random, double explorationScale) {
    requirePositiveExplorationScale(explorationScale);
    double[] draws = standardNormals(random, dimension());
    double[] deviation = Cholesky.of(precision).solveTransposed(draws);
    return plusScaled(mean(), deviation, Math.sqrt(explorationScale));
  }

  private double[][] plusOuterProduct(double[] features, double scale) {
    double[][] result = copyOf(precision);
    for (int i = 0; i < features.length; i++) {
      for (int j = 0; j < features.length; j++) {
        result[i][j] += features[i] * features[j] * scale;
      }
    }
    return result;
  }

  private static double[] plusScaled(double[] base, double[] addend, double scale) {
    double[] result = base.clone();
    for (int i = 0; i < result.length; i++) {
      result[i] += addend[i] * scale;
    }
    return result;
  }

  private static double[][] blendedMatrix(double[][] anchor, double[][] current, double keep) {
    double[][] result = new double[anchor.length][];
    for (int i = 0; i < anchor.length; i++) {
      result[i] = blendedVector(anchor[i], current[i], keep);
    }
    return result;
  }

  private static double[] blendedVector(double[] anchor, double[] current, double keep) {
    double[] result = new double[anchor.length];
    for (int i = 0; i < anchor.length; i++) {
      result[i] = anchor[i] + keep * (current[i] - anchor[i]);
    }
    return result;
  }

  private static double[] standardNormals(RandomSource random, int count) {
    double[] draws = new double[count];
    for (int i = 0; i < count; i++) {
      draws[i] = random.nextGaussian();
    }
    return draws;
  }

  private static double dot(double[] left, double[] right) {
    double sum = 0;
    for (int i = 0; i < left.length; i++) {
      sum += left[i] * right[i];
    }
    return sum;
  }

  private static double[][] copyOf(double[][] matrix) {
    double[][] copy = new double[matrix.length][];
    for (int i = 0; i < matrix.length; i++) {
      copy[i] = matrix[i].clone();
    }
    return copy;
  }

  private static void requireMean(double[] mean) {
    if (mean.length == 0) {
      throw new IllegalArgumentException("LinearPosterior.mean must not be empty");
    }
    for (double value : mean) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("LinearPosterior.mean must be finite, got " + value);
      }
    }
  }

  private static void requirePositiveVariance(double variance) {
    if (!(variance > 0) || !Double.isFinite(variance)) {
      throw new IllegalArgumentException(
          "LinearPosterior.variance must be a positive number, got " + variance);
    }
  }

  private static void requireInformationLength(double[] information, int dimension) {
    if (information.length != dimension) {
      throw new IllegalArgumentException(
          "LinearPosterior.information must have "
              + dimension
              + " values, got "
              + information.length);
    }
  }

  private static void requireObservations(double observations) {
    if (!(observations >= 0) || !Double.isFinite(observations)) {
      throw new IllegalArgumentException(
          "LinearPosterior.observations must be zero or positive, got " + observations);
    }
  }

  private void requireFeatures(double[] features) {
    if (features.length != dimension()) {
      throw new IllegalArgumentException(
          "LinearPosterior.features must have " + dimension() + " values, got " + features.length);
    }
    for (double value : features) {
      if (!Double.isFinite(value)) {
        throw new IllegalArgumentException("LinearPosterior.features must be finite");
      }
    }
  }

  private static void requireFiniteReward(double reward) {
    if (!Double.isFinite(reward)) {
      throw new IllegalArgumentException("LinearPosterior.reward must be finite, got " + reward);
    }
  }

  private static void requirePositiveNoiseVariance(double noiseVariance) {
    if (!(noiseVariance > 0) || !Double.isFinite(noiseVariance)) {
      throw new IllegalArgumentException(
          "LinearPosterior.noiseVariance must be a positive number, got " + noiseVariance);
    }
  }

  private static void requireKeep(double keep) {
    if (!(keep >= 0 && keep <= 1)) {
      throw new IllegalArgumentException(
          "LinearPosterior.keep must be between 0 and 1, got " + keep);
    }
  }

  private void requireSameDimension(LinearPosterior anchor) {
    if (anchor.dimension() != dimension()) {
      throw new IllegalArgumentException(
          "LinearPosterior.anchor must have "
              + dimension()
              + " dimensions, got "
              + anchor.dimension());
    }
  }

  private static void requirePositiveExplorationScale(double explorationScale) {
    if (!(explorationScale > 0) || !Double.isFinite(explorationScale)) {
      throw new IllegalArgumentException(
          "LinearPosterior.explorationScale must be a positive number, got " + explorationScale);
    }
  }
}
