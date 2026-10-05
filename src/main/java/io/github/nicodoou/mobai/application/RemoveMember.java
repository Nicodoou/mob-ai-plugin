package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import java.util.Optional;

public final class RemoveMember {
  private final ActiveGroups activeGroups;
  private final DisbandGroup disbandGroup;
  private final RegroupWindow regroupWindow;

  public RemoveMember(
      ActiveGroups activeGroups, DisbandGroup disbandGroup, RegroupWindow regroupWindow) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RemoveMember.activeGroups");
    this.disbandGroup = Objects.requireNonNull(disbandGroup, "RemoveMember.disbandGroup");
    this.regroupWindow = Objects.requireNonNull(regroupWindow, "RemoveMember.regroupWindow");
  }

  public RemovalOutcome execute(MobId mobId, RemovalCause cause, long tick) {
    Optional<Group> group = activeGroups.leave(mobId, tick);
    if (group.isEmpty()) {
      return RemovalOutcome.NOT_A_MEMBER;
    }
    if (!group.get().roster().isEmpty()) {
      return RemovalOutcome.REMOVED;
    }
    recordWipeIfRegrouping(group.get(), cause);
    disbandGroup.execute(group.get().id());
    return RemovalOutcome.GROUP_DISBANDED;
  }

  // Only deaths say the window was too long; a despawn says nothing about it.
  private void recordWipeIfRegrouping(Group group, RemovalCause cause) {
    if (cause == RemovalCause.DIED && group.lifecycle().state() == GroupState.REGROUPING) {
      regroupWindow.recordWiped();
    }
  }
}
