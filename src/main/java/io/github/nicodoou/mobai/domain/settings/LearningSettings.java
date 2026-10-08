package io.github.nicodoou.mobai.domain.settings;

import java.util.Objects;

/** The recipe learning of CT-30: who plans, the model, the recipe ranges and the trait memory. */
public record LearningSettings(
    PlannerKind planner,
    double modelNoiseVariance,
    double priorVariance,
    double priorSuccess,
    double explorationScale,
    double trainingExplorationScale,
    long minReserveDelayTicks,
    long maxReserveDelayTicks,
    double maxRetreatHealthFraction,
    long traitsHalfLifeTicks) {
  public LearningSettings {
    Objects.requireNonNull(planner, "LearningSettings.planner");
    SettingsChecks.requirePositive("LearningSettings.modelNoiseVariance", modelNoiseVariance);
    SettingsChecks.requirePositive("LearningSettings.priorVariance", priorVariance);
    SettingsChecks.requireBetween(
        new NamedSetting("LearningSettings.priorSuccess", priorSuccess), 0, 1);
    SettingsChecks.requirePositive("LearningSettings.explorationScale", explorationScale);
    SettingsChecks.requirePositive(
        "LearningSettings.trainingExplorationScale", trainingExplorationScale);
    requireReserveDelayRange(minReserveDelayTicks, maxReserveDelayTicks);
    requireRetreatFractionInsideUnit(maxRetreatHealthFraction);
    SettingsChecks.requireAtLeast("LearningSettings.traitsHalfLifeTicks", traitsHalfLifeTicks, 1);
  }

  private static void requireReserveDelayRange(long min, long max) {
    SettingsChecks.requireAtLeast("LearningSettings.minReserveDelayTicks", min, 1);
    SettingsChecks.requireNotAbove(
        new NamedSetting("LearningSettings.minReserveDelayTicks", min),
        new NamedSetting("LearningSettings.maxReserveDelayTicks", max));
    if (min == max) {
      throw new IllegalArgumentException(
          "LearningSettings.maxReserveDelayTicks must exceed LearningSettings.minReserveDelayTicks, got "
              + max
              + " <= "
              + min);
    }
  }

  private static void requireRetreatFractionInsideUnit(double fraction) {
    SettingsChecks.requireBetween(
        new NamedSetting("LearningSettings.maxRetreatHealthFraction", fraction), 0, 1);
    if (fraction == 0 || fraction == 1) {
      throw new IllegalArgumentException(
          "LearningSettings.maxRetreatHealthFraction must be strictly between 0 and 1, got "
              + fraction);
    }
  }
}
