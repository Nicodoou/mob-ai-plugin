package io.github.nicodoou.mobai.domain.settings;

/** How far ahead of the rest a melee fighter may run before it waits for them (CT-32). */
public record PackSettings(double maxSpreadBlocks, double engagedDistanceBlocks) {
  public PackSettings {
    SettingsChecks.requirePositive("PackSettings.maxSpreadBlocks", maxSpreadBlocks);
    SettingsChecks.requirePositive("PackSettings.engagedDistanceBlocks", engagedDistanceBlocks);
  }
}
