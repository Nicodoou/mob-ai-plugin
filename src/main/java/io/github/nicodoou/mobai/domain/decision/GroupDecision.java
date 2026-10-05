package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record GroupDecision(
    GroupId group,
    long tick,
    GroupState state,
    Optional<PlanId> plan,
    Optional<StrategyId> strategy,
    Optional<PlayerId> target,
    List<RoleAssignment> assignments) {
  public GroupDecision {
    Objects.requireNonNull(group, "GroupDecision.group");
    Objects.requireNonNull(state, "GroupDecision.state");
    Objects.requireNonNull(plan, "GroupDecision.plan");
    Objects.requireNonNull(strategy, "GroupDecision.strategy");
    Objects.requireNonNull(target, "GroupDecision.target");
    Objects.requireNonNull(assignments, "GroupDecision.assignments");
    if (tick < 0) {
      throw new IllegalArgumentException(
          "GroupDecision.tick must be zero or positive, got " + tick);
    }
    assignments = List.copyOf(assignments);
    requireDistinctMobs(assignments);
  }

  private static void requireDistinctMobs(List<RoleAssignment> assignments) {
    Set<MobId> seen = new HashSet<>();
    for (RoleAssignment assignment : assignments) {
      if (!seen.add(assignment.mob())) {
        throw new IllegalArgumentException(
            "GroupDecision.assignments has a duplicate mob " + assignment.mob().value());
      }
    }
  }
}
