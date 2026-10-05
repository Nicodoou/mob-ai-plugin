package io.github.nicodoou.mobai.domain.shared;

public record AttemptId(long value) {
  public AttemptId {
    if (value < 1) {
      throw new IllegalArgumentException("AttemptId must be at least 1, got " + value);
    }
  }
}
