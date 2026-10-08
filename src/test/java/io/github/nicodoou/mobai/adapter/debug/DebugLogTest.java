package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.DecisionTrace;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.AttackFactsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class DebugLogTest {
  private static final long TICK = 12_500;
  private static final int PARTIAL_RULE = 7;

  @TempDir Path folder;

  private final PlayerId player = new PlayerId(new UUID(0, 2));

  @Test
  void planLineHasTheExactFormat() {
    ClosedPlan plan =
        new ClosedPlan(
            new PlanId(groupId(1), 3),
            new StrategyId("FLANK"),
            player,
            PlanEndReason.TARGET_DIED,
            1.0,
            new PlanScores(1, 1, 1),
            20.0,
            100,
            TICK);

    String line = DebugLog.planLine(new TraceEvent.PlanEvent(groupId(1), TICK, plan));

    assertThat(line)
        .isEqualTo(
            "PLAN tick=12500 group="
                + groupId(1).shortId()
                + " plan=3 strategy=FLANK target="
                + player.shortId()
                + " reason=TARGET_DIED success=1.00 scores=1.00/1.00/1.00 damage=20.0");
  }

  @Test
  void attackLineHasTheExactFormat() {
    MobId mob = new MobId(new UUID(1, 1));
    AttackFacts facts =
        new AttackFactsBuilder()
            .withMob(mob)
            .withTarget(player)
            .withAttack(Attack.ZOMBIE_FRONT_STRIKE)
            .withRealDamage(0)
            .build();

    String line =
        DebugLog.attackLine(
            new TraceEvent.AttackEvent(groupId(1), TICK, "PARTIAL", PARTIAL_RULE, facts));

    assertThat(line)
        .isEqualTo(
            "ATTACK tick=12500 group="
                + groupId(1).shortId()
                + " mob="
                + mob.shortId()
                + " attack=zombie.front_strike target="
                + player.shortId()
                + " outcome=PARTIAL rule=7 damage=0.0");
  }

  @Test
  void decisionsAreNotLogged() throws Exception {
    Path file = folder.resolve("mobai-debug.log");
    DebugLog debugLog = new DebugLog(new LineFileWriter(file, NOPLogger.NOP_LOGGER));

    debugLog.record(decisionEvent());
    debugLog.shutdown();

    assertThat(Files.exists(file) ? Files.readString(file) : "").isEmpty();
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
}
