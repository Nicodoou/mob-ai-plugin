package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** The trace level of each group: its own, the one set for all, or the configured default. */
public final class TraceLevels {
  private final Supplier<DebugSettings> settings;
  private final Map<GroupId, TraceLevel> ownLevels = new HashMap<>();
  private Optional<TraceLevel> levelForAll = Optional.empty();

  public TraceLevels(Supplier<DebugSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "TraceLevels.settings");
  }

  public TraceLevel levelOf(GroupId group) {
    Objects.requireNonNull(group, "TraceLevels.group");
    TraceLevel own = ownLevels.get(group);
    if (own != null) {
      return own;
    }
    return levelForAll.orElseGet(() -> settings.get().defaultTraceLevel());
  }

  public void set(GroupId group, TraceLevel level) {
    ownLevels.put(
        Objects.requireNonNull(group, "TraceLevels.group"),
        Objects.requireNonNull(level, "TraceLevels.level"));
  }

  public void setAll(TraceLevel level) {
    Objects.requireNonNull(level, "TraceLevels.level");
    ownLevels.clear();
    levelForAll = Optional.of(level);
  }

  public boolean writes(TraceEvent event) {
    Objects.requireNonNull(event, "TraceLevels.event");
    return switch (levelOf(event.group())) {
      case OFF -> false;
      case DECISIONS -> !(event instanceof TraceEvent.AttackEvent);
      case FULL -> true;
    };
  }
}
