package io.github.nicodoou.mobai.domain.settings;

public record AttackSettings(
    long projectileTimeoutTicks,
    long patientStrikeMaxWaitTicks,
    long opportunisticShotMaxWaitTicks,
    double shootMinDistanceBlocks,
    double shootMaxDistanceBlocks,
    double flankMarginBlocks,
    double retreatDistanceBlocks,
    double evasiveMarginBlocks,
    long evasiveSafetyTicks,
    double evasiveAimMarginDegrees) {
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
    SettingsChecks.requireNonNegative("AttackSettings.evasiveMarginBlocks", evasiveMarginBlocks);
    SettingsChecks.requireAtLeast("AttackSettings.evasiveSafetyTicks", evasiveSafetyTicks, 0);
    SettingsChecks.requireBetween(
        new NamedSetting("AttackSettings.evasiveAimMarginDegrees", evasiveAimMarginDegrees), 0, 90);
    SettingsChecks.requireNotAbove(
        new NamedSetting("AttackSettings.shootMinDistanceBlocks", shootMinDistanceBlocks),
        new NamedSetting("AttackSettings.shootMaxDistanceBlocks", shootMaxDistanceBlocks));
  }
}
