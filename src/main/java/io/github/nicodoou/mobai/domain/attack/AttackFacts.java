package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;

public record AttackFacts(
    AttemptId attemptId,
    MobId mob,
    PlayerId target,
    Attack attack,
    long openTick,
    boolean targetValid,
    boolean targetInvulnerableAtOpen,
    boolean damageEventReceived,
    boolean damageCancelledByOtherPlugin,
    double realDamage,
    boolean blockedByShield,
    boolean shieldDisabled,
    ProjectileContact projectileContact,
    boolean interruptedBeforeResolution,
    boolean timedOut) {

  public AttackFacts {
    Objects.requireNonNull(attemptId, "AttackFacts.attemptId");
    Objects.requireNonNull(mob, "AttackFacts.mob");
    Objects.requireNonNull(target, "AttackFacts.target");
    Objects.requireNonNull(attack, "AttackFacts.attack");
    Objects.requireNonNull(projectileContact, "AttackFacts.projectileContact");
    if (openTick < 0) {
      throw new IllegalArgumentException(
          "AttackFacts.openTick must be zero or positive, got " + openTick);
    }
    if (!(realDamage >= 0) || !Double.isFinite(realDamage)) {
      throw new IllegalArgumentException(
          "AttackFacts.realDamage must be zero or positive, got " + realDamage);
    }
    if (!damageEventReceived
        && (realDamage > 0 || blockedByShield || damageCancelledByOtherPlugin)) {
      throw new IllegalArgumentException(
          "AttackFacts: damage, block or cancellation require a damage event");
    }
    if (shieldDisabled && !blockedByShield) {
      throw new IllegalArgumentException("AttackFacts: shieldDisabled requires blockedByShield");
    }
  }
}
