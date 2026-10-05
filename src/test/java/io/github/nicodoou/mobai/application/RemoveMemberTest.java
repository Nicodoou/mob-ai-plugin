package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RemoveMemberTest {
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final RemoveMember removeMember =
      new RemoveMember(activeGroups, new DisbandGroup(activeGroups));

  @Test
  void removeOfAMemberKeepsTheGroupWhenOthersRemain() {
    Group group = groupWithMobs(1, 2);

    RemovalOutcome outcome = removeMember.execute(mob(2), 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.REMOVED);
    assertThat(activeGroups.group(groupId(1))).containsSame(group);
    assertThat(group.roster().members()).extracting(Member::id).containsExactly(mob(1));
  }

  @Test
  void removeOfTheLastMemberDisbandsTheGroup() {
    groupWithMobs(1);

    RemovalOutcome outcome = removeMember.execute(mob(1), 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.GROUP_DISBANDED);
    assertThat(activeGroups.group(groupId(1))).isEmpty();
    assertThat(activeGroups.size()).isZero();
  }

  @Test
  void removeOfALooseMobReturnsNotAMember() {
    RemovalOutcome outcome = removeMember.execute(mob(7), 50);

    assertThat(outcome).isEqualTo(RemovalOutcome.NOT_A_MEMBER);
  }

  @Test
  void removeOfTheLeaderQueuesLeaderDiedWithTheNextLeader() {
    Group group = groupWithMobs(1, 2);

    removeMember.execute(mob(1), 50);

    assertThat(group.drainEvents())
        .containsExactly(new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50));
  }

  private Group groupWithMobs(long... mobs) {
    Group group = newGroup(1);
    activeGroups.add(group);
    for (long n : mobs) {
      activeGroups.join(groupId(1), mob(n), MobKind.ZOMBIE);
    }
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
