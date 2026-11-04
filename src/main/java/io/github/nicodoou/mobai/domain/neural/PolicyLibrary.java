package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.shared.NetworkName;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The network in force for each name; only the server thread touches it. */
public final class PolicyLibrary {
  private final Map<String, PolicyNetwork> networks = new HashMap<>();

  /** A name without loaded weights gets its seeded network the first time it is asked for. */
  public PolicyNetwork networkFor(String name) {
    NetworkName.requireValid(name);
    return networks.computeIfAbsent(name, PolicyNetwork::seeded);
  }

  public void replace(String name, PolicyNetwork network) {
    NetworkName.requireValid(name);
    networks.put(name, Objects.requireNonNull(network, "PolicyLibrary.network"));
  }

  public Set<String> names() {
    return Set.copyOf(networks.keySet());
  }
}
