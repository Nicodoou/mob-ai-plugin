package io.github.nicodoou.mobai.domain.settings;

public record GroupSettings(
    int maxGroupSize, int decisionIntervalTicks, double detectionRadiusBlocks) {
  public GroupSettings {
    SettingsChecks.requireAtLeast("GroupSettings.maxGroupSize", maxGroupSize, 1);
    SettingsChecks.requireAtLeast("GroupSettings.decisionIntervalTicks", decisionIntervalTicks, 1);
    SettingsChecks.requirePositive("GroupSettings.detectionRadiusBlocks", detectionRadiusBlocks);
  }
}
