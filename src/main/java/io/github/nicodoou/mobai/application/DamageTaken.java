package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record DamageTaken(MobId mob, PlayerId attacker, double damage, long tick) {
  public DamageTaken {
    Objects.requireNonNull(mob, "DamageTaken.mob");
    Objects.requireNonNull(attacker, "DamageTaken.attacker");
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException(
          "DamageTaken.damage must be a positive number, got " + damage);
    }
    if (tick < 0) {
      throw new IllegalArgumentException("DamageTaken.tick must be zero or positive, got " + tick);
    }
  }
}
