package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import java.util.Objects;

/** The weights a closed plan is scored with: survival weighs more against dangerous players. */
public record SuccessWeights(double damage, double speed, double survival) {
  public static SuccessWeights forDanger(SuccessSettings settings, double danger) {
    Objects.requireNonNull(settings, "SuccessWeights.settings");
    double survival =
        settings.survivalWeight()
            + (settings.survivalWeightMax() - settings.survivalWeight()) * danger;
    double rest = 1 - survival;
    double damage = damageShareOf(settings) * rest;
    return new SuccessWeights(damage, rest - damage, survival);
  }

  private static double damageShareOf(SuccessSettings settings) {
    double damageAndSpeed = settings.damageWeight() + settings.speedWeight();
    if (damageAndSpeed == 0) {
      return 0;
    }
    return settings.damageWeight() / damageAndSpeed;
  }
}
