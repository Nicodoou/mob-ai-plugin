package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActiveGroupsTest {
  private final ActiveGroups activeGroups = new ActiveGroups();

  @Test
  void addIndexesEveryExistingMember() {
    Group group = newGroup(1);
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    group.roster().addMember(mob(2), MobKind.ZOMBIE);

    activeGroups.add(group);

    assertThat(activeGroups.groupOf(mob(1))).containsSame(group);
    assertThat(activeGroups.groupOf(mob(2))).containsSame(group);
  }

  @Test
  void addRejectsADuplicateGroupId() {
    activeGroups.add(newGroup(1));

    assertThatThrownBy(() -> activeGroups.add(newGroup(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ActiveGroups already has group " + groupId(1).shortId());
  }

  @Test
  void addRejectsAMobAlreadyInAnotherGroupAndAddsNothing() {
    Group first = newGroup(1);
    first.roster().addMember(mob(1), MobKind.ZOMBIE);
    activeGroups.add(first);
    Group second = newGroup(2);
    second.roster().addMember(mob(1), MobKind.ZOMBIE);
    second.roster().addMember(mob(2), MobKind.ZOMBIE);

    assertThatThrownBy(() -> activeGroups.add(second))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mob " + mob(1).shortId() + " is already in group " + groupId(1).shortId());

    assertThat(activeGroups.group(groupId(2))).isEmpty();
    assertThat(activeGroups.groupOf(mob(2))).isEmpty();
  }

  @Test
  void joinAddsTheMemberAndIndexesIt() {
    Group group = newGroup(1);
    activeGroups.add(group);

    Member member = activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);

    assertThat(member).isEqualTo(new Member(mob(1), MobKind.ZOMBIE, 1));
    assertThat(activeGroups.groupOf(mob(1))).containsSame(group);
  }

  @Test
  void joinRejectsAnUnknownGroup() {
    assertThatThrownBy(() -> activeGroups.join(groupId(9), mob(1), MobKind.ZOMBIE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ActiveGroups has no group " + groupId(9).shortId());
  }

  @Test
  void joinRejectsAMobAlreadyInAGroup() {
    Group second = newGroup(2);
    activeGroups.add(newGroup(1));
    activeGroups.add(second);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);

    assertThatThrownBy(() -> activeGroups.join(groupId(2), mob(1), MobKind.ZOMBIE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Mob " + mob(1).shortId() + " is already in group " + groupId(1).shortId());

    assertThat(second.roster().isEmpty()).isTrue();
  }

  @Test
  void leaveRemovesTheMemberAndUnindexesIt() {
    Group group = newGroup(1);
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
    activeGroups.join(groupId(1), mob(2), MobKind.ZOMBIE);

    Optional<Group> left = activeGroups.leave(mob(2), 50);

    assertThat(left).containsSame(group);
    assertThat(activeGroups.groupOf(mob(2))).isEmpty();
    assertThat(group.roster().members()).extracting(Member::id).containsExactly(mob(1));
  }

  @Test
  void leaveOfALooseMobReturnsEmpty() {
    activeGroups.add(newGroup(1));

    Optional<Group> left = activeGroups.leave(mob(7), 50);

    assertThat(left).isEmpty();
    assertThat(activeGroups.size()).isEqualTo(1);
  }

  @Test
  void leaveOfTheLeaderQueuesLeaderDied() {
    Group group = newGroup(1);
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
    activeGroups.join(groupId(1), mob(2), MobKind.ZOMBIE);

    activeGroups.leave(mob(1), 50);

    assertThat(group.drainEvents())
        .containsExactly(new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50));
  }

  @Test
  void removeUnindexesEveryMember() {
    Group group = newGroup(1);
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
    activeGroups.join(groupId(1), mob(2), MobKind.ZOMBIE);

    Optional<Group> removed = activeGroups.remove(groupId(1));

    assertThat(removed).containsSame(group);
    assertThat(activeGroups.groupOf(mob(1))).isEmpty();
    assertThat(activeGroups.groupOf(mob(2))).isEmpty();
    assertThat(activeGroups.size()).isZero();
  }

  @Test
  void groupsKeepInsertionOrder() {
    activeGroups.add(newGroup(3));
    activeGroups.add(newGroup(1));
    activeGroups.add(newGroup(2));

    List<GroupId> ids = activeGroups.groups().stream().map(Group::id).toList();

    assertThat(ids).containsExactly(groupId(3), groupId(1), groupId(2));
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
