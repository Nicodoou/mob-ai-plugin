package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Gives each flanker of a target its own point, so flankers on the same side do not collide. */
public final class FlankFormation {
  // Each flanker further down a side stands this much further round the player.
  private static final double SLOT_STEP_DEGREES = 30.0;
  // Past straight behind, a flanker would end up on the other side.
  private static final double BEHIND_DEGREES = 180.0;

  private final CombatGeometry geometry;

  public FlankFormation(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "FlankFormation.geometry");
  }

  public Vec3 pointFor(FlankQuery query) {
    PlayerPose pose = query.pose();
    int side = sideOf(pose, query.flankers().get(query.self()));
    double angle =
        Math.min(
            CombatGeometry.FLANK_ANGLE_DEGREES + slotOf(query, side) * SLOT_STEP_DEGREES,
            BEHIND_DEGREES);
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), side * angle);
    return pose.position().plus(direction.times(query.distanceBlocks()));
  }

  private static int sideOf(PlayerPose pose, Vec3 position) {
    return CombatGeometry.sideOf(pose.facing(), position.minus(pose.position()).horizontal());
  }

  // The flanker furthest round takes the first slot, so nobody crosses another on the way.
  private int slotOf(FlankQuery query, int side) {
    List<MobId> sameSide =
        query.flankers().entrySet().stream()
            .filter(flanker -> sideOf(query.pose(), flanker.getValue()) == side)
            .sorted(furthestRoundFirst(query.pose()))
            .map(Map.Entry::getKey)
            .toList();
    return sameSide.indexOf(query.self());
  }

  private Comparator<Map.Entry<MobId, Vec3>> furthestRoundFirst(PlayerPose pose) {
    Comparator<Map.Entry<MobId, Vec3>> byAngle =
        Comparator.comparingDouble(
            flanker -> geometry.angleFromFacingDegrees(pose, flanker.getValue()));
    return byAngle.reversed().thenComparing(flanker -> flanker.getKey().value());
  }
}
