package io.github.nicodoou.mobai.domain.settings;

/** How a closed plan is scored (CT-27): three measures, weighted. */
public record SuccessSettings(
    double damageWeight, double speedWeight, double survivalWeight, long referenceKillTicks) {
  // Floating point: 0.4 + 0.4 + 0.2 is not exactly 1.
  private static final double WEIGHT_SUM_TOLERANCE = 1e-9;

  public SuccessSettings {
    SettingsChecks.requireBetween("SuccessSettings.damageWeight", damageWeight, 0, 1);
    SettingsChecks.requireBetween("SuccessSettings.speedWeight", speedWeight, 0, 1);
    SettingsChecks.requireBetween("SuccessSettings.survivalWeight", survivalWeight, 0, 1);
    SettingsChecks.requireAtLeast("SuccessSettings.referenceKillTicks", referenceKillTicks, 1);
    requireWeightsAddUpToOne(damageWeight + speedWeight + survivalWeight);
  }

  private static void requireWeightsAddUpToOne(double weightSum) {
    if (Math.abs(weightSum - 1) > WEIGHT_SUM_TOLERANCE) {
      throw new IllegalArgumentException(
          "SuccessSettings weights must add up to 1.0, got " + weightSum);
    }
  }
}
