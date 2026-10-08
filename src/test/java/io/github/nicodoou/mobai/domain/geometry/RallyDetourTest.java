package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.junit.jupiter.api.Test;

class RallyDetourTest {
  private static final double TOLERANCE = 1e-9;
  private static final double FIVE_ROOT_TWO = 5 * Math.sqrt(2);

  private final RallyDetour detour = new RallyDetour(new CombatGeometry());
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @Test
  void aClearLegGoesStraightToThePoint() {
    RallyLeg leg = new RallyLeg(pose, new Vec3(-5, 64, -5), new Vec3(5, 64, -5), 4);

    Vec3 step = detour.next(leg);

    assertVec(step, 5, 64, -5);
  }

  @Test
  void aLegThroughTheReachGoesRoundTheBack() {
    RallyLeg leg = new RallyLeg(pose, new Vec3(-5, 64, -5), new Vec3(5, 64, -5), 6);

    Vec3 step = detour.next(leg);

    assertVec(step, 0, 64, -FIVE_ROOT_TWO);
  }

  @Test
  void aHalfTurnGoesRoundTheBack() {
    RallyLeg leg = new RallyLeg(pose, new Vec3(-10, 64, 0), new Vec3(10, 64, 0), 4);

    Vec3 step = detour.next(leg);

    assertVec(step, -FIVE_ROOT_TWO, 64, -FIVE_ROOT_TWO);
  }

  @Test
  void theShortWayRoundIsCappedAt45Degrees() {
    double z = -5 * Math.sqrt(3);
    RallyLeg leg = new RallyLeg(pose, new Vec3(-5, 64, z), new Vec3(5, 64, z), 9);

    Vec3 step = detour.next(leg);

    assertVec(step, 10 * Math.sin(Math.toRadians(15)), 64, -10 * Math.cos(Math.toRadians(15)));
  }

  @Test
  void aMobInsideTheReachStepsOutToIt() {
    RallyLeg leg = new RallyLeg(pose, new Vec3(0, 64, -3), new Vec3(0, 64, 10), 6);

    Vec3 step = detour.next(leg);

    assertVec(step, 3 * Math.sqrt(2), 64, -3 * Math.sqrt(2));
  }

  @Test
  void nonPositiveKeepOutIsRejected() {
    Vec3 from = new Vec3(-5, 64, -5);
    Vec3 to = new Vec3(5, 64, -5);

    assertThatThrownBy(() -> new RallyLeg(pose, from, to, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("RallyLeg.keepOutBlocks must be a positive number, got 0.0");
  }

  private static void assertVec(Vec3 actual, double x, double y, double z) {
    assertThat(actual.x()).isCloseTo(x, within(TOLERANCE));
    assertThat(actual.y()).isCloseTo(y, within(TOLERANCE));
    assertThat(actual.z()).isCloseTo(z, within(TOLERANCE));
  }
}
