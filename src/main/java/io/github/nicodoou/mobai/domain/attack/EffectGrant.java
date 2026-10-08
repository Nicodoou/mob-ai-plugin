package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.shared.EffectKind;
import java.util.Objects;

/** An effect a landed attack leaves on the player, at a level counted from 1. */
public record EffectGrant(EffectKind kind, int level, long durationTicks) {
  public EffectGrant {
    Objects.requireNonNull(kind, "EffectGrant.kind");
    if (level < 1) {
      throw new IllegalArgumentException("EffectGrant.level must be at least 1, got " + level);
    }
    if (durationTicks < 1) {
      throw new IllegalArgumentException(
          "EffectGrant.durationTicks must be at least 1, got " + durationTicks);
    }
  }
}
