package io.github.nicodoou.mobai.adapter.runtime;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.SplittableRandom;

public final class JdkRandomSource implements RandomSource {
  private final SplittableRandom random;

  public JdkRandomSource(long seed) {
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
    if (bound < 1) {
      throw new IllegalArgumentException("JdkRandomSource.bound must be positive, got " + bound);
    }
    return random.nextInt(bound);
  }
}
