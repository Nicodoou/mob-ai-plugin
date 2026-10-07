package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlankFormationTest {
  private static final double DISTANCE = 3;
  private static final double TOLERANCE = 1e-9;
  private static final double FIRST_SLOT_X = 2.1213203435596424;
  private static final double FIRST_SLOT_Z = -2.1213203435596424;
  private static final double SECOND_SLOT_X = 0.7764571353075622;
  private static final double SECOND_SLOT_Z = -2.897777478867205;

  private final PlayerPose pose = new PlayerPose(new Vec3(0, 0, 0), new Vec3(0, 0, 1));
  private final FlankFormation formation = new FlankFormation(new CombatGeometry());

  private static MobId mob(int number) {
    return new MobId(new UUID(0, number));
  }

  private Vec3 pointOf(MobId self, Map<MobId, Vec3> flankers) {
    return formation.pointFor(new FlankQuery(pose, self, flankers, DISTANCE));
  }

  private static void assertPoint(Vec3 point, double x, double y, double z) {
    assertThat(point.x()).isCloseTo(x, within(TOLERANCE));
    assertThat(point.y()).isCloseTo(y, within(TOLERANCE));
    assertThat(point.z()).isCloseTo(z, within(TOLERANCE));
  }

  @Test
  void loneFlankerGetsTheSingleFlankPoint() {
    Vec3 position = new Vec3(2, 0, 0);

    Vec3 point = pointOf(mob(1), Map.of(mob(1), position));

    assertPoint(point, FIRST_SLOT_X, 0, FIRST_SLOT_Z);
    Vec3 expected = new CombatGeometry().flankPoint(pose, position, DISTANCE);
    assertPoint(point, expected.x(), expected.y(), expected.z());
  }

  @Test
  void furthestRoundFlankerTakesTheFirstSlot() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 1), mob(2), new Vec3(2, 0, -1));

    assertPoint(pointOf(mob(2), flankers), FIRST_SLOT_X, 0, FIRST_SLOT_Z);
    assertPoint(pointOf(mob(1), flankers), SECOND_SLOT_X, 0, SECOND_SLOT_Z);
  }

  @Test
  void thirdFlankerOnASideStandsBehind() {
    Map<MobId, Vec3> flankers =
        Map.of(mob(1), new Vec3(2, 0, 1), mob(2), new Vec3(2, 0, -1), mob(3), new Vec3(3, 0, 0));

    assertPoint(pointOf(mob(1), flankers), 0, 0, -3);
  }

  @Test
  void flankersOnOppositeSidesKeepTheirSide() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 0), mob(4), new Vec3(-2, 0, 0));

    assertPoint(pointOf(mob(1), flankers), FIRST_SLOT_X, 0, FIRST_SLOT_Z);
    assertPoint(pointOf(mob(4), flankers), -FIRST_SLOT_X, 0, FIRST_SLOT_Z);
  }

  @Test
  void tiesGoToTheLowerMobIdFirst() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 0), mob(2), new Vec3(2, 0, 0));

    assertPoint(pointOf(mob(1), flankers), FIRST_SLOT_X, 0, FIRST_SLOT_Z);
    assertPoint(pointOf(mob(2), flankers), SECOND_SLOT_X, 0, SECOND_SLOT_Z);
  }

  @Test
  void pointUsesThePlayerPositionAndHeight() {
    PlayerPose moved = new PlayerPose(new Vec3(10, 70, -5), new Vec3(0, 0, 1));
    FlankQuery query =
        new FlankQuery(moved, mob(1), Map.of(mob(1), new Vec3(12, 70, -5)), DISTANCE);

    Vec3 point = formation.pointFor(query);

    assertPoint(point, 12.121320343559642, 70, -7.121320343559642);
  }

  @Test
  void queryRejectsASelfThatIsNotAFlanker() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 0));

    assertThatThrownBy(() -> new FlankQuery(pose, mob(9), flankers, DISTANCE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "FlankQuery.self must be one of the flankers, got"
                + " 00000000-0000-0000-0000-000000000009");
  }

  @Test
  void queryRejectsNonPositiveDistance() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), new Vec3(2, 0, 0));

    assertThatThrownBy(() -> new FlankQuery(pose, mob(1), flankers, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("FlankQuery.distanceBlocks must be a positive number, got 0.0");
  }
}
