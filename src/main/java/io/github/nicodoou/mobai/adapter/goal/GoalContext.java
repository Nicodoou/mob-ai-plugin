package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;
import org.bukkit.plugin.Plugin;

/** What every goal of ours shares: the orders, the tools and the plugin's namespace. */
public record GoalContext(Plugin plugin, RoleRegistry roles, GoalTools tools) {
  public GoalContext {
    Objects.requireNonNull(plugin, "GoalContext.plugin");
    Objects.requireNonNull(roles, "GoalContext.roles");
    Objects.requireNonNull(tools, "GoalContext.tools");
  }
}
