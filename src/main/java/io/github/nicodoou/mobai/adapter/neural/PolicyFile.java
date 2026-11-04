package io.github.nicodoou.mobai.adapter.neural;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.ObservationEncoder;
import io.github.nicodoou.mobai.domain.neural.PolicyLayer;
import io.github.nicodoou.mobai.domain.neural.PolicyNetwork;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads policy.json: {"version":n,"layers":[{"w":[[...]],"b":[...]},...]}, where each layer
 * multiplies w by its input and adds b.
 */
final class PolicyFile {
  private static final Gson GSON = new Gson();

  private PolicyFile() {}

  /**
   * @throws IllegalArgumentException if the text is not a network this plugin can run
   */
  static PolicyNetwork parse(String text) {
    try {
      PolicyJson json = GSON.fromJson(text, PolicyJson.class);
      if (json == null || json.layers() == null) {
        throw new IllegalArgumentException("policy.json has no layers");
      }
      PolicyNetwork network = new PolicyNetwork(json.version(), layersOf(json));
      network.requireShape(ObservationEncoder.SIZE, NeuralAction.COUNT);
      return network;
    } catch (JsonParseException | NullPointerException exception) {
      throw new IllegalArgumentException("policy.json is malformed: " + exception, exception);
    }
  }

  private static List<PolicyLayer> layersOf(PolicyJson json) {
    List<PolicyLayer> layers = new ArrayList<>();
    for (LayerJson layer : json.layers()) {
      layers.add(new PolicyLayer(layer.w(), layer.b()));
    }
    return layers;
  }

  private record PolicyJson(int version, List<LayerJson> layers) {}

  private record LayerJson(double[][] w, double[] b) {}
}
