package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EncircleFormationTest {
  private static final double TOLERANCE = 1e-9;
  private static final double RADIUS = 1.7;
  private static final double NEAR = 3.5;

  private final EncircleFormation formation = new EncircleFormation(new CombatGeometry());
  private final EncircleRing ring = new EncircleRing(RADIUS, RADIUS, 4.0, 4.0, 1.5);
  private final PlayerPose pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1));

  private static MobId mob(int number) {
    return new MobId(new UUID(0, number));
  }

  private Vec3 around(double degrees, double distance) {
    return CombatGeometry.rotateAroundVertical(pose.facing(), degrees).times(distance);
  }

  private EncircleStep step(MobId self, Map<MobId, Vec3> pressers, Map<MobId, Vec3> flankers) {
    return formation.stepFor(new EncircleQuery(pose, self, pressers, flankers, ring));
  }

  private void assertWaypoint(EncircleStep step, double degrees, double distance) {
    Vec3 expected = around(degrees, distance);
    assertThat(step.waypoint().x()).isCloseTo(expected.x(), within(TOLERANCE));
    assertThat(step.waypoint().z()).isCloseTo(expected.z(), within(TOLERANCE));
  }

  @Test
  void aSpacingEqualToTheRadiusGivesSixSeats() {
    assertThat(formation.strikeSeats(ring)).isEqualTo(6);
  }

  @Test
  void widerSpacingGivesFewerSeats() {
    assertThat(formation.strikeSeats(new EncircleRing(1.7, 2.4, 4.0, 4.0, 1.5))).isEqualTo(4);
  }

  @Test
  void aLonePresserTakesTheSeatStraightAhead() {
    EncircleStep step = step(mob(1), Map.of(mob(1), around(20, NEAR)), Map.of());

    assertWaypoint(step, 0, RADIUS);
    assertThat(step.striking()).isTrue();
  }

  @Test
  void aLoneFlankerTakesTheSeatStraightBehind() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(160, NEAR)));

    assertWaypoint(step, 180, RADIUS);
    assertThat(step.striking()).isTrue();
  }

  @Test
  void pressersFanOutKeepingTheirOrderAroundThePlayer() {
    Map<MobId, Vec3> pressers =
        Map.of(mob(1), around(20, NEAR), mob(2), around(-20, NEAR), mob(3), around(0, NEAR));

    assertWaypoint(step(mob(2), pressers, Map.of()), -60, RADIUS);
    assertWaypoint(step(mob(3), pressers, Map.of()), 0, RADIUS);
    assertWaypoint(step(mob(1), pressers, Map.of()), 60, RADIUS);
  }

  @Test
  void flankersFanOutFromBehindKeepingTheirOrder() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), around(150, NEAR), mob(2), around(-150, NEAR));

    assertWaypoint(step(mob(1), Map.of(), flankers), 150, RADIUS);
    assertWaypoint(step(mob(2), Map.of(), flankers), -150, RADIUS);
  }

  @Test
  void flankersOnTheSameSideDoNotSwapPlaces() {
    Map<MobId, Vec3> flankers = Map.of(mob(1), around(120, NEAR), mob(2), around(170, NEAR));

    assertWaypoint(step(mob(1), Map.of(), flankers), 150, RADIUS);
    assertWaypoint(step(mob(2), Map.of(), flankers), 210, RADIUS);
  }

  @Test
  void sixFightersOfAnyMixTakeSeatsAtLeastTheSpacingApart() {
    for (int flankerCount = 0; flankerCount <= 6; flankerCount++) {
      List<Vec3> seats = seatsWith(flankerCount);

      for (int first = 0; first < seats.size(); first++) {
        for (int second = first + 1; second < seats.size(); second++) {
          assertThat(seats.get(first).distanceTo(seats.get(second)))
              .isGreaterThanOrEqualTo(RADIUS - TOLERANCE);
        }
      }
    }
  }

  // Each fighter stands in front of its own seat, so its waypoint is the seat itself.
  private List<Vec3> seatsWith(int flankerCount) {
    Map<MobId, Vec3> pressers = new HashMap<>();
    Map<MobId, Vec3> flankers = new HashMap<>();
    int presserCount = 6 - flankerCount;
    for (int index = 0; index < presserCount; index++) {
      pressers.put(mob(index), around((index - (presserCount - 1) / 2.0) * 60, NEAR));
    }
    for (int index = 0; index < flankerCount; index++) {
      flankers.put(mob(10 + index), around(180 + (index - (flankerCount - 1) / 2.0) * 60, NEAR));
    }
    List<Vec3> seats = new ArrayList<>();
    pressers.keySet().forEach(id -> seats.add(step(id, pressers, flankers).waypoint()));
    flankers.keySet().forEach(id -> seats.add(step(id, pressers, flankers).waypoint()));
    return seats;
  }

  @Test
  void theSeventhClosestWaitsOutsideTheReach() {
    Map<MobId, Vec3> pressers = new HashMap<>();
    for (int index = 0; index < 6; index++) {
      pressers.put(mob(index), around(index * 60, NEAR));
    }
    pressers.put(mob(9), around(10, 4.0));

    EncircleStep waiting = step(mob(9), pressers, Map.of());

    assertThat(waiting.striking()).isFalse();
    assertThat(waiting.waypoint().horizontal().length()).isCloseTo(4.0, within(TOLERANCE));
    assertThat(step(mob(0), pressers, Map.of()).striking()).isTrue();
  }

  @Test
  void aFarFighterWalksStraightInWhateverItsSeat() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(10, 15)));

    assertWaypoint(step, 10, RADIUS);
    assertThat(step.move()).isEqualTo(EncircleMove.WALK);
  }

  @Test
  void anEngagedFighterWalksRoundThePlayerInsteadOfThroughIt() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(10, 4.0)));

    assertWaypoint(step, 55, 4.0 * Math.cos(Math.toRadians(45)));
    assertThat(step.move()).isEqualTo(EncircleMove.WALK);
  }

  @Test
  void aWalkRoundNeverGoesInsideTheRing() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(10, RADIUS)));

    assertWaypoint(step, 55, RADIUS);
  }

  @Test
  void aFighterAtItsSeatInReachHolds() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(180, RADIUS)));

    assertThat(step.move()).isEqualTo(EncircleMove.HOLD);
  }

  @Test
  void aFighterAtItsSeatOutOfReachStepsIn() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(180, 2.4)));

    assertThat(step.move()).isEqualTo(EncircleMove.STEP_IN);
  }

  @Test
  void aPresserAlreadyHittingFromBehindStaysThere() {
    EncircleStep step = step(mob(1), Map.of(mob(1), around(180, RADIUS)), Map.of());

    assertThat(step.move()).isEqualTo(EncircleMove.HOLD);
  }

  @Test
  void aCrowdedPresserInReachKeepsWalkingToItsSeat() {
    Map<MobId, Vec3> pressers = Map.of(mob(1), around(170, RADIUS), mob(2), around(-170, RADIUS));

    assertThat(step(mob(1), pressers, Map.of()).move()).isEqualTo(EncircleMove.WALK);
  }

  @Test
  void aCrowdedFighterAtItsSeatKeepsWalkingToTheExactSpot() {
    Map<MobId, Vec3> pressers = Map.of(mob(1), around(-5, RADIUS), mob(2), around(5, RADIUS));

    EncircleStep step = step(mob(1), pressers, Map.of());

    assertWaypoint(step, -30, RADIUS);
    assertThat(step.move()).isEqualTo(EncircleMove.WALK);
  }

  @Test
  void aFlankerInReachButInSightKeepsWalkingToItsSeat() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(60, RADIUS)));

    assertThat(step.move()).isEqualTo(EncircleMove.WALK);
  }

  @Test
  void aFlankerInReachOutOfSightAndUncrowdedStaysThere() {
    EncircleStep step = step(mob(1), Map.of(), Map.of(mob(1), around(125, RADIUS)));

    assertThat(step.move()).isEqualTo(EncircleMove.HOLD);
  }

  @Test
  void aWaitingFighterAtItsPlaceHolds() {
    Map<MobId, Vec3> pressers = new HashMap<>();
    for (int index = 0; index < 6; index++) {
      pressers.put(mob(index), around(index * 60, RADIUS));
    }
    pressers.put(mob(9), around(0, 4.0));

    EncircleStep waiting = step(mob(9), pressers, Map.of());

    assertThat(waiting.striking()).isFalse();
    assertThat(waiting.move()).isEqualTo(EncircleMove.HOLD);
  }

  @Test
  void fightersOnTheSameSpotGetSeatsByTheirIds() {
    Map<MobId, Vec3> pressers = Map.of(mob(1), around(0, 3), mob(2), around(0, 3));

    assertWaypoint(step(mob(1), pressers, Map.of()), -30, RADIUS);
    assertWaypoint(step(mob(2), pressers, Map.of()), 30, RADIUS);
  }

  @Test
  void fightersComingFromOneSideEndUpInReachApartAndFlankersOutOfSight() {
    Map<MobId, Vec3> pressers = new HashMap<>();
    Map<MobId, Vec3> flankers = new HashMap<>();
    for (int index = 0; index < 4; index++) {
      pressers.put(mob(index), around(-10 + index * 7, 12));
    }
    flankers.put(mob(10), around(-20, 12));
    flankers.put(mob(11), around(25, 12));

    for (int round = 0; round < 40; round++) {
      Map<MobId, Vec3> pressersNow = Map.copyOf(pressers);
      Map<MobId, Vec3> flankersNow = Map.copyOf(flankers);
      pressersNow.keySet().forEach(id -> pressers.put(id, walked(id, pressersNow, flankersNow)));
      flankersNow.keySet().forEach(id -> flankers.put(id, walked(id, pressersNow, flankersNow)));
    }

    List<Vec3> everyone = new ArrayList<>(pressers.values());
    everyone.addAll(flankers.values());
    assertThat(everyone).allMatch(position -> position.length() <= 2.0);
    for (int first = 0; first < everyone.size(); first++) {
      for (int second = first + 1; second < everyone.size(); second++) {
        assertThat(everyone.get(first).distanceTo(everyone.get(second)))
            .isGreaterThanOrEqualTo(1.5);
      }
    }
    assertThat(flankers.values())
        .allMatch(position -> new CombatGeometry().isOutOfSight(pose, position));
  }

  private Vec3 walked(MobId id, Map<MobId, Vec3> pressers, Map<MobId, Vec3> flankers) {
    Vec3 position = pressers.containsKey(id) ? pressers.get(id) : flankers.get(id);
    EncircleStep step = step(id, pressers, flankers);
    return switch (step.move()) {
      case HOLD -> position;
      case STEP_IN -> towards(position, Vec3.ZERO);
      case WALK -> towards(position, step.waypoint());
    };
  }

  private static Vec3 towards(Vec3 from, Vec3 to) {
    Vec3 way = to.minus(from);
    double blocksPerRepath = 1.0;
    if (way.length() <= blocksPerRepath) {
      return to;
    }
    return from.plus(way.normalized().times(blocksPerRepath));
  }

  @Test
  void queryWithoutSelfIsRejected() {
    Map<MobId, Vec3> pressers = Map.of(mob(1), around(0, 3));

    assertThatThrownBy(() -> new EncircleQuery(pose, mob(2), pressers, Map.of(), ring))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "EncircleQuery.self must be one of the fighters, got"
                + " 00000000-0000-0000-0000-000000000002");
  }

  @Test
  void ringNeedsPositiveSizes() {
    assertThatThrownBy(() -> new EncircleRing(0.0, 1.7, 4.0, 4.0, 1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("EncircleRing.strikeRadiusBlocks must be a positive number, got 0.0");
  }

  @Test
  void ringSpacingMustFitAcrossTheRing() {
    assertThatThrownBy(() -> new EncircleRing(1.0, 2.5, 4.0, 4.0, 1.5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "EncircleRing.seatSpacingBlocks must fit across the ring, got 2.5 on a radius of 1.0");
  }
}
