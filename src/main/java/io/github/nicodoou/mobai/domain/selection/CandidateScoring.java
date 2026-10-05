package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import java.util.ArrayList;
import java.util.List;

final class CandidateScoring {
  private CandidateScoring() {}

  static <T> List<CandidateScore<T>> scoreByMean(
      List<SelectionCandidate<T>> candidates, SelectionSettings settings) {
    List<CandidateScore<T>> scores = new ArrayList<>(candidates.size());
    for (SelectionCandidate<T> candidate : candidates) {
      double rate = candidate.estimate().mean();
      double finalScore = candidate.baseScore() * MemoryMultiplier.of(rate, settings);
      scores.add(new CandidateScore<>(candidate.option(), rate, finalScore));
    }
    return scores;
  }

  static <T> int indexOfBest(List<CandidateScore<T>> scores) {
    int best = 0;
    for (int i = 1; i < scores.size(); i++) {
      if (scores.get(i).finalScore() > scores.get(best).finalScore()) {
        best = i;
      }
    }
    return best;
  }

  static <T> void requireCandidates(List<SelectionCandidate<T>> candidates) {
    if (candidates.isEmpty()) {
      throw new IllegalArgumentException("candidates must not be empty");
    }
  }
}
