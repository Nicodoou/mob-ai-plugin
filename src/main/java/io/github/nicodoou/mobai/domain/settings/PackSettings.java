package io.github.nicodoou.mobai.domain.settings;

/**
 * How melee fighters keep together while they chase and how zombies surround the target (CT-32).
 */
public record PackSettings(
    double maxSpreadBlocks,
    double engagedDistanceBlocks,
    double surroundMarginBlocks,
    double waitMarginBlocks) {
  public PackSettings {
    SettingsChecks.requirePositive("PackSettings.maxSpreadBlocks", maxSpreadBlocks);
    SettingsChecks.requirePositive("PackSettings.engagedDistanceBlocks", engagedDistanceBlocks);
    SettingsChecks.requireNonNegative("PackSettings.surroundMarginBlocks", surroundMarginBlocks);
    SettingsChecks.requireNonNegative("PackSettings.waitMarginBlocks", waitMarginBlocks);
  }
}
