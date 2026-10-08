package io.github.nicodoou.mobai.domain.strategy;

import java.util.ArrayList;
import java.util.List;

/** Where a·x + b·x² is highest for x in [0, 1]; the smallest such x on a tie. */
public final class UnitIntervalPeak {
  private static final double LOWER_END = 0;
  private static final double UPPER_END = 1;

  private UnitIntervalPeak() {}

  public static double of(double linear, double quadratic) {
    if (!Double.isFinite(linear) || !Double.isFinite(quadratic)) {
      throw new IllegalArgumentException(
          "UnitIntervalPeak coefficients must be finite, got " + linear + ", " + quadratic);
    }
    Curve curve = new Curve(linear, quadratic);
    return highestOf(curve, candidatesOf(curve));
  }

  private static List<Double> candidatesOf(Curve curve) {
    List<Double> candidates = new ArrayList<>(List.of(LOWER_END, UPPER_END));
    if (curve.quadratic() < 0) {
      double vertex = -curve.linear() / (2 * curve.quadratic());
      if (vertex > LOWER_END && vertex < UPPER_END) {
        candidates.add(vertex);
      }
    }
    return candidates;
  }

  private static double highestOf(Curve curve, List<Double> candidates) {
    double best = candidates.getFirst();
    for (double candidate : candidates) {
      if (curve.valueAt(candidate) > curve.valueAt(best)) {
        best = candidate;
      }
    }
    return best;
  }

  private record Curve(double linear, double quadratic) {
    double valueAt(double x) {
      return linear * x + quadratic * x * x;
    }
  }
}
