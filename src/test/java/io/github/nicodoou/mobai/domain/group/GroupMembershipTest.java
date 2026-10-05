package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupMembershipTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final MobId MOB_2 = new MobId(new UUID(1, 2));
  private static final MobId MOB_3 = new MobId(new UUID(1, 3));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));

  private static Group newGroup() {
    return new Group(
        GROUP,
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }

  @Test
  void membersGetIncreasingJoinOrders() {
    Group group = newGroup();

    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.SPIDER);
    group.roster().addMember(MOB_3, MobKind.SKELETON);

    assertThat(group.roster().members())
        .containsExactly(
            new Member(MOB_1, MobKind.ZOMBIE, 1),
            new Member(MOB_2, MobKind.SPIDER, 2),
            new Member(MOB_3, MobKind.SKELETON, 3));
  }

  @Test
  void firstMemberIsTheLeader() {
    Group group = newGroup();

    assertThat(group.roster().leader()).isEmpty();

    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.ZOMBIE);

    assertThat(group.roster().leader()).contains(MOB_1);
  }

  @Test
  void rejectsDuplicateMembers() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);

    assertThatThrownBy(() -> group.roster().addMember(MOB_1, MobKind.ZOMBIE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group 00000000 already has member 00000000-0000-0001-0000-000000000001");
  }

  @Test
  void leaderLeavingPromotesTheOldestMemberAndEmitsLeaderDied() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.ZOMBIE);
    group.roster().addMember(MOB_3, MobKind.ZOMBIE);

    group.removeMember(MOB_1, 500);

    assertThat(group.roster().leader()).contains(MOB_2);
    assertThat(group.drainEvents())
        .containsExactly(new LeaderDied(GROUP, MOB_1, Optional.of(MOB_2), 500));
  }

  @Test
  void lastLeaderLeavingEmitsLeaderDiedWithoutSuccessor() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);

    group.removeMember(MOB_1, 500);

    assertThat(group.drainEvents())
        .containsExactly(new LeaderDied(GROUP, MOB_1, Optional.empty(), 500));
    assertThat(group.roster().isEmpty()).isTrue();
  }

  @Test
  void nonLeaderLeavingEmitsNothing() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.ZOMBIE);

    group.removeMember(MOB_2, 500);

    assertThat(group.drainEvents()).isEmpty();
  }

  @Test
  void removingAnUnknownMobDoesNothing() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    List<Member> before = group.roster().members();

    assertThatCode(() -> group.removeMember(new MobId(new UUID(9, 9)), 500))
        .doesNotThrowAnyException();

    assertThat(group.roster().members()).isEqualTo(before);
  }

  @Test
  void restoredMembersKeepTheirOrderAndTheNextJoinOrderContinues() {
    Group group = newGroup();

    group.roster().restoreMember(new Member(MOB_3, MobKind.ZOMBIE, 7));
    group.roster().restoreMember(new Member(MOB_1, MobKind.ZOMBIE, 2));

    assertThat(group.roster().members().stream().map(Member::id)).containsExactly(MOB_1, MOB_3);
    assertThat(group.roster().leader()).contains(MOB_1);
    assertThat(group.roster().addMember(MOB_2, MobKind.ZOMBIE).joinOrder()).isEqualTo(8);
  }

  @Test
  void rejectsRestoringARepeatedJoinOrder() {
    Group group = newGroup();
    group.roster().restoreMember(new Member(MOB_1, MobKind.ZOMBIE, 2));

    assertThatThrownBy(() -> group.roster().restoreMember(new Member(MOB_2, MobKind.ZOMBIE, 2)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group 00000000 already has join order 2");
  }

  @Test
  void spiderTargetsBelongToSpidersAndLeaveWithThem() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.roster().addMember(MOB_2, MobKind.SPIDER);

    group.roster().assignSpiderTarget(MOB_2, ALICE);

    assertThat(group.roster().spiderTarget(MOB_2)).contains(ALICE);

    group.removeMember(MOB_2, 500);

    assertThat(group.roster().spiderTarget(MOB_2)).isEmpty();
  }

  @Test
  void onlySpidersHaveSpiderTargets() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);

    assertThatThrownBy(() -> group.roster().assignSpiderTarget(MOB_1, ALICE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group 00000000: member 00000000-0000-0001-0000-000000000001 is not a spider");
  }

  @Test
  void drainingEventsEmptiesThePendingList() {
    Group group = newGroup();
    group.roster().addMember(MOB_1, MobKind.ZOMBIE);
    group.removeMember(MOB_1, 500);

    assertThat(group.drainEvents()).hasSize(1);

    assertThat(group.drainEvents()).isEmpty();
  }
}
