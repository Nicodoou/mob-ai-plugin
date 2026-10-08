package io.github.nicodoou.mobai.simulation;

import java.util.List;
import java.util.OptionalInt;

/** How close an episode got to the best recipe (CT-30 gate). */
final class EpisodeMetrics {
  static final int WINDOW = 10;
  static final double COMPETENCE_TOLERANCE = 0.05;

  private EpisodeMetrics() {}

  static double regretOfFirst(List<Double> trueScores, double optimum, int plans) {
    requirePlans(trueScores, plans);
    return optimum - average(trueScores.subList(0, plans));
  }

  static double regretOfLast(List<Double> trueScores, double optimum, int plans) {
    requirePlans(trueScores, plans);
    return optimum - average(trueScores.subList(trueScores.size() - plans, trueScores.size()));
  }

  static OptionalInt plansToCompetent(List<Double> trueScores, double optimum) {
    double competentScore = optimum - COMPETENCE_TOLERANCE;
    for (int start = 0; start + WINDOW <= trueScores.size(); start++) {
      if (average(trueScores.subList(start, start + WINDOW)) >= competentScore) {
        return OptionalInt.of(start + WINDOW);
      }
    }
    return OptionalInt.empty();
  }

  private static double average(List<Double> scores) {
    return scores.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
  }

  private static void requirePlans(List<Double> trueScores, int plans) {
    if (plans < 1 || plans > trueScores.size()) {
      throw new IllegalArgumentException(
          "EpisodeMetrics.plans must be between 1 and " + trueScores.size() + ", got " + plans);
    }
  }
}
