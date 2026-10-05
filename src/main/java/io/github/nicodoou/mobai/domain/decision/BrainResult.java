package io.github.nicodoou.mobai.domain.decision;

import java.util.Objects;
import java.util.Optional;

public record BrainResult(
    GroupDecision decision, DecisionTrace trace, Optional<ClosedPlan> closedPlan) {
  public BrainResult {
    Objects.requireNonNull(decision, "BrainResult.decision");
    Objects.requireNonNull(trace, "BrainResult.trace");
    Objects.requireNonNull(closedPlan, "BrainResult.closedPlan");
  }
}
