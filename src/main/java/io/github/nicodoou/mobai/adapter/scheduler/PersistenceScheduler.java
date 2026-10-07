package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.application.SaveMemories;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;

/** Copies memories on the main thread and writes them on its own thread, one write at a time. */
public final class PersistenceScheduler {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;

  private final SaveMemories saveMemories;
  private final Supplier<StoredState> state;
  private final Logger logger;
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("MobAI-memory-writer").daemon().factory());

  public PersistenceScheduler(
      SaveMemories saveMemories, Supplier<StoredState> state, Logger logger) {
    this.saveMemories = Objects.requireNonNull(saveMemories, "PersistenceScheduler.saveMemories");
    this.state = Objects.requireNonNull(state, "PersistenceScheduler.state");
    this.logger = Objects.requireNonNull(logger, "PersistenceScheduler.logger");
  }

  public void tick(long tick, long saveIntervalTicks) {
    if (tick > 0 && tick % saveIntervalTicks == 0) {
      saveInBackground();
    }
  }

  public void saveInBackground() {
    StoredMemories memories = saveMemories.capture(state.get());
    writer.execute(() -> writeLogged(memories));
  }

  public void shutdown() {
    writer.shutdown();
    awaitPendingWrite();
    writeLogged(saveMemories.capture(state.get()));
  }

  private void awaitPendingWrite() {
    try {
      if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        logger.warn("MobAI memory writer did not finish within {} s", SHUTDOWN_WAIT_SECONDS);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      logger.warn("MobAI shutdown was interrupted while waiting for the memory writer", exception);
    }
  }

  // A failed write is retried on the next cycle; it must never take the server down.
  private void writeLogged(StoredMemories memories) {
    try {
      saveMemories.write(memories);
    } catch (RuntimeException exception) {
      logger.error("Could not save MobAI memories", exception);
    }
  }
}
