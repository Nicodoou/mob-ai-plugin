package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanEndDetectorTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final MobId M1 = mobId(1);
  private static final MobId M2 = mobId(2);
  private static final MobId M3 = mobId(3);
  private static final MobId M4 = mobId(4);

  private final PlanEndDetector detector =
      new PlanEndDetector(() -> TestSettings.defaults().plan());
  private final Plan plan = planWith(M1, M2, M3, M4);

  @Test
  void planInProgressHasNoEndReason() {
    assertThat(detector.detect(plan, 299)).isEmpty();
  }

  @Test
  void targetUnseenForTargetLostTicksIsLost() {
    assertThat(detector.detect(plan, 300)).contains(PlanEndReason.TARGET_LOST);
  }

  @Test
  void seeingTheTargetResetsTheLostCount() {
    assertThat(detector.detect(plan.withTargetSeenAt(250), 300)).isEmpty();
  }

  @Test
  void planTimesOutAtMaxDuration() {
    Plan seenRecently = plan.withTargetSeenAt(690);

    assertThat(detector.detect(seenRecently, 699)).isEmpty();
    assertThat(detector.detect(seenRecently, 700)).contains(PlanEndReason.TIMED_OUT);
  }

  @Test
  void lostWinsOverTimeout() {
    assertThat(detector.detect(plan, 700)).contains(PlanEndReason.TARGET_LOST);
  }

  @Test
  void moreThanHalfRetreatingEndsThePlan() {
    Plan seen = plan.withTargetSeenAt(150);
    Plan twoRetreating = seen.withRole(M1, Role.RETREAT).withRole(M2, Role.RETREAT);
    Plan threeRetreating = twoRetreating.withRole(M3, Role.RETREAT);

    assertThat(detector.detect(twoRetreating, 150)).isEmpty();
    assertThat(detector.detect(threeRetreating, 150)).contains(PlanEndReason.GROUP_RETREATED);
  }

  @Test
  void deadMembersCountAsRetreated() {
    Plan seen = plan.withTargetSeenAt(150);
    Plan oneGoneOneRetreating = seen.withoutMember(M1).withRole(M2, Role.RETREAT);
    Plan twoGoneOneRetreating = oneGoneOneRetreating.withoutMember(M3);

    assertThat(detector.detect(oneGoneOneRetreating, 150)).isEmpty();
    assertThat(detector.detect(twoGoneOneRetreating, 150)).contains(PlanEndReason.GROUP_RETREATED);
  }

  @Test
  void planWithoutMembersNeverRetreats() {
    assertThat(detector.detect(planWith(), 100)).isEmpty();
  }

  @Test
  void targetWithinLostDistanceOfAMobIsVisible() {
    GroupSnapshot atLimit = snapshotWithMobAt(new Vec3(0, 64, 32));
    GroupSnapshot beyondLimit = snapshotWithMobAt(new Vec3(0, 64, 32.5));

    assertThat(detector.isTargetVisible(atLimit, ALICE)).isTrue();
    assertThat(detector.isTargetVisible(beyondLimit, ALICE)).isFalse();
  }

  @Test
  void absentOrDeadTargetIsNotVisible() {
    GroupSnapshot withoutAlice =
        new GroupSnapshotBuilder().withMob(mobAt(new Vec3(0, 64, 1))).build();
    GroupSnapshot deadAlice =
        new GroupSnapshotBuilder()
            .withMob(mobAt(new Vec3(0, 64, 1)))
            .withPlayer(alice().withHealth(0).build())
            .build();

    assertThat(detector.isTargetVisible(withoutAlice, ALICE)).isFalse();
    assertThat(detector.isTargetVisible(deadAlice, ALICE)).isFalse();
  }

  @Test
  void targetIsNotVisibleWithoutMobs() {
    GroupSnapshot withoutMobs = new GroupSnapshotBuilder().withPlayer(alice().build()).build();

    assertThat(detector.isTargetVisible(withoutMobs, ALICE)).isFalse();
  }

  private static MobId mobId(int number) {
    return new MobId(new UUID(1, number));
  }

  private static Plan planWith(MobId... mobs) {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (MobId mob : mobs) {
      roles.put(mob, Role.PRESS);
    }
    PlanId id = new PlanId(new GroupId(new UUID(0, 3)), 1);
    return Plan.start(id, new PlanStart(new StrategyId("FLANK"), ALICE, roles, 20, 100));
  }

  private static PlayerSnapshotBuilder alice() {
    return new PlayerSnapshotBuilder().withId(ALICE).withPosition(new Vec3(0, 64, 0));
  }

  private static MobSnapshot mobAt(Vec3 position) {
    return new MobSnapshotBuilder().withPosition(position).build();
  }

  private static GroupSnapshot snapshotWithMobAt(Vec3 position) {
    return new GroupSnapshotBuilder().withMob(mobAt(position)).withPlayer(alice().build()).build();
  }
}
