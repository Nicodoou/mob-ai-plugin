package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class CombatSampleLinesTest {
  private static final long TICK = 100;
  private static final String TRACE_FILE = "trace.jsonl";
  private static final UUID ATTACKER = new UUID(3, 3);

  @TempDir Path folder;

  private TraceWriter writer;

  @BeforeEach
  void buildTheWriterOverATemporaryFile() {
    writer = new TraceWriter(new LineFileWriter(folder.resolve(TRACE_FILE), NOPLogger.NOP_LOGGER));
  }

  @Test
  void aMobSampleIsOneLineWithItsKind() throws IOException {
    writer.write(mobSample());
    writer.shutdown();

    List<String> lines = readLines();
    assertThat(lines).hasSize(1);
    assertThat(lines.get(0)).startsWith("{\"kind\":\"MobSample\",\"event\":{\"tick\":100");
  }

  @Test
  void anEmptyOptionalIsWrittenAsNull() throws IOException {
    writer.write(mobSample());
    writer.shutdown();

    JsonObject event = JsonParser.parseString(readLines().get(0)).getAsJsonObject();
    JsonObject sample = event.getAsJsonObject("event");
    assertThat(sample.has("order")).isTrue();
    assertThat(sample.get("order").isJsonNull()).isTrue();
  }

  @Test
  void samplesAndEventsShareTheFile() throws IOException {
    writer.write(planEvent());
    writer.write(groupSample());
    writer.shutdown();

    List<String> lines = readLines();
    assertThat(lines).hasSize(2);
    assertThat(lines.get(0)).startsWith("{\"kind\":\"PlanEvent\"");
    assertThat(lines.get(1)).startsWith("{\"kind\":\"GroupSample\"");
  }

  @Test
  void aDamageSampleKeepsItsCauseAndAttacker() throws IOException {
    writer.write(damageSample());
    writer.shutdown();

    String line = readLines().get(0);
    assertThat(line).startsWith("{\"kind\":\"DamageSample\"");
    assertThat(line).contains("FIRE_TICK");
    assertThat(line).contains(ATTACKER.toString());
  }

  @Test
  void anActionSampleKeepsItsActionAndForce() throws IOException {
    writer.write(shootSample());
    writer.shutdown();

    String line = readLines().get(0);
    assertThat(line).contains("\"action\":\"SHOOT\"");
    assertThat(line).contains("\"force\":1.0");
  }

  private List<String> readLines() throws IOException {
    return Files.readString(folder.resolve(TRACE_FILE)).lines().toList();
  }

  private static GroupId groupId() {
    return new GroupId(new UUID(0, 1));
  }

  private static CombatSample.MobSample mobSample() {
    return new CombatSample.MobSample(
        TICK,
        groupId(),
        new MobId(new UUID(1, 1)),
        MobKind.ZOMBIE,
        new Vec3(1, 64, 2),
        Vec3.ZERO,
        20,
        20,
        true,
        0,
        Optional.empty(),
        List.of("mobai_press"),
        Optional.empty());
  }

  private static CombatSample.DamageSample damageSample() {
    return new CombatSample.DamageSample(
        TICK,
        Optional.of(groupId()),
        new UUID(1, 1),
        "zombie",
        "FIRE_TICK",
        1,
        1,
        20,
        false,
        false,
        Optional.of(ATTACKER),
        Optional.of("skeleton"),
        Optional.empty());
  }

  private static CombatSample.ActionSample shootSample() {
    return new CombatSample.ActionSample(
        TICK,
        new PlayerId(new UUID(2, 2)),
        CombatSample.PlayerAction.SHOOT,
        "bow",
        Optional.of(1.0));
  }

  private static CombatSample.GroupSample groupSample() {
    return new CombatSample.GroupSample(
        TICK,
        groupId(),
        GroupState.OBSERVING,
        Optional.empty(),
        Optional.empty(),
        1,
        PackShape.of(List.of(mobSample())),
        Optional.empty(),
        Optional.empty());
  }

  private static TraceEvent planEvent() {
    ClosedPlan plan =
        new ClosedPlan(
            new PlanId(groupId(), 1),
            new StrategyId("FLANK"),
            new PlayerId(new UUID(0, 2)),
            PlanEndReason.TARGET_DIED,
            1.0,
            new PlanScores(1, 1, 1),
            0,
            0,
            20.0,
            TICK,
            TICK,
            Optional.empty());
    return new TraceEvent.PlanEvent(groupId(), TICK, plan);
  }
}
