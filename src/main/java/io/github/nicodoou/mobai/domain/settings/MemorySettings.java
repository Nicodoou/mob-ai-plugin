package io.github.nicodoou.mobai.domain.settings;

public record MemorySettings(long halfLifeTicks, double learningSpeed, double partialHitWeight) {
  public MemorySettings {
    SettingsChecks.requireAtLeast("MemorySettings.halfLifeTicks", halfLifeTicks, 1);
    SettingsChecks.requireBetween("MemorySettings.learningSpeed", learningSpeed, 0, 1);
    SettingsChecks.requireBetween("MemorySettings.partialHitWeight", partialHitWeight, 0, 1);
  }
}
