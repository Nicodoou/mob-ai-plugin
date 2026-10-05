package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.port.RandomSource;
import java.util.ArrayDeque;
import java.util.Deque;

public final class ScriptedRandomSource implements RandomSource {
  private final Deque<Double> units = new ArrayDeque<>();
  private final Deque<Double> gaussians = new ArrayDeque<>();
  private final Deque<Integer> indexes = new ArrayDeque<>();

  public ScriptedRandomSource() {}

  public ScriptedRandomSource withUnits(double... values) {
    for (double v : values) {
      units.addLast(v);
    }
    return this;
  }

  public ScriptedRandomSource withGaussians(double... values) {
    for (double v : values) {
      gaussians.addLast(v);
    }
    return this;
  }

  public ScriptedRandomSource withIndexes(int... values) {
    for (int v : values) {
      indexes.addLast(v);
    }
    return this;
  }

  @Override
  public double nextUnit() {
    if (units.isEmpty()) {
      throw new IllegalStateException("no scripted unit value left");
    }
    return units.removeFirst();
  }

  @Override
  public double nextGaussian() {
    if (gaussians.isEmpty()) {
      throw new IllegalStateException("no scripted gaussian value left");
    }
    return gaussians.removeFirst();
  }

  @Override
  public int nextIndex(int bound) {
    if (indexes.isEmpty()) {
      throw new IllegalStateException("no scripted index value left");
    }
    int value = indexes.removeFirst();
    if (value < 0 || value >= bound) {
      throw new IllegalStateException("scripted index " + value + " is outside [0, " + bound + ")");
    }
    return value;
  }

  public boolean isExhausted() {
    return units.isEmpty() && gaussians.isEmpty() && indexes.isEmpty();
  }
}
