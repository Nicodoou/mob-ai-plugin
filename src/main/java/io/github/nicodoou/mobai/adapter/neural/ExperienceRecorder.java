package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.adapter.debug.LineFileWriter;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.settings.NeuralSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Writes each network's experience. A fight opens with a group's first step and closes with its end
 * line, so a line never lands outside a fight and the trainer can cut episodes by group.
 */
public final class ExperienceRecorder {
  private final Function<String, LineFileWriter> writerFactory;
  private final Supplier<NeuralSettings> settings;
  private final ActiveGroups activeGroups;
  private final ExperienceLines lines = new ExperienceLines();
  private final Map<String, LineFileWriter> writers = new HashMap<>();
  private final Map<GroupId, String> openFights = new LinkedHashMap<>();

  public ExperienceRecorder(
      Function<String, LineFileWriter> writerFactory,
      Supplier<NeuralSettings> settings,
      ActiveGroups activeGroups) {
    this.writerFactory = Objects.requireNonNull(writerFactory, "ExperienceRecorder.writerFactory");
    this.settings = Objects.requireNonNull(settings, "ExperienceRecorder.settings");
    this.activeGroups = Objects.requireNonNull(activeGroups, "ExperienceRecorder.activeGroups");
  }

  public void decided(NeuralDecided decision) {
    Objects.requireNonNull(decision, "ExperienceRecorder.decision");
    if (!settings.get().experienceEnabled() || decision.steps().isEmpty()) {
      return;
    }
    openFights.put(decision.groupId(), decision.network());
    writerOf(decision.network()).append(lines.steps(decision));
  }

  public void record(ExperienceEvent event) {
    Objects.requireNonNull(event, "ExperienceRecorder.event");
    String network = openFights.get(event.group());
    if (network != null) {
      writerOf(network).append(lines.event(event));
    }
  }

  public void endFight(GroupId group, long tick, FightWinner winner) {
    String network = openFights.remove(group);
    if (network != null) {
      writerOf(network).append(lines.end(tick, group, winner));
    }
  }

  /** A group that left by any path other than a win still has to close its fight. */
  public void closeVanishedFights(long tick) {
    for (GroupId group : List.copyOf(openFights.keySet())) {
      if (activeGroups.group(group).isEmpty()) {
        endFight(group, tick, FightWinner.NONE);
      }
    }
  }

  public void shutdown(long tick) {
    List.copyOf(openFights.keySet()).forEach(group -> endFight(group, tick, FightWinner.NONE));
    writers.values().forEach(LineFileWriter::shutdown);
    writers.clear();
  }

  private LineFileWriter writerOf(String network) {
    return writers.computeIfAbsent(network, writerFactory);
  }
}
