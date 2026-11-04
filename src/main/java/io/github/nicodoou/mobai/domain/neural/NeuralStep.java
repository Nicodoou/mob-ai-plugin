package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.List;
import java.util.Objects;

/** What the network saw and chose for one mob, as the trainer needs it. */
public record NeuralStep(
    MobId mob, MobKind kind, List<Double> observation, NeuralAction action, double logProbability) {
  public NeuralStep {
    Objects.requireNonNull(mob, "NeuralStep.mob");
    Objects.requireNonNull(kind, "NeuralStep.kind");
    observation = List.copyOf(Objects.requireNonNull(observation, "NeuralStep.observation"));
    Objects.requireNonNull(action, "NeuralStep.action");
  }
}
