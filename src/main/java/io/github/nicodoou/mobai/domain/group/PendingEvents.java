package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.event.DomainEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class PendingEvents {
  private final List<DomainEvent> events = new ArrayList<>();

  void add(DomainEvent event) {
    events.add(Objects.requireNonNull(event, "PendingEvents.event"));
  }

  List<DomainEvent> drain() {
    List<DomainEvent> drained = List.copyOf(events);
    events.clear();
    return drained;
  }
}
