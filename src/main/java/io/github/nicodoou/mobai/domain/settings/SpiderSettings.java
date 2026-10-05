package io.github.nicodoou.mobai.domain.settings;

public record SpiderSettings(long slownessDurationTicks, int slownessLevel) {
  public SpiderSettings {
    SettingsChecks.requireAtLeast("SpiderSettings.slownessDurationTicks", slownessDurationTicks, 1);
    SettingsChecks.requireAtLeast("SpiderSettings.slownessLevel", slownessLevel, 1);
  }
}
