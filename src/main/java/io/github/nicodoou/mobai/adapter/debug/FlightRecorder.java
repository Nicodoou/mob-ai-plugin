package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/** The last trace events of each group, kept in memory whatever the trace level. */
public final class FlightRecorder {
  // Disbanded groups would otherwise stay here forever.
  static final int MAX_GROUPS = 256;

  private static final int INITIAL_CAPACITY = 16;
  private static final float LOAD_FACTOR = 0.75f;
  private static final boolean ACCESS_ORDER = true;

  private final Supplier<DebugSettings> settings;

  @SuppressWarnings("serial") // never serialized
  private final Map<GroupId, ArrayDeque<TraceEvent>> events =
      new LinkedHashMap<>(INITIAL_CAPACITY, LOAD_FACTOR, ACCESS_ORDER) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<GroupId, ArrayDeque<TraceEvent>> eldest) {
          return size() > MAX_GROUPS;
        }
      };

  public FlightRecorder(Supplier<DebugSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "FlightRecorder.settings");
  }

  public void record(TraceEvent event) {
    Objects.requireNonNull(event, "FlightRecorder.event");
    ArrayDeque<TraceEvent> recent =
        events.computeIfAbsent(event.group(), group -> new ArrayDeque<>());
    recent.addLast(event);
    int excess = recent.size() - settings.get().flightRecorderEvents();
    for (int dropped = 0; dropped < excess; dropped++) {
      recent.removeFirst();
    }
  }

  public List<TraceEvent> recent(GroupId group) {
    Objects.requireNonNull(group, "FlightRecorder.group");
    ArrayDeque<TraceEvent> recent = events.get(group);
    return recent == null ? List.of() : List.copyOf(recent);
  }
}
