package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import java.util.List;
import java.util.Optional;

public final class InMemoryMemoryRepository implements MemoryRepository {
  private Optional<StoredMemories> saved = Optional.empty();
  private int saveCount;

  @Override
  public MemoryLoad load() {
    return saved
        .map(
            memories -> new MemoryLoad(Optional.of(memories.state()), memories.groups(), List.of()))
        .orElseGet(MemoryLoad::empty);
  }

  @Override
  public void save(StoredMemories memories) {
    saved = Optional.of(memories);
    saveCount++;
  }

  public int saveCount() {
    return saveCount;
  }
}
