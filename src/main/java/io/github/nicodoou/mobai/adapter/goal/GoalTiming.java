package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.settings.PressureSettings;
import io.github.nicodoou.mobai.domain.settings.VolleySettings;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The plugin's clock and the attack, volley, pack and pressure settings, for the goals' rhythms and
 * waits.
 */
public record GoalTiming(
    ServerClock clock,
    Supplier<AttackSettings> attack,
    Supplier<VolleySettings> volley,
    Supplier<PackSettings> pack,
    Supplier<PressureSettings> pressure) {
  public GoalTiming {
    Objects.requireNonNull(clock, "GoalTiming.clock");
    Objects.requireNonNull(attack, "GoalTiming.attack");
    Objects.requireNonNull(volley, "GoalTiming.volley");
    Objects.requireNonNull(pack, "GoalTiming.pack");
    Objects.requireNonNull(pressure, "GoalTiming.pressure");
  }
}
