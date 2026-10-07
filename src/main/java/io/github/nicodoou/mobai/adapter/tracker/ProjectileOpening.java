package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.UUID;

public record ProjectileOpening(
    UUID projectile,
    MobId mob,
    PlayerId target,
    Attack attack,
    long tick,
    boolean targetInvulnerable) {
  public ProjectileOpening {
    Objects.requireNonNull(projectile, "ProjectileOpening.projectile");
    Objects.requireNonNull(mob, "ProjectileOpening.mob");
    Objects.requireNonNull(target, "ProjectileOpening.target");
    Objects.requireNonNull(attack, "ProjectileOpening.attack");
    if (tick < 0) {
      throw new IllegalArgumentException(
          "ProjectileOpening.tick must be zero or positive, got " + tick);
    }
  }
}
