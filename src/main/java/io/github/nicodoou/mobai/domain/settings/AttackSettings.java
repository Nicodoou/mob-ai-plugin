package io.github.nicodoou.mobai.domain.settings;

public record AttackSettings(
    long projectileTimeoutTicks,
    long patientStrikeMaxWaitTicks,
    long opportunisticShotMaxWaitTicks,
    double shootMinDistanceBlocks,
    double shootMaxDistanceBlocks,
    double flankDistanceBlocks,
    double retreatDistanceBlocks) {
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
    SettingsChecks.requireNotAbove(
        "AttackSettings.shootMinDistanceBlocks",
        shootMinDistanceBlocks,
        "AttackSettings.shootMaxDistanceBlocks",
        shootMaxDistanceBlocks);
  }
}
