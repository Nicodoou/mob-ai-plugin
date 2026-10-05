package io.github.nicodoou.mobai.domain.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public final class DomainEventPublisher {
  // A subscriber may publish while handling an event; a deeper chain means a subscriber loop.
  private static final int MAX_NESTED_PUBLISH_DEPTH = 8;

  private final List<Subscription<?>> subscriptions = new ArrayList<>();
  private int depth = 0;

  private record Subscription<E extends DomainEvent>(
      Class<E> type, Consumer<? super E> subscriber) {
    void deliver(DomainEvent event) {
      if (type.isInstance(event)) {
        subscriber.accept(type.cast(event));
      }
    }
  }

  public <E extends DomainEvent> void subscribe(Class<E> type, Consumer<? super E> subscriber) {
    Objects.requireNonNull(type, "DomainEventPublisher.type");
    Objects.requireNonNull(subscriber, "DomainEventPublisher.subscriber");
    subscriptions.add(new Subscription<>(type, subscriber));
  }

  public void publish(DomainEvent event) {
    Objects.requireNonNull(event, "DomainEventPublisher.event");
    if (depth >= MAX_NESTED_PUBLISH_DEPTH) {
      throw new IllegalStateException(
          "DomainEventPublisher: nested publish deeper than "
              + MAX_NESTED_PUBLISH_DEPTH
              + " levels for "
              + event);
    }
    depth++;
    try {
      deliverToAll(event);
    } finally {
      depth--;
    }
  }

  private void deliverToAll(DomainEvent event) {
    for (Subscription<?> subscription : List.copyOf(subscriptions)) {
      subscription.deliver(event);
    }
  }
}
