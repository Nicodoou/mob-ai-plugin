package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.bobAt;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withHealth;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withPosition;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BrainExecutingTest {
  private static final int DIRECT_ASSAULT_INDEX = 0;
  private static final int FLANK_INDEX = 1;
  private static final int FIRST_ZOMBIE = 0;
  private static final int FLANKING_ZOMBIE = 3;
  private static final int NEAR_SPIDER = 8;
  private static final double LOW_HEALTH = 5;
  private static final double RECOVERED_HEALTH = 12;
  private static final double FULL_SUCCESS_FRACTION = 0.5;

  private final BrainFixture fixture = BrainFixture.choosingStrategy(DIRECT_ASSAULT_INDEX);

  private List<MobSnapshot> startPlan(BrainFixture brainFixture) {
    List<MobSnapshot> mobs = brainFixture.catalogGroup();
    brainFixture.decide(START_TICK, mobs, alice());
    return mobs;
  }

  private Plan plan() {
    return fixture.group().lifecycle().plan().orElseThrow();
  }

  @Test
  void visibleTargetResetsTheLostCount() {
    List<MobSnapshot> mobs = startPlan(fixture);

    BrainResult result = fixture.decide(START_TICK + 100, mobs, alice());

    assertThat(plan().lastTargetSeenTick()).isEqualTo(START_TICK + 100);
    assertThat(result.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(result.trace().plan()).contains(new PlanId(GROUP_ID, 1));
    assertThat(result.trace().endReason()).isEmpty();
  }

  @Test
  void lostTargetClosesThePlanAndObservesAgain() {
    List<MobSnapshot> mobs = startPlan(fixture);
    BrainResult stillLooking = fixture.decide(START_TICK + 190, mobs);

    BrainResult lost = fixture.decide(START_TICK + 200, mobs);
    BrainResult next = fixture.decide(START_TICK + 210, mobs, alice());

    assertThat(stillLooking.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(lost.trace().endReason()).contains(PlanEndReason.TARGET_LOST);
    assertThat(lost.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.TARGET_LOST);
    assertThat(lost.closedPlan().orElseThrow().id()).isEqualTo(new PlanId(GROUP_ID, 1));
    assertThat(lost.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(lost.decision().assignments()).isEmpty();
    assertThat(lost.decision().plan()).isEmpty();
    assertThat(next.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(next.decision().plan()).contains(new PlanId(GROUP_ID, 2));
  }

  @Test
  void planTimesOut() {
    List<MobSnapshot> mobs = startPlan(fixture);
    BrainResult halfway = fixture.decide(START_TICK + 300, mobs, alice());
    BrainResult almost = fixture.decide(START_TICK + 590, mobs, alice());

    BrainResult result = fixture.decide(START_TICK + 600, mobs, alice());

    assertThat(halfway.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(almost.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(result.trace().endReason()).contains(PlanEndReason.TIMED_OUT);
    assertThat(result.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.TIMED_OUT);
    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
  }

  @Test
  void woundedMobRetreatsAndComesBackToItsStartingRole() {
    BrainFixture flanking = BrainFixture.choosingStrategy(FLANK_INDEX);
    List<MobSnapshot> mobs = startPlan(flanking);
    MobId zombie = mobs.get(FLANKING_ZOMBIE).id();

    BrainResult wounded =
        flanking.decide(START_TICK + 10, withHealth(mobs, FLANKING_ZOMBIE, LOW_HEALTH), alice());
    BrainResult healed =
        flanking.decide(
            START_TICK + 20, withHealth(mobs, FLANKING_ZOMBIE, RECOVERED_HEALTH), alice());

    assertThat(wounded.decision().strategy()).contains(FlankStrategy.ID);
    RoleAssignment retreat = wounded.decision().assignments().get(FLANKING_ZOMBIE);
    assertThat(retreat.role()).isEqualTo(Role.RETREAT);
    assertThat(retreat.suggestedAttack()).isEmpty();
    assertThat(retreat.recovering()).isFalse();
    assertThat(wounded.trace().newlyRetreating()).containsExactly(zombie);
    RoleAssignment back = healed.decision().assignments().get(FLANKING_ZOMBIE);
    assertThat(back.role()).isEqualTo(Role.FLANK);
    assertThat(back.suggestedAttack()).isPresent();
    assertThat(healed.trace().returningFromRetreat()).containsExactly(zombie);
    assertThat(healed.trace().newlyRetreating()).isEmpty();
  }

  @Test
  void retreatingMobHealsOnlyAwayFromPlayers() {
    List<MobSnapshot> mobs = startPlan(fixture);
    List<MobSnapshot> wounded = withHealth(mobs, FIRST_ZOMBIE, LOW_HEALTH);

    BrainResult close =
        fixture.decide(
            START_TICK + 10, withPosition(wounded, FIRST_ZOMBIE, new Vec3(0, 64, 11)), alice());
    BrainResult away =
        fixture.decide(
            START_TICK + 20, withPosition(wounded, FIRST_ZOMBIE, new Vec3(0, 64, 13)), alice());

    RoleAssignment closeOrder = close.decision().assignments().get(FIRST_ZOMBIE);
    RoleAssignment awayOrder = away.decision().assignments().get(FIRST_ZOMBIE);
    assertThat(closeOrder.role()).isEqualTo(Role.RETREAT);
    assertThat(closeOrder.recovering()).isFalse();
    assertThat(awayOrder.role()).isEqualTo(Role.RETREAT);
    assertThat(awayOrder.recovering()).isTrue();
    assertThat(awayOrder.target()).contains(ALICE);
  }

  @Test
  void memberJoiningMidPlanGetsTheBasicRole() {
    List<MobSnapshot> mobs = startPlan(fixture);
    MobSnapshot newcomer =
        new MobSnapshotBuilder()
            .withId(new MobId(new UUID(1, 100)))
            .withKind(MobKind.SKELETON)
            .withPosition(new Vec3(0, 64, 8))
            .build();
    fixture.group().roster().addMember(newcomer.id(), newcomer.kind());
    List<MobSnapshot> withNewcomer = new ArrayList<>(mobs);
    withNewcomer.add(newcomer);

    BrainResult result = fixture.decide(START_TICK + 10, withNewcomer, alice());

    RoleAssignment order = result.decision().assignments().getLast();
    assertThat(order.mob()).isEqualTo(newcomer.id());
    assertThat(order.role()).isEqualTo(Role.SHOOT);
    assertThat(order.suggestedAttack()).isPresent();
    assertThat(plan().roleOf(newcomer.id())).contains(Role.SHOOT);
    assertThat(plan().startingRoleOf(newcomer.id())).isEmpty();
  }

  @Test
  void woundedMemberJoiningMidPlanStartsRetreating() {
    List<MobSnapshot> mobs = startPlan(fixture);
    MobSnapshot newcomer =
        new MobSnapshotBuilder()
            .withId(new MobId(new UUID(1, 101)))
            .withKind(MobKind.ZOMBIE)
            .withPosition(new Vec3(0, 64, 8))
            .withHealth(LOW_HEALTH)
            .build();
    fixture.group().roster().addMember(newcomer.id(), newcomer.kind());
    List<MobSnapshot> withNewcomer = new ArrayList<>(mobs);
    withNewcomer.add(newcomer);

    BrainResult result = fixture.decide(START_TICK + 10, withNewcomer, alice());

    RoleAssignment order = result.decision().assignments().getLast();
    assertThat(order.role()).isEqualTo(Role.RETREAT);
    assertThat(order.suggestedAttack()).isEmpty();
    assertThat(result.trace().newlyRetreating()).containsExactly(newcomer.id());
    assertThat(plan().startingRoleOf(newcomer.id())).isEmpty();
  }

  @Test
  void spidersFollowTheNearestPlayerAndRememberIt() {
    List<MobSnapshot> mobs = startPlan(fixture);
    MobId spider = mobs.get(NEAR_SPIDER).id();
    Optional<PlayerId> startingTarget = fixture.group().roster().spiderTarget(spider);

    BrainResult result = fixture.decide(START_TICK + 10, mobs, alice(), bobAt(new Vec3(12, 64, 5)));

    assertThat(startingTarget).contains(ALICE);
    RoleAssignment order = result.decision().assignments().get(NEAR_SPIDER);
    assertThat(order.target()).contains(BOB);
    assertThat(fixture.group().roster().spiderTarget(spider)).contains(BOB);
    assertThat(result.decision().target()).contains(ALICE);
  }

  @Test
  void groupRetreatStartsRegroupingWithRetreatOrders() {
    List<MobSnapshot> mobs = startPlan(fixture);
    List<MobSnapshot> wounded = mobs;
    for (int index = 0; index < 5; index++) {
      wounded = withHealth(wounded, index, LOW_HEALTH);
    }

    BrainResult result = fixture.decide(START_TICK + 10, wounded, alice());

    assertThat(result.trace().endReason()).contains(PlanEndReason.GROUP_RETREATED);
    assertThat(result.trace().newlyRetreating()).hasSize(5);
    assertThat(result.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.GROUP_RETREATED);
    assertThat(result.decision().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(result.decision().plan()).isEmpty();
    assertThat(result.decision().target()).contains(ALICE);
    assertThat(result.decision().assignments())
        .hasSize(9)
        .allSatisfy(
            order -> {
              assertThat(order.role()).isEqualTo(Role.RETREAT);
              assertThat(order.suggestedAttack()).isEmpty();
              assertThat(order.target()).contains(ALICE);
            });
  }

  @Test
  void planClosedBetweenDecisionsIsEvaluated() {
    List<MobSnapshot> mobs = startPlan(fixture);
    fixture
        .group()
        .lifecycle()
        .closePlan(PlanEndReason.TARGET_DIED, START_TICK + 5, FULL_SUCCESS_FRACTION);

    BrainResult result = fixture.decide(START_TICK + 10, mobs, alice());

    assertThat(result.trace().stateBefore()).isEqualTo(GroupState.EVALUATING);
    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
    assertThat(result.closedPlan()).isEmpty();
    assertThat(fixture.group().lifecycle().plan()).isEmpty();
  }
}
