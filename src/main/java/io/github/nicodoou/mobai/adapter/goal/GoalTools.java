package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** What our goals use besides the orders: the weapons, the timing and the waypoints. */
public record GoalTools(Weapons weapons, GoalTiming timing, Waypoints waypoints) {
  public GoalTools {
    Objects.requireNonNull(weapons, "GoalTools.weapons");
    Objects.requireNonNull(timing, "GoalTools.timing");
    Objects.requireNonNull(waypoints, "GoalTools.waypoints");
  }
}
