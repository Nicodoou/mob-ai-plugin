package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.junit.jupiter.api.Test;

class PlayerPoseTest {
  @Test
  void facingIsFlattenedAndNormalized() {
    PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(3, 5, 4));

    assertThat(pose.facing().x()).isCloseTo(0.6, within(1e-9));
    assertThat(pose.facing().y()).isCloseTo(0.0, within(1e-9));
    assertThat(pose.facing().z()).isCloseTo(0.8, within(1e-9));
  }

  @Test
  void rejectsVerticalFacing() {
    assertThatThrownBy(() -> new PlayerPose(Vec3.ZERO, new Vec3(0, -1, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "PlayerPose.facing must have a horizontal component, got Vec3[x=0.0, y=-1.0, z=0.0]");
  }
}
