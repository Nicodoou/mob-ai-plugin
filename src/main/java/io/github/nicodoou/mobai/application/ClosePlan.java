package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.DangerObservation;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.strategy.RecipeOutcome;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.Objects;
import java.util.Optional;

public final class ClosePlan {
  // The group's own plan counts in full; observers (phase 2) will count less.
  private static final double OWN_PLAN_WEIGHT = 1.0;

  private final ActiveGroups activeGroups;
  private final RecipePlanner recipePlanner;

  public ClosePlan(ActiveGroups activeGroups, RecipePlanner recipePlanner) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "ClosePlan.activeGroups");
    this.recipePlanner = Objects.requireNonNull(recipePlanner, "ClosePlan.recipePlanner");
  }

  public Optional<RecordChange> execute(PlanClosed event) {
    return activeGroups.group(event.groupId()).flatMap(group -> record(group, event.plan()));
  }

  // Recipe plans teach only the recipe model, so the two planners can be compared side by side.
  private Optional<RecordChange> record(Group group, ClosedPlan plan) {
    Optional<RecordChange> change = Optional.empty();
    if (plan.recipe().isPresent()) {
      learnRecipe(group, plan);
    } else {
      change = Optional.of(recordStrategy(group, plan));
    }
    recordDanger(group, plan);
    return change;
  }

  private void learnRecipe(Group group, ClosedPlan plan) {
    RecipePlay play = plan.recipe().orElseThrow();
    LinearPosterior current =
        recipePlanner.current(group.memory().recipeModel(plan.target()), plan.endTick());
    group
        .memory()
        .storeRecipeModel(
            plan.target(),
            recipePlanner.learned(
                current, new RecipeOutcome(play, plan.success(), plan.endTick())));
  }

  private static RecordChange recordStrategy(Group group, ClosedPlan plan) {
    return group
        .memory()
        .recordStrategy(
            new StrategyObservation(
                plan.target(), plan.strategy(), plan.success(), OWN_PLAN_WEIGHT, plan.endTick()));
  }

  private static void recordDanger(Group group, ClosedPlan plan) {
    group
        .memory()
        .recordDanger(
            new DangerObservation(
                plan.target(), plan.groupHealthLost(), plan.damageDealt(), plan.endTick()));
  }
}
