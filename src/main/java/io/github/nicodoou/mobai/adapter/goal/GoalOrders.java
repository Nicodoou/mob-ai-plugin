package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** How every goal of ours reads its order: its own role only, and a target that is still valid. */
final class GoalOrders {
  private GoalOrders() {}

  static Optional<RoleAssignment> orderFor(Mob mob, RoleRegistry roles, Role role) {
    return roles.assignmentOf(new MobId(mob.getUniqueId())).filter(order -> order.role() == role);
  }

  static Optional<Player> validTarget(RoleAssignment order, Mob mob) {
    return order
        .target()
        .map(PlayerId::value)
        .map(Bukkit::getPlayer)
        .filter(player -> TargetChecks.isValidTarget(player, mob));
  }
}
