package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.Objects;

public record GroupKnowledge(GroupMemory memory, ThreatLedger threat) {
  public GroupKnowledge {
    Objects.requireNonNull(memory, "GroupKnowledge.memory");
    Objects.requireNonNull(threat, "GroupKnowledge.threat");
  }
}
