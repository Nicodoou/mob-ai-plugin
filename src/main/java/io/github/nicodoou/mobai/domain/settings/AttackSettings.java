package io.github.nicodoou.mobai.domain.settings;

public record AttackSettings(
    long projectileTimeoutTicks,
    long patientStrikeMaxWaitTicks,
    long opportunisticShotMaxWaitTicks,
    double shootMinDistanceBlocks,
    double shootMaxDistanceBlocks,
    double flankDistanceBlocks,
    double retreatDistanceBlocks,
    double evasiveChargeThreshold,
    double evasiveDistanceBlocks) {
  public AttackSettings {
    SettingsChecks.requireAtLeast(
        "AttackSettings.projectileTimeoutTicks", projectileTimeoutTicks, 1);
    SettingsChecks.requireAtLeast(
        "AttackSettings.patientStrikeMaxWaitTicks", patientStrikeMaxWaitTicks, 1);
    SettingsChecks.requireAtLeast(
        "AttackSettings.opportunisticShotMaxWaitTicks", opportunisticShotMaxWaitTicks, 1);
    SettingsChecks.requirePositive("AttackSettings.shootMinDistanceBlocks", shootMinDistanceBlocks);
    SettingsChecks.requirePositive("AttackSettings.shootMaxDistanceBlocks", shootMaxDistanceBlocks);
    SettingsChecks.requirePositive("AttackSettings.flankDistanceBlocks", flankDistanceBlocks);
    SettingsChecks.requirePositive("AttackSettings.retreatDistanceBlocks", retreatDistanceBlocks);
    SettingsChecks.requireBetween(
        "AttackSettings.evasiveChargeThreshold", evasiveChargeThreshold, 0, 1);
    SettingsChecks.requirePositive("AttackSettings.evasiveDistanceBlocks", evasiveDistanceBlocks);
    SettingsChecks.requireNotAbove(
        "AttackSettings.shootMinDistanceBlocks",
        shootMinDistanceBlocks,
        "AttackSettings.shootMaxDistanceBlocks",
        shootMaxDistanceBlocks);
  }
}
