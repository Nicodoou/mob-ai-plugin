package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/**
 * The flank in two phases (CT-16): while the player sees it, the flanker sidesteps out of sight by
 * the shortest way; once out of sight, it closes in on its slot behind the player.
 */
public final class FlankManeuver {
  // At most this much further round per step: each leg stays outside the keep-out distance.
  private static final double EVADE_STEP_DEGREES = 45.0;
  // Inside melee reach, with room for the player stepping away.
  static final double CLOSE_IN_BLOCKS = MinecraftConstants.MELEE_REACH_BLOCKS * 0.75;

  private final CombatGeometry geometry;
  private final FlankFormation formation;

  public FlankManeuver(CombatGeometry geometry, FlankFormation formation) {
    this.geometry = Objects.requireNonNull(geometry, "FlankManeuver.geometry");
    this.formation = Objects.requireNonNull(formation, "FlankManeuver.formation");
  }

  /** {@code query.distanceBlocks()} is how far the flanker keeps while the player sees it. */
  public FlankStep next(FlankQuery query) {
    Vec3 position = query.flankers().get(query.self());
    if (geometry.isOutOfSight(query.pose(), position)) {
      return new FlankStep(closeIn(query), true);
    }
    return new FlankStep(sidestep(query.pose(), position, query.distanceBlocks()), false);
  }

  private Vec3 closeIn(FlankQuery query) {
    return formation.pointFor(
        new FlankQuery(query.pose(), query.self(), query.flankers(), CLOSE_IN_BLOCKS));
  }

  // The foot of the perpendicular on the next direction: the shortest way round, never closer
  // than the keep-out distance.
  private Vec3 sidestep(PlayerPose pose, Vec3 position, double keepOutBlocks) {
    double angle = geometry.angleFromFacingDegrees(pose, position);
    double nextAngle = Math.min(angle + EVADE_STEP_DEGREES, CombatGeometry.FLANK_ANGLE_DEGREES);
    double distance = position.minus(pose.position()).horizontal().length();
    double radius = Math.max(keepOutBlocks, distance * Math.cos(Math.toRadians(nextAngle - angle)));
    int side = CombatGeometry.sideOf(pose.facing(), position.minus(pose.position()).horizontal());
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), side * nextAngle);
    return pose.position().plus(direction.times(radius));
  }
}
