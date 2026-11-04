package io.github.nicodoou.mobai.domain.neural;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.NeuralAssignment;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.NeuralSettings;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Asks a group's network what each of its mobs does in this decision. */
public final class NeuralPlanner {
  public static final StrategyId STRATEGY_ID = new StrategyId("NEURAL");

  private final PolicyLibrary library;
  private final ActionSampler sampler;
  private final Supplier<NeuralSettings> settings;
  private final ObservationEncoder encoder = new ObservationEncoder();

  public NeuralPlanner(
      PolicyLibrary library, RandomSource random, Supplier<NeuralSettings> settings) {
    this.library = Objects.requireNonNull(library, "NeuralPlanner.library");
    this.sampler = new ActionSampler(random);
    this.settings = Objects.requireNonNull(settings, "NeuralPlanner.settings");
  }

  public PolicyLibrary library() {
    return library;
  }

  public String networkOf(Group group) {
    return group
        .neuralAssignment()
        .map(NeuralAssignment::network)
        .orElseGet(() -> settings.get().defaultNetwork());
  }

  // Snapshot order fixes the order of the draws, so incidents can be replayed.
  public List<NeuralStep> decide(String network, GroupSnapshot snapshot, NeuralContext context) {
    PolicyNetwork policy = library.networkFor(network);
    return snapshot.mobs().stream()
        .map(mob -> stepFor(policy, mob, new DecisionView(snapshot, context)))
        .toList();
  }

  private NeuralStep stepFor(PolicyNetwork policy, MobSnapshot mob, DecisionView view) {
    double[] observation = encoder.encode(mob, view.snapshot(), view.context());
    double[] probabilities = policy.probabilities(observation, NeuralAction.maskOf(mob.kind()));
    int action = sampler.sample(probabilities);
    return new NeuralStep(
        mob.id(),
        mob.kind(),
        Arrays.stream(observation).boxed().toList(),
        NeuralAction.values()[action],
        Math.log(probabilities[action]));
  }

  private record DecisionView(GroupSnapshot snapshot, NeuralContext context) {}
}
