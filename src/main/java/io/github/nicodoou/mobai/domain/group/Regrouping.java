package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import java.util.Optional;

/** A regroup in progress: when it started and where the group meets (CT-29). */
public record Regrouping(long startTick, Optional<Vec3> rallyPoint) {
  public Regrouping {
    if (startTick < 0) {
      throw new IllegalArgumentException(
          "Regrouping.startTick must be zero or positive, got " + startTick);
    }
    Objects.requireNonNull(rallyPoint, "Regrouping.rallyPoint");
  }
}
