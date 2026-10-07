package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.UUID;

public record ProjectileHit(
    UUID projectile, PlayerId victim, double realDamage, boolean blocked, boolean cancelled) {
  public ProjectileHit {
    Objects.requireNonNull(projectile, "ProjectileHit.projectile");
    Objects.requireNonNull(victim, "ProjectileHit.victim");
    if (!(realDamage >= 0) || !Double.isFinite(realDamage)) {
      throw new IllegalArgumentException(
          "ProjectileHit.realDamage must be zero or positive, got " + realDamage);
    }
  }
}
