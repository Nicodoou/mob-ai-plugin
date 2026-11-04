package io.github.nicodoou.mobai.domain.neural;

import java.util.Objects;

/** One linear step of a network: multiplies the weights by the input and adds the biases. */
public final class PolicyLayer {
  // A trained network stays far below this; above it, the logits could overflow into infinity.
  private static final double MAX_WEIGHT_MAGNITUDE = 1e6;

  private final double[][] weights;
  private final double[] biases;

  /**
   * @param weights one row per output
   */
  public PolicyLayer(double[][] weights, double[] biases) {
    Objects.requireNonNull(weights, "PolicyLayer.weights");
    Objects.requireNonNull(biases, "PolicyLayer.biases");
    requireShape(weights, biases);
    requireUsableValues(biases);
    this.weights = new double[weights.length][];
    for (int row = 0; row < weights.length; row++) {
      requireUsableValues(weights[row]);
      this.weights[row] = weights[row].clone();
    }
    this.biases = biases.clone();
  }

  public int inputs() {
    return weights[0].length;
  }

  public int outputs() {
    return biases.length;
  }

  double[] apply(double[] input) {
    double[] output = new double[biases.length];
    for (int row = 0; row < biases.length; row++) {
      output[row] = weightedSum(weights[row], input) + biases[row];
    }
    return output;
  }

  private static double weightedSum(double[] rowWeights, double[] input) {
    double sum = 0;
    for (int column = 0; column < input.length; column++) {
      sum += rowWeights[column] * input[column];
    }
    return sum;
  }

  private static void requireShape(double[][] weights, double[] biases) {
    if (weights.length == 0 || weights.length != biases.length) {
      throw new IllegalArgumentException(
          "PolicyLayer needs one bias per weight row, got "
              + weights.length
              + " rows and "
              + biases.length
              + " biases");
    }
    int columns = Objects.requireNonNull(weights[0], "PolicyLayer.weights row").length;
    for (double[] row : weights) {
      if (Objects.requireNonNull(row, "PolicyLayer.weights row").length != columns
          || columns == 0) {
        throw new IllegalArgumentException(
            "PolicyLayer rows must share a positive length, got " + row.length + " and " + columns);
      }
    }
  }

  private static void requireUsableValues(double[] values) {
    for (double value : values) {
      if (!(Math.abs(value) <= MAX_WEIGHT_MAGNITUDE)) {
        throw new IllegalArgumentException(
            "PolicyLayer values must be finite and at most "
                + MAX_WEIGHT_MAGNITUDE
                + " in magnitude, got "
                + value);
      }
    }
  }
}
