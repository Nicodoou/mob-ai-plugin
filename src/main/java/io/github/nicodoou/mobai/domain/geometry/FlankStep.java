package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/** Where a flanker walks next, and whether it already is out of the player's sight. */
public record FlankStep(Vec3 waypoint, boolean outOfSight) {
  public FlankStep {
    Objects.requireNonNull(waypoint, "FlankStep.waypoint");
  }
}
