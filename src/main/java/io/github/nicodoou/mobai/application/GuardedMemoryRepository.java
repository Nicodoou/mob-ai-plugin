package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.port.MemoryLoad;
import io.github.nicodoou.mobai.domain.port.MemoryRepository;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import java.util.Objects;

public final class GuardedMemoryRepository implements MemoryRepository {
  private final MemoryRepository delegate;
  private boolean loaded;

  public GuardedMemoryRepository(MemoryRepository delegate) {
    this.delegate = Objects.requireNonNull(delegate, "GuardedMemoryRepository.delegate");
  }

  @Override
  public MemoryLoad load() {
    MemoryLoad load = delegate.load();
    loaded = true;
    return load;
  }

  // A save after a failed load would delete every group file that could not be read.
  @Override
  public void save(StoredMemories memories) {
    if (!loaded) {
      throw new IllegalStateException(
          "Memories were never loaded successfully; saving now would delete stored groups");
    }
    delegate.save(memories);
  }
}
