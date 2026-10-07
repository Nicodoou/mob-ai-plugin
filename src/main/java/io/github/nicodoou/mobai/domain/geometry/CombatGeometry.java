package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
  // A low arc is enough for any target in bow range; above 45° the arrow lands closer, not farther.
  private static final double MIN_LAUNCH_PITCH_DEGREES = -60.0;
  private static final double MAX_LAUNCH_PITCH_DEGREES = 45.0;
  // Each step halves the pitch range: 30 steps leave it far below a thousandth of a degree.
  private static final int PITCH_SEARCH_STEPS = 30;
  // Longer than any arrow flight that can still hit (an arrow at 1.6 blocks per tick covers 15
  // blocks in about 10 ticks).
  private static final int MAX_FLIGHT_TICKS = 200;

  // An ally this close to the line between a shooter's eye and its target would take the arrow.
  static final double LINE_OF_FIRE_CLEARANCE_BLOCKS = 1.0;
  // Turns tried around the target, nearest first, to find a lane without allies.
  private static final List<Double> LANE_TURNS_DEGREES =
      List.of(0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0);

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

  public boolean isLineOfFireClear(Vec3 from, Vec3 to, List<Vec3> allies) {
    return allies.stream()
        .noneMatch(ally -> distanceToSegment(ally, from, to) < LINE_OF_FIRE_CLEARANCE_BLOCKS);
  }

  /** The first spot round the target, from {@code slot}, with a clear line of fire to it. */
  public Optional<Vec3> clearLane(Vec3 slot, Vec3 target, List<Vec3> allies) {
    Vec3 offset = slot.minus(target).horizontal();
    return LANE_TURNS_DEGREES.stream()
        .map(turn -> rotateAroundVertical(offset, turn))
        .map(turned -> new Vec3(target.x() + turned.x(), slot.y(), target.z() + turned.z()))
        .filter(spot -> isLineOfFireClear(spot, target, allies))
        .findFirst();
  }

  public Vec3 predictedAimPoint(Vec3 shooterEye, Vec3 aimPoint, Vec3 movementPerTick) {
    double flightTicks =
        shooterEye.distanceTo(aimPoint) / MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;
    return aimPoint.plus(movementPerTick.times(flightTicks));
  }

  /** The arrow velocity whose flight, with Minecraft's drag and gravity, reaches the aim point. */
  public Vec3 leadShotVelocity(Vec3 shooterEye, Vec3 predictedAimPoint) {
    Vec3 delta = predictedAimPoint.minus(shooterEye);
    double horizontalDistance = delta.horizontal().length();
    if (horizontalDistance == 0) {
      return delta.normalized().times(MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK);
    }
    double pitch = launchPitchRadians(horizontalDistance, delta.y());
    Vec3 flat = delta.horizontal().normalized().times(Math.cos(pitch));
    return new Vec3(flat.x(), Math.sin(pitch), flat.z())
        .times(MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK);
  }

  // Below 45° the arrow arrives higher the higher it is launched, so halving the range finds the
  // low arc; an aim point out of reach gets the longest shot.
  private static double launchPitchRadians(double horizontalDistance, double rise) {
    double low = Math.toRadians(MIN_LAUNCH_PITCH_DEGREES);
    double high = Math.toRadians(MAX_LAUNCH_PITCH_DEGREES);
    for (int step = 0; step < PITCH_SEARCH_STEPS; step++) {
      double middle = (low + high) / 2;
      if (heightAtDistance(middle, horizontalDistance) < rise) {
        low = middle;
      } else {
        high = middle;
      }
    }
    return (low + high) / 2;
  }

  // Minecraft's arrow: it moves, then keeps 99 % of its speed and falls 0.05 blocks per tick.
  private static double heightAtDistance(double pitchRadians, double horizontalDistance) {
    double horizontalSpeed =
        Math.cos(pitchRadians) * MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;
    double verticalSpeed = Math.sin(pitchRadians) * MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;
    double travelled = 0;
    double height = 0;
    for (int tick = 0; tick < MAX_FLIGHT_TICKS; tick++) {
      if (travelled + horizontalSpeed >= horizontalDistance) {
        return height + verticalSpeed * (horizontalDistance - travelled) / horizontalSpeed;
      }
      travelled += horizontalSpeed;
      height += verticalSpeed;
      horizontalSpeed *= MinecraftConstants.ARROW_DRAG_PER_TICK;
      verticalSpeed =
          verticalSpeed * MinecraftConstants.ARROW_DRAG_PER_TICK
              - MinecraftConstants.ARROW_GRAVITY_PER_TICK;
    }
    return Double.NEGATIVE_INFINITY;
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

  private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
    Vec3 segment = to.minus(from);
    double lengthSquared = segment.dot(segment);
    if (lengthSquared == 0) {
      return point.distanceTo(from);
    }
    double along = Math.clamp(point.minus(from).dot(segment) / lengthSquared, 0.0, 1.0);
    return point.distanceTo(from.plus(segment.times(along)));
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
