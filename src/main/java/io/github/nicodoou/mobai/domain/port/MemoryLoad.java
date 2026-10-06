package io.github.nicodoou.mobai.domain.port;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MemoryLoad(
    Optional<StoredState> state, List<StoredGroup> groups, List<String> quarantinedFiles) {
  public MemoryLoad {
    Objects.requireNonNull(state, "MemoryLoad.state");
    groups = List.copyOf(groups);
    quarantinedFiles = List.copyOf(quarantinedFiles);
  }

  public static MemoryLoad empty() {
    return new MemoryLoad(Optional.empty(), List.of(), List.of());
  }
}
