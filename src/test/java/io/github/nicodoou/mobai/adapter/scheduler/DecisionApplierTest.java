package io.github.nicodoou.mobai.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionApplierTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final RoleRegistry roles = new RoleRegistry();
  private final DecisionApplier applier = new DecisionApplier(roles);
  private final List<Member> members =
      List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.ZOMBIE, 2));

  @Test
  void membersWithAnOrderGetIt() {
    RoleAssignment order = pressOrder(mob(1));

    applier.apply(decisionWith(order), members);

    assertThat(roles.assignmentOf(mob(1))).contains(order);
    assertThat(roles.assignmentOf(mob(2))).isEmpty();
  }

  @Test
  void membersWithoutAnOrderLoseTheirOldOne() {
    roles.assign(pressOrder(mob(2)));

    applier.apply(decisionWith(pressOrder(mob(1))), members);

    assertThat(roles.assignmentOf(mob(2))).isEmpty();
  }

  @Test
  void retainOnlyKeepsActiveMembers() {
    roles.assign(pressOrder(mob(1)));
    roles.assign(pressOrder(mob(9)));

    applier.retainOnly(Set.of(mob(1)));

    assertThat(roles.assignmentOf(mob(1))).isPresent();
    assertThat(roles.assignmentOf(mob(9))).isEmpty();
  }

  private GroupDecision decisionWith(RoleAssignment order) {
    return new GroupDecision(
        new GroupId(new UUID(0, 1)),
        100,
        GroupState.EXECUTING,
        Optional.empty(),
        Optional.empty(),
        Optional.of(player),
        List.of(order));
  }

  private RoleAssignment pressOrder(MobId mob) {
    return new RoleAssignment(
        mob, Role.PRESS, Optional.of(player), Optional.of(Attack.ZOMBIE_FRONT_STRIKE), false);
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(1, id));
  }
}
