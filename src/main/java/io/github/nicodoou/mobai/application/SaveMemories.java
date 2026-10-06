package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import java.util.Objects;

public final class SaveMemories {
  private final ActiveGroups activeGroups;
  private final MemoryRepository repository;
  private final StoredMemoriesMapper mapper = new StoredMemoriesMapper();

  public SaveMemories(ActiveGroups activeGroups, MemoryRepository repository) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "SaveMemories.activeGroups");
    this.repository = Objects.requireNonNull(repository, "SaveMemories.repository");
  }

  public StoredMemories capture(StoredState state) {
    return new StoredMemories(state, activeGroups.groups().stream().map(mapper::toStored).toList());
  }

  public void write(StoredMemories memories) {
    repository.save(memories);
  }
}
