package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.SplittableRandom;

public final class SeededRandomSource implements RandomSource {
  private final SplittableRandom random;

  public SeededRandomSource(long seed) {
    this.random = new SplittableRandom(seed);
  }

  @Override
  public double nextUnit() {
    return random.nextDouble();
  }

  @Override
  public double nextGaussian() {
    return random.nextGaussian();
  }

  @Override
  public int nextIndex(int bound) {
    if (bound <= 0) {
      throw new IllegalArgumentException("bound must be positive, got " + bound);
    }
    return random.nextInt(bound);
  }
}
