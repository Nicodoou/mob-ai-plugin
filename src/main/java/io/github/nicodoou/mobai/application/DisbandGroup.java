package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;
import java.util.Optional;

public final class DisbandGroup {
  private final ActiveGroups activeGroups;
  private final GroupEvents groupEvents;

  public DisbandGroup(ActiveGroups activeGroups, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DisbandGroup.activeGroups");
    this.groupEvents = Objects.requireNonNull(groupEvents, "DisbandGroup.groupEvents");
  }

  // Removed first, so subscribers never write into a group that is going away.
  public Optional<Group> execute(GroupId groupId) {
    Optional<Group> removed = activeGroups.remove(groupId);
    removed.ifPresent(groupEvents::publishPending);
    return removed;
  }
}
