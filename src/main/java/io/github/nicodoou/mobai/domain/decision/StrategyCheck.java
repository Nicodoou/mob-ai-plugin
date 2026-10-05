package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Objects;

/** Whether a strategy could run with this composition, and its requirement in words. */
public record StrategyCheck(StrategyId strategy, boolean viable, String requirement) {
  public StrategyCheck {
    Objects.requireNonNull(strategy, "StrategyCheck.strategy");
    Objects.requireNonNull(requirement, "StrategyCheck.requirement");
  }
}
