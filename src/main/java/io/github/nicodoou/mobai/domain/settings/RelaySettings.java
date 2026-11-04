package io.github.nicodoou.mobai.domain.settings;

public record RelaySettings(
    long arrowMarginTicks,
    long arrowSlackTicks,
    long landingGraceTicks,
    long maxShotHoldTicks,
    long readyStaleTicks) {
  // Paper calls a running goal every other tick, so a mob still waiting renews itself this often.
  private static final long MIN_READY_STALE_TICKS = 2;

  public RelaySettings {
    SettingsChecks.requireAtLeast("RelaySettings.arrowMarginTicks", arrowMarginTicks, 0);
    SettingsChecks.requireAtLeast("RelaySettings.arrowSlackTicks", arrowSlackTicks, 1);
    SettingsChecks.requireAtLeast("RelaySettings.landingGraceTicks", landingGraceTicks, 0);
    SettingsChecks.requireAtLeast("RelaySettings.maxShotHoldTicks", maxShotHoldTicks, 1);
    SettingsChecks.requireAtLeast(
        "RelaySettings.readyStaleTicks", readyStaleTicks, MIN_READY_STALE_TICKS);
  }
}
