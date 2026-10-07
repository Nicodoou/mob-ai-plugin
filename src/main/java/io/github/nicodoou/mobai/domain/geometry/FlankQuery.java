package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.Objects;

/** One flanker asking for its point, given every flanker of the same target. */
public record FlankQuery(
    PlayerPose pose, MobId self, Map<MobId, Vec3> flankers, double distanceBlocks) {
  public FlankQuery {
    Objects.requireNonNull(pose, "FlankQuery.pose");
    Objects.requireNonNull(self, "FlankQuery.self");
    Objects.requireNonNull(flankers, "FlankQuery.flankers");
    flankers = Map.copyOf(flankers);
    if (!flankers.containsKey(self)) {
      throw new IllegalArgumentException(
          "FlankQuery.self must be one of the flankers, got " + self.value());
    }
    if (!(distanceBlocks > 0) || !Double.isFinite(distanceBlocks)) {
      throw new IllegalArgumentException(
          "FlankQuery.distanceBlocks must be a positive number, got " + distanceBlocks);
    }
  }
}
