package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** What our goals use besides the orders: the attacker, the timing and the waypoints. */
public record GoalTools(MeleeAttacker attacker, GoalTiming timing, Waypoints waypoints) {
  public GoalTools {
    Objects.requireNonNull(attacker, "GoalTools.attacker");
    Objects.requireNonNull(timing, "GoalTools.timing");
    Objects.requireNonNull(waypoints, "GoalTools.waypoints");
  }
}
