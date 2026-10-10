package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/**
 * What our goals use besides the orders: the weapons, the timing, the waypoints and the rally
 * route.
 */
public record GoalTools(
    Weapons weapons,
    GoalTiming timing,
    Waypoints waypoints,
    RallyRoute rallyRoute,
    PerchClaims perches) {
  public GoalTools {
    Objects.requireNonNull(weapons, "GoalTools.weapons");
    Objects.requireNonNull(timing, "GoalTools.timing");
    Objects.requireNonNull(waypoints, "GoalTools.waypoints");
    Objects.requireNonNull(rallyRoute, "GoalTools.rallyRoute");
    Objects.requireNonNull(perches, "GoalTools.perches");
  }
}
