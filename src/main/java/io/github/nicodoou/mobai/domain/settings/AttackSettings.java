package io.github.nicodoou.mobai.domain.settings;

public record AttackSettings(
    long projectileTimeoutTicks,
    long patientStrikeMaxWaitTicks,
    long opportunisticShotMaxWaitTicks,
    double shootMinDistanceBlocks,
    double shootMaxDistanceBlocks,
    double flankMarginBlocks,
    double retreatDistanceBlocks,
    double evasiveChargeThreshold,
    double evasiveMarginBlocks) {
  public AttackSettings {
    SettingsChecks.requireAtLeast(
        "AttackSettings.projectileTimeoutTicks", projectileTimeoutTicks, 1);
    SettingsChecks.requireAtLeast(
        "AttackSettings.patientStrikeMaxWaitTicks", patientStrikeMaxWaitTicks, 1);
    SettingsChecks.requireAtLeast(
        "AttackSettings.opportunisticShotMaxWaitTicks", opportunisticShotMaxWaitTicks, 1);
    SettingsChecks.requirePositive("AttackSettings.shootMinDistanceBlocks", shootMinDistanceBlocks);
    SettingsChecks.requirePositive("AttackSettings.shootMaxDistanceBlocks", shootMaxDistanceBlocks);
    SettingsChecks.requireNonNegative("AttackSettings.flankMarginBlocks", flankMarginBlocks);
    SettingsChecks.requirePositive("AttackSettings.retreatDistanceBlocks", retreatDistanceBlocks);
    SettingsChecks.requireBetween(
        "AttackSettings.evasiveChargeThreshold", evasiveChargeThreshold, 0, 1);
    SettingsChecks.requireNonNegative("AttackSettings.evasiveMarginBlocks", evasiveMarginBlocks);
    SettingsChecks.requireNotAbove(
        "AttackSettings.shootMinDistanceBlocks",
        shootMinDistanceBlocks,
        "AttackSettings.shootMaxDistanceBlocks",
        shootMaxDistanceBlocks);
  }
}
