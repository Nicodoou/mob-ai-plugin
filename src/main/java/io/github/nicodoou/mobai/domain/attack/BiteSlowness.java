package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.settings.SpiderSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** A spider bite that lands slows the player; an active slowness is neither renewed nor stacked. */
public final class BiteSlowness {
  private final Supplier<SpiderSettings> settings;

  public BiteSlowness(Supplier<SpiderSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "BiteSlowness.settings");
  }

  public Optional<EffectGrant> afterAttack(
      Attack attack, AttackOutcome outcome, int currentSlownessLevel) {
    requireValid(attack, outcome, currentSlownessLevel);
    if (!slows(attack, outcome, currentSlownessLevel)) {
      return Optional.empty();
    }
    SpiderSettings spider = settings.get();
    return Optional.of(
        new EffectGrant(
            EffectKind.SLOWNESS, spider.slownessLevel(), spider.slownessDurationTicks()));
  }

  private static void requireValid(Attack attack, AttackOutcome outcome, int currentSlownessLevel) {
    Objects.requireNonNull(attack, "BiteSlowness.attack");
    Objects.requireNonNull(outcome, "BiteSlowness.outcome");
    if (currentSlownessLevel < 0) {
      throw new IllegalArgumentException(
          "BiteSlowness.currentSlownessLevel must be zero or positive, got "
              + currentSlownessLevel);
    }
  }

  private static boolean slows(Attack attack, AttackOutcome outcome, int currentSlownessLevel) {
    if (attack != Attack.SPIDER_BITE) {
      return false;
    }
    // A bite the shield stopped never reached the player, so it carries no venom.
    if (!(outcome instanceof AttackOutcome.Hit)) {
      return false;
    }
    return currentSlownessLevel <= 0;
  }
}
