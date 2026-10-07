package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
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
    ClosedPlan plan = event.plan();
    return String.format(
        Locale.ROOT,
        "PLAN tick=%d group=%s plan=%d strategy=%s target=%s reason=%s success=%.2f damage=%.1f",
        event.tick(),
        event.group().shortId(),
        plan.id().sequence(),
        plan.strategy().value(),
        plan.target().shortId(),
        plan.reason(),
        plan.success(),
        plan.damageDealt());
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
