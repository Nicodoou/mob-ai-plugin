package io.github.nicodoou.mobai.adapter.neural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.google.gson.Gson;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.ObservationEncoder;
import io.github.nicodoou.mobai.domain.neural.PolicyNetwork;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PolicyFileTest {
  private static final int HIDDEN = 64;

  private static double[][] constantWeights(int rows, int columns, double value) {
    double[][] weights = new double[rows][columns];
    for (double[] row : weights) {
      Arrays.fill(row, value);
    }
    return weights;
  }

  private static Map<String, Object> layer(int inputs, int outputs, double bias) {
    double[] biases = new double[outputs];
    Arrays.fill(biases, bias);
    return Map.of("w", constantWeights(outputs, inputs, 0.01), "b", biases);
  }

  private static String policyJson(int version, List<Map<String, Object>> layers) {
    return new Gson().toJson(Map.of("version", version, "layers", layers));
  }

  static List<Map<String, Object>> validLayers() {
    List<Map<String, Object>> layers = new ArrayList<>();
    layers.add(layer(ObservationEncoder.SIZE, HIDDEN, 0));
    layers.add(layer(HIDDEN, HIDDEN, 0));
    layers.add(layer(HIDDEN, NeuralAction.COUNT, 0.5));
    return layers;
  }

  static String validJson(int version) {
    return policyJson(version, validLayers());
  }

  @Test
  void aValidFileBecomesARunnableNetworkOfItsVersion() {
    PolicyNetwork network = PolicyFile.parse(validJson(7));

    assertThat(network.version()).isEqualTo(7);
    double[] logits = network.logits(new double[ObservationEncoder.SIZE]);
    assertThat(logits).hasSize(NeuralAction.COUNT);
    assertThat(logits[0]).isCloseTo(0.5, within(1e-9));
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "not json",
        "{}",
        "{\"version\":1,\"layers\":[]}",
        "{\"version\":1,\"layers\":[{\"w\":[[1.0]]}]}",
        "{\"version\":1,\"layers\":[{\"b\":[1.0]}]}",
        "{\"version\":1,\"layers\":[{\"w\":[[1.0,2.0],[3.0]],\"b\":[1.0,2.0]}]}",
        "{\"version\":1,\"layers\":[{\"w\":[[1.0]],\"b\":[1.0,2.0]}]}",
        "{\"version\":1,\"layers\":[{\"w\":[null],\"b\":[1.0]}]}",
        "{\"version\":1,\"layers\":[null]}"
      })
  void aMalformedFileIsRejected(String text) {
    assertThatThrownBy(() -> PolicyFile.parse(text)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aNetworkOfAnotherInputSizeIsRejected() {
    List<Map<String, Object>> layers = validLayers();
    layers.set(0, layer(ObservationEncoder.SIZE + 1, HIDDEN, 0));

    assertThatThrownBy(() -> PolicyFile.parse(policyJson(1, layers)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("inputs");
  }

  @Test
  void aNetworkOfAnotherOutputSizeIsRejected() {
    List<Map<String, Object>> layers = validLayers();
    layers.set(2, layer(HIDDEN, NeuralAction.COUNT - 1, 0));

    assertThatThrownBy(() -> PolicyFile.parse(policyJson(1, layers)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("outputs");
  }

  @Test
  void layersThatDoNotChainAreRejected() {
    List<Map<String, Object>> layers = validLayers();
    layers.set(1, layer(HIDDEN + 1, HIDDEN, 0));

    assertThatThrownBy(() -> PolicyFile.parse(policyJson(1, layers)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWeightThatIsNotANumberIsRejected() {
    String text = "{\"version\":1,\"layers\":[{\"w\":[[NaN]],\"b\":[1.0]}]}";

    assertThatThrownBy(() -> PolicyFile.parse(text)).isInstanceOf(IllegalArgumentException.class);
  }
}
