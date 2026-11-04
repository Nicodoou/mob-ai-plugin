package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/**
 * The next point of a fighter's way to its seat, what to do about it, and whether the seat is one
 * of the strike seats or one of the seats that wait for a free place.
 */
public record EncircleStep(Vec3 waypoint, EncircleMove move, boolean striking) {
  public EncircleStep {
    Objects.requireNonNull(waypoint, "EncircleStep.waypoint");
    Objects.requireNonNull(move, "EncircleStep.move");
  }
}
