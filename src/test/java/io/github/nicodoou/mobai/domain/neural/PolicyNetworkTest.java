package io.github.nicodoou.mobai.domain.neural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class PolicyNetworkTest {
  private static final double TOLERANCE = 1e-9;
  private static final double TANH_OF_ONE = 0.7615941559557649;
  private static final double[] UNIT_INPUT = {1, 0};

  // Hidden layer: identity, so its activations are tanh(1) and tanh(0) = 0.
  // Output layer: logits tanh(1), 0.5 and 2 * tanh(1).
  private static PolicyNetwork handMadeNetwork() {
    return new PolicyNetwork(
        5,
        List.of(
            new PolicyLayer(new double[][] {{1, 0}, {0, 1}}, new double[] {0, 0}),
            new PolicyLayer(new double[][] {{1, 0}, {0, 1}, {2, 0}}, new double[] {0, 0.5, 0})));
  }

  @Test
  void theLastLayerGivesLogitsAndTheOthersPassThroughTanh() {
    double[] logits = handMadeNetwork().logits(UNIT_INPUT);

    assertThat(logits[0]).isCloseTo(TANH_OF_ONE, within(TOLERANCE));
    assertThat(logits[1]).isCloseTo(0.5, within(TOLERANCE));
    assertThat(logits[2]).isCloseTo(2 * TANH_OF_ONE, within(TOLERANCE));
  }

  @Test
  void theProbabilitiesAreTheSoftmaxOfTheLogits() {
    double[] probabilities =
        handMadeNetwork().probabilities(UNIT_INPUT, new boolean[] {true, true, true});

    assertThat(probabilities[0]).isCloseTo(0.2556556740500902, within(TOLERANCE));
    assertThat(probabilities[1]).isCloseTo(0.19680971730053762, within(TOLERANCE));
    assertThat(probabilities[2]).isCloseTo(0.5475346086493721, within(TOLERANCE));
  }

  @Test
  void aMaskedOutputHasNoProbabilityAndTheRestShareIt() {
    double[] probabilities =
        handMadeNetwork().probabilities(UNIT_INPUT, new boolean[] {true, false, true});

    assertThat(probabilities[0]).isCloseTo(0.3183002578054738, within(TOLERANCE));
    assertThat(probabilities[1]).isZero();
    assertThat(probabilities[2]).isCloseTo(0.6816997421945262, within(TOLERANCE));
  }

  @Test
  void aMaskThatAllowsNothingIsAnError() {
    assertThatThrownBy(
            () -> handMadeNetwork().probabilities(UNIT_INPUT, new boolean[] {false, false, false}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hugeLogitsStillGiveProbabilities() {
    PolicyNetwork network =
        new PolicyNetwork(
            1, List.of(new PolicyLayer(new double[][] {{1e6}, {-1e6}}, new double[] {0, 0})));

    double[] probabilities = network.probabilities(new double[] {1}, new boolean[] {true, true});

    assertThat(probabilities[0]).isCloseTo(1, within(TOLERANCE));
    assertThat(probabilities[1]).isZero();
  }

  @Test
  void anInputOfTheWrongSizeIsAnError() {
    assertThatThrownBy(() -> handMadeNetwork().logits(new double[] {1}))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("2 inputs");
  }

  @Test
  void layersThatDoNotChainAreRejected() {
    PolicyLayer twoOutputs = new PolicyLayer(new double[][] {{1}, {1}}, new double[] {0, 0});

    assertThatThrownBy(() -> new PolicyNetwork(1, List.of(twoOutputs, twoOutputs)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNetworkWithoutLayersIsRejected() {
    assertThatThrownBy(() -> new PolicyNetwork(1, List.of()))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void weightsThatAreNotFiniteAreRejected() {
    assertThatThrownBy(() -> new PolicyLayer(new double[][] {{Double.NaN}}, new double[] {0}))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> new PolicyLayer(new double[][] {{1}}, new double[] {Double.POSITIVE_INFINITY}))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aLayerKeepsItsOwnCopyOfTheWeights() {
    double[][] weights = {{1, 0}, {0, 1}};
    PolicyLayer layer = new PolicyLayer(weights, new double[] {0, 0});

    weights[0][0] = 100;

    assertThat(new PolicyNetwork(1, List.of(layer)).logits(UNIT_INPUT)[0])
        .isCloseTo(1, within(TOLERANCE));
  }

  @Test
  void aSeededNetworkHasTheAgreedShapeAndStartsUntrained() {
    PolicyNetwork network = PolicyNetwork.seeded("turnos-1");

    network.requireShape(ObservationEncoder.SIZE, NeuralAction.COUNT);
    assertThat(network.version()).isZero();
  }

  @Test
  void theSameNameAlwaysSeedsTheSameNetwork() {
    double[] input = new double[ObservationEncoder.SIZE];
    input[0] = 1;

    assertThat(PolicyNetwork.seeded("turnos-1").logits(input))
        .containsExactly(PolicyNetwork.seeded("turnos-1").logits(input));
  }

  @Test
  void differentNamesSeedDifferentNetworks() {
    double[] input = new double[ObservationEncoder.SIZE];
    input[0] = 1;

    assertThat(PolicyNetwork.seeded("turnos-1").logits(input))
        .isNotEqualTo(PolicyNetwork.seeded("turnos-2").logits(input));
  }

  @Test
  void aSeededNetworkStartsCloseToAUniformChoice() {
    double[] input = new double[ObservationEncoder.SIZE];
    Arrays.fill(input, 1);

    double[] probabilities =
        PolicyNetwork.seeded("cerco-3")
            .probabilities(input, new boolean[] {true, true, true, true, false, false});

    assertThat(probabilities[0]).isCloseTo(0.25, within(0.05));
    assertThat(probabilities[3]).isCloseTo(0.25, within(0.05));
  }

  @Test
  void aShapeOtherThanTheAgreedOneIsRejected() {
    assertThatThrownBy(() -> handMadeNetwork().requireShape(ObservationEncoder.SIZE, 3))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
