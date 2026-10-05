package io.github.nicodoou.mobai.domain.target;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.Objects;
import java.util.Optional;

/** Everything the selector reads for one group decision. */
public record TargetQuery(
    GroupSnapshot snapshot,
    GroupMemory memory,
    ThreatLedger threat,
    Optional<PlayerId> committedTarget) {
  public TargetQuery {
    Objects.requireNonNull(snapshot, "TargetQuery.snapshot");
    Objects.requireNonNull(memory, "TargetQuery.memory");
    Objects.requireNonNull(threat, "TargetQuery.threat");
    Objects.requireNonNull(committedTarget, "TargetQuery.committedTarget");
  }
}
