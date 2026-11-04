package io.github.nicodoou.mobai.domain.event;

import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.List;
import java.util.Objects;

/** A network chose the orders of a group; the steps go out together, one write per decision. */
public record NeuralDecided(GroupId groupId, long tick, String network, List<NeuralStep> steps)
    implements DomainEvent {
  public NeuralDecided {
    Objects.requireNonNull(groupId, "NeuralDecided.groupId");
    Objects.requireNonNull(network, "NeuralDecided.network");
    steps = List.copyOf(Objects.requireNonNull(steps, "NeuralDecided.steps"));
  }
}
