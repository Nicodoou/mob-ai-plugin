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

  // A valid shape accepts within a handful of draws; hitting this limit means the input or the
  // algorithm is broken, so it fails loudly instead of looping forever.
  private static final int MAX_DRAWS = 1_000;

  private final RandomSource random;

  public BetaSampler(RandomSource random) {
    this.random = Objects.requireNonNull(random, "BetaSampler.random");
  }

  public double sample(SuccessEstimate estimate) {
    double alphaGamma = sampleGamma(estimate.alpha());
    double betaGamma = sampleGamma(estimate.beta());
    return proportion(alphaGamma, betaGamma);
  }

  private static double proportion(double part, double other) {
    double total = part + other;
    if (total == 0) {
      return NEUTRAL_RATE;
    }
    return part / total;
  }

  private double sampleGamma(double shape) {
    if (shape < 1) {
      return sampleBoostedGamma(shape);
    }
    return sampleMarsagliaTsang(shape);
  }

  // Marsaglia-Tsang only works for shapes of at least 1; smaller shapes are sampled at shape + 1
  // and scaled down by U^(1/shape).
  private double sampleBoostedGamma(double shape) {
    double boosted = sampleMarsagliaTsang(shape + 1);
    return boosted * Math.pow(random.nextUnit(), 1 / shape);
  }

  private double sampleMarsagliaTsang(double shape) {
    double d = shape - ONE_THIRD;
    double c = 1 / Math.sqrt(NINE * d);
    for (int draw = 0; draw < MAX_DRAWS; draw++) {
      Proposal proposal = propose(c);
      if (isAccepted(proposal, d, random.nextUnit())) {
        return d * proposal.cubedV();
      }
    }
    throw new IllegalStateException(
        "gamma sampler rejected " + MAX_DRAWS + " proposals for shape " + shape);
  }

  private Proposal propose(double c) {
    for (int draw = 0; draw < MAX_DRAWS; draw++) {
      double z = random.nextGaussian();
      double v = 1 + c * z;
      if (v > 0) {
        return new Proposal(z, v * v * v);
      }
    }
    throw new IllegalStateException(
        "gamma sampler found no positive proposal in " + MAX_DRAWS + " draws (c = " + c + ")");
  }

  private static boolean isAccepted(Proposal proposal, double d, double u) {
    double z = proposal.z();
    double cubedV = proposal.cubedV();
    if (u < 1 - SQUEEZE * z * z * z * z) {
      return true;
    }
    return Math.log(u) < HALF * z * z + d * (1 - cubedV + Math.log(cubedV));
  }

  private record Proposal(double z, double cubedV) {}
}
