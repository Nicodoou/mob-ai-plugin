package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LifecycleCaptureTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));

  @Test
  void planningIsRejected() {
    assertThatThrownBy(
            () ->
                new LifecycleCapture(
                    GroupState.PLANNING,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    OptionalLong.empty(),
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LifecycleCapture.state cannot be PLANNING");
  }

  @Test
  void planOutsideExecutionIsRejected() {
    Plan plan = Plan.start(new PlanId(groupId(1), 1), pressStart());

    assertThatThrownBy(
            () ->
                new LifecycleCapture(
                    GroupState.OBSERVING,
                    Optional.of(plan),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    OptionalLong.empty(),
                    1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "LifecycleCapture.plan must be present only while EXECUTING or EVALUATING,"
                + " got OBSERVING with plan present");
  }

  @Test
  void regroupStartOutsideRegroupingIsRejected() {
    assertThatThrownBy(
            () ->
                new LifecycleCapture(
                    GroupState.OBSERVING,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    OptionalLong.of(5),
                    0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "LifecycleCapture.regroupStartTick must be present only while REGROUPING,"
                + " got OBSERVING");
  }

  @Test
  void planSequenceMustMatchThePlan() {
    Plan plan = Plan.start(new PlanId(groupId(1), 1), pressStart());

    assertThatThrownBy(
            () ->
                new LifecycleCapture(
                    GroupState.EXECUTING,
                    Optional.of(plan),
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    OptionalLong.empty(),
                    2))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("LifecycleCapture.planSequence must match the plan, got 2 for plan 1");
  }

  @Test
  void executingGroupRoundTrips() {
    Group original = executingGroup(1);
    original.lifecycle().recordPlanDamage(player, 4.0);
    original.lifecycle().markTargetSeen(150);
    Group restored = groupWithMobOne(1);

    restored.lifecycle().restore(original.lifecycle().capture());

    assertThat(restored.lifecycle().capture()).isEqualTo(original.lifecycle().capture());
  }

  @Test
  void regroupingGroupRoundTrips() {
    Group original = executingGroup(1);
    original.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 200, TestSettings.scoring());
    original.lifecycle().finishEvaluation();
    Group restored = groupWithMobOne(1);

    restored.lifecycle().restore(original.lifecycle().capture());

    LifecycleCapture capture = restored.lifecycle().capture();
    assertThat(capture).isEqualTo(original.lifecycle().capture());
    assertThat(capture.state()).isEqualTo(GroupState.REGROUPING);
    assertThat(capture.committedTarget()).contains(player);
    assertThat(capture.lastEndReason()).contains(PlanEndReason.GROUP_RETREATED);
    assertThat(capture.regroupStartTick()).hasValue(200);
  }

  @Test
  void restoreRejectsAPlanOfAnotherGroup() {
    LifecycleCapture capture = executingGroup(1).lifecycle().capture();
    Group other = groupWithMobOne(2);

    assertThatThrownBy(() -> other.lifecycle().restore(capture))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Group "
                + groupId(2).shortId()
                + " cannot restore a plan of group "
                + groupId(1).shortId());
  }

  @Test
  void restoreRejectsAPlanWithANonMember() {
    LifecycleCapture capture = executingGroup(1).lifecycle().capture();
    Group withoutMobOne = newGroup(1);
    withoutMobOne.roster().addMember(mob(2), MobKind.ZOMBIE);

    assertThatThrownBy(() -> withoutMobOne.lifecycle().restore(capture))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group " + groupId(1).shortId() + " has no member " + mob(1).value());
  }

  @Test
  void restoreRequiresObserving() {
    LifecycleCapture capture = executingGroup(1).lifecycle().capture();
    Group executing = executingGroup(1);

    assertThatThrownBy(() -> executing.lifecycle().restore(capture))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Group " + groupId(1).shortId() + " cannot restore its lifecycle while EXECUTING");
  }

  private Group executingGroup(long n) {
    Group group = groupWithMobOne(n);
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(pressStart());
    return group;
  }

  private static Group groupWithMobOne(long n) {
    Group group = newGroup(n);
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    return group;
  }

  private PlanStart pressStart() {
    return new PlanStart(
        new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20, 100);
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static Group newGroup(long n) {
    return new Group(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }
}
