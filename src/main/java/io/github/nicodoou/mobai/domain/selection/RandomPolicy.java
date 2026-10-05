package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class RandomPolicy implements SelectionPolicy {
  private final Supplier<SelectionSettings> settings;
  private final RandomSource random;

  public RandomPolicy(Supplier<SelectionSettings> settings, RandomSource random) {
    this.settings = Objects.requireNonNull(settings, "RandomPolicy.settings");
    this.random = Objects.requireNonNull(random, "RandomPolicy.random");
  }

  @Override
  public <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates) {
    CandidateScoring.requireCandidates(candidates);
    List<CandidateScore<T>> scores = CandidateScoring.scoreByMean(candidates, settings.get());
    int index = random.nextIndex(candidates.size());
    return new SelectionResult<>(candidates.get(index).option(), scores, true);
  }
}
