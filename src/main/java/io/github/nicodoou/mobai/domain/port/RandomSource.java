package io.github.nicodoou.mobai.domain.port;

/** Every random draw of the domain goes through here, so tests and replays are reproducible. */
public interface RandomSource {
  /** Uniform value in [0, 1). */
  double nextUnit();

  /** Standard normal value (mean 0, standard deviation 1). */
  double nextGaussian();

  /** Uniform integer in [0, bound); bound must be positive. */
  int nextIndex(int bound);
}
