package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.List;
import java.util.Objects;

public final class ResetMemories {
  private final ActiveGroups activeGroups;

  public ResetMemories(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "ResetMemories.activeGroups");
  }

  public int resetPlayer(PlayerId player) {
    List<Group> touched =
        activeGroups.groups().stream().filter(group -> remembers(group, player)).toList();
    touched.forEach(group -> group.memory().clearPlayer(player));
    return touched.size();
  }

  public int resetAll() {
    List<Group> groups = activeGroups.groups();
    groups.forEach(group -> group.memory().clear());
    return groups.size();
  }

  private static boolean remembers(Group group, PlayerId player) {
    return group.memory().attackRecords().containsKey(player)
        || group.memory().strategyRecords().containsKey(player);
  }
}
