package io.github.nicodoou.mobai.domain.event;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import java.util.Optional;

public record LeaderDied(GroupId groupId, MobId formerLeader, Optional<MobId> newLeader, long tick)
    implements DomainEvent {
  public LeaderDied {
    Objects.requireNonNull(groupId, "LeaderDied.groupId");
    Objects.requireNonNull(formerLeader, "LeaderDied.formerLeader");
    Objects.requireNonNull(newLeader, "LeaderDied.newLeader");
  }
}
