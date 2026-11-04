package io.github.nicodoou.mobai.domain.neural;

import java.util.List;
import java.util.Objects;

/** An immutable perceptron: tanh after every layer but the last, whose outputs are logits. */
public final class PolicyNetwork {
  public static final int HIDDEN_SIZE = 64;

  private static final int NEVER_TRAINED_VERSION = 0;
  // Small enough that a new network starts out close to a uniform choice.
  private static final double INITIAL_WEIGHT_MAGNITUDE = 0.05;

  private final int version;
  private final List<PolicyLayer> layers;

  public PolicyNetwork(int version, List<PolicyLayer> layers) {
    this.version = version;
    this.layers = List.copyOf(Objects.requireNonNull(layers, "PolicyNetwork.layers"));
    requireChained(this.layers);
  }

  /** Two hidden layers of small weights drawn from the name, the same on every server start. */
  public static PolicyNetwork seeded(String name) {
    SeededWeights weights = SeededWeights.forName(name, INITIAL_WEIGHT_MAGNITUDE);
    return new PolicyNetwork(
        NEVER_TRAINED_VERSION,
        List.of(
            weights.layer(ObservationEncoder.SIZE, HIDDEN_SIZE),
            weights.layer(HIDDEN_SIZE, HIDDEN_SIZE),
            weights.layer(HIDDEN_SIZE, NeuralAction.COUNT)));
  }

  public int version() {
    return version;
  }

  public void requireShape(int inputs, int outputs) {
    int ownInputs = layers.getFirst().inputs();
    int ownOutputs = layers.getLast().outputs();
    if (ownInputs != inputs || ownOutputs != outputs) {
      throw new IllegalArgumentException(
          "PolicyNetwork must map "
              + inputs
              + " inputs to "
              + outputs
              + " outputs, got "
              + ownInputs
              + " and "
              + ownOutputs);
    }
  }

  public double[] logits(double[] input) {
    Objects.requireNonNull(input, "PolicyNetwork.input");
    if (input.length != layers.getFirst().inputs()) {
      throw new IllegalArgumentException(
          "PolicyNetwork expects " + layers.getFirst().inputs() + " inputs, got " + input.length);
    }
    double[] activation = input;
    for (int index = 0; index < layers.size() - 1; index++) {
      activation = tanh(layers.get(index).apply(activation));
    }
    return layers.getLast().apply(activation);
  }

  /** One probability per output; a masked output has exactly zero. */
  public double[] probabilities(double[] input, boolean[] mask) {
    double[] logits = logits(input);
    if (mask.length != logits.length) {
      throw new IllegalArgumentException(
          "PolicyNetwork mask must have " + logits.length + " entries, got " + mask.length);
    }
    return maskedSoftmax(logits, mask);
  }

  // Subtracting the largest logit keeps the exponentials in range however large the logits are.
  private static double[] maskedSoftmax(double[] logits, boolean[] mask) {
    double largest = largestAllowed(logits, mask);
    double[] scores = new double[logits.length];
    double total = 0;
    for (int index = 0; index < logits.length; index++) {
      scores[index] = mask[index] ? Math.exp(logits[index] - largest) : 0;
      total += scores[index];
    }
    for (int index = 0; index < scores.length; index++) {
      scores[index] /= total;
    }
    return scores;
  }

  private static double largestAllowed(double[] logits, boolean[] mask) {
    double largest = Double.NEGATIVE_INFINITY;
    for (int index = 0; index < logits.length; index++) {
      if (mask[index]) {
        largest = Math.max(largest, logits[index]);
      }
    }
    if (largest == Double.NEGATIVE_INFINITY) {
      throw new IllegalArgumentException("PolicyNetwork mask allows no output");
    }
    return largest;
  }

  private static double[] tanh(double[] values) {
    double[] result = new double[values.length];
    for (int index = 0; index < values.length; index++) {
      result[index] = Math.tanh(values[index]);
    }
    return result;
  }

  private static void requireChained(List<PolicyLayer> layers) {
    if (layers.isEmpty()) {
      throw new IllegalArgumentException("PolicyNetwork needs at least one layer");
    }
    int outputs = layers.getFirst().outputs();
    for (PolicyLayer layer : layers.subList(1, layers.size())) {
      if (layer.inputs() != outputs) {
        throw new IllegalArgumentException(
            "PolicyNetwork layer takes "
                + layer.inputs()
                + " inputs after "
                + outputs
                + " outputs");
      }
      outputs = layer.outputs();
    }
  }
}
