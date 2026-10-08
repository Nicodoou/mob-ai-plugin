package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import java.util.Objects;

/** How dangerous a player is to this group, from 0 (ordinary) to 1 (very good) (CT-27). */
public final class DangerLevel {
  private DangerLevel() {}

  public static double of(DangerRecord record, SuccessSettings settings) {
    Objects.requireNonNull(record, "DangerLevel.record");
    Objects.requireNonNull(settings, "DangerLevel.settings");
    double ratio = healthLostPerDamage(record, settings);
    double level =
        (ratio - settings.dangerRatioLow())
            / (settings.dangerRatioHigh() - settings.dangerRatioLow());
    return Math.clamp(level, 0.0, 1.0);
  }

  // The prior counts as some fighting at the low ratio, so a short fight reads as ordinary.
  private static double healthLostPerDamage(DangerRecord record, SuccessSettings settings) {
    double priorDamage = settings.dangerPriorDamage();
    double priorHealthLost = settings.dangerRatioLow() * priorDamage;
    return (record.healthLost() + priorHealthLost) / (record.damageDealt() + priorDamage);
  }
}
