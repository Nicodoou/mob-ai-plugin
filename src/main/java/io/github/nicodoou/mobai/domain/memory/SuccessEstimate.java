package io.github.nicodoou.mobai.domain.memory;

public record SuccessEstimate(double alpha, double beta, double observedAttempts) {
  public SuccessEstimate {
    if (!(alpha > 0) || !Double.isFinite(alpha)) {
      throw new IllegalArgumentException("SuccessEstimate.alpha must be positive, got " + alpha);
    }
    if (!(beta > 0) || !Double.isFinite(beta)) {
      throw new IllegalArgumentException("SuccessEstimate.beta must be positive, got " + beta);
    }
    if (!(observedAttempts >= 0)) {
      throw new IllegalArgumentException(
          "SuccessEstimate.observedAttempts must be zero or positive, got " + observedAttempts);
    }
  }

  public double mean() {
    return alpha / (alpha + beta);
  }
}
