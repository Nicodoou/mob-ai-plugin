package io.github.nicodoou.mobai.domain.settings;

import java.util.Objects;

public record MobAiSettings(
    GroupSettings group,
    MemorySettings memory,
    SelectionSettings selection,
    TargetSettings target,
    PlanSettings plan,
    AttackSettings attack,
    SpiderSettings spider,
    PersistenceSettings persistence,
    DebugSettings debug,
    RetreatSettings retreat,
    VolleySettings volley,
    SuccessSettings success,
    LearningSettings learning) {
  public MobAiSettings {
    Objects.requireNonNull(group, "MobAiSettings.group");
    Objects.requireNonNull(memory, "MobAiSettings.memory");
    Objects.requireNonNull(selection, "MobAiSettings.selection");
    Objects.requireNonNull(target, "MobAiSettings.target");
    Objects.requireNonNull(plan, "MobAiSettings.plan");
    Objects.requireNonNull(attack, "MobAiSettings.attack");
    Objects.requireNonNull(spider, "MobAiSettings.spider");
    Objects.requireNonNull(persistence, "MobAiSettings.persistence");
    Objects.requireNonNull(debug, "MobAiSettings.debug");
    Objects.requireNonNull(retreat, "MobAiSettings.retreat");
    Objects.requireNonNull(volley, "MobAiSettings.volley");
    Objects.requireNonNull(success, "MobAiSettings.success");
    Objects.requireNonNull(learning, "MobAiSettings.learning");
    requireRecoveryAboveRetreat(plan, retreat);
    requireRecoveryAboveLearnedRetreat(learning, retreat);
  }

  // Without this margin a mob would change role on every decision.
  private static void requireRecoveryAboveRetreat(PlanSettings plan, RetreatSettings retreat) {
    if (retreat.recoveryHealthFraction() <= plan.retreatHealthFraction()) {
      throw new IllegalArgumentException(
          "RetreatSettings.recoveryHealthFraction must exceed PlanSettings.retreatHealthFraction, got "
              + retreat.recoveryHealthFraction()
              + " <= "
              + plan.retreatHealthFraction());
    }
  }

  // A recipe could otherwise retreat a mob at the same health that sends it back to fight.
  private static void requireRecoveryAboveLearnedRetreat(
      LearningSettings learning, RetreatSettings retreat) {
    if (learning.maxRetreatHealthFraction() >= retreat.recoveryHealthFraction()) {
      throw new IllegalArgumentException(
          "LearningSettings.maxRetreatHealthFraction must be below RetreatSettings.recoveryHealthFraction, got "
              + learning.maxRetreatHealthFraction()
              + " >= "
              + retreat.recoveryHealthFraction());
    }
  }
}
