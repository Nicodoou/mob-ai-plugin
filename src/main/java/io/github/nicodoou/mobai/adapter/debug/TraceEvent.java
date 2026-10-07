package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.DecisionTrace;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;

/** One entry of a group's trace: what the brain decided, how an attack went, how a plan closed. */
public sealed interface TraceEvent {
  GroupId group();

  long tick();

  record DecisionEvent(GroupId group, long tick, GroupDecision decision, DecisionTrace trace)
      implements TraceEvent {
    public DecisionEvent {
      Objects.requireNonNull(group, "DecisionEvent.group");
      Objects.requireNonNull(decision, "DecisionEvent.decision");
      Objects.requireNonNull(trace, "DecisionEvent.trace");
    }
  }

  record AttackEvent(GroupId group, long tick, String outcome, int rule, AttackFacts facts)
      implements TraceEvent {
    public AttackEvent {
      Objects.requireNonNull(group, "AttackEvent.group");
      Objects.requireNonNull(outcome, "AttackEvent.outcome");
      Objects.requireNonNull(facts, "AttackEvent.facts");
    }
  }

  record PlanEvent(GroupId group, long tick, ClosedPlan plan) implements TraceEvent {
    public PlanEvent {
      Objects.requireNonNull(group, "PlanEvent.group");
      Objects.requireNonNull(plan, "PlanEvent.plan");
    }
  }

  static String outcomeLabel(AttackOutcome outcome) {
    return switch (outcome) {
      case AttackOutcome.Hit hit -> "HIT";
      case AttackOutcome.Partial partial -> "PARTIAL";
      case AttackOutcome.Miss miss -> "MISS";
      case AttackOutcome.Neutral neutral -> "NEUTRAL:" + neutral.cause();
    };
  }
}
