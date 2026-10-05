package io.github.nicodoou.mobai.domain.target;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record TargetSelection(Optional<PlayerId> target, List<TargetScore> scores) {
  public TargetSelection {
    Objects.requireNonNull(target, "TargetSelection.target");
    Objects.requireNonNull(scores, "TargetSelection.scores");
    scores = List.copyOf(scores);
  }
}
