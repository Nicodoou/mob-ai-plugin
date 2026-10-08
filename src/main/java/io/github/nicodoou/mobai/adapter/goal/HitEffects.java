package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.attack.BiteSlowness;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import java.util.Objects;
import org.bukkit.entity.Player;

/** Effects a landed strike leaves on the player, such as the spider's slowness. */
public final class HitEffects {
  private static final int ABSENT_LEVEL = 0;

  private final BiteSlowness biteSlowness;
  private final VersionTranslator translator;

  public HitEffects(BiteSlowness biteSlowness, VersionTranslator translator) {
    this.biteSlowness = Objects.requireNonNull(biteSlowness, "HitEffects.biteSlowness");
    this.translator = Objects.requireNonNull(translator, "HitEffects.translator");
  }

  public void apply(Player target, Attack attack, AttackOutcome outcome) {
    int currentSlowness =
        translator.effectLevels(target).getOrDefault(EffectKind.SLOWNESS, ABSENT_LEVEL);
    biteSlowness
        .afterAttack(attack, outcome, currentSlowness)
        .ifPresent(grant -> target.addPotionEffect(translator.potionEffectOf(grant)));
  }
}
