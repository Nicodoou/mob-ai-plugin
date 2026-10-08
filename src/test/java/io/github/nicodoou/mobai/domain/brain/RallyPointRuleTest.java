package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RallyPointRuleTest {
  private static final PlayerId TARGET = new PlayerId(new UUID(0, 10));
  private static final PlayerId OTHER = new PlayerId(new UUID(0, 11));
  private static final PlayerId THIRD = new PlayerId(new UUID(0, 12));
  private static final double SHORT_RALLY_DISTANCE = 5;
  private static final double DIAGONAL_RALLY_DISTANCE = 10;
  private static final double DEAD = 0;

  private final RetreatSettings defaults = TestSettings.defaults().retreat();
  private final RallyPointRule rule = new RallyPointRule(() -> defaults);

  @Test
  void pointLiesBeyondTheCenterAwayFromThePlayer() {
    GroupSnapshot snapshot = snapshot(twoMobs(), player(TARGET, new Vec3(-10, 64, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(14, 64, 0));
  }

  @Test
  void diagonalDirectionIsNormalized() {
    RallyPointRule diagonal = new RallyPointRule(() -> withDistance(DIAGONAL_RALLY_DISTANCE));
    GroupSnapshot snapshot =
        snapshot(List.of(mob(1, new Vec3(0, 64, 0))), player(TARGET, new Vec3(-3, 64, -4)));

    Optional<Vec3> point = diagonal.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(6, 64, 8));
  }

  @Test
  void heightIsTheMobsAverageAndTheShiftIsHorizontal() {
    GroupSnapshot snapshot =
        snapshot(
            List.of(mob(1, new Vec3(0, 64, 0)), mob(2, new Vec3(0, 70, 4))),
            player(TARGET, new Vec3(0, 80, -10)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(0, 67, 14));
  }

  @Test
  void distanceComesFromTheSettings() {
    RallyPointRule shorter = new RallyPointRule(() -> withDistance(SHORT_RALLY_DISTANCE));
    GroupSnapshot snapshot = snapshot(twoMobs(), player(TARGET, new Vec3(-10, 64, 0)));

    Optional<Vec3> point = shorter.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(7, 64, 0));
  }

  @Test
  void withoutACommittedTargetTheNearestPlayerIsTheDanger() {
    GroupSnapshot snapshot =
        snapshot(
            twoMobs(), player(TARGET, new Vec3(-10, 64, 0)), player(OTHER, new Vec3(40, 64, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.empty());

    assertPoint(point, new Vec3(14, 64, 0));
  }

  @Test
  void aDeadCommittedTargetFallsBackToTheNearestLivingPlayer() {
    GroupSnapshot snapshot =
        snapshot(
            twoMobs(),
            player(TARGET, new Vec3(-10, 64, 0)),
            player(OTHER, new Vec3(40, 64, 0)),
            deadPlayer(THIRD, new Vec3(2, 64, 1)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(THIRD));

    assertPoint(point, new Vec3(14, 64, 0));
  }

  @Test
  void aLivingCommittedTargetWinsOverACloserPlayer() {
    GroupSnapshot snapshot =
        snapshot(
            twoMobs(), player(TARGET, new Vec3(2, 64, -30)), player(OTHER, new Vec3(-5, 64, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(2, 64, 12));
  }

  @Test
  void withoutLivingPlayersTheCenterIsThePoint() {
    GroupSnapshot snapshot = snapshot(twoMobs(), deadPlayer(TARGET, new Vec3(-10, 64, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(2, 64, 0));
  }

  @Test
  void aPlayerOnTheCenterLeavesTheCenter() {
    GroupSnapshot snapshot = snapshot(twoMobs(), player(TARGET, new Vec3(2, 50, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(point, new Vec3(2, 64, 0));
  }

  @Test
  void noMobsNoPoint() {
    GroupSnapshot snapshot = snapshot(List.of(), player(TARGET, new Vec3(-10, 64, 0)));

    Optional<Vec3> point = rule.pointFor(snapshot, Optional.of(TARGET));

    assertThat(point).isEmpty();
  }

  @Test
  void settingsAreReadOnEveryCall() {
    AtomicReference<RetreatSettings> settings = new AtomicReference<>(defaults);
    RallyPointRule reloadable = new RallyPointRule(settings::get);
    GroupSnapshot snapshot = snapshot(twoMobs(), player(TARGET, new Vec3(-10, 64, 0)));

    Optional<Vec3> before = reloadable.pointFor(snapshot, Optional.of(TARGET));
    settings.set(withDistance(SHORT_RALLY_DISTANCE));
    Optional<Vec3> after = reloadable.pointFor(snapshot, Optional.of(TARGET));

    assertPoint(before, new Vec3(14, 64, 0));
    assertPoint(after, new Vec3(7, 64, 0));
  }

  private static void assertPoint(Optional<Vec3> point, Vec3 expected) {
    assertThat(point).isPresent();
    assertThat(point.get().x()).isCloseTo(expected.x(), within(1e-9));
    assertThat(point.get().y()).isCloseTo(expected.y(), within(1e-9));
    assertThat(point.get().z()).isCloseTo(expected.z(), within(1e-9));
  }

  private RetreatSettings withDistance(double rallyDistanceBlocks) {
    return new RetreatSettings(
        defaults.recoveryHealthFraction(),
        defaults.healSafeDistanceBlocks(),
        defaults.regroupInitialTicks(),
        defaults.regroupMinTicks(),
        defaults.regroupMaxTicks(),
        defaults.regroupStepTicks(),
        rallyDistanceBlocks,
        defaults.rallyArrivalBlocks());
  }

  private static List<MobSnapshot> twoMobs() {
    return List.of(mob(1, new Vec3(0, 64, 0)), mob(2, new Vec3(4, 64, 0)));
  }

  private static MobSnapshot mob(long id, Vec3 position) {
    return new MobSnapshotBuilder()
        .withId(new MobId(new UUID(1, id)))
        .withPosition(position)
        .build();
  }

  private static PlayerSnapshot player(PlayerId id, Vec3 position) {
    return new PlayerSnapshotBuilder().withId(id).withPosition(position).build();
  }

  private static PlayerSnapshot deadPlayer(PlayerId id, Vec3 position) {
    return new PlayerSnapshotBuilder().withId(id).withPosition(position).withHealth(DEAD).build();
  }

  private static GroupSnapshot snapshot(List<MobSnapshot> mobs, PlayerSnapshot... players) {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder();
    mobs.forEach(builder::withMob);
    List.of(players).forEach(builder::withPlayer);
    return builder.build();
  }
}
