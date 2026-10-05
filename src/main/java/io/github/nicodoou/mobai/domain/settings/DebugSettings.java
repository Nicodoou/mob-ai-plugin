package io.github.nicodoou.mobai.domain.settings;

import java.util.Objects;

public record DebugSettings(TraceLevel defaultTraceLevel, int flightRecorderEvents) {
  public DebugSettings {
    Objects.requireNonNull(defaultTraceLevel, "DebugSettings.defaultTraceLevel");
    SettingsChecks.requireAtLeast("DebugSettings.flightRecorderEvents", flightRecorderEvents, 1);
  }
}
