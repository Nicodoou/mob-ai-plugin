package io.github.nicodoou.mobai.domain.settings;

public record PersistenceSettings(long saveIntervalTicks) {
  public PersistenceSettings {
    SettingsChecks.requireAtLeast("PersistenceSettings.saveIntervalTicks", saveIntervalTicks, 1);
  }
}
