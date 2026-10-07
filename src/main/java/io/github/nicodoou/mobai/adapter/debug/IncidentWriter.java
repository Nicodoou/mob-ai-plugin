package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import io.github.nicodoou.mobai.application.IncidentReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;

/** Writes incident files on its own thread, so a failing decision never waits for the disk. */
public final class IncidentWriter {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;

  private final Path folder;
  private final Logger logger;
  private final IncidentJson json = new IncidentJson();
  private final Gson gson = DebugGson.create();
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(
          Thread.ofPlatform().name("MobAI-incident-writer").daemon().factory());

  public IncidentWriter(Path folder, Logger logger) {
    this.folder = Objects.requireNonNull(folder, "IncidentWriter.folder");
    this.logger = Objects.requireNonNull(logger, "IncidentWriter.logger");
  }

  public Path write(IncidentReport report, List<TraceEvent> blackBox) {
    Objects.requireNonNull(report, "IncidentWriter.report");
    List<TraceEvent> events = List.copyOf(blackBox);
    writer.execute(() -> writeLogged(report, events));
    return incidentPath(report.id());
  }

  public void shutdown() {
    writer.shutdown();
    try {
      if (!writer.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
        logger.warn("MobAI incident writer did not finish within {} s", SHUTDOWN_WAIT_SECONDS);
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      logger.warn(
          "MobAI shutdown was interrupted while waiting for the incident writer", exception);
    }
  }

  // A failed write must never take the server down.
  private void writeLogged(IncidentReport report, List<TraceEvent> blackBox) {
    try {
      writeFiles(report, blackBox);
      logger.info("MobAI incident {} written to {}", report.id(), incidentPath(report.id()));
    } catch (IOException | RuntimeException exception) {
      logger.error("Could not write MobAI incident {}", report.id(), exception);
    }
  }

  private void writeFiles(IncidentReport report, List<TraceEvent> blackBox) throws IOException {
    Files.createDirectories(folder);
    Files.writeString(incidentPath(report.id()), json.write(report));
    Files.writeString(blackBoxPath(report.id()), gson.toJson(entriesOf(blackBox)));
  }

  private Path incidentPath(String id) {
    return folder.resolve("incident-" + id + ".json");
  }

  private Path blackBoxPath(String id) {
    return folder.resolve("incident-" + id + "-blackbox.json");
  }

  private static List<BlackBoxEntry> entriesOf(List<TraceEvent> events) {
    return events.stream()
        .map(event -> new BlackBoxEntry(event.getClass().getSimpleName(), event))
        .toList();
  }

  private record BlackBoxEntry(String kind, TraceEvent event) {}
}
