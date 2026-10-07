package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CombatGeometryTest {
  private static final double ATTACKER_DISTANCE = 4.0;

  private final CombatGeometry geometry = new CombatGeometry();
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  @ParameterizedTest
  @ValueSource(doubles = {8, 10, 43, 82})
  void attackersWithinNinetyDegreesAreInsideTheShieldArc(double degrees) {
    assertThat(geometry.isInShieldArc(pose, attackerAt(degrees))).isTrue();
  }

  @ParameterizedTest
  @ValueSource(doubles = {98, 107, 111, 180})
  void attackersBeyondNinetyDegreesAreOutsideTheShieldArc(double degrees) {
    assertThat(geometry.isInShieldArc(pose, attackerAt(degrees))).isFalse();
  }

  @Test
  void attackerExactlyAtNinetyDegreesIsInsideTheShieldArc() {
    assertThat(geometry.isInShieldArc(pose, new Vec3(4, 0, 0))).isTrue();
  }

  @Test
  void attackerDirectlyAboveCountsAsInFront() {
    Vec3 above = new Vec3(0, 3, 0);

    assertThat(geometry.angleFromFacingDegrees(pose, above)).isCloseTo(0.0, within(1e-9));
    assertThat(geometry.isInShieldArc(pose, above)).isTrue();
  }

  @Test
  void angleFromFacingUsesThePlayerPosition() {
    PlayerPose moved = new PlayerPose(new Vec3(10, 64, -5), new Vec3(1, 0, 0));

    assertThat(geometry.angleFromFacingDegrees(moved, new Vec3(10, 64, -1)))
        .isCloseTo(90.0, within(1e-9));
    assertThat(geometry.angleFromFacingDegrees(moved, new Vec3(6, 64, -5)))
        .isCloseTo(180.0, within(1e-9));
  }

  @Test
  void beyondOneHundredTwentyDegreesIsOutOfSight() {
    assertThat(geometry.isOutOfSight(pose, new Vec3(-1.7143346014042247, 0, -1.0300761498201085)))
        .isTrue();
    assertThat(geometry.isOutOfSight(pose, new Vec3(0, 0, -2))).isTrue();
  }

  @Test
  void withinOneHundredTwentyDegreesIsInSight() {
    assertThat(geometry.isOutOfSight(pose, new Vec3(-1.7492394142787917, 0, -0.969619240492674)))
        .isFalse();
    assertThat(geometry.isOutOfSight(pose, new Vec3(2, 0, 0))).isFalse();
  }

  @Test
  void flankPointGoesBehindOnTheMobsSide() {
    assertVec(
        geometry.flankPoint(pose, new Vec3(2, 0, 0), 3), 2.121320343559643, 0, -2.1213203435596424);
    assertVec(
        geometry.flankPoint(pose, new Vec3(-2, 0, 0), 3),
        -2.121320343559643,
        0,
        -2.1213203435596424);
  }

  @Test
  void mobInLineWithTheFacingFlanksToThePositiveSide() {
    assertVec(
        geometry.flankPoint(pose, new Vec3(0, 0, 5), 3),
        -2.121320343559643,
        0,
        -2.1213203435596424);
  }

  @Test
  void flankPointUsesThePlayerPositionAndHeight() {
    PlayerPose moved = new PlayerPose(new Vec3(10, 64, -5), new Vec3(1, 0, 0));

    assertVec(
        geometry.flankPoint(moved, new Vec3(12, 70, -1), 3),
        7.878679656440358,
        64,
        -2.878679656440357);
  }

  @Test
  void flankPointIsOutsideTheShieldArc() {
    PlayerPose moved = new PlayerPose(new Vec3(10, 64, -5), new Vec3(1, 0, 0));
    Vec3 point = geometry.flankPoint(moved, new Vec3(12, 70, -1), 3);

    assertThat(geometry.angleFromFacingDegrees(moved, point)).isCloseTo(135.0, within(1e-9));
    assertThat(geometry.isInShieldArc(moved, point)).isFalse();
  }

  @Test
  void rejectsNonPositiveFlankDistance() {
    assertThatThrownBy(() -> geometry.flankPoint(pose, new Vec3(2, 0, 0), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CombatGeometry.distanceBlocks must be a positive number, got 0.0");
  }

  @Test
  void retreatPointMovesAwayFromTheDanger() {
    assertVec(geometry.retreatPoint(new Vec3(3, 64, 4), new Vec3(0, 60, 0), 16), 12.6, 64, 16.8);
  }

  @Test
  void retreatPointWithoutHorizontalSeparationGoesTowardsPositiveX() {
    assertVec(geometry.retreatPoint(new Vec3(5, 64, 5), new Vec3(5, 70, 5), 16), 21, 64, 5);
  }

  @Test
  void coverCandidatesFanOutFromStraightAway() {
    List<Vec3> candidates = geometry.coverCandidates(new Vec3(0, 64, 5), new Vec3(0, 64, 0), 16);

    assertThat(candidates).hasSize(14);
    assertVec(candidates.get(0), 0, 64, 16);
    assertVec(candidates.get(1), -8, 64, 13.85640646055102);
    assertVec(candidates.get(2), 8, 64, 13.85640646055102);
    assertVec(candidates.get(5), -16, 64, 0);
    assertVec(candidates.get(6), 16, 64, 0);
    assertVec(candidates.get(7), 0, 64, 20);
  }

  @Test
  void coverCandidatesStayAtTheMobsHeight() {
    List<Vec3> candidates = geometry.coverCandidates(new Vec3(0, 70, 5), new Vec3(0, 64, 0), 16);

    assertThat(candidates).hasSize(14);
    assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.y()).isEqualTo(70));
  }

  @Test
  void coverCandidatesWithoutSeparationGoTowardsPositiveX() {
    List<Vec3> candidates = geometry.coverCandidates(new Vec3(3, 64, 3), new Vec3(3, 60, 3), 16);

    assertVec(candidates.get(0), 19, 64, 3);
  }

  @Test
  void rejectsNonPositiveCoverRadius() {
    assertThatThrownBy(() -> geometry.coverCandidates(Vec3.ZERO, new Vec3(1, 0, 1), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("CombatGeometry.distanceBlocks must be a positive number, got 0.0");
  }

  @Test
  void predictedAimPointAddsMovementDuringFlight() {
    assertVec(
        geometry.predictedAimPoint(Vec3.ZERO, new Vec3(12, 0, 16), new Vec3(0.2, 0, -0.1)),
        14.5,
        0,
        14.75);
  }

  @Test
  void stationaryTargetIsAimedAtDirectly() {
    assertVec(geometry.predictedAimPoint(Vec3.ZERO, new Vec3(12, 0, 16), Vec3.ZERO), 12, 0, 16);
  }

  @Test
  void leadShotVelocityRaisesTheAimAndKeepsArrowSpeed() {
    Vec3 velocity = geometry.leadShotVelocity(Vec3.ZERO, new Vec3(3, 0, 4));

    assertVec(velocity, 0.9413574486632834, 0.31378581622109447, 1.2551432648843779);
    assertThat(velocity.length()).isCloseTo(1.6, within(1e-9));
  }

  @Test
  void leadShotVelocityUsesTheShooterEye() {
    assertVec(
        geometry.leadShotVelocity(new Vec3(1, 65.5, 2), new Vec3(13, 64, -7)),
        1.2736476034687862,
        0.15920595043359828,
        -0.9552357026015896);
  }

  private static Vec3 attackerAt(double degrees) {
    double radians = Math.toRadians(degrees);
    return new Vec3(
        ATTACKER_DISTANCE * Math.sin(radians), 0, ATTACKER_DISTANCE * Math.cos(radians));
  }

  private static void assertVec(Vec3 actual, double x, double y, double z) {
    assertThat(actual.x()).isCloseTo(x, within(1e-9));
    assertThat(actual.y()).isCloseTo(y, within(1e-9));
    assertThat(actual.z()).isCloseTo(z, within(1e-9));
  }
}
