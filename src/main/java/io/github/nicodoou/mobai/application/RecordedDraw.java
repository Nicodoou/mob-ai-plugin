package io.github.nicodoou.mobai.application;

import java.util.Objects;

public record RecordedDraw(DrawKind kind, double value, int bound) {
  public RecordedDraw {
    Objects.requireNonNull(kind, "RecordedDraw.kind");
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("RecordedDraw.value must be finite, got " + value);
    }
    if (kind == DrawKind.INDEX) {
      requireIndexWithinBound(value, bound);
    } else if (bound != 0) {
      throw new IllegalArgumentException(
          "RecordedDraw.bound must be 0 for " + kind + ", got " + bound);
    }
  }

  private static void requireIndexWithinBound(double value, int bound) {
    if (bound < 1) {
      throw new IllegalArgumentException(
          "RecordedDraw.bound must be positive for INDEX, got " + bound);
    }
    if (value != Math.rint(value) || value < 0 || value >= bound) {
      throw new IllegalArgumentException(
          "RecordedDraw.value must be an index below " + bound + ", got " + value);
    }
  }
}
