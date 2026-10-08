package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankFormation;
import io.github.nicodoou.mobai.domain.geometry.FlankManeuver;
import io.github.nicodoou.mobai.domain.geometry.FlankStep;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaypointsTest {
  private static final double TOLERANCE = 1e-9;

  private final Waypoints waypoints =
      new Waypoints(
          new CombatGeometry(),
          new FlankManeuver(new CombatGeometry(), new FlankFormation(new CombatGeometry())),
          () -> TestSettings.defaults().attack());
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @Test
  void flankStepKeepsTheConfiguredDistanceWhileSeen() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(0, 0, 2));

    FlankStep step = waypoints.flankStep(new PlayerTarget(pose, 3.0), mob(1), flankers);

    assertThat(step.waypoint().horizontal().length()).isCloseTo(4.0, within(TOLERANCE));
  }

  @Test
  void sideStepPointClearsTheMobsWidthPlusTheMargin() {
    PlayerPose standing = new PlayerPose(new Vec3(0, 64, 0), new Vec3(0, 0, 1));

    Vec3 point = waypoints.sideStepPoint(standing, new Vec3(1, 64, 1), 0.3);

    double expectedDegrees = Math.toDegrees(Math.atan(0.3 / 1.5)) + 15.0;
    assertThat(point.minus(standing.position()).horizontal().length()).isCloseTo(1.5, within(1e-6));
    assertThat(new CombatGeometry().angleFromFacingDegrees(standing, point))
        .isCloseTo(expectedDegrees, within(1e-6));
  }

  @Test
  void onlyOutOfSightCountsAsFlank() {
    assertThat(waypoints.isOutOfSight(pose, new Vec3(0, 0, -2))).isTrue();
    assertThat(waypoints.isOutOfSight(pose, new Vec3(2, 0, 0))).isFalse();
    assertThat(waypoints.isOutOfSight(pose, new Vec3(0, 0, 2))).isFalse();
  }

  @Test
  void retreatPointStopsAtTheRetreatDistance() {
    Vec3 point = waypoints.retreatPoint(new Vec3(3, 64, 4), new Vec3(0, 60, 0)).orElseThrow();

    assertThat(point.x()).isCloseTo(9.6, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(12.8, within(TOLERANCE));
  }

  @Test
  void mobFarEnoughHoldsItsGround() {
    Vec3 danger = new Vec3(0, 64, 0);

    assertThat(waypoints.retreatPoint(new Vec3(0, 64, 16), danger)).isEmpty();
    Vec3 point = waypoints.retreatPoint(new Vec3(0, 64, 15.9), danger).orElseThrow();
    assertThat(point.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(16, within(TOLERANCE));
  }

  @Test
  void retreatDistanceIgnoresHeight() {
    Vec3 point = waypoints.retreatPoint(new Vec3(0, 80, 10), new Vec3(0, 64, 0)).orElseThrow();

    assertThat(point.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(80, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(16, within(TOLERANCE));
  }

  @Test
  void evadePointStepsOutOfReach() {
    Vec3 point = waypoints.evadePoint(new Vec3(0, 64, 2), new Vec3(0, 64, 0), 3.0).orElseThrow();

    assertThat(point.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(3.5, within(TOLERANCE));
  }

  @Test
  void zombieAlreadyOutOfReachDoesNotMove() {
    Vec3 danger = new Vec3(0, 64, 0);

    assertThat(waypoints.evadePoint(new Vec3(0, 64, 3.5), danger, 3.0)).isEmpty();
    assertThat(waypoints.evadePoint(new Vec3(0, 64, 5), danger, 3.0)).isEmpty();
  }

  @Test
  void keepAwayPointStepsOutToTheDistance() {
    Vec3 point = waypoints.keepAwayPoint(new Vec3(0, 64, 2), new Vec3(0, 64, 0), 4.5).orElseThrow();

    assertThat(point.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(4.5, within(TOLERANCE));
  }

  @Test
  void keepAwayPointIsEmptyWhenAlreadyThatFar() {
    Vec3 danger = new Vec3(0, 64, 0);

    assertThat(waypoints.keepAwayPoint(new Vec3(0, 64, 4.5), danger, 4.5)).isEmpty();
    assertThat(waypoints.keepAwayPoint(new Vec3(0, 64, 6), danger, 4.5)).isEmpty();
  }

  @Test
  void longerReachKeepsFlankersFarther() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(0, 0, 2));

    FlankStep step = waypoints.flankStep(new PlayerTarget(pose, 4.5), mob(1), flankers);

    assertThat(step.waypoint().horizontal().length()).isCloseTo(5.5, within(TOLERANCE));
  }

  @Test
  void evadePointGrowsWithReach() {
    Vec3 point = waypoints.evadePoint(new Vec3(0, 64, 2), new Vec3(0, 64, 0), 4.5).orElseThrow();

    assertThat(point.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(5, within(TOLERANCE));
  }

  @Test
  void playerTargetRejectsNonPositiveReach() {
    assertThatThrownBy(() -> new PlayerTarget(pose, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlayerTarget.reachBlocks must be a positive number, got 0.0");
  }

  @Test
  void shooterSlotSitsMidwayInTheBowRange() {
    Map<MobId, Vec3> shooters = Map.of(mob(1), new Vec3(0, 64, 5));

    Vec3 slot = waypoints.shooterSlot(new Vec3(0, 64, 0), mob(1), shooters);

    assertThat(slot.x()).isCloseTo(0, within(TOLERANCE));
    assertThat(slot.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(slot.z()).isCloseTo(11.5, within(TOLERANCE));
  }

  @Test
  void lineOfFireAndLanesComeFromTheGeometry() {
    List<Vec3> blocking = List.of(new Vec3(0, 0, 10));
    List<Vec3> allies = List.of(new Vec3(0, 65, 12));

    Vec3 lane = waypoints.clearLane(new Vec3(0, 65, 25), new Vec3(0, 65, 0), allies).orElseThrow();

    assertThat(waypoints.isLineOfFireClear(Vec3.ZERO, new Vec3(0, 0, 20), blocking)).isFalse();
    assertThat(lane.x()).isCloseTo(-12.499999999999998, within(TOLERANCE));
    assertThat(lane.y()).isCloseTo(65, within(TOLERANCE));
    assertThat(lane.z()).isCloseTo(21.65063509461097, within(TOLERANCE));
  }

  @Test
  void coverCandidatesUseTheRetreatDistance() {
    List<Vec3> candidates = waypoints.coverCandidates(new Vec3(0, 64, 5), new Vec3(0, 64, 0));

    assertThat(candidates).hasSize(14);
    assertThat(candidates.get(0).z()).isCloseTo(16, within(TOLERANCE));
    assertThat(candidates.get(7).z()).isCloseTo(20, within(TOLERANCE));
  }

  @Test
  void perchCandidatesStayWithinBowRange() {
    Vec3 target = new Vec3(0, 64, 0);

    List<Vec3> candidates = waypoints.perchCandidates(new Vec3(0, 64, 12), target);

    assertThat(candidates).hasSize(5);
    assertThat(candidates)
        .allSatisfy(
            candidate ->
                assertThat(candidate.minus(target).horizontal().length())
                    .isCloseTo(12.0, within(TOLERANCE)));
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
