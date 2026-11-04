package io.github.nicodoou.mobai.domain.settings;

import io.github.nicodoou.mobai.domain.shared.NetworkName;
import java.util.Objects;

/**
 * @param defaultNetwork the network of a group without one of its own, when the planner is NEURAL
 */
public record NeuralSettings(
    boolean experienceEnabled,
    String defaultNetwork,
    long policyCheckIntervalTicks,
    ArenaSettings arena) {
  public NeuralSettings {
    NetworkName.requireValid(defaultNetwork);
    SettingsChecks.requireAtLeast(
        "NeuralSettings.policyCheckIntervalTicks", policyCheckIntervalTicks, 1);
    Objects.requireNonNull(arena, "NeuralSettings.arena");
  }
}
