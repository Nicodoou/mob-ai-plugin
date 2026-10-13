package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/** Where a pressing zombie goes, and whether it strikes from there or waits its turn. */
public record SurroundSpot(Vec3 point, boolean striking) {
  public SurroundSpot {
    Objects.requireNonNull(point, "SurroundSpot.point");
  }
}
