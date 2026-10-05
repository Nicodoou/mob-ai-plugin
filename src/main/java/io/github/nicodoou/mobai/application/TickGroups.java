package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.Objects;
import java.util.Optional;

public final class TickGroups {
  private final ActiveGroups activeGroups;
  private final Brain brain;
  private final GroupEvents groupEvents;

  public TickGroups(ActiveGroups activeGroups, Brain brain, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "TickGroups.activeGroups");
    this.brain = Objects.requireNonNull(brain, "TickGroups.brain");
    this.groupEvents = Objects.requireNonNull(groupEvents, "TickGroups.groupEvents");
  }

  public Optional<BrainResult> execute(GroupSnapshot snapshot) {
    return activeGroups.group(snapshot.groupId()).map(group -> decide(group, snapshot));
  }

  private BrainResult decide(Group group, GroupSnapshot snapshot) {
    BrainResult result = brain.decide(group, snapshot);
    groupEvents.publishPending(group);
    return result;
  }
}
