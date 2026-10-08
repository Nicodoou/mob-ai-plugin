package io.github.nicodoou.mobai.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class EpisodeMetricsTest {
  private static final int HALF_EPISODE = 10;
  private static final double POOR_SCORE = 0.1;
  private static final double GOOD_SCORE = 0.8;

  private static List<Double> poorThenGood() {
    List<Double> scores = new ArrayList<>();
    for (int plan = 0; plan < HALF_EPISODE; plan++) {
      scores.add(POOR_SCORE);
    }
    for (int plan = 0; plan < HALF_EPISODE; plan++) {
      scores.add(GOOD_SCORE);
    }
    return scores;
  }

  @Test
  void regretsAverageTheFirstAndLastPlans() {
    List<Double> scores = poorThenGood();

    double first = EpisodeMetrics.regretOfFirst(scores, 0.81, HALF_EPISODE);
    double last = EpisodeMetrics.regretOfLast(scores, 0.81, HALF_EPISODE);

    assertThat(first).isEqualTo(0.71, within(1e-9));
    assertThat(last).isEqualTo(0.01, within(1e-9));
  }

  @Test
  void competenceNeedsAWholeWindowCloseToTheOptimum() {
    List<Double> scores = poorThenGood();

    assertThat(EpisodeMetrics.plansToCompetent(scores, 0.81)).hasValue(20);
    assertThat(EpisodeMetrics.plansToCompetent(scores, 0.9)).isEmpty();
  }
}
