package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.AttackTurns;
import java.util.List;
import java.util.Objects;

/** The turns after a decision and the orders that carry them. */
public record DealtTurns(AttackTurns turns, List<RoleAssignment> orders) {
  public DealtTurns {
    Objects.requireNonNull(turns, "DealtTurns.turns");
    Objects.requireNonNull(orders, "DealtTurns.orders");
    orders = List.copyOf(orders);
  }
}
