package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.List;

/** Hands back recorded numbers in order and fails as soon as a replay asks for something else. */
public final class ReplayRandomSource implements RandomSource {
  private final List<RecordedDraw> draws;
  private int next;

  public ReplayRandomSource(List<RecordedDraw> draws) {
    this.draws = List.copyOf(draws);
  }

  @Override
  public double nextUnit() {
    return take(DrawKind.UNIT, 0).value();
  }

  @Override
  public double nextGaussian() {
    return take(DrawKind.GAUSSIAN, 0).value();
  }

  @Override
  public int nextIndex(int bound) {
    return (int) take(DrawKind.INDEX, bound).value();
  }

  public int remaining() {
    return draws.size() - next;
  }

  private RecordedDraw take(DrawKind kind, int bound) {
    if (remaining() == 0) {
      throw new IllegalStateException("Replay ran out of draws after " + draws.size());
    }
    RecordedDraw recorded = draws.get(next);
    if (recorded.kind() != kind || recorded.bound() != bound) {
      throw new IllegalStateException(
          "Replay diverged at draw "
              + (next + 1)
              + ": recorded "
              + recorded.kind()
              + " with bound "
              + recorded.bound()
              + ", asked for "
              + kind
              + " with bound "
              + bound);
    }
    next++;
    return recorded;
  }
}
