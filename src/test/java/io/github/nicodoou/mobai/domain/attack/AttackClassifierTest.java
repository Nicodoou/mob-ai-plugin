package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.testsupport.AttackFactsBuilder;
import org.junit.jupiter.api.Test;

class AttackClassifierTest {
  private final AttackClassifier classifier = new AttackClassifier();

  @Test
  void invalidTargetIsNeutral() {
    AttackFacts facts = new AttackFactsBuilder().withTargetValid(false).build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.TARGET_INVALID, 1);
  }

  @Test
  void invalidTargetWinsOverEveryOtherRule() {
    AttackFacts facts =
        new AttackFactsBuilder()
            .withTargetValid(false)
            .withDamageCancelledByOtherPlugin(true)
            .withInterruptedBeforeResolution(true)
            .build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.TARGET_INVALID, 1);
  }

  @Test
  void cancelledDamageIsNeutral() {
    AttackFacts facts =
        new AttackFactsBuilder().withDamageCancelledByOtherPlugin(true).withRealDamage(0).build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.DAMAGE_CANCELLED, 2);
  }

  @Test
  void invulnerableTargetWithoutDamageIsNeutral() {
    AttackFacts facts =
        new AttackFactsBuilder().noDamageEvent().withTargetInvulnerableAtOpen(true).build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.TARGET_INVULNERABLE, 3);
  }

  @Test
  void invulnerableTargetThatStillTookDamageIsAHit() {
    AttackFacts facts =
        new AttackFactsBuilder().withTargetInvulnerableAtOpen(true).withRealDamage(1).build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Hit(), 6);
  }

  @Test
  void allyHitByArrowIsNeutral() {
    AttackFacts facts =
        new AttackFactsBuilder()
            .withAttack(Attack.SKELETON_DIRECT_SHOT)
            .noDamageEvent()
            .withProjectileContact(ProjectileContact.ALLY)
            .build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.ALLY_HIT, 4);
  }

  @Test
  void interruptedAttackIsNeutral() {
    AttackFacts facts =
        new AttackFactsBuilder().noDamageEvent().withInterruptedBeforeResolution(true).build();

    Classification result = classifier.classify(facts);

    assertNeutral(result, facts, NeutralCause.INTERRUPTED, 5);
  }

  @Test
  void realDamageIsAHit() {
    AttackFacts facts = new AttackFactsBuilder().build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Hit(), 6);
  }

  @Test
  void damageFullyAbsorbedIsStillAHit() {
    AttackFacts facts = new AttackFactsBuilder().withRealDamage(2.4).build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Hit(), 6);
  }

  @Test
  void blockedHitIsPartial() {
    AttackFacts facts = new AttackFactsBuilder().blockedHit().build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Partial(), 7);
  }

  @Test
  void blockedHitThatDisablesTheShieldIsAHit() {
    AttackFacts facts = new AttackFactsBuilder().blockedHit().withShieldDisabled(true).build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Hit(), 7);
  }

  @Test
  void arrowThatHitABlockIsAMiss() {
    AttackFacts facts =
        new AttackFactsBuilder()
            .withAttack(Attack.SKELETON_LEAD_SHOT)
            .noDamageEvent()
            .withProjectileContact(ProjectileContact.BLOCK)
            .build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Miss(), 8);
  }

  @Test
  void arrowThatTimedOutIsAMiss() {
    AttackFacts facts =
        new AttackFactsBuilder()
            .withAttack(Attack.SKELETON_LEAD_SHOT)
            .noDamageEvent()
            .withTimedOut(true)
            .build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Miss(), 8);
  }

  @Test
  void meleeWithoutDamageAgainstAVulnerableTargetIsAMiss() {
    AttackFacts facts = new AttackFactsBuilder().noDamageEvent().build();

    Classification result = classifier.classify(facts);

    assertOutcome(result, facts, new AttackOutcome.Miss(), 8);
  }

  private void assertNeutral(
      Classification result, AttackFacts facts, NeutralCause cause, int rule) {
    assertOutcome(result, facts, new AttackOutcome.Neutral(cause), rule);
  }

  private void assertOutcome(
      Classification result, AttackFacts facts, AttackOutcome outcome, int rule) {
    assertThat(result.outcome()).isEqualTo(outcome);
    assertThat(result.trace().rule()).isEqualTo(rule);
    assertThat(result.trace().facts()).isSameAs(facts);
  }
}
