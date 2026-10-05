package io.github.nicodoou.mobai.domain.attack;

import java.util.Objects;

public record Classification(AttackOutcome outcome, ClassificationTrace trace) {
  public Classification {
    Objects.requireNonNull(outcome, "Classification.outcome");
    Objects.requireNonNull(trace, "Classification.trace");
  }
}
