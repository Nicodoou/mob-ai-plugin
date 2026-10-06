package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record MeleeOpening(
    MobId mob, PlayerId target, Attack attack, long tick, boolean targetInvulnerable) {
  public MeleeOpening {
    Objects.requireNonNull(mob, "MeleeOpening.mob");
    Objects.requireNonNull(target, "MeleeOpening.target");
    Objects.requireNonNull(attack, "MeleeOpening.attack");
    if (tick < 0) {
      throw new IllegalArgumentException("MeleeOpening.tick must be zero or positive, got " + tick);
    }
  }
}
