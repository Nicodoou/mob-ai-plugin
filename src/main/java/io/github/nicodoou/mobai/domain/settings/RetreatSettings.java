package io.github.nicodoou.mobai.domain.settings;

public record RetreatSettings(
    double recoveryHealthFraction,
    double healSafeDistanceBlocks,
    long regroupInitialTicks,
    long regroupMinTicks,
    long regroupMaxTicks,
    long regroupStepTicks) {
  public RetreatSettings {
    SettingsChecks.requireBetween(
        "RetreatSettings.recoveryHealthFraction", recoveryHealthFraction, 0, 1);
    SettingsChecks.requirePositive(
        "RetreatSettings.healSafeDistanceBlocks", healSafeDistanceBlocks);
    SettingsChecks.requireAtLeast("RetreatSettings.regroupMinTicks", regroupMinTicks, 1);
    SettingsChecks.requireNotAbove(
        "RetreatSettings.regroupMinTicks",
        regroupMinTicks,
        "RetreatSettings.regroupMaxTicks",
        regroupMaxTicks);
    SettingsChecks.requireBetween(
        "RetreatSettings.regroupInitialTicks",
        regroupInitialTicks,
        regroupMinTicks,
        regroupMaxTicks);
    SettingsChecks.requireAtLeast("RetreatSettings.regroupStepTicks", regroupStepTicks, 1);
  }
}
