package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.FlankFormation;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WaypointsTest {
  private static final double TOLERANCE = 1e-9;

  private final Waypoints waypoints =
      new Waypoints(
          new CombatGeometry(),
          new FlankFormation(new CombatGeometry()),
          () -> TestSettings.defaults().attack());
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @Test
  void flankPointUsesTheConfiguredDistance() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 0));

    Vec3 point = waypoints.flankPoint(pose, mob(1), flankers);

    assertThat(point.x()).isCloseTo(2.1213203435596424, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(0, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(-2.1213203435596424, within(TOLERANCE));
  }

  @Test
  void onlyOutsideTheShieldArcCountsAsFlank() {
    assertThat(waypoints.isOutsideTheShieldArc(pose, new Vec3(0, 0, -2))).isTrue();
    assertThat(waypoints.isOutsideTheShieldArc(pose, new Vec3(0, 0, 2))).isFalse();
    assertThat(waypoints.isOutsideTheShieldArc(pose, new Vec3(2, 0, 0))).isFalse();
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
