package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.adapter.snapshot.SnapshotFactory;
import io.github.nicodoou.mobai.application.TickGroups;
import java.util.Objects;

/** What deciding for one group needs: its snapshot, the use case and where the orders go. */
public record DecisionParts(
    SnapshotFactory snapshots, TickGroups tickGroups, DecisionApplier applier) {
  public DecisionParts {
    Objects.requireNonNull(snapshots, "DecisionParts.snapshots");
    Objects.requireNonNull(tickGroups, "DecisionParts.tickGroups");
    Objects.requireNonNull(applier, "DecisionParts.applier");
  }
}
