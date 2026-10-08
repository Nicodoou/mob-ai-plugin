package io.github.nicodoou.mobai.domain.settings;

import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import java.util.Objects;

public record SelectionSettings(
    SelectionPolicyType defaultPolicy,
    double memoryMultiplierMin,
    double memoryMultiplierMax,
    double epsilon,
    int exploreFirstAttempts) {
  public SelectionSettings {
    Objects.requireNonNull(defaultPolicy, "SelectionSettings.defaultPolicy");
    SettingsChecks.requirePositive("SelectionSettings.memoryMultiplierMin", memoryMultiplierMin);
    SettingsChecks.requirePositive("SelectionSettings.memoryMultiplierMax", memoryMultiplierMax);
    SettingsChecks.requireNotAbove(
        new NamedSetting("SelectionSettings.memoryMultiplierMin", memoryMultiplierMin),
        new NamedSetting("SelectionSettings.memoryMultiplierMax", memoryMultiplierMax));
    SettingsChecks.requireBetween(new NamedSetting("SelectionSettings.epsilon", epsilon), 0, 1);
    SettingsChecks.requireAtLeast(
        "SelectionSettings.exploreFirstAttempts", exploreFirstAttempts, 0);
  }
}
