package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecordPlayerDeathTest {
  private static final StrategyId STRATEGY = new StrategyId("DIRECT_ASSAULT");

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DomainEventPublisher publisher = new DomainEventPublisher();
  private final List<DomainEvent> published = new ArrayList<>();
  private final RecordPlayerDeath recordPlayerDeath;

  RecordPlayerDeathTest() {
    publisher.subscribe(PlanClosed.class, new ClosePlan(activeGroups)::execute);
    publisher.subscribe(DomainEvent.class, published::add);
    recordPlayerDeath =
        new RecordPlayerDeath(
            activeGroups, new SettingsHolder(TestSettings.defaults()), new GroupEvents(publisher));
  }

  @Test
  void deathOfThePlanTargetClosesThePlanAsTargetDied() {
    Group group = executingGroup(1, 1);

    List<ClosedPlan> closed = recordPlayerDeath.execute(player, 160);

    assertThat(closed).hasSize(1);
    assertThat(closed.getFirst().reason()).isEqualTo(PlanEndReason.TARGET_DIED);
    assertThat(closed.getFirst().success()).isEqualTo(1.0);
    assertThat(closed.getFirst().endTick()).isEqualTo(160);
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EVALUATING);
  }

  @Test
  void deathRecordsTheStrategySuccessThroughClosePlan() {
    Group group = executingGroup(1, 1);

    recordPlayerDeath.execute(player, 160);

    assertThat(group.memory().strategyRecords().get(player).get(STRATEGY))
        .isEqualTo(new AttackRecord(1.0, 1.0, 160));
  }

  @Test
  void deathOfAnotherPlayerLeavesThePlanOpen() {
    Group group = executingGroup(1, 1);

    List<ClosedPlan> closed = recordPlayerDeath.execute(otherPlayer, 160);

    assertThat(closed).isEmpty();
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EXECUTING);
  }

  @Test
  void deathClosesThePlanOfEveryGroupTargetingThePlayer() {
    executingGroup(1, 1);
    executingGroup(2, 2);

    List<ClosedPlan> closed = recordPlayerDeath.execute(player, 160);

    assertThat(closed).hasSize(2);
    assertThat(closed.getFirst().id().group()).isEqualTo(groupId(1));
  }

  @Test
  void deathIgnoresAGroupThatIsNotExecuting() {
    Group group = newGroup(1);
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);

    List<ClosedPlan> closed = recordPlayerDeath.execute(player, 160);

    assertThat(closed).isEmpty();
    assertThat(published).isEmpty();
  }

  private Group executingGroup(long groupNumber, long mobNumber) {
    Group group = newGroup(groupNumber);
    activeGroups.add(group);
    activeGroups.join(groupId(groupNumber), mob(mobNumber), MobKind.ZOMBIE);
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(
            new PlanStart(
                STRATEGY, player, Map.of(mob(mobNumber), Role.PRESS), 20.0, 100, Optional.empty()));
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
