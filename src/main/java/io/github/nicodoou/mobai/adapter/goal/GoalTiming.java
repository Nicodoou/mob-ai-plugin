package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import java.util.Objects;
import java.util.function.Supplier;

/** The plugin's clock and the attack settings, for the goals' rhythms and waits. */
public record GoalTiming(ServerClock clock, Supplier<AttackSettings> attack) {
  public GoalTiming {
    Objects.requireNonNull(clock, "GoalTiming.clock");
    Objects.requireNonNull(attack, "GoalTiming.attack");
  }
}
