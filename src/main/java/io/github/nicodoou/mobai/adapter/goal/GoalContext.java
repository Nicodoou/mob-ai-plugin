package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** What every goal of ours shares: the orders, the attacker and the plugin's namespace. */
public record GoalContext(Plugin plugin, RoleRegistry roles, MeleeAttacker attacker) {
  public GoalContext {
    Objects.requireNonNull(plugin, "GoalContext.plugin");
    Objects.requireNonNull(roles, "GoalContext.roles");
    Objects.requireNonNull(attacker, "GoalContext.attacker");
  }
}
