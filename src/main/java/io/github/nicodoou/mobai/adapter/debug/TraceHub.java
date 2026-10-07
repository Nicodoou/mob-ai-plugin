package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.List;
import java.util.Objects;

/** The single entry for trace events; later stages add more destinations here (WP-29B). */
public final class TraceHub {
  private final ActiveGroups activeGroups;
  private final FlightRecorder recorder;

  public TraceHub(ActiveGroups activeGroups, FlightRecorder recorder) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "TraceHub.activeGroups");
    this.recorder = Objects.requireNonNull(recorder, "TraceHub.recorder");
  }

  public void decided(GroupId group, long tick, BrainResult result) {
    recorder.record(new TraceEvent.DecisionEvent(group, tick, result.decision(), result.trace()));
  }

  public void attacked(MobId mob, long tick, Classification classification) {
    activeGroups
        .groupOf(mob)
        .ifPresent(group -> recorder.record(attackEvent(group.id(), tick, classification)));
  }

  public void planClosed(PlanClosed event) {
    recorder.record(new TraceEvent.PlanEvent(event.groupId(), event.tick(), event.plan()));
  }

  public List<TraceEvent> recent(GroupId group) {
    return recorder.recent(group);
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
