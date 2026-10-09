package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.application.GroupCaptureMapper;
import io.github.nicodoou.mobai.application.IncidentFailure;
import io.github.nicodoou.mobai.application.IncidentLocation;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.application.TraitCaptureMapper;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.Objects;
import java.util.Optional;

/** Watches each decision: copies the group before it and turns a failure into an incident. */
public final class DecisionWitness {
  private static final IncidentLocation LOCATION =
      new IncidentLocation("domain", "Brain", "decide", "TickGroups");

  private final WitnessParts parts;
  private final TraceHub hub;
  private final IncidentWriter writer;
  private final GroupCaptureMapper mapper = new GroupCaptureMapper();
  private final TraitCaptureMapper traits = new TraitCaptureMapper();

  public DecisionWitness(WitnessParts parts, TraceHub hub, IncidentWriter writer) {
    this.parts = Objects.requireNonNull(parts, "DecisionWitness.parts");
    this.hub = Objects.requireNonNull(hub, "DecisionWitness.hub");
    this.writer = Objects.requireNonNull(writer, "DecisionWitness.writer");
  }

  // Pending events go out first so the copy holds nothing the replay would not generate.
  public Observation before(Group group, GroupSnapshot snapshot) {
    parts.groupEvents().publishPending(group);
    parts.draws().clear();
    return new Observation(
        snapshot,
        mapper.capture(group),
        parts.regroupWindow().currentTicks(),
        traits.forSnapshot(parts.traitLedger(), snapshot),
        parts.recipeBase().capture());
  }

  public void succeeded(Group group, Observation observation, BrainResult result) {
    hub.decided(group.id(), observation.snapshot().tick(), result);
  }

  public IncidentReport failed(Group group, Observation observation, RuntimeException failure) {
    IncidentReport report = incident(group, observation, failure);
    writer.write(report, hub.recent(group.id()));
    return report;
  }

  private IncidentReport incident(Group group, Observation observation, RuntimeException failure) {
    GroupSnapshot snapshot = observation.snapshot();
    return new IncidentReport(
        group.id().shortId() + "-" + snapshot.tick(),
        snapshot.tick(),
        LOCATION,
        Optional.of(IncidentFailure.of(failure)),
        observation.before(),
        observation.regroupWindowTicks(),
        snapshot,
        parts.settings().current(),
        parts.draws().draws(),
        Optional.empty(),
        mapper.capture(group),
        parts.regroupWindow().currentTicks(),
        observation.traitsBefore(),
        traits.forSnapshot(parts.traitLedger(), snapshot),
        observation.base());
  }
}
