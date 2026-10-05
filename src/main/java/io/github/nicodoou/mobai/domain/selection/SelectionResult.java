package io.github.nicodoou.mobai.domain.selection;

import java.util.List;
import java.util.Objects;

public record SelectionResult<T>(T chosen, List<CandidateScore<T>> scores, boolean randomPick) {
  public SelectionResult {
    Objects.requireNonNull(chosen, "SelectionResult.chosen");
    scores = List.copyOf(scores);
  }
}
