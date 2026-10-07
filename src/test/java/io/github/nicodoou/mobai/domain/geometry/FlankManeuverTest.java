package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlankManeuverTest {
  private static final double TOLERANCE = 1e-9;
  private static final double KEEP_OUT_BLOCKS = 4.0;

  private final CombatGeometry geometry = new CombatGeometry();
  private final FlankFormation formation = new FlankFormation(geometry);
  private final FlankManeuver maneuver = new FlankManeuver(geometry, formation);
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @Test
  void seenStraightAheadSidestepsFortyFiveDegrees() {
    FlankStep step = maneuver.next(alone(new Vec3(0, 0, 5)));

    assertThat(step.outOfSight()).isFalse();
    assertVec(step.waypoint(), -2.8284271247461903, 0, 2.8284271247461903);
  }

  @Test
  void sidestepStaysOnTheMobsSide() {
    FlankStep step = maneuver.next(alone(new Vec3(3, 0, 4)));

    assertVec(step.waypoint(), 3.959797974644666, 0, 0.5656854249492387);
  }

  @Test
  void farFlankerTakesTheShortestWay() {
    FlankStep step = maneuver.next(alone(new Vec3(0, 0, 20)));

    assertVec(step.waypoint(), -10, 0, 10);
  }

  @Test
  void lastSidestepExitsAtTheFlankAngle() {
    FlankStep step = maneuver.next(alone(new Vec3(-5.908846518073248, 0, -1.0418890660015818)));

    assertThat(step.outOfSight()).isFalse();
    assertVec(step.waypoint(), -3.4753677920374146, 0, -3.475367792037414);
  }

  @Test
  void sidestepNeverGoesInsideTheKeepOutDistance() {
    FlankStep step = maneuver.next(alone(new Vec3(0, 0, 2)));

    assertThat(step.waypoint().horizontal().length()).isCloseTo(4.0, within(TOLERANCE));
  }

  @Test
  void outOfSightClosesInOnItsSlot() {
    FlankStep step = maneuver.next(alone(new Vec3(2, 0, -2)));

    assertThat(step.outOfSight()).isTrue();
    assertVec(step.waypoint(), 1.0606601717798214, 0, -1.0606601717798212);
  }

  @Test
  void closeInPointIsWithinStrikeReach() {
    FlankStep step = maneuver.next(alone(new Vec3(0, 0, -5)));

    double distance = step.waypoint().horizontal().length();
    assertThat(distance).isCloseTo(1.5, within(TOLERANCE));
    assertThat(distance).isLessThan(MinecraftConstants.MELEE_REACH_BLOCKS);
  }

  @Test
  void closeInUsesTheFormation() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, -1), mob(2), new Vec3(2, 0, -3));

    FlankStep step = maneuver.next(new FlankQuery(pose, mob(2), flankers, KEEP_OUT_BLOCKS));

    Vec3 slot = formation.pointFor(new FlankQuery(pose, mob(2), flankers, 1.5));
    assertThat(step.outOfSight()).isTrue();
    assertVec(step.waypoint(), slot.x(), slot.y(), slot.z());
  }

  private FlankQuery alone(Vec3 position) {
    return new FlankQuery(pose, mob(1), Map.of(mob(1), position), KEEP_OUT_BLOCKS);
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(0, id));
  }

  private static void assertVec(Vec3 actual, double x, double y, double z) {
    assertThat(actual.x()).isCloseTo(x, within(TOLERANCE));
    assertThat(actual.y()).isCloseTo(y, within(TOLERANCE));
    assertThat(actual.z()).isCloseTo(z, within(TOLERANCE));
  }
}
