package io.github.nicodoou.mobai.adapter.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import org.junit.jupiter.api.Test;

class EntityReadingsTest {
  private static final double TOLERANCE = 1e-9;

  @Test
  void yawZeroFacesSouth() {
    Vec3 facing = EntityReadings.facingFromYaw(0);

    assertFacing(facing, 0, 0, 1);
  }

  @Test
  void yawNinetyFacesWest() {
    Vec3 facing = EntityReadings.facingFromYaw(90);

    assertFacing(facing, -1, 0, 0);
  }

  @Test
  void yawMinusNinetyFacesEast() {
    Vec3 facing = EntityReadings.facingFromYaw(-90);

    assertFacing(facing, 1, 0, 0);
  }

  @Test
  void yawOneEightyFacesNorth() {
    Vec3 facing = EntityReadings.facingFromYaw(180);

    assertFacing(facing, 0, 0, -1);
  }

  @Test
  void healthIsClampedToTheValidRange() {
    double above = EntityReadings.clampHealth(21, 20);
    double below = EntityReadings.clampHealth(-1, 20);
    double inside = EntityReadings.clampHealth(7.5, 20);

    assertThat(above).isEqualTo(20);
    assertThat(below).isEqualTo(0);
    assertThat(inside).isEqualTo(7.5);
  }

  private static void assertFacing(Vec3 facing, double x, double y, double z) {
    assertThat(facing.x()).isCloseTo(x, within(TOLERANCE));
    assertThat(facing.y()).isCloseTo(y, within(TOLERANCE));
    assertThat(facing.z()).isCloseTo(z, within(TOLERANCE));
  }
}
