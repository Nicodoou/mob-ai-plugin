package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyFactory;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.StrategyCatalog;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;
import io.github.nicodoou.mobai.domain.target.KillTimeEstimator;
import io.github.nicodoou.mobai.domain.target.SpiderTargetRule;
import io.github.nicodoou.mobai.domain.target.TargetSelector;
import java.util.Objects;
import java.util.function.Supplier;

/** The rule objects the brain coordinates; built once at startup. */
public record BrainParts(
    TargetSelector targetSelector,
    SpiderTargetRule spiderTargetRule,
    StrategyCatalog strategies,
    SelectionPolicyFactory policies,
    AttackSuggester attackSuggester,
    PlanEndDetector planEndDetector,
    RetreatRule retreatRule,
    RegroupRule regroupRule,
    RegroupWindow regroupWindow,
    RallyPointRule rallyPointRule,
    RecipePlanner recipePlanner,
    TraitLedger traitLedger) {
  public BrainParts {
    Objects.requireNonNull(targetSelector, "BrainParts.targetSelector");
    Objects.requireNonNull(spiderTargetRule, "BrainParts.spiderTargetRule");
    Objects.requireNonNull(strategies, "BrainParts.strategies");
    Objects.requireNonNull(policies, "BrainParts.policies");
    Objects.requireNonNull(attackSuggester, "BrainParts.attackSuggester");
    Objects.requireNonNull(planEndDetector, "BrainParts.planEndDetector");
    Objects.requireNonNull(retreatRule, "BrainParts.retreatRule");
    Objects.requireNonNull(regroupRule, "BrainParts.regroupRule");
    Objects.requireNonNull(regroupWindow, "BrainParts.regroupWindow");
    Objects.requireNonNull(rallyPointRule, "BrainParts.rallyPointRule");
    Objects.requireNonNull(recipePlanner, "BrainParts.recipePlanner");
    Objects.requireNonNull(traitLedger, "BrainParts.traitLedger");
  }

  /** The one way the plugin, the tests and incident replays assemble a brain. */
  public static BrainParts standard(
      Supplier<MobAiSettings> settings, RandomSource random, RegroupWindow regroupWindow) {
    return new BrainParts(
        new TargetSelector(
            () -> settings.get().target(),
            new KillTimeEstimator(() -> settings.get().target(), () -> settings.get().attack())),
        new SpiderTargetRule(() -> settings.get().target()),
        new StrategyCatalog(new CombatGeometry()),
        new SelectionPolicyFactory(() -> settings.get().selection(), random),
        new AttackSuggester(),
        new PlanEndDetector(() -> settings.get().plan()),
        new RetreatRule(() -> settings.get().plan(), () -> settings.get().retreat()),
        new RegroupRule(() -> settings.get().retreat(), regroupWindow),
        regroupWindow,
        new RallyPointRule(() -> settings.get().retreat()),
        new RecipePlanner(settings, random, new RecipeBase()),
        new TraitLedger(() -> settings.get().learning()));
  }
}
