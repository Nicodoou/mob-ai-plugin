package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;
import java.util.Optional;

public final class DisbandGroup {
  private final ActiveGroups activeGroups;

  public DisbandGroup(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DisbandGroup.activeGroups");
  }

  public Optional<Group> execute(GroupId groupId) {
    return activeGroups.remove(groupId);
  }
}
