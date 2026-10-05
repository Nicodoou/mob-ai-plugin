package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class ThompsonSamplingPolicy implements SelectionPolicy {
  private final Supplier<SelectionSettings> settings;
  private final BetaSampler sampler;

  public ThompsonSamplingPolicy(Supplier<SelectionSettings> settings, BetaSampler sampler) {
    this.settings = Objects.requireNonNull(settings, "ThompsonSamplingPolicy.settings");
    this.sampler = Objects.requireNonNull(sampler, "ThompsonSamplingPolicy.sampler");
  }

  @Override
  public <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates) {
    CandidateScoring.requireCandidates(candidates);
    SelectionSettings current = settings.get();
    List<CandidateScore<T>> scores = new ArrayList<>(candidates.size());
    for (SelectionCandidate<T> candidate : candidates) {
      double rate = sampler.sample(candidate.estimate());
      double finalScore = candidate.baseScore() * MemoryMultiplier.of(rate, current);
      scores.add(new CandidateScore<>(candidate.option(), rate, finalScore));
    }
    int index = CandidateScoring.indexOfBest(scores);
    return new SelectionResult<>(candidates.get(index).option(), scores, false);
  }
}
