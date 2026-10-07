package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.ArrayList;
import java.util.List;

public final class CombatGeometry {
  // A flanker stands well outside the shield arc so a small turn of the player does not cover it.
  private static final double FLANK_MARGIN_DEGREES = 45.0;
  static final double FLANK_ANGLE_DEGREES =
      MinecraftConstants.SHIELD_HALF_ARC_DEGREES + FLANK_MARGIN_DEGREES;
  // 90° of shield arc plus a margin, so a small turn of the mouse does not reveal a flanker.
  private static final double VISION_MARGIN_DEGREES = 30.0;
  static final double VISION_HALF_ANGLE_DEGREES =
      MinecraftConstants.SHIELD_HALF_ARC_DEGREES + VISION_MARGIN_DEGREES;
  // Without horizontal separation there is no "away"; any fixed direction keeps it deterministic.
  private static final Vec3 DEFAULT_RETREAT_DIRECTION = new Vec3(1, 0, 0);
  private static final int POSITIVE_SIDE = 1;
  private static final int NEGATIVE_SIDE = -1;
  // Past 90° from straight away, the mob would walk back past the danger.
  private static final List<Double> COVER_FAN_DEGREES =
      List.of(0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0);
  // A second, wider ring for when the first one is all open ground.
  private static final double COVER_OUTER_RING_EXTRA_BLOCKS = 4.0;

  public double angleFromFacingDegrees(PlayerPose pose, Vec3 point) {
    Vec3 offset = point.minus(pose.position()).horizontal();
    if (offset.length() == 0) {
      return 0;
    }
    return pose.facing().angleDegreesTo(offset);
  }

  public boolean isInShieldArc(PlayerPose pose, Vec3 attackerPosition) {
    return angleFromFacingDegrees(pose, attackerPosition)
        <= MinecraftConstants.SHIELD_HALF_ARC_DEGREES;
  }

  public boolean isOutOfSight(PlayerPose pose, Vec3 mobPosition) {
    return angleFromFacingDegrees(pose, mobPosition) > VISION_HALF_ANGLE_DEGREES;
  }

  public Vec3 flankPoint(PlayerPose pose, Vec3 mobPosition, double distanceBlocks) {
    requirePositiveDistance(distanceBlocks);
    Vec3 offset = mobPosition.minus(pose.position()).horizontal();
    int side = sideOf(pose.facing(), offset);
    Vec3 direction = rotateAroundVertical(pose.facing(), side * FLANK_ANGLE_DEGREES);
    return pose.position().plus(direction.times(distanceBlocks));
  }

  public Vec3 retreatPoint(Vec3 mobPosition, Vec3 dangerPosition, double distanceBlocks) {
    requirePositiveDistance(distanceBlocks);
    Vec3 away = awayDirection(mobPosition, dangerPosition);
    return mobPosition.plus(away.times(distanceBlocks));
  }

  public List<Vec3> coverCandidates(Vec3 mobPosition, Vec3 dangerPosition, double radiusBlocks) {
    requirePositiveDistance(radiusBlocks);
    Vec3 away = awayDirection(mobPosition, dangerPosition);
    Vec3 center = new Vec3(dangerPosition.x(), mobPosition.y(), dangerPosition.z());
    List<Vec3> candidates = new ArrayList<>(coverRing(away, center, radiusBlocks));
    candidates.addAll(coverRing(away, center, radiusBlocks + COVER_OUTER_RING_EXTRA_BLOCKS));
    return List.copyOf(candidates);
  }

  public Vec3 predictedAimPoint(Vec3 shooterEye, Vec3 aimPoint, Vec3 movementPerTick) {
    double flightTicks =
        shooterEye.distanceTo(aimPoint) / MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;
    return aimPoint.plus(movementPerTick.times(flightTicks));
  }

  public Vec3 leadShotVelocity(Vec3 shooterEye, Vec3 predictedAimPoint) {
    Vec3 delta = predictedAimPoint.minus(shooterEye);
    double horizontalDistance = delta.horizontal().length();
    Vec3 raised =
        new Vec3(
            delta.x(),
            delta.y() + horizontalDistance * MinecraftConstants.ARROW_ARC_FACTOR,
            delta.z());
    return raised.normalized().times(MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK);
  }

  static int sideOf(Vec3 facing, Vec3 offset) {
    double determinant = facing.x() * offset.z() - facing.z() * offset.x();
    return determinant >= 0 ? POSITIVE_SIDE : NEGATIVE_SIDE;
  }

  static Vec3 rotateAroundVertical(Vec3 direction, double degrees) {
    double radians = Math.toRadians(degrees);
    double cosine = Math.cos(radians);
    double sine = Math.sin(radians);
    return new Vec3(
        direction.x() * cosine - direction.z() * sine,
        direction.y(),
        direction.x() * sine + direction.z() * cosine);
  }

  private static Vec3 awayDirection(Vec3 mobPosition, Vec3 dangerPosition) {
    Vec3 away = mobPosition.minus(dangerPosition).horizontal();
    if (away.length() == 0) {
      return DEFAULT_RETREAT_DIRECTION;
    }
    return away.normalized();
  }

  private static List<Vec3> coverRing(Vec3 away, Vec3 center, double radiusBlocks) {
    return COVER_FAN_DEGREES.stream()
        .map(degrees -> center.plus(rotateAroundVertical(away, degrees).times(radiusBlocks)))
        .toList();
  }

  private static void requirePositiveDistance(double distanceBlocks) {
    if (!(distanceBlocks > 0) || !Double.isFinite(distanceBlocks)) {
      throw new IllegalArgumentException(
          "CombatGeometry.distanceBlocks must be a positive number, got " + distanceBlocks);
    }
  }
}
