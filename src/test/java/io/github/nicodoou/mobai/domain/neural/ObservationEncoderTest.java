package io.github.nicodoou.mobai.domain.neural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ObservationEncoderTest {
  private static final PlayerId TARGET = new PlayerId(new UUID(0, 10));
  private static final MobId SELF = new MobId(new UUID(0, 1));
  private static final MobId NEAR_ALLY = new MobId(new UUID(0, 2));
  private static final MobId FAR_ALLY = new MobId(new UUID(0, 3));
  private static final GroupId GROUP = new GroupId(new UUID(0, 99));
  private static final double GROUND = 64;
  private static final double TOLERANCE = 1e-9;

  private final ObservationEncoder encoder = new ObservationEncoder();

  private static MobSnapshot mob(MobId id, MobKind kind, Vec3 position, double health) {
    return new MobSnapshot(id, kind, position, health, 20);
  }

  private static MobSnapshot selfAt(Vec3 position) {
    return mob(SELF, MobKind.SKELETON, position, 5);
  }

  private static PlayerSnapshot targetFacingSouth() {
    return new PlayerSnapshotBuilder()
        .withId(TARGET)
        .withPosition(new Vec3(0, GROUND, 0))
        .withFacing(new Vec3(0, 0, 1))
        .withHealth(15)
        .withArmorPoints(10)
        .withBlocking(true)
        .build();
  }

  private double[] encodeAlone(MobSnapshot self, PlayerSnapshot target) {
    GroupSnapshot snapshot = new GroupSnapshot(GROUP, 100, List.of(self), List.of(target));
    return encoder.encode(self, snapshot, NeuralContext.opening(TARGET));
  }

  @Test
  void theVectorHasItsDocumentedSize() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), targetFacingSouth());

    assertThat(observation).hasSize(ObservationEncoder.SIZE);
  }

  @Test
  void ownStateIsOneHotKindAndSignedHealth() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), targetFacingSouth());

    assertThat(observation[0]).isZero();
    assertThat(observation[1]).isEqualTo(1);
    assertThat(observation[2]).isZero();
    assertThat(observation[3]).isCloseTo(-0.5, within(TOLERANCE));
  }

  @Test
  void aMobRightInFrontOfTheTargetHasCosineOneAndSineZero() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), targetFacingSouth());

    assertThat(observation[4]).isEqualTo(1);
    assertThat(observation[5]).isCloseTo(0, within(TOLERANCE));
    assertThat(observation[7]).isCloseTo(0, within(TOLERANCE));
    assertThat(observation[8]).isCloseTo(1, within(TOLERANCE));
  }

  @Test
  void aMobAtTheBackOfTheTargetHasCosineMinusOne() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, -16)), targetFacingSouth());

    assertThat(observation[8]).isCloseTo(-1, within(TOLERANCE));
  }

  @Test
  void theSineTellsTheTwoSidesOfTheTargetApart() {
    double[] east = encodeAlone(selfAt(new Vec3(16, GROUND, 0)), targetFacingSouth());
    double[] west = encodeAlone(selfAt(new Vec3(-16, GROUND, 0)), targetFacingSouth());

    assertThat(east[7]).isCloseTo(1, within(TOLERANCE));
    assertThat(west[7]).isCloseTo(-1, within(TOLERANCE));
  }

  @Test
  void aFarMobHasItsHeightAndDistanceCapped() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND - 100, 500)), targetFacingSouth());

    assertThat(observation[5]).isEqualTo(1);
    assertThat(observation[6]).isEqualTo(1);
  }

  @Test
  void theTargetStateIsSignedOrBinary() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), targetFacingSouth());

    assertThat(observation[9]).isCloseTo(0.5, within(TOLERANCE));
    assertThat(observation[10]).isCloseTo(0, within(TOLERANCE));
    assertThat(observation[11]).isEqualTo(1);
    assertThat(observation[12]).isZero();
  }

  @Test
  void aTargetRunningAtTheMobClosesAtPositiveSpeed() {
    PlayerSnapshot running =
        new PlayerSnapshotBuilder()
            .withId(TARGET)
            .withPosition(new Vec3(0, GROUND, 0))
            .withMovementPerTick(new Vec3(0, 0, 0.2))
            .build();

    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), running);

    assertThat(observation[13]).isCloseTo(0.5, within(TOLERANCE));
    assertThat(observation[14]).isCloseTo(0.5, within(TOLERANCE));
  }

  @Test
  void aTargetOutOfTheSnapshotLeavesItsInputsAtTheirNeutralValues() {
    MobSnapshot self = selfAt(new Vec3(0, GROUND, 16));
    GroupSnapshot snapshot = new GroupSnapshot(GROUP, 100, List.of(self), List.of());

    double[] observation = encoder.encode(self, snapshot, NeuralContext.opening(TARGET));

    assertThat(observation[4]).isZero();
    assertThat(observation[5]).isEqualTo(1);
    for (int index = 6; index <= 14; index++) {
      assertThat(observation[index]).isZero();
    }
  }

  @Test
  void alliesAreCountedWithoutTheMobItself() {
    GroupSnapshot snapshot = groupOfThree();

    double[] observation =
        encoder.encode(snapshot.mobs().getFirst(), snapshot, NeuralContext.opening(TARGET));

    assertThat(observation[15]).isCloseTo(1.0 / 12, within(TOLERANCE));
    assertThat(observation[16]).isZero();
    assertThat(observation[17]).isCloseTo(1.0 / 12, within(TOLERANCE));
  }

  @Test
  void nearAlliesCountOnlyWithinFourBlocks() {
    GroupSnapshot snapshot = groupOfThree();

    double[] observation =
        encoder.encode(snapshot.mobs().getFirst(), snapshot, NeuralContext.opening(TARGET));

    assertThat(observation[18]).isCloseTo(1.0 / 8, within(TOLERANCE));
    assertThat(observation[19]).isCloseTo(3.0 / 32 * 2 - 1, within(TOLERANCE));
  }

  @Test
  void aMobWithoutAlliesReadsAsFarFromThem() {
    double[] observation = encodeAlone(selfAt(new Vec3(0, GROUND, 16)), targetFacingSouth());

    assertThat(observation[19]).isEqualTo(1);
  }

  @Test
  void strengthAndPlanAgeAreSigned() {
    GroupSnapshot snapshot = groupOfThree();
    NeuralContext context = new NeuralContext(TARGET, 600, Map.of(), 0.25);

    double[] observation = encoder.encode(snapshot.mobs().getFirst(), snapshot, context);

    assertThat(observation[20]).isCloseTo(-0.5, within(TOLERANCE));
    assertThat(observation[21]).isCloseTo(0, within(TOLERANCE));
  }

  @Test
  void strengthAboveTheStartReadsAsFull() {
    GroupSnapshot snapshot = groupOfThree();
    NeuralContext context = new NeuralContext(TARGET, 0, Map.of(), 1.2);

    double[] observation = encoder.encode(snapshot.mobs().getFirst(), snapshot, context);

    assertThat(observation[20]).isEqualTo(1);
  }

  @Test
  void aFirstDecisionHasNoPreviousAction() {
    GroupSnapshot snapshot = groupOfThree();

    double[] observation =
        encoder.encode(snapshot.mobs().getFirst(), snapshot, NeuralContext.opening(TARGET));

    for (int index = 22; index < ObservationEncoder.SIZE; index++) {
      assertThat(observation[index]).isZero();
    }
  }

  @Test
  void previousActionsSeparateTheMobFromItsAllies() {
    GroupSnapshot snapshot = groupOfThree();
    NeuralContext context =
        new NeuralContext(
            TARGET, 0, Map.of(SELF, Role.FLANK, NEAR_ALLY, Role.FLANK, FAR_ALLY, Role.RETREAT), 1);

    double[] observation = encoder.encode(snapshot.mobs().getFirst(), snapshot, context);

    assertThat(observation[22 + NeuralAction.FLANK.ordinal()]).isEqualTo(1);
    assertThat(observation[22 + NeuralAction.RETREAT.ordinal()]).isZero();
    assertThat(observation[28 + NeuralAction.FLANK.ordinal()])
        .isCloseTo(1.0 / 12, within(TOLERANCE));
    assertThat(observation[28 + NeuralAction.RETREAT.ordinal()])
        .isCloseTo(1.0 / 12, within(TOLERANCE));
  }

  @Test
  void everyInputStaysApproximatelyInRangeInACrowdedFight() {
    MobSnapshot self = mob(SELF, MobKind.ZOMBIE, new Vec3(0, GROUND, 0), 20);
    List<MobSnapshot> crowd =
        IntStream.range(100, 130)
            .mapToObj(
                index ->
                    mob(new MobId(new UUID(0, index)), MobKind.ZOMBIE, new Vec3(1, GROUND, 0), 20))
            .toList();
    List<MobSnapshot> mobs = new ArrayList<>(crowd);
    mobs.add(self);
    GroupSnapshot snapshot = new GroupSnapshot(GROUP, 100, mobs, List.of(targetFacingSouth()));

    double[] observation = encoder.encode(self, snapshot, NeuralContext.opening(TARGET));

    for (double value : observation) {
      assertThat(value).isBetween(-1.0, 1.0);
    }
  }

  private static GroupSnapshot groupOfThree() {
    return new GroupSnapshot(
        GROUP,
        100,
        List.of(
            mob(SELF, MobKind.ZOMBIE, new Vec3(10, GROUND, 10), 20),
            mob(NEAR_ALLY, MobKind.ZOMBIE, new Vec3(13, GROUND, 10), 20),
            mob(FAR_ALLY, MobKind.SPIDER, new Vec3(30, GROUND, 10), 16)),
        List.of(targetFacingSouth()));
  }
}
