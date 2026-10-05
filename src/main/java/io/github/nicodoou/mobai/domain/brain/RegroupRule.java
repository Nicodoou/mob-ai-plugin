package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class RegroupRule {
  private final Supplier<RetreatSettings> settings;
  private final RegroupWindow window;

  public RegroupRule(Supplier<RetreatSettings> settings, RegroupWindow window) {
    this.settings = Objects.requireNonNull(settings, "RegroupRule.settings");
    this.window = Objects.requireNonNull(window, "RegroupRule.window");
  }

  public Optional<RegroupEndReason> detect(GroupSnapshot snapshot, long regroupStartTick) {
    if (hasRecoveredMajority(snapshot)) {
      return Optional.of(RegroupEndReason.RECOVERED);
    }
    if (snapshot.tick() - regroupStartTick >= window.currentTicks()) {
      return Optional.of(RegroupEndReason.WINDOW_EXPIRED);
    }
    return Optional.empty();
  }

  private boolean hasRecoveredMajority(GroupSnapshot snapshot) {
    long recovered = snapshot.mobs().stream().filter(this::isRecovered).count();
    return 2 * recovered > snapshot.mobs().size();
  }

  private boolean isRecovered(MobSnapshot mob) {
    return mob.health() >= settings.get().recoveryHealthFraction() * mob.maxHealth();
  }
}
