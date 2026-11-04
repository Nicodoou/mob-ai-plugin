package io.github.nicodoou.mobai.adapter.neural;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.debug.LineFileWriter;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class ExperienceListenerTest {
  private static final String NETWORK = "turnos-1";
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));
  private static final MobId MEMBER = new MobId(new UUID(1, 1));
  private static final MobId OUTSIDER = new MobId(new UUID(1, 2));
  private static final GroupId GROUP = new GroupId(new UUID(0, 1));

  @TempDir Path folder;

  private final MobAiSettings settings = TestSettings.defaults();
  private final ActiveGroups activeGroups = new ActiveGroups();
  private ExperienceRecorder recorder;
  private ExperienceListener listener;

  @BeforeEach
  void startFight() {
    Group group =
        new Group(
            GROUP,
            SelectionPolicyType.RANDOM,
            new GroupKnowledge(
                new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
    group.roster().addMember(MEMBER, MobKind.ZOMBIE);
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(
            new PlanStart(
                new StrategyId("NEURAL"),
                PLAYER,
                Map.of(MEMBER, Role.PRESS),
                20.0,
                100,
                Optional.empty()));
    activeGroups.add(group);
    recorder =
        new ExperienceRecorder(
            network ->
                new LineFileWriter(
                    folder.resolve(network).resolve("experience.jsonl"), NOPLogger.NOP_LOGGER),
            settings::neural,
            activeGroups);
    recorder.decided(
        new NeuralDecided(
            GROUP,
            100,
            NETWORK,
            List.of(new NeuralStep(MEMBER, MobKind.ZOMBIE, List.of(0.0), NeuralAction.PRESS, -1))));
    listener = new ExperienceListener(recorder, activeGroups, new FakeServerClock(110));
  }

  @Test
  void aTargetKilledByTheGroupIsAWinForTheMobs() throws IOException {
    listener.targetDied(PLAYER, Optional.of(MEMBER));

    assertThat(lastLine()).contains("\"winner\":\"MOBS\"");
  }

  @Test
  void aTargetKilledByAMonsterOfNoGroupIsNoWinForAnyone() throws IOException {
    listener.targetDied(PLAYER, Optional.of(OUTSIDER));

    assertThat(lastLine()).contains("\"winner\":\"NONE\"");
  }

  @Test
  void aTargetKilledByTheWorldIsNoWinForAnyone() throws IOException {
    listener.targetDied(PLAYER, Optional.empty());

    assertThat(lastLine()).contains("\"winner\":\"NONE\"");
  }

  private String lastLine() throws IOException {
    recorder.shutdown(999);
    return Files.readAllLines(folder.resolve(NETWORK).resolve("experience.jsonl")).getLast();
  }
}
