package io.github.nicodoou.mobai.application;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TickGroupsTest {
  private final BrainFixture fixture = BrainFixture.seeded(7);
  private final List<MobSnapshot> mobs = fixture.catalogGroup();
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DomainEventPublisher publisher = new DomainEventPublisher();
  private final List<DomainEvent> published = new ArrayList<>();
  private final TickGroups tickGroups =
      new TickGroups(activeGroups, fixture.brain(), new GroupEvents(publisher));

  TickGroupsTest() {
    publisher.subscribe(DomainEvent.class, published::add);
  }

  @Test
  void tickDecidesForTheGroupOfTheSnapshot() {
    activeGroups.add(fixture.group());

    Optional<BrainResult> result =
        tickGroups.execute(BrainFixture.snapshot(START_TICK, mobs, BrainFixture.alice()));

    assertThat(result).isPresent();
    assertThat(result.get().decision().group()).isEqualTo(GROUP_ID);
    assertThat(result.get().decision().state()).isEqualTo(GroupState.EXECUTING);
  }

  @Test
  void tickOfAGroupThatIsNoLongerActiveReturnsEmpty() {
    Optional<BrainResult> result =
        tickGroups.execute(BrainFixture.snapshot(START_TICK, mobs, BrainFixture.alice()));

    assertThat(result).isEmpty();
    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.OBSERVING);
  }

  @Test
  void tickPublishesThePlanClosedOfTheDecision() {
    activeGroups.add(fixture.group());
    tickGroups.execute(BrainFixture.snapshot(START_TICK, mobs, BrainFixture.alice()));

    tickGroups.execute(BrainFixture.snapshot(START_TICK + 700, mobs, BrainFixture.alice()));

    assertThat(published).hasSize(1);
    assertThat(published.getFirst()).isInstanceOf(PlanClosed.class);
    assertThat(((PlanClosed) published.getFirst()).plan().reason())
        .isEqualTo(PlanEndReason.TIMED_OUT);
    assertThat(fixture.group().drainEvents()).isEmpty();
  }
}
