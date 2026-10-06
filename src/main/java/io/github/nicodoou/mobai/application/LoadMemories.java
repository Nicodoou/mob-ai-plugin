package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LoadMemories {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final MemoryRepository repository;
  private final StoredMemoriesMapper mapper = new StoredMemoriesMapper();

  public LoadMemories(
      ActiveGroups activeGroups, SettingsHolder settings, MemoryRepository repository) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "LoadMemories.activeGroups");
    this.settings = Objects.requireNonNull(settings, "LoadMemories.settings");
    this.repository = Objects.requireNonNull(repository, "LoadMemories.repository");
  }

  public LoadReport execute() {
    requireNoActiveGroups();
    MemoryLoad load = repository.load();
    List<GroupId> loaded = new ArrayList<>();
    List<GroupId> skipped = new ArrayList<>();
    for (StoredGroup stored : load.groups()) {
      (restore(stored) ? loaded : skipped).add(stored.id());
    }
    return new LoadReport(load.state(), loaded, skipped, load.quarantinedFiles());
  }

  private void requireNoActiveGroups() {
    if (activeGroups.size() > 0) {
      throw new IllegalStateException(
          "LoadMemories must run before any group exists, found " + activeGroups.size());
    }
  }

  // A group that cannot be rebuilt is skipped so the rest of the memories still load.
  private boolean restore(StoredGroup stored) {
    try {
      activeGroups.add(mapper.toGroup(stored, settings));
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
