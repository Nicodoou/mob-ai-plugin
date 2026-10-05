package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.group.Group;
import java.util.Objects;

public final class GroupEvents {
  private final DomainEventPublisher publisher;

  public GroupEvents(DomainEventPublisher publisher) {
    this.publisher = Objects.requireNonNull(publisher, "GroupEvents.publisher");
  }

  public void publishPending(Group group) {
    group.drainEvents().forEach(publisher::publish);
  }
}
