package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
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

    FlankStep step = waypoints.flankStep(pose, mob(1), flankers);

    assertThat(step.waypoint().horizontal().length()).isCloseTo(4.0, within(TOLERANCE));
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
  void backOffPointReachesTheMinimumRange() {
    Vec3 point = waypoints.backOffPoint(new Vec3(3, 64, 4), new Vec3(0, 64, 0));

    assertThat(point.x()).isCloseTo(4.8, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(64, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(6.4, within(TOLERANCE));
  }

  @Test
  void coverCandidatesUseTheRetreatDistance() {
    List<Vec3> candidates = waypoints.coverCandidates(new Vec3(0, 64, 5), new Vec3(0, 64, 0));

    assertThat(candidates).hasSize(14);
    assertThat(candidates.get(0).z()).isCloseTo(16, within(TOLERANCE));
    assertThat(candidates.get(7).z()).isCloseTo(20, within(TOLERANCE));
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
