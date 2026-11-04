package io.github.nicodoou.mobai.adapter.neural;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.debug.LineFileWriter;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.NeuralSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class ExperienceRecorderTest {
  private static final GroupId GROUP = new GroupId(new UUID(0, 3));
  private static final MobId MOB = new MobId(new UUID(0, 1));
  private static final String NETWORK = "turnos-1";

  @TempDir Path folder;

  private final MobAiSettings settings = TestSettings.defaults();
  private final AtomicReference<NeuralSettings> neural = new AtomicReference<>(settings.neural());
  private final ActiveGroups activeGroups = new ActiveGroups();
  private ExperienceRecorder recorder;

  @BeforeEach
  void setUp() {
    activeGroups.add(
        new Group(
            GROUP,
            SelectionPolicyType.RANDOM,
            new GroupKnowledge(
                new GroupMemory(settings::memory), new ThreatLedger(settings::target))));
    recorder =
        new ExperienceRecorder(
            network ->
                new LineFileWriter(
                    folder.resolve(network).resolve("experience.jsonl"), NOPLogger.NOP_LOGGER),
            neural::get,
            activeGroups);
  }

  private static NeuralDecided decision(String network) {
    return new NeuralDecided(
        GROUP,
        100,
        network,
        List.of(new NeuralStep(MOB, MobKind.ZOMBIE, List.of(0.0), NeuralAction.PRESS, -1)));
  }

  private static ExperienceEvent hit() {
    return new ExperienceEvent(105, GROUP, Optional.of(MOB), ExperienceEvent.Kind.DAMAGE_TAKEN, 2);
  }

  private List<String> linesOf(String network) throws IOException {
    recorder.shutdown(999);
    Path file = folder.resolve(network).resolve("experience.jsonl");
    return Files.exists(file) ? Files.readAllLines(file) : List.of();
  }

  @Test
  void stepsEventsAndTheEndGoToTheFileOfTheNetwork() throws IOException {
    recorder.decided(decision(NETWORK));
    recorder.record(hit());
    recorder.endFight(GROUP, 110, FightWinner.PLAYER);

    assertThat(linesOf(NETWORK))
        .hasSize(3)
        .satisfiesExactly(
            line -> assertThat(line).contains("\"t\":\"step\""),
            line -> assertThat(line).contains("\"t\":\"event\""),
            line -> assertThat(line).contains("\"winner\":\"PLAYER\""));
  }

  @Test
  void eachNetworkHasItsOwnFile() throws IOException {
    recorder.decided(decision(NETWORK));

    assertThat(linesOf("cerco-1")).isEmpty();
    assertThat(linesOf(NETWORK)).isNotEmpty();
  }

  @Test
  void nothingIsWrittenOutsideAFight() throws IOException {
    recorder.record(hit());
    recorder.endFight(GROUP, 110, FightWinner.MOBS);

    assertThat(linesOf(NETWORK)).isEmpty();
  }

  @Test
  void aFightEndsOnlyOnce() throws IOException {
    recorder.decided(decision(NETWORK));
    recorder.endFight(GROUP, 110, FightWinner.MOBS);
    recorder.endFight(GROUP, 111, FightWinner.MOBS);

    assertThat(linesOf(NETWORK)).filteredOn(line -> line.contains("\"end\"")).hasSize(1);
  }

  @Test
  void theSwitchInTheConfigurationStopsTheSteps() throws IOException {
    NeuralSettings on = neural.get();
    neural.set(new NeuralSettings(false, on.defaultNetwork(), 100, on.arena()));

    recorder.decided(decision(NETWORK));

    assertThat(linesOf(NETWORK)).isEmpty();
  }

  @Test
  void aGroupThatVanishesInAFightEndsItWithNoWinner() throws IOException {
    recorder.decided(decision(NETWORK));
    activeGroups.remove(GROUP);

    recorder.closeVanishedFights(120);

    assertThat(linesOf(NETWORK)).last().asString().contains("\"tick\":120", "\"winner\":\"NONE\"");
  }

  @Test
  void aGroupStillThereKeepsItsFightOpen() throws IOException {
    recorder.decided(decision(NETWORK));

    recorder.closeVanishedFights(120);
    recorder.record(hit());

    assertThat(linesOf(NETWORK)).anyMatch(line -> line.contains("\"t\":\"event\""));
  }

  @Test
  void shuttingDownEndsTheOpenFightsWithNoWinner() throws IOException {
    recorder.decided(decision(NETWORK));

    assertThat(linesOf(NETWORK)).last().asString().contains("\"tick\":999", "\"winner\":\"NONE\"");
  }
}
