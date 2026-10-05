package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Objects;

public record StrategyObservation(
    PlayerId player, StrategyId strategy, double credit, double weight, long tick) {
  public StrategyObservation {
    Objects.requireNonNull(player, "StrategyObservation.player");
    Objects.requireNonNull(strategy, "StrategyObservation.strategy");
    if (!(credit >= 0 && credit <= 1)) {
      throw new IllegalArgumentException(
          "StrategyObservation.credit must be between 0.0 and 1.0, got " + credit);
    }
    if (!(weight > 0 && weight <= 1)) {
      throw new IllegalArgumentException(
          "StrategyObservation.weight must be greater than 0.0 and at most 1.0, got " + weight);
    }
  }
}
