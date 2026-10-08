package io.github.nicodoou.mobai.domain.settings;

public record VolleySettings(
    long pressTicks, long fallBackTicks, long fireTicks, double fallBackMarginBlocks) {
  public VolleySettings {
    SettingsChecks.requireAtLeast("VolleySettings.pressTicks", pressTicks, 1);
    SettingsChecks.requireAtLeast("VolleySettings.fallBackTicks", fallBackTicks, 1);
    SettingsChecks.requireAtLeast("VolleySettings.fireTicks", fireTicks, 1);
    SettingsChecks.requireNonNegative("VolleySettings.fallBackMarginBlocks", fallBackMarginBlocks);
  }
}
