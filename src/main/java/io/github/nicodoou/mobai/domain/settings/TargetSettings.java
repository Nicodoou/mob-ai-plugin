package io.github.nicodoou.mobai.domain.settings;

public record TargetSettings(
    long threatWindowTicks,
    double commitmentBonus,
    double baseThreat,
    double approachSpeedBlocksPerSecond,
    double weaknessThreatMultiplierPerLevel) {
  public TargetSettings {
    SettingsChecks.requireAtLeast("TargetSettings.threatWindowTicks", threatWindowTicks, 1);
    SettingsChecks.requireNonNegative("TargetSettings.commitmentBonus", commitmentBonus);
    SettingsChecks.requirePositive("TargetSettings.baseThreat", baseThreat);
    SettingsChecks.requirePositive(
        "TargetSettings.approachSpeedBlocksPerSecond", approachSpeedBlocksPerSecond);
    SettingsChecks.requirePositive(
        "TargetSettings.weaknessThreatMultiplierPerLevel", weaknessThreatMultiplierPerLevel);
    SettingsChecks.requireBetween(
        "TargetSettings.weaknessThreatMultiplierPerLevel", weaknessThreatMultiplierPerLevel, 0, 1);
  }
}
