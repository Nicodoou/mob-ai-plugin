package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.Member;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoveMemberTest {
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));

  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DomainEventPublisher publisher = new DomainEventPublisher();
  private final List<DomainEvent> published = new ArrayList<>();
  private final RegroupWindow regroupWindow =
      new RegroupWindow(() -> TestSettings.defaults().retreat());
  private final DisbandGroup disbandGroup =
      new DisbandGroup(activeGroups, new GroupEvents(publisher));
  private final RemoveMember removeMember =
      new RemoveMember(activeGroups, disbandGroup, regroupWindow);

  RemoveMemberTest() {
    publisher.subscribe(DomainEvent.class, published::add);
  }

  @Test
  void removeOfAMemberKeepsTheGroupWhenOthersRemain() {
    Group group = groupWithMobs(1, 2);

    RemovalOutcome outcome = removeMember.execute(mob(2), RemovalCause.DIED, 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.REMOVED);
    assertThat(activeGroups.group(groupId(1))).containsSame(group);
    assertThat(group.roster().members()).extracting(Member::id).containsExactly(mob(1));
  }

  @Test
  void removeOfTheLastMemberDisbandsTheGroup() {
    groupWithMobs(1);

    RemovalOutcome outcome = removeMember.execute(mob(1), RemovalCause.DIED, 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.GROUP_DISBANDED);
    assertThat(activeGroups.group(groupId(1))).isEmpty();
    assertThat(activeGroups.size()).isZero();
  }

  @Test
  void removeOfALooseMobReturnsNotAMember() {
    RemovalOutcome outcome = removeMember.execute(mob(7), RemovalCause.DIED, 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.NOT_A_MEMBER);
  }

  @Test
  void removeOfTheLeaderQueuesLeaderDiedWithTheNextLeader() {
    Group group = groupWithMobs(1, 2);

    removeMember.execute(mob(1), RemovalCause.DIED, 50);

    assertThat(group.drainEvents())
        .containsExactly(new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50));
  }

  @Test
  void lastMemberLeaderDiedReachesSubscribersWhenTheGroupDisbands() {
    groupWithMobs(1);

    removeMember.execute(mob(1), RemovalCause.DIED, 50);

    assertThat(published).containsExactly(new LeaderDied(groupId(1), mob(1), Optional.empty(), 50));
  }

  @Test
  void lastDeathWhileRegroupingShortensTheRegroupWindow() {
    regroupingGroupWithMobs(1);

    removeMember.execute(mob(1), RemovalCause.DIED, 300);

    assertThat(regroupWindow.currentTicks()).isEqualTo(550);
  }

  @Test
  void lastDespawnWhileRegroupingKeepsTheRegroupWindow() {
    regroupingGroupWithMobs(1);

    removeMember.execute(mob(1), RemovalCause.DESPAWNED, 300);

    assertThat(regroupWindow.currentTicks()).isEqualTo(600);
  }

  @Test
  void lastDeathOutsideRegroupingKeepsTheRegroupWindow() {
    groupWithMobs(1);

    removeMember.execute(mob(1), RemovalCause.DIED, 300);

    assertThat(regroupWindow.currentTicks()).isEqualTo(600);
  }

  @Test
  void deathWhileRegroupingWithSurvivorsKeepsTheRegroupWindow() {
    regroupingGroupWithMobs(1, 2);

    RemovalOutcome outcome = removeMember.execute(mob(2), RemovalCause.DIED, 300);

    assertThat(outcome).isEqualTo(RemovalOutcome.REMOVED);
    assertThat(regroupWindow.currentTicks()).isEqualTo(600);
  }

  private Group groupWithMobs(long... mobs) {
    Group group = newGroup(1);
    activeGroups.add(group);
    for (long n : mobs) {
      activeGroups.join(groupId(1), mob(n), MobKind.ZOMBIE);
    }
    return group;
  }

  private Group regroupingGroupWithMobs(long... mobs) {
    Group group = groupWithMobs(mobs);
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (long n : mobs) {
      roles.put(mob(n), Role.PRESS);
    }
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(new PlanStart(new StrategyId("DIRECT_ASSAULT"), PLAYER, roles, 20.0, 100));
    group.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 200, 0.5);
    group.lifecycle().finishEvaluation();
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
