package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class EpsilonGreedyPolicy implements SelectionPolicy {
  private final Supplier<SelectionSettings> settings;
  private final RandomSource random;

  public EpsilonGreedyPolicy(Supplier<SelectionSettings> settings, RandomSource random) {
    this.settings = Objects.requireNonNull(settings, "EpsilonGreedyPolicy.settings");
    this.random = Objects.requireNonNull(random, "EpsilonGreedyPolicy.random");
  }

  @Override
  public <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates) {
    CandidateScoring.requireCandidates(candidates);
    SelectionSettings current = settings.get();
    List<CandidateScore<T>> scores = CandidateScoring.scoreByMean(candidates, current);
    double roll = random.nextUnit();
    if (roll < current.epsilon()) {
      int index = random.nextIndex(candidates.size());
      return new SelectionResult<>(candidates.get(index).option(), scores, true);
    }
    int index = CandidateScoring.indexOfBest(scores);
    return new SelectionResult<>(candidates.get(index).option(), scores, false);
  }
}
