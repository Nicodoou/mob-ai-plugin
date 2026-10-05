package io.github.nicodoou.mobai.domain.selection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SelectionPolicyTest {
  private static final SelectionSettings DEFAULTS = TestSettings.defaults().selection();
  private static final Supplier<SelectionSettings> SETTINGS = () -> DEFAULTS;

  private static SelectionCandidate<String> candidate(
      String option, double baseScore, SuccessEstimate estimate) {
    return new SelectionCandidate<>(option, baseScore, estimate);
  }

  private static SuccessEstimate estimate(double alpha, double beta, double attempts) {
    return new SuccessEstimate(alpha, beta, attempts);
  }

  private static SelectionPolicy thompson(RandomSource random) {
    return new ThompsonSamplingPolicy(SETTINGS, new BetaSampler(random));
  }

  private static List<SelectionCandidate<String>> clearPair() {
    return List.of(
        candidate("A", 1, estimate(90, 10, 100)), candidate("B", 1, estimate(10, 90, 100)));
  }

  @Test
  void everyPolicyRejectsEmptyCandidates() {
    List<SelectionCandidate<String>> empty = List.of();
    List<SelectionPolicy> policies =
        List.of(
            thompson(new ScriptedRandomSource()),
            new EpsilonGreedyPolicy(SETTINGS, new ScriptedRandomSource()),
            new ExploreFirstPolicy(SETTINGS, new ScriptedRandomSource()),
            new RandomPolicy(SETTINGS, new ScriptedRandomSource()));

    for (SelectionPolicy policy : policies) {
      assertThatThrownBy(() -> policy.choose(empty))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("candidates must not be empty");
    }
  }

  @Test
  void thompsonPicksAClearWinnerAlmostAlways() {
    SelectionPolicy policy = thompson(new SeededRandomSource(11));
    List<SelectionCandidate<String>> candidates =
        List.of(
            candidate("A", 1, estimate(900, 100, 1000)),
            candidate("B", 1, estimate(100, 900, 1000)));

    int wins = 0;
    for (int i = 0; i < 1_000; i++) {
      if (policy.choose(candidates).chosen().equals("A")) {
        wins++;
      }
    }

    assertThat(wins).isGreaterThanOrEqualTo(990);
  }

  @Test
  void thompsonFrequencyMatchesTheProbabilityOfBeingBest() {
    SelectionPolicy policy = thompson(new SeededRandomSource(12));
    List<SelectionCandidate<String>> candidates =
        List.of(candidate("A", 1, estimate(3, 2, 3)), candidate("B", 1, estimate(2, 3, 3)));
    int draws = 20_000;

    int wins = 0;
    for (int i = 0; i < draws; i++) {
      if (policy.choose(candidates).chosen().equals("A")) {
        wins++;
      }
    }

    assertThat((double) wins / draws).isCloseTo(0.7571, within(0.015));
  }

  @Test
  void thompsonBaseScoreScalesTheFinalScore() {
    SelectionPolicy policy = thompson(new SeededRandomSource(13));
    List<SelectionCandidate<String>> candidates =
        List.of(
            candidate("A", 2, estimate(5000, 5000, 10000)),
            candidate("B", 1, estimate(5000, 5000, 10000)));

    int wins = 0;
    for (int i = 0; i < 100; i++) {
      if (policy.choose(candidates).chosen().equals("A")) {
        wins++;
      }
    }

    assertThat(wins).isEqualTo(100);
  }

  @Test
  void thompsonReportsEveryCandidateScoreInOrder() {
    SelectionPolicy policy = thompson(new SeededRandomSource(14));
    List<SelectionCandidate<String>> candidates =
        List.of(
            candidate("A", 1, estimate(2, 2, 4)),
            candidate("B", 2, estimate(3, 1, 4)),
            candidate("C", 3, estimate(1, 3, 4)));

    SelectionResult<String> result = policy.choose(candidates);

    assertThat(result.scores()).extracting(CandidateScore::option).containsExactly("A", "B", "C");
    double[] bases = {1, 2, 3};
    for (int i = 0; i < bases.length; i++) {
      CandidateScore<String> score = result.scores().get(i);
      assertThat(score.finalScore()).isCloseTo(bases[i] * (0.5 + score.rate()), within(1e-9));
    }
    assertThat(result.randomPick()).isFalse();
  }

  @Test
  void epsilonGreedyExploresWithProbabilityEpsilon() {
    SelectionPolicy policy = new EpsilonGreedyPolicy(SETTINGS, new SeededRandomSource(15));
    List<SelectionCandidate<String>> candidates = clearPair();
    int draws = 20_000;

    int randomPicks = 0;
    int bWins = 0;
    for (int i = 0; i < draws; i++) {
      SelectionResult<String> result = policy.choose(candidates);
      if (result.randomPick()) {
        randomPicks++;
      }
      if (result.chosen().equals("B")) {
        bWins++;
      }
    }

    assertThat((double) randomPicks / draws).isCloseTo(0.10, within(0.01));
    assertThat((double) bWins / draws).isCloseTo(0.05, within(0.01));
  }

  @Test
  void epsilonGreedyDrawsInAFixedOrder() {
    ScriptedRandomSource exploring = new ScriptedRandomSource().withUnits(0.05).withIndexes(1);
    ScriptedRandomSource exploiting = new ScriptedRandomSource().withUnits(0.5);

    SelectionResult<String> explored =
        new EpsilonGreedyPolicy(SETTINGS, exploring).choose(clearPair());
    SelectionResult<String> exploited =
        new EpsilonGreedyPolicy(SETTINGS, exploiting).choose(clearPair());

    assertThat(explored.chosen()).isEqualTo("B");
    assertThat(explored.randomPick()).isTrue();
    assertThat(exploited.chosen()).isEqualTo("A");
    assertThat(exploited.randomPick()).isFalse();
    assertThat(exploiting.isExhausted()).isTrue();
  }

  @Test
  void epsilonZeroAlwaysExploits() {
    SelectionSettings noExploration =
        new SelectionSettings(DEFAULTS.defaultPolicy(), 0.5, 1.5, 0.0, 10);
    SelectionPolicy policy =
        new EpsilonGreedyPolicy(() -> noExploration, new SeededRandomSource(16));

    for (int i = 0; i < 1_000; i++) {
      SelectionResult<String> result = policy.choose(clearPair());

      assertThat(result.chosen()).isEqualTo("A");
      assertThat(result.randomPick()).isFalse();
    }
  }

  @Test
  void exploreFirstPicksAtRandomBelowTheThreshold() {
    SelectionPolicy policy =
        new ExploreFirstPolicy(SETTINGS, new ScriptedRandomSource().withIndexes(1));
    List<SelectionCandidate<String>> candidates =
        List.of(candidate("A", 1, estimate(3, 1, 3)), candidate("B", 1, estimate(1, 3, 4)));

    SelectionResult<String> result = policy.choose(candidates);

    assertThat(result.chosen()).isEqualTo("B");
    assertThat(result.randomPick()).isTrue();
  }

  @Test
  void exploreFirstExploitsOnceTheThresholdIsReached() {
    ScriptedRandomSource random = new ScriptedRandomSource();
    SelectionPolicy policy = new ExploreFirstPolicy(SETTINGS, random);
    List<SelectionCandidate<String>> candidates =
        List.of(candidate("A", 1, estimate(5, 2, 6)), candidate("B", 1, estimate(2, 3, 4)));

    SelectionResult<String> result = policy.choose(candidates);

    assertThat(result.chosen()).isEqualTo("A");
    assertThat(result.randomPick()).isFalse();
    assertThat(random.isExhausted()).isTrue();
  }

  @Test
  void tiesGoToTheFirstCandidate() {
    SelectionPolicy policy = new ExploreFirstPolicy(SETTINGS, new ScriptedRandomSource());
    SuccessEstimate tied = estimate(5, 5, 10);

    SelectionResult<String> aFirst =
        policy.choose(List.of(candidate("A", 1, tied), candidate("B", 1, tied)));
    SelectionResult<String> bFirst =
        policy.choose(List.of(candidate("B", 1, tied), candidate("A", 1, tied)));

    assertThat(aFirst.chosen()).isEqualTo("A");
    assertThat(bFirst.chosen()).isEqualTo("B");
  }

  @Test
  void randomPolicyIsUniformAndIgnoresMemory() {
    SelectionPolicy policy = new RandomPolicy(SETTINGS, new SeededRandomSource(17));
    List<SelectionCandidate<String>> candidates =
        List.of(
            candidate("A", 1, estimate(900, 100, 1000)),
            candidate("B", 1, estimate(100, 900, 1000)),
            candidate("C", 1, estimate(500, 500, 1000)));
    int draws = 30_000;

    int winsA = 0;
    int winsB = 0;
    int winsC = 0;
    for (int i = 0; i < draws; i++) {
      switch (policy.choose(candidates).chosen()) {
        case "A" -> winsA++;
        case "B" -> winsB++;
        default -> winsC++;
      }
    }

    assertThat((double) winsA / draws).isCloseTo(1.0 / 3, within(0.02));
    assertThat((double) winsB / draws).isCloseTo(1.0 / 3, within(0.02));
    assertThat((double) winsC / draws).isCloseTo(1.0 / 3, within(0.02));
  }
}
