package io.github.nicodoou.mobai.domain.attack;

import java.util.Optional;

public final class AttackClassifier {
  private static final int RULE_TARGET_INVALID = 1;
  private static final int RULE_DAMAGE_CANCELLED = 2;
  private static final int RULE_TARGET_INVULNERABLE = 3;
  private static final int RULE_ALLY_HIT = 4;
  private static final int RULE_INTERRUPTED = 5;
  private static final int RULE_REAL_DAMAGE = 6;
  private static final int RULE_SHIELD = 7;
  private static final int RULE_MISS = 8;

  public Classification classify(AttackFacts facts) {
    Optional<Classification> neutral = classifyNeutral(facts);
    if (neutral.isPresent()) {
      return neutral.get();
    }
    return classifyContact(facts);
  }

  private Optional<Classification> classifyNeutral(AttackFacts facts) {
    if (!facts.targetValid()) {
      return Optional.of(neutral(NeutralCause.TARGET_INVALID, RULE_TARGET_INVALID, facts));
    }
    if (facts.damageCancelledByOtherPlugin()) {
      return Optional.of(neutral(NeutralCause.DAMAGE_CANCELLED, RULE_DAMAGE_CANCELLED, facts));
    }
    if (facts.targetInvulnerableAtOpen() && facts.realDamage() == 0 && !facts.blockedByShield()) {
      return Optional.of(
          neutral(NeutralCause.TARGET_INVULNERABLE, RULE_TARGET_INVULNERABLE, facts));
    }
    if (facts.projectileContact() == ProjectileContact.ALLY) {
      return Optional.of(neutral(NeutralCause.ALLY_HIT, RULE_ALLY_HIT, facts));
    }
    if (facts.interruptedBeforeResolution()) {
      return Optional.of(neutral(NeutralCause.INTERRUPTED, RULE_INTERRUPTED, facts));
    }
    return Optional.empty();
  }

  private Classification classifyContact(AttackFacts facts) {
    if (facts.realDamage() > 0) {
      return result(new AttackOutcome.Hit(), RULE_REAL_DAMAGE, facts);
    }
    if (facts.blockedByShield()) {
      AttackOutcome outcome =
          facts.shieldDisabled() ? new AttackOutcome.Hit() : new AttackOutcome.Partial();
      return result(outcome, RULE_SHIELD, facts);
    }
    return result(new AttackOutcome.Miss(), RULE_MISS, facts);
  }

  private Classification neutral(NeutralCause cause, int rule, AttackFacts facts) {
    return result(new AttackOutcome.Neutral(cause), rule, facts);
  }

  private Classification result(AttackOutcome outcome, int rule, AttackFacts facts) {
    return new Classification(outcome, new ClassificationTrace(rule, facts));
  }
}
