package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.attack.ClassificationTrace;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.event.PlanClosed;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.AttackFactsBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class TraceHubTest {
  private static final long TICK = 500;
  private static final int RECORDER_EVENTS = 10;
  private static final int RULE = 1;
  private static final String TRACE_FILE = "trace.jsonl";
  private static final String DEBUG_FILE = "mobai-debug.log";

  @TempDir Path folder;

  private final MobAiSettings settings = TestSettings.defaults();
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final FlightRecorder recorder =
      new FlightRecorder(() -> new DebugSettings(TraceLevel.OFF, RECORDER_EVENTS));
  private final TraceLevels levels =
      new TraceLevels(() -> new DebugSettings(TraceLevel.OFF, RECORDER_EVENTS));

  private TraceWriter traceWriter;
  private DebugLog debugLog;
  private TraceHub hub;

  @BeforeEach
  void buildTheHubOverTemporaryFiles() {
    traceWriter =
        new TraceWriter(new LineFileWriter(folder.resolve(TRACE_FILE), NOPLogger.NOP_LOGGER));
    debugLog = new DebugLog(new LineFileWriter(folder.resolve(DEBUG_FILE), NOPLogger.NOP_LOGGER));
    hub =
        new TraceHub(activeGroups, new TraceDestinations(recorder, levels, traceWriter, debugLog));
    activeGroups.add(newGroup());
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
  }

  @Test
  void everythingReachesTheBlackBox() throws IOException {
    levels.set(groupId(1), TraceLevel.OFF);

    hub.planClosed(planClosed());
    shutdown();

    assertThat(hub.recent(groupId(1))).hasSize(1);
    assertThat(read(TRACE_FILE)).isEmpty();
  }

  @Test
  void traceFileFollowsTheLevel() throws IOException {
    levels.set(groupId(1), TraceLevel.DECISIONS);

    hub.planClosed(planClosed());
    hub.attacked(mob(1), TICK, classification());
    shutdown();

    assertThat(read(TRACE_FILE).lines().toList())
        .hasSize(1)
        .allSatisfy(line -> assertThat(line).startsWith("{\"kind\":\"PlanEvent\""));
  }

  @Test
  void debugLogGetsPlansAndAttacksAtAnyLevel() throws IOException {
    levels.set(groupId(1), TraceLevel.OFF);

    hub.planClosed(planClosed());
    hub.attacked(mob(1), TICK, classification());
    shutdown();

    List<String> lines = read(DEBUG_FILE).lines().toList();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith("PLAN ");
    assertThat(lines.get(1)).startsWith("ATTACK ");
  }

  private void shutdown() {
    traceWriter.shutdown();
    debugLog.shutdown();
  }

  private String read(String name) throws IOException {
    Path file = folder.resolve(name);
    return Files.exists(file) ? Files.readString(file) : "";
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private Group newGroup() {
    return new Group(
        groupId(1),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
  }

  private static PlanClosed planClosed() {
    return new PlanClosed(
        new ClosedPlan(
            new PlanId(groupId(1), 1),
            new StrategyId("FLANK"),
            new PlayerId(new UUID(0, 2)),
            PlanEndReason.TARGET_DIED,
            1.0,
            new PlanScores(1, 1, 1),
            0,
            0,
            20.0,
            TICK,
            TICK));
  }

  private static Classification classification() {
    return new Classification(
        new AttackOutcome.Hit(), new ClassificationTrace(RULE, new AttackFactsBuilder().build()));
  }
}
