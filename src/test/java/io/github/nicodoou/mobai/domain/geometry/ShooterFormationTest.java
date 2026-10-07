package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShooterFormationTest {
  private static final double RADIUS = 25;
  private static final double TOLERANCE = 1e-9;
  private static final double SIDE_X = 21.65063509461097;
  private static final double SIDE_Z = -12.5;

  private final Vec3 center = new Vec3(0, 64, 0);
  private final ShooterFormation formation = new ShooterFormation();

  private static MobId mob(int number) {
    return new MobId(new UUID(0, number));
  }

  private Vec3 pointOf(MobId self, Map<MobId, Vec3> shooters) {
    return formation.pointFor(new ShooterQuery(center, self, shooters, RADIUS));
  }

  private static void assertPoint(Vec3 point, double x, double y, double z) {
    assertThat(point.x()).isCloseTo(x, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(y, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(z, within(TOLERANCE));
  }

  @Test
  void loneShooterKeepsItsBearing() {
    Map<MobId, Vec3> shooters = Map.of(mob(1), new Vec3(0, 64, 10));

    assertPoint(pointOf(mob(1), shooters), 0, 64, 25);
  }

  @Test
  void twoShootersStandOpposite() {
    Map<MobId, Vec3> shooters = Map.of(mob(1), new Vec3(0, 64, 10), mob(2), new Vec3(10, 64, 0));

    assertPoint(pointOf(mob(1), shooters), 0, 64, 25);
    assertPoint(pointOf(mob(2), shooters), 0, 64, -25);
  }

  @Test
  void threeShootersStandAHundredTwentyApart() {
    Map<MobId, Vec3> shooters =
        Map.of(
            mob(1), new Vec3(0, 64, 10),
            mob(2), new Vec3(10, 64, 0),
            mob(3), new Vec3(-10, 64, 0));

    assertPoint(pointOf(mob(2), shooters), SIDE_X, 64, SIDE_Z);
    assertPoint(pointOf(mob(3), shooters), -21.650635094610962, 64, SIDE_Z);
  }

  @Test
  void theLowestIdAnchorsTheRing() {
    Map<MobId, Vec3> shooters = Map.of(mob(2), new Vec3(0, 64, 10), mob(1), new Vec3(10, 64, 0));

    assertPoint(pointOf(mob(1), shooters), 25, 64, 0);
    assertPoint(pointOf(mob(2), shooters), -25, 64, 0);
  }

  @Test
  void shootersKeepTheirOrderAroundTheTarget() {
    Map<MobId, Vec3> shooters =
        Map.of(
            mob(1), new Vec3(0, 64, 10),
            mob(3), new Vec3(10, 64, 0),
            mob(2), new Vec3(-10, 64, 0));

    assertPoint(pointOf(mob(3), shooters), SIDE_X, 64, SIDE_Z);
    assertPoint(pointOf(mob(2), shooters), -SIDE_X, 64, SIDE_Z);
  }

  @Test
  void queryRejectsASelfThatIsNotAShooter() {
    Map<MobId, Vec3> shooters = Map.of(mob(1), new Vec3(0, 64, 10));

    assertThatThrownBy(() -> new ShooterQuery(center, mob(9), shooters, RADIUS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "ShooterQuery.self must be one of the shooters, got "
                + "00000000-0000-0000-0000-000000000009");
  }

  @Test
  void queryRejectsNonPositiveRadius() {
    Map<MobId, Vec3> shooters = Map.of(mob(1), new Vec3(0, 64, 10));

    assertThatThrownBy(() -> new ShooterQuery(center, mob(1), shooters, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ShooterQuery.radiusBlocks must be a positive number, got 0.0");
  }
}
