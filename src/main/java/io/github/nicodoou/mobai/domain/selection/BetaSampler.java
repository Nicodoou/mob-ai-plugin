package io.github.nicodoou.mobai.domain.selection;

import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.Objects;

public final class BetaSampler {
  // Constants of the Marsaglia-Tsang (2000) gamma sampler.
  private static final double ONE_THIRD = 1.0 / 3;
  private static final double NINE = 9;
  private static final double SQUEEZE = 0.0331;
  private static final double HALF = 0.5;

  // Only reachable with shapes below 1 and a uniform draw of exactly 0.
  private static final double NEUTRAL_RATE = 0.5;

  private final RandomSource random;

  public BetaSampler(RandomSource random) {
    this.random = Objects.requireNonNull(random, "BetaSampler.random");
  }

  public double sample(SuccessEstimate estimate) {
    double x = sampleGamma(estimate.alpha());
    double y = sampleGamma(estimate.beta());
    double total = x + y;
    if (total == 0) {
      return NEUTRAL_RATE;
    }
    return x / total;
  }

  private double sampleGamma(double shape) {
    if (shape < 1) {
      double boosted = sampleGamma(shape + 1);
      double u = random.nextUnit();
      return boosted * Math.pow(u, 1 / shape);
    }
    double d = shape - ONE_THIRD;
    double c = 1 / Math.sqrt(NINE * d);
    while (true) {
      double z;
      double v;
      do {
        z = random.nextGaussian();
        v = 1 + c * z;
      } while (v <= 0);
      v = v * v * v;
      double u = random.nextUnit();
      if (u < 1 - SQUEEZE * z * z * z * z) {
        return d * v;
      }
      if (Math.log(u) < HALF * z * z + d * (1 - v + Math.log(v))) {
        return d * v;
      }
    }
  }
}
