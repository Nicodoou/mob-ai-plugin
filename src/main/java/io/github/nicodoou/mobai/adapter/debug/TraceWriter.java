package io.github.nicodoou.mobai.adapter.debug;

import com.google.gson.Gson;
import java.util.Objects;

/** Writes trace events as JSON Lines, one event per line. */
public final class TraceWriter {
  private final LineFileWriter lines;
  private final Gson gson = DebugGson.compact();

  public TraceWriter(LineFileWriter lines) {
    this.lines = Objects.requireNonNull(lines, "TraceWriter.lines");
  }

  public void write(TraceEvent event) {
    Objects.requireNonNull(event, "TraceWriter.event");
    lines.append(gson.toJson(new TraceLine(kindOf(event), event)));
  }

  public void shutdown() {
    lines.shutdown();
  }

  private static String kindOf(TraceEvent event) {
    return event.getClass().getSimpleName();
  }

  private record TraceLine(String kind, TraceEvent event) {}
}
