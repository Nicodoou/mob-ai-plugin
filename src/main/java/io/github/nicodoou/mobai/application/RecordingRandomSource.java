package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Wraps the real random source and records every number the brain draws. */
public final class RecordingRandomSource implements RandomSource {
  private final RandomSource delegate;
  private final List<RecordedDraw> draws = new ArrayList<>();

  public RecordingRandomSource(RandomSource delegate) {
    this.delegate = Objects.requireNonNull(delegate, "RecordingRandomSource.delegate");
  }

  @Override
  public double nextUnit() {
    double value = delegate.nextUnit();
    draws.add(new RecordedDraw(DrawKind.UNIT, value, 0));
    return value;
  }

  @Override
  public double nextGaussian() {
    double value = delegate.nextGaussian();
    draws.add(new RecordedDraw(DrawKind.GAUSSIAN, value, 0));
    return value;
  }

  @Override
  public int nextIndex(int bound) {
    int index = delegate.nextIndex(bound);
    draws.add(new RecordedDraw(DrawKind.INDEX, index, bound));
    return index;
  }

  public List<RecordedDraw> draws() {
    return List.copyOf(draws);
  }

  public void clear() {
    draws.clear();
  }
}
