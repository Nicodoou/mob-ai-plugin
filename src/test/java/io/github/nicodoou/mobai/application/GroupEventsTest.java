package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupEventsTest {
  private final DomainEventPublisher publisher = new DomainEventPublisher();
  private final List<DomainEvent> published = new ArrayList<>();
  private final GroupEvents groupEvents = new GroupEvents(publisher);

  GroupEventsTest() {
    publisher.subscribe(DomainEvent.class, published::add);
  }

  @Test
  void publishPendingDeliversEventsInOrder() {
    Group group = groupWithThreeMobs();
    group.removeMember(mob(1), 50);
    group.removeMember(mob(2), 60);

    groupEvents.publishPending(group);

    assertThat(published)
        .containsExactly(
            new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50),
            new LeaderDied(groupId(1), mob(2), Optional.of(mob(3)), 60));
  }

  @Test
  void publishPendingDrainsTheGroup() {
    Group group = groupWithThreeMobs();
    group.removeMember(mob(1), 50);
    group.removeMember(mob(2), 60);

    groupEvents.publishPending(group);
    groupEvents.publishPending(group);

    assertThat(group.drainEvents()).isEmpty();
    assertThat(published).hasSize(2);
  }

  private static Group groupWithThreeMobs() {
    Group group = newGroup(1);
    for (long n = 1; n <= 3; n++) {
      group.roster().addMember(mob(n), MobKind.ZOMBIE);
    }
    return group;
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static Group newGroup(long n) {
    return new Group(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }
}
