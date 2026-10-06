package io.github.nicodoou.mobai.domain.port;

/** Where the group memories live between restarts; the plugin never touches files directly. */
public interface MemoryRepository {
  /** Throws UncheckedIOException on a disk error and IllegalStateException on a newer format. */
  MemoryLoad load();

  /** Replaces everything stored: groups that are not in {@code memories} are deleted. */
  void save(StoredMemories memories);
}
