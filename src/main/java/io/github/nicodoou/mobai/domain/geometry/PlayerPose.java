package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

public record PlayerPose(Vec3 position, Vec3 facing) {
  public PlayerPose {
    Objects.requireNonNull(position, "PlayerPose.position");
    Objects.requireNonNull(facing, "PlayerPose.facing");
    Vec3 horizontalFacing = facing.horizontal();
    if (horizontalFacing.length() == 0) {
      throw new IllegalArgumentException(
          "PlayerPose.facing must have a horizontal component, got " + facing);
    }
    facing = horizontalFacing.normalized();
  }
}
