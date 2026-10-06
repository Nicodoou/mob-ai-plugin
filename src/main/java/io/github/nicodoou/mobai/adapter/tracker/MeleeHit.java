package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record MeleeHit(
    MobId attacker, PlayerId victim, double realDamage, boolean blocked, boolean cancelled) {
  public MeleeHit {
    Objects.requireNonNull(attacker, "MeleeHit.attacker");
    Objects.requireNonNull(victim, "MeleeHit.victim");
    if (!(realDamage >= 0) || !Double.isFinite(realDamage)) {
      throw new IllegalArgumentException(
          "MeleeHit.realDamage must be zero or positive, got " + realDamage);
    }
  }
}
