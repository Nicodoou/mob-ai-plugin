package io.github.nicodoou.mobai.domain.settings;

public record PressureSettings(
    double engageDistanceBlocks,
    double reserveDelayFraction,
    long volleySyncHoldTicks,
    long blockedLaneWaitTicks) {
  public PressureSettings {
    SettingsChecks.requirePositive("PressureSettings.engageDistanceBlocks", engageDistanceBlocks);
    SettingsChecks.requireBetween(
        new NamedSetting("PressureSettings.reserveDelayFraction", reserveDelayFraction), 0, 1);
    SettingsChecks.requireAtLeast("PressureSettings.volleySyncHoldTicks", volleySyncHoldTicks, 0);
    SettingsChecks.requireAtLeast("PressureSettings.blockedLaneWaitTicks", blockedLaneWaitTicks, 0);
  }
}
