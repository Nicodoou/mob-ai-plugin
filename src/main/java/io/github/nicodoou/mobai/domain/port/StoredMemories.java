package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record StoredMemories(StoredState state, List<StoredGroup> groups) {
  public StoredMemories {
    Objects.requireNonNull(state, "StoredMemories.state");
    groups = List.copyOf(groups);
    Set<GroupId> seen = new HashSet<>();
    for (StoredGroup group : groups) {
      if (!seen.add(group.id())) {
        throw new IllegalArgumentException(
            "StoredMemories.groups has a duplicate group " + group.id().shortId());
      }
    }
  }
}
