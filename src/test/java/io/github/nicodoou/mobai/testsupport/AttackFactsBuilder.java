package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.UUID;

public final class AttackFactsBuilder {
  private AttemptId attemptId = new AttemptId(1);
  private MobId mob = new MobId(new UUID(0, 1));
  private PlayerId target = new PlayerId(new UUID(0, 2));
  private Attack attack = Attack.ZOMBIE_FRONT_STRIKE;
  private long openTick = 100;
  private boolean targetValid = true;
  private boolean targetInvulnerableAtOpen = false;
  private boolean damageEventReceived = true;
  private boolean damageCancelledByOtherPlugin = false;
  private double realDamage = 3.0;
  private boolean blockedByShield = false;
  private boolean shieldDisabled = false;
  private ProjectileContact projectileContact = ProjectileContact.NONE;
  private boolean interruptedBeforeResolution = false;
  private boolean timedOut = false;

  public AttackFactsBuilder withAttemptId(AttemptId attemptId) {
    this.attemptId = attemptId;
    return this;
  }

  public AttackFactsBuilder withMob(MobId mob) {
    this.mob = mob;
    return this;
  }

  public AttackFactsBuilder withTarget(PlayerId target) {
    this.target = target;
    return this;
  }

  public AttackFactsBuilder withAttack(Attack attack) {
    this.attack = attack;
    return this;
  }

  public AttackFactsBuilder withOpenTick(long openTick) {
    this.openTick = openTick;
    return this;
  }

  public AttackFactsBuilder withTargetValid(boolean targetValid) {
    this.targetValid = targetValid;
    return this;
  }

  public AttackFactsBuilder withTargetInvulnerableAtOpen(boolean targetInvulnerableAtOpen) {
    this.targetInvulnerableAtOpen = targetInvulnerableAtOpen;
    return this;
  }

  public AttackFactsBuilder withDamageEventReceived(boolean damageEventReceived) {
    this.damageEventReceived = damageEventReceived;
    return this;
  }

  public AttackFactsBuilder withDamageCancelledByOtherPlugin(boolean damageCancelledByOtherPlugin) {
    this.damageCancelledByOtherPlugin = damageCancelledByOtherPlugin;
    return this;
  }

  public AttackFactsBuilder withRealDamage(double realDamage) {
    this.realDamage = realDamage;
    return this;
  }

  public AttackFactsBuilder withBlockedByShield(boolean blockedByShield) {
    this.blockedByShield = blockedByShield;
    return this;
  }

  public AttackFactsBuilder withShieldDisabled(boolean shieldDisabled) {
    this.shieldDisabled = shieldDisabled;
    return this;
  }

  public AttackFactsBuilder withProjectileContact(ProjectileContact projectileContact) {
    this.projectileContact = projectileContact;
    return this;
  }

  public AttackFactsBuilder withInterruptedBeforeResolution(boolean interruptedBeforeResolution) {
    this.interruptedBeforeResolution = interruptedBeforeResolution;
    return this;
  }

  public AttackFactsBuilder withTimedOut(boolean timedOut) {
    this.timedOut = timedOut;
    return this;
  }

  public AttackFactsBuilder noDamageEvent() {
    this.damageEventReceived = false;
    this.realDamage = 0;
    return this;
  }

  public AttackFactsBuilder blockedHit() {
    this.realDamage = 0;
    this.blockedByShield = true;
    return this;
  }

  public AttackFacts build() {
    return new AttackFacts(
        attemptId,
        mob,
        target,
        attack,
        openTick,
        targetValid,
        targetInvulnerableAtOpen,
        damageEventReceived,
        damageCancelledByOtherPlugin,
        realDamage,
        blockedByShield,
        shieldDisabled,
        projectileContact,
        interruptedBeforeResolution,
        timedOut);
  }
}
