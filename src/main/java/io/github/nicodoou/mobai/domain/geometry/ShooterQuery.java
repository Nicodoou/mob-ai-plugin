package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.Objects;

/** One shooter asking for its place, given every shooter of the same target. */
public record ShooterQuery(
    Vec3 center, MobId self, Map<MobId, Vec3> shooters, double radiusBlocks) {
  public ShooterQuery {
    Objects.requireNonNull(center, "ShooterQuery.center");
    Objects.requireNonNull(self, "ShooterQuery.self");
    Objects.requireNonNull(shooters, "ShooterQuery.shooters");
    shooters = Map.copyOf(shooters);
    if (!shooters.containsKey(self)) {
      throw new IllegalArgumentException(
          "ShooterQuery.self must be one of the shooters, got " + self.value());
    }
    if (!(radiusBlocks > 0) || !Double.isFinite(radiusBlocks)) {
      throw new IllegalArgumentException(
          "ShooterQuery.radiusBlocks must be a positive number, got " + radiusBlocks);
    }
  }
}
