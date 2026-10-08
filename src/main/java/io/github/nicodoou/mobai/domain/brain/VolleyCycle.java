package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.settings.VolleySettings;
import java.util.Objects;
import java.util.function.Supplier;

/** The volley cycle (CT-23): melee press, then open up, then the shooters fire together. */
public final class VolleyCycle {
  private final Supplier<VolleySettings> settings;

  public VolleyCycle(Supplier<VolleySettings> settings) {
    this.settings = Objects.requireNonNull(settings, "VolleyCycle.settings");
  }

  public VolleyPhase phaseAt(long planAgeTicks) {
    VolleySettings volley = settings.get();
    long cycle = volley.pressTicks() + volley.fallBackTicks() + volley.fireTicks();
    long inCycle = planAgeTicks % cycle;
    if (inCycle < volley.pressTicks()) {
      return VolleyPhase.PRESSING;
    }
    if (inCycle < volley.pressTicks() + volley.fallBackTicks()) {
      return VolleyPhase.FALLING_BACK;
    }
    return VolleyPhase.FIRING;
  }
}
