package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.DecisionTrace;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.AttackFactsBuilder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class TraceLevelsTest {
  private static final int RECORDER_EVENTS = 10;
  private static final long TICK = 100;

  private final AtomicReference<TraceLevel> configured = new AtomicReference<>(TraceLevel.OFF);
  private final TraceLevels levels =
      new TraceLevels(() -> new DebugSettings(configured.get(), RECORDER_EVENTS));

  @Test
  void defaultComesFromTheSettings() {
    assertThat(levels.levelOf(groupId(1))).isEqualTo(TraceLevel.OFF);

    configured.set(TraceLevel.DECISIONS);

    assertThat(levels.levelOf(groupId(1))).isEqualTo(TraceLevel.DECISIONS);
  }

  @Test
  void ownLevelWins() {
    levels.set(groupId(1), TraceLevel.FULL);

    assertThat(levels.levelOf(groupId(1))).isEqualTo(TraceLevel.FULL);
    assertThat(levels.levelOf(groupId(2))).isEqualTo(TraceLevel.OFF);
  }

  @Test
  void levelForAllReplacesOwnLevels() {
    levels.set(groupId(1), TraceLevel.FULL);

    levels.setAll(TraceLevel.DECISIONS);

    assertThat(levels.levelOf(groupId(1))).isEqualTo(TraceLevel.DECISIONS);
    assertThat(levels.levelOf(groupId(2))).isEqualTo(TraceLevel.DECISIONS);
  }

  @Test
  void decisionsLevelWritesDecisionsAndPlansButNotAttacks() {
    levels.set(groupId(1), TraceLevel.DECISIONS);

    assertThat(levels.writes(decisionEvent())).isTrue();
    assertThat(levels.writes(planEvent())).isTrue();
    assertThat(levels.writes(attackEvent())).isFalse();
  }

  @Test
  void offWritesNothingAndFullWritesEverything() {
    levels.set(groupId(1), TraceLevel.OFF);
    assertThat(levels.writes(decisionEvent())).isFalse();
    assertThat(levels.writes(planEvent())).isFalse();
    assertThat(levels.writes(attackEvent())).isFalse();

    levels.set(groupId(1), TraceLevel.FULL);

    assertThat(levels.writes(decisionEvent())).isTrue();
    assertThat(levels.writes(planEvent())).isTrue();
    assertThat(levels.writes(attackEvent())).isTrue();
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static TraceEvent decisionEvent() {
    GroupDecision decision =
        new GroupDecision(
            groupId(1),
            TICK,
            GroupState.OBSERVING,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            List.of());
    DecisionTrace trace =
        new DecisionTrace(
            groupId(1),
            TICK,
            GroupState.OBSERVING,
            GroupState.OBSERVING,
            Optional.empty(),
            Optional.empty(),
            List.of(),
            Optional.empty(),
            List.of(),
            List.of(),
            Optional.empty(),
            Optional.empty(),
            false,
            List.of());
    return new TraceEvent.DecisionEvent(groupId(1), TICK, decision, trace);
  }

  private static TraceEvent planEvent() {
    ClosedPlan plan =
        new ClosedPlan(
            new PlanId(groupId(1), 1),
            new StrategyId("FLANK"),
            new PlayerId(new UUID(0, 2)),
            PlanEndReason.TARGET_DIED,
            1.0,
            20.0,
            TICK,
            TICK);
    return new TraceEvent.PlanEvent(groupId(1), TICK, plan);
  }

  private static TraceEvent attackEvent() {
    AttackFacts facts = new AttackFactsBuilder().build();
    return new TraceEvent.AttackEvent(groupId(1), TICK, "HIT", 1, facts);
  }
}
