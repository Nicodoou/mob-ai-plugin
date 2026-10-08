package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.junit.jupiter.api.Test;

class BodyFacingTest {
  private static final double TOLERANCE_DEGREES = 1e-4;

  private final Vec3 origin = new Vec3(0, 64, 0);

  @Test
  void yawFollowsMinecraftsConvention() {
    assertThat(BodyFacing.yawTowards(origin, new Vec3(0, 64, 5)))
        .isCloseTo(0f, within((float) TOLERANCE_DEGREES));
    assertThat(BodyFacing.yawTowards(origin, new Vec3(5, 64, 0)))
        .isCloseTo(-90f, within((float) TOLERANCE_DEGREES));
    assertThat(BodyFacing.yawTowards(origin, new Vec3(-5, 64, 0)))
        .isCloseTo(90f, within((float) TOLERANCE_DEGREES));
    assertThat(Math.abs(BodyFacing.yawTowards(origin, new Vec3(0, 64, -5))))
        .isCloseTo(180f, within((float) TOLERANCE_DEGREES));
  }

  @Test
  void heightDoesNotTurnTheBody() {
    float level = BodyFacing.yawTowards(origin, new Vec3(0, 64, 5));

    float above = BodyFacing.yawTowards(origin, new Vec3(0, 80, 5));

    assertThat(above).isCloseTo(0f, within((float) TOLERANCE_DEGREES));
    assertThat(above).isEqualTo(level);
  }
}
