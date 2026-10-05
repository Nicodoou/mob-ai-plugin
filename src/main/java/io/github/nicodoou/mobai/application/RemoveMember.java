package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import java.util.Optional;

public final class RemoveMember {
  private final ActiveGroups activeGroups;
  private final DisbandGroup disbandGroup;

  public RemoveMember(ActiveGroups activeGroups, DisbandGroup disbandGroup) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RemoveMember.activeGroups");
    this.disbandGroup = Objects.requireNonNull(disbandGroup, "RemoveMember.disbandGroup");
  }

  public RemovalOutcome execute(MobId mobId, long tick) {
    Optional<Group> group = activeGroups.leave(mobId, tick);
    if (group.isEmpty()) {
      return RemovalOutcome.NOT_A_MEMBER;
    }
    if (!group.get().roster().isEmpty()) {
      return RemovalOutcome.REMOVED;
    }
    disbandGroup.execute(group.get().id());
    return RemovalOutcome.GROUP_DISBANDED;
  }
}
