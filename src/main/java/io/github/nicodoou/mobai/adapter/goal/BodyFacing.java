package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Vec3;

/** Minecraft's yaw for an entity's body: 0 faces +Z (south) and it grows towards -X (west). */
final class BodyFacing {
  private BodyFacing() {}

  // Not the arrow's convention (ShotAim.rotationOf uses atan2(x, z)): do not "unify" them.
  static float yawTowards(Vec3 from, Vec3 to) {
    Vec3 offset = to.minus(from);
    return (float) Math.toDegrees(Math.atan2(-offset.x(), offset.z()));
  }
}
