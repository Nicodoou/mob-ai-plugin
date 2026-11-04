package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;

/** The group an order came from and the tactic that group fights by. */
public record OrderSource(GroupId group, CombatTactic tactic) {
  public OrderSource {
    Objects.requireNonNull(group, "OrderSource.group");
    Objects.requireNonNull(tactic, "OrderSource.tactic");
  }

  /**
   * Whether two mobs share their places around a target. A tactic keeps them within its group; the
   * groups without a tactic share them among themselves, as every group did before tactics.
   */
  boolean sharesPlacesWith(OrderSource other) {
    if (tactic == CombatTactic.NONE) {
      return other.tactic == CombatTactic.NONE;
    }
    return group.equals(other.group);
  }
}
