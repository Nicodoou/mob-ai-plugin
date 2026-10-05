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
    RetreatSettings retreat) {
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
    requireRecoveryAboveRetreat(plan, retreat);
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
}
