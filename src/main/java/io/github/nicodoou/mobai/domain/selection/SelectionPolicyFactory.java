package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class SelectionPolicyFactory {
  private final Map<SelectionPolicyType, SelectionPolicy> policies =
      new EnumMap<>(SelectionPolicyType.class);

  public SelectionPolicyFactory(Supplier<SelectionSettings> settings, RandomSource random) {
    Objects.requireNonNull(settings, "SelectionPolicyFactory.settings");
    Objects.requireNonNull(random, "SelectionPolicyFactory.random");
    policies.put(
        SelectionPolicyType.THOMPSON_SAMPLING,
        new ThompsonSamplingPolicy(settings, new BetaSampler(random)));
    policies.put(SelectionPolicyType.EXPLORE_FIRST, new ExploreFirstPolicy(settings, random));
    policies.put(SelectionPolicyType.EPSILON_GREEDY, new EpsilonGreedyPolicy(settings, random));
    policies.put(SelectionPolicyType.RANDOM, new RandomPolicy(settings, random));
  }

  public SelectionPolicy forType(SelectionPolicyType type) {
    Objects.requireNonNull(type, "SelectionPolicyFactory.type");
    return policies.get(type);
  }
}
