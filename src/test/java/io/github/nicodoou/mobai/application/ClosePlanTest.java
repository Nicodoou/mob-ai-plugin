package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClosePlanTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final ClosePlan closePlan = new ClosePlan(activeGroups);

  @Test
  void planClosedRecordsTheStrategyWithFullWeight() {
    activeGroups.add(newGroup(1));

    Optional<RecordChange> change = closePlan.execute(closedEvent());

    assertThat(change).isPresent();
    assertThat(change.get().after()).isEqualTo(new AttackRecord(0.4, 1.0, 700));
  }

  @Test
  void planClosedOfAGroupThatIsNoLongerActiveRecordsNothing() {
    Optional<RecordChange> change = closePlan.execute(closedEvent());

    assertThat(change).isEmpty();
  }

  @Test
  void closingAPlanRecordsTheDanger() {
    Group group = newGroup(1);
    activeGroups.add(group);

    closePlan.execute(closedEvent());

    assertThat(group.memory().dangerRecord(player, 700))
        .isEqualTo(new DangerRecord(12.0, 3.0, 700));
  }

  private PlanClosed closedEvent() {
    return new PlanClosed(
        new ClosedPlan(
            new PlanId(groupId(1), 1),
            new StrategyId("FLANK"),
            player,
            PlanEndReason.TIMED_OUT,
            0.4,
            new PlanScores(1, 1, 1),
            0,
            12.0,
            3.0,
            100,
            700));
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
