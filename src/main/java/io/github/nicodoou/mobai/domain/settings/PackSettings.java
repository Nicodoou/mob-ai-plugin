package io.github.nicodoou.mobai.domain.settings;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;

/** How melee fighters keep together while they chase and how they encircle the target (CT-32). */
public record PackSettings(
    double maxSpreadBlocks,
    double engagedDistanceBlocks,
    double surroundMarginBlocks,
    double waitMarginBlocks,
    double minChaseBlocks,
    double maxChaseBlocks,
    double encircleStrikeRadiusBlocks,
    double encircleSeatSpacingBlocks,
    double encircleCrowdingBlocks) {
  public PackSettings {
    SettingsChecks.requirePositive("PackSettings.maxSpreadBlocks", maxSpreadBlocks);
    SettingsChecks.requirePositive("PackSettings.engagedDistanceBlocks", engagedDistanceBlocks);
    SettingsChecks.requireNonNegative("PackSettings.surroundMarginBlocks", surroundMarginBlocks);
    SettingsChecks.requireNonNegative("PackSettings.waitMarginBlocks", waitMarginBlocks);
    SettingsChecks.requirePositive("PackSettings.minChaseBlocks", minChaseBlocks);
    SettingsChecks.requirePositive("PackSettings.maxChaseBlocks", maxChaseBlocks);
    SettingsChecks.requireNotAbove(
        new NamedSetting("PackSettings.minChaseBlocks", minChaseBlocks),
        new NamedSetting("PackSettings.maxChaseBlocks", maxChaseBlocks));
    SettingsChecks.requirePositive(
        "PackSettings.encircleStrikeRadiusBlocks", encircleStrikeRadiusBlocks);
    SettingsChecks.requirePositive(
        "PackSettings.encircleSeatSpacingBlocks", encircleSeatSpacingBlocks);
    SettingsChecks.requirePositive("PackSettings.encircleCrowdingBlocks", encircleCrowdingBlocks);
    SettingsChecks.requireNotAbove(
        new NamedSetting("PackSettings.encircleStrikeRadiusBlocks", encircleStrikeRadiusBlocks),
        new NamedSetting("the melee reach", MinecraftConstants.MELEE_REACH_BLOCKS));
    SettingsChecks.requireNotAbove(
        new NamedSetting("PackSettings.encircleSeatSpacingBlocks", encircleSeatSpacingBlocks),
        new NamedSetting("the ring's diameter", 2 * encircleStrikeRadiusBlocks));
  }
}
