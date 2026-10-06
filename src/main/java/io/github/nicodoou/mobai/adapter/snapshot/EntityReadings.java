package io.github.nicodoou.mobai.adapter.snapshot;

import io.github.nicodoou.mobai.domain.shared.Vec3;

final class EntityReadings {
  private EntityReadings() {}

  // Minecraft's yaw: 0 faces +Z (south) and grows clockwise seen from above.
  static Vec3 facingFromYaw(float yawDegrees) {
    double radians = Math.toRadians(yawDegrees);
    return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
  }

  // The server can report health above the maximum for a tick, which a snapshot would reject.
  static double clampHealth(double health, double maxHealth) {
    return Math.clamp(health, 0, maxHealth);
  }
}
