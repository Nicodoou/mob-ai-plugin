package io.github.nicodoou.mobai.domain.event;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;

public record PlanClosed(ClosedPlan plan) implements DomainEvent {
  public PlanClosed {
    Objects.requireNonNull(plan, "PlanClosed.plan");
  }

  @Override
  public GroupId groupId() {
    return plan.id().group();
  }

  @Override
  public long tick() {
    return plan.endTick();
  }
}
