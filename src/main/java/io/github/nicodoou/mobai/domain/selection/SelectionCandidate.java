package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import java.util.Objects;

public record SelectionCandidate<T>(T option, double baseScore, SuccessEstimate estimate) {
  public SelectionCandidate {
    Objects.requireNonNull(option, "SelectionCandidate.option");
    if (!(baseScore >= 0) || !Double.isFinite(baseScore)) {
      throw new IllegalArgumentException(
          "SelectionCandidate.baseScore must be zero or positive, got " + baseScore);
    }
    Objects.requireNonNull(estimate, "SelectionCandidate.estimate");
  }
}
