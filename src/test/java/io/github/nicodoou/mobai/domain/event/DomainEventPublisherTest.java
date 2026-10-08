package io.github.nicodoou.mobai.domain.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DomainEventPublisherTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB_1 = new MobId(new UUID(1, 1));
  private static final int NESTED_LIMIT = 8;

  private final LeaderDied leaderDied = new LeaderDied(GROUP, MOB_1, Optional.empty(), 10);
  private final PlanClosed planClosed =
      new PlanClosed(
          new ClosedPlan(
              new PlanId(GROUP, 1),
              new StrategyId("FLANK"),
              new PlayerId(new UUID(0, 10)),
              PlanEndReason.TIMED_OUT,
              0.5,
              new PlanScores(1, 1, 1),
              0,
              0,
              5,
              100,
              700));

  @Test
  void deliversOnlyToSubscribersOfTheEventType() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    List<DomainEvent> planClosedReceived = new ArrayList<>();
    List<DomainEvent> leaderDiedReceived = new ArrayList<>();
    publisher.subscribe(PlanClosed.class, planClosedReceived::add);
    publisher.subscribe(LeaderDied.class, leaderDiedReceived::add);

    publisher.publish(leaderDied);

    assertThat(planClosedReceived).isEmpty();
    assertThat(leaderDiedReceived).containsExactly(leaderDied);
  }

  @Test
  void subscribersOfTheInterfaceReceiveEveryEvent() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    List<DomainEvent> received = new ArrayList<>();
    publisher.subscribe(DomainEvent.class, received::add);

    publisher.publish(leaderDied);
    publisher.publish(planClosed);

    assertThat(received).containsExactly(leaderDied, planClosed);
  }

  @Test
  void deliversInSubscriptionOrder() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    List<String> order = new ArrayList<>();
    publisher.subscribe(LeaderDied.class, event -> order.add("a"));
    publisher.subscribe(LeaderDied.class, event -> order.add("b"));
    publisher.subscribe(LeaderDied.class, event -> order.add("c"));

    publisher.publish(leaderDied);

    assertThat(order).containsExactly("a", "b", "c");
  }

  @Test
  void subscriberAddedDuringDeliveryWaitsForTheNextEvent() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    List<DomainEvent> lateReceived = new ArrayList<>();
    publisher.subscribe(
        LeaderDied.class, event -> publisher.subscribe(LeaderDied.class, lateReceived::add));

    publisher.publish(leaderDied);

    assertThat(lateReceived).isEmpty();

    publisher.publish(leaderDied);

    assertThat(lateReceived).containsExactly(leaderDied);
  }

  @Test
  void nestedPublishingHasALimit() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    publisher.subscribe(LeaderDied.class, publisher::publish);

    assertThatThrownBy(() -> publisher.publish(leaderDied))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("DomainEventPublisher: nested publish deeper than 8 levels for");
  }

  @Test
  void publisherRecoversAfterAFailedDelivery() {
    DomainEventPublisher publisher = new DomainEventPublisher();
    AtomicInteger calls = new AtomicInteger();
    AtomicInteger counter = new AtomicInteger();
    publisher.subscribe(
        LeaderDied.class,
        event -> {
          if (calls.incrementAndGet() <= NESTED_LIMIT) {
            throw new IllegalArgumentException("failing subscriber");
          }
          counter.incrementAndGet();
        });

    for (int attempt = 0; attempt < NESTED_LIMIT; attempt++) {
      assertThatThrownBy(() -> publisher.publish(leaderDied))
          .isInstanceOf(IllegalArgumentException.class);
    }
    publisher.publish(leaderDied);

    assertThat(counter.get()).isEqualTo(1);
  }
}
