package io.github.nicodoou.mobai.domain.geometry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SurroundFormationTest {
  private static final double TOLERANCE = 1e-9;
  private static final double ZOMBIE_SPACING = 2.5127416997969525;
  private static final double STRIKE_RADIUS = 1.8;
  private static final double SIDE_X = 1.7994083626765378;
  private static final double SIDE_Z = 0.046146986139311647;

  private final Vec3 center = new Vec3(0, 64, 0);
  private final MobId a = mob(1);
  private final MobId b = mob(2);
  private final MobId c = mob(3);
  private final MobId d = mob(4);
  private final SurroundRing ring = new SurroundRing(STRIKE_RADIUS, ZOMBIE_SPACING, 4.0);
  private final SurroundFormation formation = new SurroundFormation();

  private static MobId mob(int number) {
    return new MobId(new UUID(0, number));
  }

  private SurroundSpot spotOf(MobId self, Map<MobId, Vec3> pressers) {
    return formation.spotFor(new SurroundQuery(center, self, pressers, ring));
  }

  private static void assertPoint(SurroundSpot spot, double x, double y, double z) {
    assertThat(spot.point().x()).isCloseTo(x, within(TOLERANCE));
    assertThat(spot.point().y()).isCloseTo(y, within(TOLERANCE));
    assertThat(spot.point().z()).isCloseTo(z, within(TOLERANCE));
  }

  private Map<MobId, Vec3> threeInFront() {
    return Map.of(a, new Vec3(-1, 64, 10), b, new Vec3(0, 64, 10), c, new Vec3(1, 64, 10));
  }

  @Test
  void zombieSpacingClearsTheSweep() {
    assertThat(formation.sweepSafeSpacingBlocks(0.6, 0.25))
        .isCloseTo(ZOMBIE_SPACING, within(TOLERANCE));
  }

  @Test
  void widerMobsNeedMoreRoom() {
    assertThat(formation.sweepSafeSpacingBlocks(1.4, 0.25))
        .isCloseTo(3.644112549695428, within(TOLERANCE));
  }

  @Test
  void threeZombiesFitInFrontAtStrikeRange() {
    assertThat(formation.strikeSlots(ring)).isEqualTo(3);
  }

  @Test
  void tooWideForTwoLeavesOneSlot() {
    assertThat(formation.strikeSlots(new SurroundRing(1.8, 3.65, 4.0))).isEqualTo(1);
  }

  @Test
  void aLonePresserStrikesStraightOn() {
    SurroundSpot spot = spotOf(a, Map.of(a, new Vec3(0, 64, 10)));

    assertPoint(spot, 0, 64, 1.8);
    assertThat(spot.striking()).isTrue();
  }

  @Test
  void arcFollowsThePack() {
    SurroundSpot spot = spotOf(a, Map.of(a, new Vec3(10, 64, 0)));

    assertPoint(spot, 1.8, 64, 0);
    assertThat(spot.striking()).isTrue();
  }

  @Test
  void threePressersSpreadAcrossTheArc() {
    Map<MobId, Vec3> pressers = threeInFront();

    SurroundSpot first = spotOf(a, pressers);
    SurroundSpot second = spotOf(b, pressers);
    SurroundSpot third = spotOf(c, pressers);

    assertPoint(first, -SIDE_X, 64, SIDE_Z);
    assertPoint(second, 0, 64, 1.8);
    assertPoint(third, SIDE_X, 64, SIDE_Z);
    assertThat(first.striking() && second.striking() && third.striking()).isTrue();
    assertThat(first.point().distanceTo(second.point()))
        .isCloseTo(ZOMBIE_SPACING, within(TOLERANCE));
  }

  @Test
  void twoPressersSitHalfASlotEachSide() {
    Map<MobId, Vec3> pressers = Map.of(a, new Vec3(-1, 64, 10), b, new Vec3(1, 64, 10));

    assertPoint(spotOf(a, pressers), -1.2563708498984762, 64, 1.2890043783965128);
    assertPoint(spotOf(b, pressers), 1.2563708498984762, 64, 1.2890043783965128);
  }

  @Test
  void theFourthWaitsOutsideTheReach() {
    Map<MobId, Vec3> pressers =
        Map.of(
            a, new Vec3(-1, 64, 10),
            b, new Vec3(0, 64, 10),
            c, new Vec3(1, 64, 10),
            d, new Vec3(0, 64, -12));

    SurroundSpot waiting = spotOf(d, pressers);

    assertPoint(waiting, 0, 64, -4);
    assertThat(waiting.striking()).isFalse();
    assertPoint(spotOf(b, pressers), 0, 64, 1.8);
  }

  @Test
  void theClosestOnesGetTheSlots() {
    Map<MobId, Vec3> pressers =
        Map.of(
            a, new Vec3(-1, 64, 10),
            b, new Vec3(0, 64, 10),
            c, new Vec3(1, 64, 10),
            d, new Vec3(0, 64, 5));

    SurroundSpot waiting = spotOf(c, pressers);

    assertPoint(waiting, 0.39801487608399566, 64, 3.9801487608399566);
    assertThat(waiting.striking()).isFalse();
    assertThat(spotOf(d, pressers).striking()).isTrue();
  }

  @Test
  void queryWithoutSelfIsRejected() {
    Map<MobId, Vec3> pressers = Map.of(a, new Vec3(0, 64, 10));

    assertThatThrownBy(() -> new SurroundQuery(center, d, pressers, ring))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "SurroundQuery.self must be one of the pressers, got"
                + " 00000000-0000-0000-0000-000000000004");
  }

  @Test
  void ringNeedsPositiveSizes() {
    assertThatThrownBy(() -> new SurroundRing(0.0, 2.5, 4.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SurroundRing.strikeRadiusBlocks must be a positive number, got 0.0");
  }
}
