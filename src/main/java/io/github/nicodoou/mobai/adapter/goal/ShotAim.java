package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Objects;

/** The arrow velocity of each shot: where the target is, or where it is going to be. */
public final class ShotAim {
  private final CombatGeometry geometry;

  public ShotAim(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "ShotAim.geometry");
  }

  public Vec3 velocity(ShotRequest request) {
    return geometry.leadShotVelocity(request.eye(), aimPoint(request));
  }

  /** Whether no ally stands in the lane towards the point this shot is aimed at (B-04). */
  public boolean isLaneClear(ShotRequest request, List<Vec3> allies) {
    return geometry.isLineOfFireClear(request.eye(), aimPoint(request), allies);
  }

  // Minecraft's projectile convention (the one its own shots use): yaw 0 flies towards +Z and
  // grows towards +X. An arrow facing anywhere else drifts sideways until the game turns it.
  public ArrowRotation rotationOf(Vec3 velocity) {
    double horizontal = velocity.horizontal().length();
    return new ArrowRotation(
        (float) Math.toDegrees(Math.atan2(velocity.x(), velocity.z())),
        (float) Math.toDegrees(Math.atan2(velocity.y(), horizontal)));
  }

  // The opportunistic shot is about when to shoot, not how: it aims like the lead shot.
  private Vec3 aimPoint(ShotRequest request) {
    return switch (request.attack()) {
      case SKELETON_DIRECT_SHOT -> request.targetCenter();
      case SKELETON_LEAD_SHOT, SKELETON_OPPORTUNISTIC_SHOT ->
          geometry.predictedAimPoint(
              request.eye(), request.targetCenter(), request.movementPerTick());
      default ->
          throw new IllegalArgumentException(
              "ShotAim: " + request.attack() + " is not a skeleton shot");
    };
  }
}
