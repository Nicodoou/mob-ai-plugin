package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record AttackResolution(
    MobId mob,
    PlayerId target,
    Attack attack,
    AttackOutcome outcome,
    double damageDealt,
    long tick) {
  public AttackResolution {
    Objects.requireNonNull(mob, "AttackResolution.mob");
    Objects.requireNonNull(target, "AttackResolution.target");
    Objects.requireNonNull(attack, "AttackResolution.attack");
    Objects.requireNonNull(outcome, "AttackResolution.outcome");
    if (!(damageDealt >= 0) || !Double.isFinite(damageDealt)) {
      throw new IllegalArgumentException(
          "AttackResolution.damageDealt must be zero or positive, got " + damageDealt);
    }
    if (tick < 0) {
      throw new IllegalArgumentException(
          "AttackResolution.tick must be zero or positive, got " + tick);
    }
  }
}
