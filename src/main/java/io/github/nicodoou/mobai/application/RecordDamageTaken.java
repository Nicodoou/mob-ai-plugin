package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.Objects;
import java.util.Optional;

public final class RecordDamageTaken {
  private final ActiveGroups activeGroups;

  public RecordDamageTaken(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordDamageTaken.activeGroups");
  }

  public Optional<GroupId> execute(DamageTaken damageTaken) {
    Optional<Group> group = activeGroups.groupOf(damageTaken.mob());
    group.ifPresent(
        found ->
            found
                .threat()
                .recordDamage(damageTaken.attacker(), damageTaken.damage(), damageTaken.tick()));
    return group.map(Group::id);
  }
}
