package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import java.util.Objects;

/** What our goals use besides the orders: the attacker, the plugin's clock and the waypoints. */
public record GoalTools(MeleeAttacker attacker, ServerClock clock, Waypoints waypoints) {
  public GoalTools {
    Objects.requireNonNull(attacker, "GoalTools.attacker");
    Objects.requireNonNull(clock, "GoalTools.clock");
    Objects.requireNonNull(waypoints, "GoalTools.waypoints");
  }
}
