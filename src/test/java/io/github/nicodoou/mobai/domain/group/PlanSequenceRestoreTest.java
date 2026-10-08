package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanSequenceRestoreTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final Group group = newGroup(1);

  @Test
  void restoredSequenceContinuesWithTheNextPlan() {
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    group.lifecycle().restorePlanSequence(3);

    group.lifecycle().beginPlanning();
    Plan plan = group.lifecycle().startPlan(pressStart());

    assertThat(plan.id().sequence()).isEqualTo(4);
  }

  @Test
  void restoreCannotGoBack() {
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(pressStart());
    group.lifecycle().closePlan(PlanEndReason.TIMED_OUT, 200, TestSettings.scoring());
    group.lifecycle().finishEvaluation();

    assertThatThrownBy(() -> group.lifecycle().restorePlanSequence(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Group " + groupId(1).shortId() + " cannot restore plan sequence 0 below 1");
  }

  @Test
  void restoreRequiresObserving() {
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    group.lifecycle().beginPlanning();
    group.lifecycle().startPlan(pressStart());

    assertThatThrownBy(() -> group.lifecycle().restorePlanSequence(5))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(
            "Group " + groupId(1).shortId() + " cannot restore the plan sequence while EXECUTING");
  }

  private PlanStart pressStart() {
    return new PlanStart(
        new StrategyId("DIRECT_ASSAULT"),
        player,
        Map.of(mob(1), Role.PRESS),
        20,
        100,
        Optional.empty());
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
