package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Writes each member's order from its group's decision; members without one lose theirs. */
public final class DecisionApplier {
  private final RoleRegistry roles;

  public DecisionApplier(RoleRegistry roles) {
    this.roles = Objects.requireNonNull(roles, "DecisionApplier.roles");
  }

  public void apply(GroupDecision decision, List<Member> members) {
    Map<MobId, RoleAssignment> orders = ordersByMob(decision);
    for (Member member : members) {
      applyTo(member.id(), orders);
    }
  }

  public void retainOnly(Set<MobId> members) {
    roles.retainOnly(members);
  }

  private void applyTo(MobId mob, Map<MobId, RoleAssignment> orders) {
    RoleAssignment order = orders.get(mob);
    if (order == null) {
      roles.clear(mob);
      return;
    }
    roles.assign(order);
  }

  private static Map<MobId, RoleAssignment> ordersByMob(GroupDecision decision) {
    Map<MobId, RoleAssignment> orders = new HashMap<>();
    for (RoleAssignment assignment : decision.assignments()) {
      orders.put(assignment.mob(), assignment);
    }
    return orders;
  }
}
