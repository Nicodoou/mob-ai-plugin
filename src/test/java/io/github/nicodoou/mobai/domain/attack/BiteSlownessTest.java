package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.settings.SpiderSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class BiteSlownessTest {
  private static final int NO_SLOWNESS = 0;

  private final BiteSlowness slowness = new BiteSlowness(() -> new SpiderSettings(60, 1));

  @Test
  void aBiteThatHitsGrantsTheConfiguredSlowness() {
    Optional<EffectGrant> grant =
        slowness.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), NO_SLOWNESS);

    assertThat(grant).contains(new EffectGrant(EffectKind.SLOWNESS, 1, 60));
  }

  @Test
  void levelAndDurationComeFromTheSettings() {
    BiteSlowness custom = new BiteSlowness(() -> new SpiderSettings(100, 2));

    Optional<EffectGrant> grant =
        custom.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), NO_SLOWNESS);

    assertThat(grant).contains(new EffectGrant(EffectKind.SLOWNESS, 2, 100));
  }

  @Test
  void anActiveSlownessIsNotRenewed() {
    Optional<EffectGrant> grant =
        slowness.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), 1);

    assertThat(grant).isEmpty();
  }

  @Test
  void aWeakerSlownessIsNotUpgraded() {
    BiteSlowness stronger = new BiteSlowness(() -> new SpiderSettings(60, 2));

    Optional<EffectGrant> grant =
        stronger.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), 1);

    assertThat(grant).isEmpty();
  }

  @Test
  void aBlockedBiteGrantsNothing() {
    Optional<EffectGrant> grant =
        slowness.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Partial(), NO_SLOWNESS);

    assertThat(grant).isEmpty();
  }

  @Test
  void aMissedOrNeutralBiteGrantsNothing() {
    AttackOutcome neutral = new AttackOutcome.Neutral(NeutralCause.INTERRUPTED);

    Optional<EffectGrant> missed =
        slowness.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Miss(), NO_SLOWNESS);
    Optional<EffectGrant> interrupted =
        slowness.afterAttack(Attack.SPIDER_BITE, neutral, NO_SLOWNESS);

    assertThat(missed).isEmpty();
    assertThat(interrupted).isEmpty();
  }

  @Test
  void otherAttacksGrantNothing() {
    Optional<EffectGrant> grant =
        slowness.afterAttack(Attack.ZOMBIE_FRONT_STRIKE, new AttackOutcome.Hit(), NO_SLOWNESS);

    assertThat(grant).isEmpty();
  }

  @Test
  void settingsAreReadOnEveryBite() {
    AtomicReference<SpiderSettings> current = new AtomicReference<>(new SpiderSettings(60, 1));
    BiteSlowness live = new BiteSlowness(current::get);
    Optional<EffectGrant> first =
        live.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), NO_SLOWNESS);

    current.set(new SpiderSettings(100, 2));
    Optional<EffectGrant> second =
        live.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), NO_SLOWNESS);

    assertThat(first).contains(new EffectGrant(EffectKind.SLOWNESS, 1, 60));
    assertThat(second).contains(new EffectGrant(EffectKind.SLOWNESS, 2, 100));
  }

  @Test
  void negativeCurrentLevelIsRejected() {
    assertThatThrownBy(() -> slowness.afterAttack(Attack.SPIDER_BITE, new AttackOutcome.Hit(), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("BiteSlowness.currentSlownessLevel must be zero or positive, got -1");
  }
}
