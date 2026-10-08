package io.github.nicodoou.mobai.domain.settings;

public record RetreatSettings(
    double recoveryHealthFraction,
    double healSafeDistanceBlocks,
    long regroupInitialTicks,
    long regroupMinTicks,
    long regroupMaxTicks,
    long regroupStepTicks,
    double rallyDistanceBlocks,
    double rallyArrivalBlocks) {
  public RetreatSettings {
    SettingsChecks.requireBetween(
        new NamedSetting("RetreatSettings.recoveryHealthFraction", recoveryHealthFraction), 0, 1);
    SettingsChecks.requirePositive(
        "RetreatSettings.healSafeDistanceBlocks", healSafeDistanceBlocks);
    SettingsChecks.requireAtLeast("RetreatSettings.regroupMinTicks", regroupMinTicks, 1);
    SettingsChecks.requireNotAbove(
        new NamedSetting("RetreatSettings.regroupMinTicks", regroupMinTicks),
        new NamedSetting("RetreatSettings.regroupMaxTicks", regroupMaxTicks));
    SettingsChecks.requireBetween(
        new NamedSetting("RetreatSettings.regroupInitialTicks", regroupInitialTicks),
        regroupMinTicks,
        regroupMaxTicks);
    SettingsChecks.requireAtLeast("RetreatSettings.regroupStepTicks", regroupStepTicks, 1);
    SettingsChecks.requirePositive("RetreatSettings.rallyDistanceBlocks", rallyDistanceBlocks);
    SettingsChecks.requirePositive("RetreatSettings.rallyArrivalBlocks", rallyArrivalBlocks);
  }
}
