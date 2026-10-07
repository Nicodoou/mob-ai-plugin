package io.github.nicodoou.mobai.adapter.debug;

import java.util.Objects;

/**
 * Where trace events go: the black box always, the trace file by level, the debug log for plans and
 * attacks.
 */
public record TraceDestinations(
    FlightRecorder recorder, TraceLevels levels, TraceWriter writer, DebugLog debugLog) {
  public TraceDestinations {
    Objects.requireNonNull(recorder, "TraceDestinations.recorder");
    Objects.requireNonNull(levels, "TraceDestinations.levels");
    Objects.requireNonNull(writer, "TraceDestinations.writer");
    Objects.requireNonNull(debugLog, "TraceDestinations.debugLog");
  }
}
