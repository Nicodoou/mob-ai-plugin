package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/**
 * The next step to the rally point: straight if the way keeps out of reach, else round the player
 * (CT-29).
 */
public final class RallyDetour {
  // At most this much further round per step, as in the flank.
  private static final double MAX_TURN_DEGREES = 45.0;
  private static final double FULL_TURN_DEGREES = 360.0;
  private static final double HALF_TURN_DEGREES = 180.0;

  private final CombatGeometry geometry;

  public RallyDetour(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "RallyDetour.geometry");
  }

  public Vec3 next(RallyLeg leg) {
    if (isClear(leg)) {
      return leg.to();
    }
    return detour(leg);
  }

  private boolean isClear(RallyLeg leg) {
    Vec3 start = leg.from().minus(leg.pose().position()).horizontal();
    Vec3 way = leg.to().minus(leg.from()).horizontal();
    double along = way.length() == 0 ? 0 : Math.clamp(-start.dot(way) / way.dot(way), 0.0, 1.0);
    return start.plus(way.times(along)).length() >= leg.keepOutBlocks();
  }

  private Vec3 detour(RallyLeg leg) {
    PlayerPose pose = leg.pose();
    double from = signedAngle(pose, leg.from());
    double turn =
        Math.clamp(
            shortestTurn(from, signedAngle(pose, leg.to())), -MAX_TURN_DEGREES, MAX_TURN_DEGREES);
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), from + turn);
    Vec3 point = pose.position().plus(direction.times(radiusFor(leg)));
    return new Vec3(point.x(), leg.from().y(), point.z());
  }

  private static double radiusFor(RallyLeg leg) {
    double distance = leg.from().minus(leg.pose().position()).horizontal().length();
    return Math.max(leg.keepOutBlocks(), distance);
  }

  private double signedAngle(PlayerPose pose, Vec3 point) {
    int side = CombatGeometry.sideOf(pose.facing(), point.minus(pose.position()).horizontal());
    return side * geometry.angleFromFacingDegrees(pose, point);
  }

  private static double shortestTurn(double from, double to) {
    double turn =
        ((to - from) % FULL_TURN_DEGREES + FULL_TURN_DEGREES + HALF_TURN_DEGREES)
                % FULL_TURN_DEGREES
            - HALF_TURN_DEGREES;
    // A half turn either way is as short; going on the mob's own side passes behind the player.
    if (turn == -HALF_TURN_DEGREES) {
      return from >= 0 ? HALF_TURN_DEGREES : -HALF_TURN_DEGREES;
    }
    return turn;
  }
}
