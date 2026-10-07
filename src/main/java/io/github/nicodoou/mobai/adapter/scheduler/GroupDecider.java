package io.github.nicodoou.mobai.adapter.scheduler;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;

/** Decides for one group; a failure is logged with its context and does not stop the others. */
public final class GroupDecider {
  private final DecisionParts parts;
  private final Logger logger;

  public GroupDecider(DecisionParts parts, Logger logger) {
    this.parts = Objects.requireNonNull(parts, "GroupDecider.parts");
    this.logger = Objects.requireNonNull(logger, "GroupDecider.logger");
  }

  public void decide(Group group, long tick) {
    try {
      parts.snapshots().snapshotOf(group, tick).ifPresent(snapshot -> decideWith(group, snapshot));
    } catch (RuntimeException exception) {
      logger.error(
          "MobAI group {} failed to decide at tick {}", group.id().shortId(), tick, exception);
    }
  }

  public void retainOrdersOf(ActiveGroups activeGroups) {
    parts.applier().retainOnly(memberIds(activeGroups));
  }

  private void decideWith(Group group, GroupSnapshot snapshot) {
    parts
        .tickGroups()
        .execute(snapshot)
        .ifPresent(result -> parts.applier().apply(result.decision(), group.roster().members()));
  }

  private static Set<MobId> memberIds(ActiveGroups activeGroups) {
    Set<MobId> ids = new HashSet<>();
    for (Group group : activeGroups.groups()) {
      group.roster().members().forEach(member -> ids.add(member.id()));
    }
    return ids;
  }
}
