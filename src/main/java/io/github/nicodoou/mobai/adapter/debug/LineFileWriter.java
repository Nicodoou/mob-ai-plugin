package io.github.nicodoou.mobai.adapter.debug;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;

/** Appends lines to one file on its own thread; a disk error is reported once, then counted. */
public final class LineFileWriter {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;
  private static final String THREAD_PREFIX = "MobAI-";

  private final Path file;
  private final Logger logger;
  private final AtomicInteger failures = new AtomicInteger();
  private final ExecutorService writer;

  public LineFileWriter(Path file, Logger logger) {
    this.file = Objects.requireNonNull(file, "LineFileWriter.file");
    this.logger = Objects.requireNonNull(logger, "LineFileWriter.logger");
    this.writer =
        Executors.newSingleThreadExecutor(
            Thread.ofPlatform().name(THREAD_PREFIX + file.getFileName()).daemon().factory());
  }

  public void append(String line) {
    Objects.requireNonNull(line, "LineFileWriter.line");
    writer.execute(() -> appendLogged(line));
  }

  public void shutdown() {
    writer.shutdown();
    awaitTermination();
    int total = failures.get();
    if (total > 0) {
      logger.warn("MobAI could not write {} lines to {}", total, file);
    }
  }

  private void awaitTermination() {
    try {
      if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        logger.warn("MobAI writer of {} did not finish within {} s", file, SHUTDOWN_WAIT_SECONDS);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      logger.warn("MobAI shutdown was interrupted while waiting for the writer of {}", file);
    }
  }

  // A failed write must never take the server down, nor flood the console.
  private void appendLogged(String line) {
    try {
      Files.createDirectories(file.getParent());
      Files.writeString(
          file,
          line + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException exception) {
      if (failures.incrementAndGet() == 1) {
        logger.error("Could not write {}", file, exception);
      }
    }
  }
}
