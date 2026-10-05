package io.github.nicodoou.mobai.domain.settings;

public record PlanSettings(
    long maxPlanDurationTicks,
    double targetLostDistanceBlocks,
    long targetLostTicks,
    double retreatHealthFraction,
    double fullSuccessDamageFraction) {
  public PlanSettings {
    SettingsChecks.requireAtLeast("PlanSettings.maxPlanDurationTicks", maxPlanDurationTicks, 1);
    SettingsChecks.requirePositive(
        "PlanSettings.targetLostDistanceBlocks", targetLostDistanceBlocks);
    SettingsChecks.requireAtLeast("PlanSettings.targetLostTicks", targetLostTicks, 1);
    SettingsChecks.requireBetween(
        "PlanSettings.retreatHealthFraction", retreatHealthFraction, 0, 1);
    SettingsChecks.requirePositive(
        "PlanSettings.fullSuccessDamageFraction", fullSuccessDamageFraction);
    SettingsChecks.requireBetween(
        "PlanSettings.fullSuccessDamageFraction", fullSuccessDamageFraction, 0, 1);
  }
}
