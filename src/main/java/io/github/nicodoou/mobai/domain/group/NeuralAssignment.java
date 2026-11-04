package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.NetworkName;
import java.util.Objects;

/** A group fights with this network whatever the configured planner says. */
public record NeuralAssignment(String network, CombatTactic tactic) {
  public NeuralAssignment {
    NetworkName.requireValid(network);
    Objects.requireNonNull(tactic, "NeuralAssignment.tactic");
  }

  public static NeuralAssignment forNetwork(String network) {
    return new NeuralAssignment(network, CombatTactic.ofNetwork(network));
  }
}
