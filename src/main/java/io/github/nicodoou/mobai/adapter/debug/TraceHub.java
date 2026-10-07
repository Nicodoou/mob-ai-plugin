package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.List;
import java.util.Objects;

/** The single entry for trace events: each one is sent to every destination from one place. */
public final class TraceHub {
  private final ActiveGroups activeGroups;
  private final TraceDestinations destinations;

  public TraceHub(ActiveGroups activeGroups, TraceDestinations destinations) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "TraceHub.activeGroups");
    this.destinations = Objects.requireNonNull(destinations, "TraceHub.destinations");
  }

  public void decided(GroupId group, long tick, BrainResult result) {
    route(new TraceEvent.DecisionEvent(group, tick, result.decision(), result.trace()));
  }

  public void attacked(MobId mob, long tick, Classification classification) {
    activeGroups
        .groupOf(mob)
        .ifPresent(group -> route(attackEvent(group.id(), tick, classification)));
  }

  public void planClosed(PlanClosed event) {
    route(new TraceEvent.PlanEvent(event.groupId(), event.tick(), event.plan()));
  }

  public List<TraceEvent> recent(GroupId group) {
    return destinations.recorder().recent(group);
  }

  private void route(TraceEvent event) {
    destinations.recorder().record(event);
    if (destinations.levels().writes(event)) {
      destinations.writer().write(event);
    }
    destinations.debugLog().record(event);
  }

  private static TraceEvent attackEvent(GroupId group, long tick, Classification classification) {
    return new TraceEvent.AttackEvent(
        group,
        tick,
        TraceEvent.outcomeLabel(classification.outcome()),
        classification.trace().rule(),
        classification.trace().facts());
  }
}
