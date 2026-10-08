package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShotAimTest {
  private static final double TOLERANCE = 1e-9;
  private static final double ROTATION_TOLERANCE = 1e-4;
  private static final double ARROW_SPEED = MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;

  private final CombatGeometry geometry = new CombatGeometry();
  private final ShotAim aim = new ShotAim(geometry);
  private final Vec3 eye = new Vec3(0, 65.6, 0);
  private final Vec3 center = new Vec3(10, 64.9, 0);
  private final Vec3 movement = new Vec3(0, 0, 0.2);

  @Test
  void directShotAimsWhereTheTargetIs() {
    Vec3 velocity = aim.velocity(request(Attack.SKELETON_DIRECT_SHOT));

    assertSameVector(velocity, geometry.leadShotVelocity(eye, center));
  }

  @Test
  void leadShotAimsWhereTheTargetWillBe() {
    Vec3 velocity = aim.velocity(request(Attack.SKELETON_LEAD_SHOT));

    assertSameVector(velocity, leadVelocity());
    Vec3 direct = aim.velocity(request(Attack.SKELETON_DIRECT_SHOT));
    assertThat(velocity.z()).isNotCloseTo(direct.z(), within(TOLERANCE));
  }

  @Test
  void opportunisticShotAimsLikeTheLeadShot() {
    Vec3 velocity = aim.velocity(request(Attack.SKELETON_OPPORTUNISTIC_SHOT));

    assertSameVector(velocity, leadVelocity());
  }

  @Test
  void everyShotFliesAtArrowSpeed() {
    List<Attack> shots =
        List.of(
            Attack.SKELETON_DIRECT_SHOT,
            Attack.SKELETON_LEAD_SHOT,
            Attack.SKELETON_OPPORTUNISTIC_SHOT);

    for (Attack shot : shots) {
      assertThat(aim.velocity(request(shot)).length()).isCloseTo(ARROW_SPEED, within(TOLERANCE));
    }
  }

  @Test
  void arrowFacesWhereItFliesHorizontally() {
    assertRotation(new Vec3(0, 0, ARROW_SPEED), 0, 0);
    assertRotation(new Vec3(ARROW_SPEED, 0, 0), 90, 0);
    assertRotation(new Vec3(-ARROW_SPEED, 0, 0), -90, 0);
    assertRotation(new Vec3(0, 0, -ARROW_SPEED), 180, 0);
  }

  @Test
  void arrowFacesUpAndDownWithItsFlight() {
    assertRotation(new Vec3(0, 1, 1), 0, 45);
    assertRotation(new Vec3(0, -1, 1), 0, -45);
  }

  @Test
  void meleeAttacksAreNotShots() {
    assertThatThrownBy(() -> aim.velocity(request(Attack.ZOMBIE_FRONT_STRIKE)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ShotAim: ZOMBIE_FRONT_STRIKE is not a skeleton shot");
  }

  // B-04: the lane has to be checked towards the point the arrow flies to, not the target.
  @Test
  void laneIsCheckedTowardsWhereTheShotFlies() {
    Vec3 fastMovement = new Vec3(0, 0, 0.5);
    Vec3 leadPoint = geometry.predictedAimPoint(eye, center, fastMovement);
    Vec3 allyInTheLeadLane = eye.plus(leadPoint.minus(eye).times(0.8));
    List<Vec3> allies = List.of(allyInTheLeadLane);

    ShotRequest lead = new ShotRequest(Attack.SKELETON_LEAD_SHOT, eye, center, fastMovement);
    ShotRequest direct = new ShotRequest(Attack.SKELETON_DIRECT_SHOT, eye, center, fastMovement);

    assertThat(aim.isLaneClear(lead, allies)).isFalse();
    assertThat(aim.isLaneClear(direct, allies)).isTrue();
  }

  private ShotRequest request(Attack attack) {
    return new ShotRequest(attack, eye, center, movement);
  }

  private Vec3 leadVelocity() {
    return geometry.leadShotVelocity(eye, geometry.predictedAimPoint(eye, center, movement));
  }

  private static void assertSameVector(Vec3 actual, Vec3 expected) {
    assertThat(actual.x()).isCloseTo(expected.x(), within(TOLERANCE));
    assertThat(actual.y()).isCloseTo(expected.y(), within(TOLERANCE));
    assertThat(actual.z()).isCloseTo(expected.z(), within(TOLERANCE));
  }

  private void assertRotation(Vec3 velocity, double yaw, double pitch) {
    ArrowRotation rotation = aim.rotationOf(velocity);

    assertThat((double) rotation.yaw()).isCloseTo(yaw, within(ROTATION_TOLERANCE));
    assertThat((double) rotation.pitch()).isCloseTo(pitch, within(ROTATION_TOLERANCE));
  }
}
