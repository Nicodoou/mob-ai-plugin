package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GroupLifecycleTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final MobId MOB_2 = new MobId(new UUID(1, 2));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 11));
  private static final StrategyId FLANK_STRATEGY = new StrategyId("FLANK");
  private static final double FULL_SUCCESS_FRACTION = 0.5;

  private final PlanStart start =
      new PlanStart(FLANK_STRATEGY, ALICE, Map.of(MOB_1, Role.PRESS), 20, 100);
  private Group group;

  @BeforeEach
  void createGroup() {
    group =
        new Group(
            GROUP,
            SelectionPolicyType.THOMPSON_SAMPLING,
            new GroupKnowledge(
                new GroupMemory(() -> TestSettings.defaults().memory()),
                new ThreatLedger(() -> TestSettings.defaults().target())));
    group.addMember(MOB_1, MobKind.ZOMBIE);
    group.addMember(MOB_2, MobKind.SPIDER);
  }

  private void enterExecuting() {
    group.beginPlanning();
    group.startPlan(start);
  }

  @Test
  void newGroupIsObservingWithoutPlan() {
    assertThat(group.state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.plan()).isEmpty();
    assertThat(group.committedTarget()).isEmpty();
    assertThat(group.planSequence()).isZero();
  }

  @Test
  void fullCycleWalksTheFourStates() {
    group.beginPlanning();

    assertThat(group.state()).isEqualTo(GroupState.PLANNING);

    Plan plan = group.startPlan(start);

    assertThat(group.state()).isEqualTo(GroupState.EXECUTING);
    assertThat(plan.id()).isEqualTo(new PlanId(GROUP, 1));

    group.closePlan(PlanEndReason.TIMED_OUT, 700, FULL_SUCCESS_FRACTION);

    assertThat(group.state()).isEqualTo(GroupState.EVALUATING);

    group.finishEvaluation();

    assertThat(group.state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.plan()).isEmpty();
  }

  @Test
  void illegalTransitionsAreRejected() {
    assertThatThrownBy(() -> group.startPlan(start))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot start a plan while OBSERVING");
    assertThatThrownBy(() -> group.closePlan(PlanEndReason.TIMED_OUT, 700, FULL_SUCCESS_FRACTION))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot close a plan while OBSERVING");

    group.beginPlanning();

    assertThatThrownBy(() -> group.beginPlanning())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot begin planning while PLANNING");
  }

  @Test
  void planIdsCountUpPerGroup() {
    enterExecuting();
    group.closePlan(PlanEndReason.TIMED_OUT, 700, FULL_SUCCESS_FRACTION);
    group.finishEvaluation();

    group.beginPlanning();
    Plan second = group.startPlan(start);

    assertThat(second.id()).isEqualTo(new PlanId(GROUP, 2));
    assertThat(group.planSequence()).isEqualTo(2);
  }

  @Test
  void startPlanRejectsRolesForNonMembers() {
    MobId stranger = new MobId(new UUID(9, 9));
    PlanStart foreign = new PlanStart(FLANK_STRATEGY, ALICE, Map.of(stranger, Role.PRESS), 20, 100);
    group.beginPlanning();

    assertThatThrownBy(() -> group.startPlan(foreign))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group 00000000 has no member 00000000-0000-0009-0000-000000000009");
    assertThat(group.state()).isEqualTo(GroupState.PLANNING);
  }

  @Test
  void onlyDamageToThePlanTargetCounts() {
    enterExecuting();

    group.recordPlanDamage(ALICE, 3);
    group.recordPlanDamage(BOB, 4);

    assertThat(group.plan().orElseThrow().damageDealt()).isCloseTo(3, within(1e-9));
  }

  @Test
  void damageOutsideAPlanIsIgnored() {
    assertThatCode(() -> group.recordPlanDamage(ALICE, 3)).doesNotThrowAnyException();
  }

  @Test
  void closingComputesSuccessAndCommitsToTheTarget() {
    enterExecuting();
    group.recordPlanDamage(ALICE, 5);

    ClosedPlan closed = group.closePlan(PlanEndReason.TIMED_OUT, 700, FULL_SUCCESS_FRACTION);

    assertThat(closed)
        .isEqualTo(
            new ClosedPlan(
                new PlanId(GROUP, 1),
                FLANK_STRATEGY,
                ALICE,
                PlanEndReason.TIMED_OUT,
                0.5,
                5.0,
                100,
                700));
    assertThat(group.committedTarget()).contains(ALICE);
    assertThat(group.drainEvents()).containsExactly(new PlanClosed(closed));
  }

  @Test
  void targetDeathIsAFullSuccess() {
    enterExecuting();

    ClosedPlan closed = group.closePlan(PlanEndReason.TARGET_DIED, 700, FULL_SUCCESS_FRACTION);

    assertThat(closed.success()).isCloseTo(1, within(1e-9));
  }

  @Test
  void planIsReadableDuringEvaluation() {
    enterExecuting();

    group.closePlan(PlanEndReason.TIMED_OUT, 700, FULL_SUCCESS_FRACTION);

    assertThat(group.plan()).isPresent();
    assertThat(group.state()).isEqualTo(GroupState.EVALUATING);
  }

  @Test
  void assignRoleChangesOnlyThatMob() {
    enterExecuting();

    group.assignRole(MOB_1, Role.RETREAT);

    assertThat(group.plan().orElseThrow().roleOf(MOB_1)).contains(Role.RETREAT);
  }

  @Test
  void removingAMemberDropsItsRoleFromThePlan() {
    enterExecuting();

    group.removeMember(MOB_1, 300);

    assertThat(group.plan().orElseThrow().roleOf(MOB_1)).isEmpty();
  }

  @Test
  void markTargetSeenUpdatesThePlan() {
    enterExecuting();

    group.markTargetSeen(300);

    assertThat(group.plan().orElseThrow().ticksSinceTargetSeen(350)).isEqualTo(50);
  }
}
