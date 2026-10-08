package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.VolleySettings;
import java.util.Objects;
import java.util.function.Supplier;

/** The plugin's clock and the attack and volley settings, for the goals' rhythms and waits. */
public record GoalTiming(
    ServerClock clock, Supplier<AttackSettings> attack, Supplier<VolleySettings> volley) {
  public GoalTiming {
    Objects.requireNonNull(clock, "GoalTiming.clock");
    Objects.requireNonNull(attack, "GoalTiming.attack");
    Objects.requireNonNull(volley, "GoalTiming.volley");
  }
}
