package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.Locale;
import java.util.Objects;

/**
 * One readable line per closed plan and per attack, always on: the MVP is validated by counting
 * them.
 */
public final class DebugLog {
  private final LineFileWriter lines;

  public DebugLog(LineFileWriter lines) {
    this.lines = Objects.requireNonNull(lines, "DebugLog.lines");
  }

  public void record(TraceEvent event) {
    Objects.requireNonNull(event, "DebugLog.event");
    switch (event) {
      case TraceEvent.PlanEvent plan -> lines.append(planLine(plan));
      case TraceEvent.AttackEvent attack -> lines.append(attackLine(attack));
      case TraceEvent.DecisionEvent decision -> {}
    }
  }

  public void shutdown() {
    lines.shutdown();
  }

  static String planLine(TraceEvent.PlanEvent event) {
    return strategyPart(event) + event.plan().recipe().map(DebugLog::recipePart).orElse("");
  }

  private static String strategyPart(TraceEvent.PlanEvent event) {
    ClosedPlan plan = event.plan();
    return String.format(
        Locale.ROOT,
        "PLAN tick=%d group=%s plan=%d strategy=%s target=%s reason=%s success=%.2f"
            + " scores=%.2f/%.2f/%.2f danger=%.2f damage=%.1f",
        event.tick(),
        event.group().shortId(),
        plan.id().sequence(),
        plan.strategy().value(),
        plan.target().shortId(),
        plan.reason(),
        plan.success(),
        plan.scores().damage(),
        plan.scores().speed(),
        plan.scores().survival(),
        plan.danger(),
        plan.damageDealt());
  }

  private static String recipePart(RecipePlay play) {
    PlanRecipe recipe = play.recipe();
    PlayerTraits traits = play.traits();
    return String.format(
        Locale.ROOT,
        " recipe=z%d/%d/%d s%d/%d volley=%s delay=%d retreat=%.2f traits=%.2f/%.2f/%.2f",
        recipe.zombies().press(),
        recipe.zombies().flank(),
        recipe.zombies().reserve(),
        recipe.spiders().press(),
        recipe.spiders().flank(),
        recipe.volley(),
        recipe.reserveDelayTicks(),
        recipe.retreatHealthFraction(),
        traits.shield(),
        traits.ranged(),
        traits.armor());
  }

  static String attackLine(TraceEvent.AttackEvent event) {
    AttackFacts facts = event.facts();
    return String.format(
        Locale.ROOT,
        "ATTACK tick=%d group=%s mob=%s attack=%s target=%s outcome=%s rule=%d damage=%.1f",
        event.tick(),
        event.group().shortId(),
        facts.mob().shortId(),
        facts.attack().id(),
        facts.target().shortId(),
        event.outcome(),
        event.rule(),
        facts.realDamage());
  }
}
