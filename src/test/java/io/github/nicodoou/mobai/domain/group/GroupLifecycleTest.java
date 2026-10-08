package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.Optional;
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
  private static final Vec3 RALLY_POINT = new Vec3(14, 64, 0);

  private final PlanStart start =
      new PlanStart(FLANK_STRATEGY, ALICE, Map.of(MOB_1, Role.PRESS), 20, 100, Optional.empty());
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
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.SPIDER);
  }

  private void enterExecuting() {
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(start);
  }

  @Test
  void newGroupIsObservingWithoutPlan() {
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.lifecycle().plan()).isEmpty();
    assertThat(group.lifecycle().committedTarget()).isEmpty();
    assertThat(group.lifecycle().planSequence()).isZero();
  }

  @Test
  void fullCycleWalksTheFourStates() {
    group.lifecycle().beginPlanning();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.PLANNING);

    Plan plan = group.lifecycle().startPlan(start);

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(plan.id()).isEqualTo(new PlanId(GROUP, 1));

    group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 700, TestSettings.scoring());

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EVALUATING);

    group.lifecycle().finishEvaluation();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.lifecycle().plan()).isEmpty();
  }

  @Test
  void illegalTransitionsAreRejected() {
    assertThatThrownBy(() -> group.lifecycle().startPlan(start))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot start a plan while OBSERVING");
    assertThatThrownBy(
            () -> group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 700, TestSettings.scoring()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot close a plan while OBSERVING");

    group.lifecycle().beginPlanning();

    assertThatThrownBy(() -> group.lifecycle().beginPlanning())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot begin planning while PLANNING");
  }

  @Test
  void planIdsCountUpPerGroup() {
    enterExecuting();
    group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 700, TestSettings.scoring());
    group.lifecycle().finishEvaluation();

    group.lifecycle().beginPlanning();
    Plan second = group.lifecycle().startPlan(start);

    assertThat(second.id()).isEqualTo(new PlanId(GROUP, 2));
    assertThat(group.lifecycle().planSequence()).isEqualTo(2);
  }

  @Test
  void startPlanRejectsRolesForNonMembers() {
    MobId stranger = new MobId(new UUID(9, 9));
    PlanStart foreign =
        new PlanStart(
            FLANK_STRATEGY, ALICE, Map.of(stranger, Role.PRESS), 20, 100, Optional.empty());
    group.lifecycle().beginPlanning();

    assertThatThrownBy(() -> group.lifecycle().startPlan(foreign))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group 00000000 has no member 00000000-0000-0009-0000-000000000009");
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.PLANNING);
  }

  @Test
  void onlyDamageToThePlanTargetCounts() {
    enterExecuting();

    group.lifecycle().recordPlanDamage(ALICE, 3);
    group.lifecycle().recordPlanDamage(BOB, 4);

    assertThat(group.lifecycle().plan().orElseThrow().damageDealt()).isCloseTo(3, within(1e-9));
  }

  @Test
  void damageOutsideAPlanIsIgnored() {
    assertThatCode(() -> group.lifecycle().recordPlanDamage(ALICE, 3)).doesNotThrowAnyException();
  }

  @Test
  void closingComputesSuccessAndCommitsToTheTarget() {
    enterExecuting();
    group.lifecycle().recordPlanDamage(ALICE, 5);

    ClosedPlan closed =
        group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 700, TestSettings.scoring());

    assertThat(closed)
        .isEqualTo(
            new ClosedPlan(
                new PlanId(GROUP, 1),
                FLANK_STRATEGY,
                ALICE,
                PlanEndReason.TIMED_OUT,
                (0.4 * 0.5 + 0.4 * 0.25) / 0.8,
                new PlanScores(0.5, 0.25, 1),
                0,
                0,
                5.0,
                100,
                700,
                Optional.empty()));
    assertThat(group.lifecycle().committedTarget()).contains(ALICE);
    assertThat(group.drainEvents()).containsExactly(new PlanClosed(closed));
  }

  @Test
  void targetDeathIsAFullSuccess() {
    enterExecuting();

    ClosedPlan closed =
        group.lifecycle().closePlan(PlanEndReason.TARGET_DIED, 700, TestSettings.scoring());

    assertThat(closed.success()).isCloseTo(1, within(1e-9));
  }

  @Test
  void planIsReadableDuringEvaluation() {
    enterExecuting();

    group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 700, TestSettings.scoring());

    assertThat(group.lifecycle().plan()).isPresent();
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EVALUATING);
  }

  @Test
  void assignRoleChangesOnlyThatMob() {
    enterExecuting();

    group.lifecycle().assignRole(MOB_1, Role.RETREAT);

    assertThat(group.lifecycle().plan().orElseThrow().roleOf(MOB_1)).contains(Role.RETREAT);
  }

  @Test
  void removingAMemberDropsItsRoleFromThePlan() {
    enterExecuting();

    group.removeMember(MOB_1, 300);

    assertThat(group.lifecycle().plan().orElseThrow().roleOf(MOB_1)).isEmpty();
  }

  @Test
  void groupRetreatLeadsToRegrouping() {
    enterExecuting();
    group.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 700, TestSettings.scoring());

    group.lifecycle().finishEvaluation();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(group.lifecycle().regrouping().map(Regrouping::startTick)).contains(700L);
    assertThat(group.lifecycle().plan()).isEmpty();

    group.lifecycle().finishRegrouping();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.lifecycle().regrouping()).isEmpty();
  }

  @Test
  void otherEndReasonsSkipRegrouping() {
    enterExecuting();
    group.lifecycle().closePlan(PlanEndReason.TARGET_LOST, 700, TestSettings.scoring());

    group.lifecycle().finishEvaluation();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.lifecycle().regrouping()).isEmpty();
  }

  @Test
  void regroupingTransitionsAreGuarded() {
    assertThatThrownBy(() -> group.lifecycle().finishRegrouping())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot finish regrouping while OBSERVING");

    enterExecuting();
    group.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 700, TestSettings.scoring());
    group.lifecycle().finishEvaluation();

    assertThatThrownBy(() -> group.lifecycle().beginPlanning())
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot begin planning while REGROUPING");
  }

  @Test
  void tooHurtGroupRegroupsWithoutAPlan() {
    group.lifecycle().regroupWithoutPlan(500);

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(group.lifecycle().regrouping().map(Regrouping::startTick)).contains(500L);
    assertThat(group.lifecycle().plan()).isEmpty();
    assertThat(group.lifecycle().planSequence()).isZero();
    assertThat(group.lifecycle().committedTarget()).isEmpty();

    group.lifecycle().finishRegrouping();

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
  }

  @Test
  void regroupWindowRestartsAtTheGivenTick() {
    group.lifecycle().regroupWithoutPlan(500);

    group.lifecycle().restartRegroupWindow(1100);

    assertThat(group.lifecycle().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(group.lifecycle().regrouping().map(Regrouping::startTick)).contains(1100L);
  }

  @Test
  void rallyPointLivesOnlyWhileRegrouping() {
    group.lifecycle().regroupWithoutPlan(500);

    assertThat(group.lifecycle().regrouping()).contains(new Regrouping(500, Optional.empty()));

    group.lifecycle().rallyAt(RALLY_POINT);

    assertThat(group.lifecycle().regrouping())
        .contains(new Regrouping(500, Optional.of(RALLY_POINT)));

    group.lifecycle().restartRegroupWindow(900);

    assertThat(group.lifecycle().regrouping())
        .contains(new Regrouping(900, Optional.of(RALLY_POINT)));

    group.lifecycle().finishRegrouping();

    assertThat(group.lifecycle().regrouping()).isEmpty();
    assertThat(group.lifecycle().capture().rallyPoint()).isEmpty();

    group.lifecycle().regroupWithoutPlan(1200);

    assertThat(group.lifecycle().regrouping()).contains(new Regrouping(1200, Optional.empty()));
  }

  @Test
  void rallyPointNeedsRegrouping() {
    assertThatThrownBy(() -> group.lifecycle().rallyAt(RALLY_POINT))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot set a rally point while OBSERVING");
  }

  @Test
  void newRegroupTransitionsAreGuarded() {
    assertThatThrownBy(() -> group.lifecycle().restartRegroupWindow(10))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot restart the regroup window while OBSERVING");

    enterExecuting();

    assertThatThrownBy(() -> group.lifecycle().regroupWithoutPlan(10))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Group 00000000 cannot regroup without a plan while EXECUTING");
  }

  @Test
  void regroupTicksMustNotBeNegative() {
    assertThatThrownBy(() -> group.lifecycle().regroupWithoutPlan(-1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlanLifecycle.tick must be zero or positive, got -1");
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
  }

  @Test
  void markTargetSeenUpdatesThePlan() {
    enterExecuting();

    group.lifecycle().markTargetSeen(300);

    assertThat(group.lifecycle().plan().orElseThrow().ticksSinceTargetSeen(350)).isEqualTo(50);
  }
}
