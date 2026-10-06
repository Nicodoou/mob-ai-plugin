package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LoadReport(
    Optional<StoredState> state,
    List<GroupId> loadedGroups,
    List<GroupId> skippedGroups,
    List<String> quarantinedFiles) {
  public LoadReport {
    Objects.requireNonNull(state, "LoadReport.state");
    loadedGroups = List.copyOf(loadedGroups);
    skippedGroups = List.copyOf(skippedGroups);
    quarantinedFiles = List.copyOf(quarantinedFiles);
  }
}
