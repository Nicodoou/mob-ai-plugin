package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescribeGroupTest {
  private static final StrategyId DIRECT_ASSAULT = new StrategyId("DIRECT_ASSAULT");

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DescribeGroup describeGroup = new DescribeGroup(activeGroups);

  @Test
  void allListsEveryGroupInOrder() {
    activeGroups.add(newGroup(2));
    activeGroups.add(newGroup(1));

    assertThat(describeGroup.all())
        .extracting(GroupStatusView::id)
        .containsExactly(groupId(2), groupId(1));
  }

  @Test
  void findMatchesTheShortId() {
    activeGroups.add(newGroup(1));
    activeGroups.add(newGroup(2));

    Optional<GroupStatusView> found = describeGroup.find(groupId(1).shortId());
    Optional<GroupStatusView> missing = describeGroup.find("zzzzzzzz");

    assertThat(found).map(GroupStatusView::id).contains(groupId(1));
    assertThat(missing).isEmpty();
  }

  @Test
  void executingGroupShowsItsPlan() {
    Group group = groupWithMember(1);
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(directAssault());

    GroupStatusView view = describeGroup.find(groupId(1).shortId()).orElseThrow();

    assertThat(view.state()).isEqualTo(GroupState.EXECUTING);
    assertThat(view.strategy()).isEqualTo(Optional.of(DIRECT_ASSAULT));
    assertThat(view.target()).isEqualTo(Optional.of(player));
    assertThat(view.planSequence()).isEqualTo(1);
  }

  @Test
  void regroupingGroupShowsTheTargetItKeepsAwayFrom() {
    Group group = groupWithMember(1);
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(directAssault());
    group.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 200, TestSettings.scoring());
    group.lifecycle().finishEvaluation();

    GroupStatusView view = describeGroup.find(groupId(1).shortId()).orElseThrow();

    assertThat(view.state()).isEqualTo(GroupState.REGROUPING);
    assertThat(view.strategy()).isEmpty();
    assertThat(view.target()).isEqualTo(Optional.of(player));
  }

  private PlanStart directAssault() {
    return new PlanStart(
        DIRECT_ASSAULT, player, Map.of(mob(1), Role.PRESS), 20, 100, Optional.empty());
  }

  private Group groupWithMember(long n) {
    Group group = newGroup(n);
    activeGroups.add(group);
    activeGroups.join(groupId(n), mob(1), MobKind.ZOMBIE);
    return group;
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
