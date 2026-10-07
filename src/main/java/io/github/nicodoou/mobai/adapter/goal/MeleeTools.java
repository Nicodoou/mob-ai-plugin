package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.port.ServerClock;
import java.util.Objects;

/** What a melee goal needs to strike: the attacker that records the hit and the plugin's clock. */
public record MeleeTools(MeleeAttacker attacker, ServerClock clock) {
  public MeleeTools {
    Objects.requireNonNull(attacker, "MeleeTools.attacker");
    Objects.requireNonNull(clock, "MeleeTools.clock");
  }
}
