package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** The order each mob follows until the next decision; goals only read it. */
public final class RoleRegistry {
  private final Map<MobId, RoleAssignment> assignments = new HashMap<>();

  public void assign(RoleAssignment assignment) {
    assignments.put(assignment.mob(), assignment);
  }

  public Optional<RoleAssignment> assignmentOf(MobId mob) {
    return Optional.ofNullable(assignments.get(mob));
  }

  public void clear(MobId mob) {
    assignments.remove(mob);
  }

  public void retainOnly(Set<MobId> members) {
    assignments.keySet().retainAll(members);
  }

  public int size() {
    return assignments.size();
  }
}
