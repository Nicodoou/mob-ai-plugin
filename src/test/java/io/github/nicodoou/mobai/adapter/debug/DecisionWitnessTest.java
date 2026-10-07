package io.github.nicodoou.mobai.adapter.debug;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.GroupEvents;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.application.RecordingRandomSource;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.application.TickGroups;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.event.DomainEventPublisher;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import io.github.nicodoou.mobai.testsupport.TraceReplay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.helpers.NOPLogger;

class DecisionWitnessTest {
  private static final long SEED = 7;
  private static final long MID_PLAN_TICK = START_TICK + 20;

  @TempDir Path folder;

  private final MobAiSettings settings = TestSettings.defaults();
  private final SettingsHolder holder = new SettingsHolder(settings);
  private final RegroupWindow window = new RegroupWindow(settings::retreat);
  private final RecordingRandomSource draws =
      new RecordingRandomSource(new SeededRandomSource(SEED));
  private final Brain brain =
      new Brain(() -> settings, BrainParts.standard(() -> settings, draws, window));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DomainEventPublisher publisher = new DomainEventPublisher();
  private final GroupEvents groupEvents = new GroupEvents(publisher);
  private final TickGroups tickGroups = new TickGroups(activeGroups, brain, groupEvents);
  private final List<MobSnapshot> mobs = BrainFixture.catalogMobs();
  private final Group group = newGroup();

  private TraceHub hub;
  private IncidentWriter writer;
  private DecisionWitness witness;

  @BeforeEach
  void decideTwiceAndTakeDamage() {
    FlightRecorder recorder = new FlightRecorder(settings::debug);
    hub = new TraceHub(activeGroups, destinations(recorder));
    writer = new IncidentWriter(folder, NOPLogger.NOP_LOGGER);
    witness =
        new DecisionWitness(new WitnessParts(groupEvents, draws, window, holder), hub, writer);
    mobs.forEach(mob -> group.roster().addMember(mob.id(), mob.kind()));
    activeGroups.add(group);
    decide(START_TICK);
    decide(START_TICK + 10);
    group.threat().recordDamage(ALICE, 3.0, START_TICK + 12);
  }

  @Test
  void pendingEventsArePublishedBeforeTheCopy() {
    List<LeaderDied> received = new ArrayList<>();
    publisher.subscribe(LeaderDied.class, received::add);
    group.removeMember(group.roster().leader().orElseThrow(), MID_PLAN_TICK);

    witness.before(group, BrainFixture.snapshot(MID_PLAN_TICK, mobs, alice()));

    assertThat(received).hasSize(1);
    assertThat(group.drainEvents()).isEmpty();
  }

  @Test
  void successIsRecordedInTheFlightRecorder() {
    decide(MID_PLAN_TICK);

    List<TraceEvent> recent = hub.recent(GROUP_ID);

    assertThat(recent.getLast()).isInstanceOf(TraceEvent.DecisionEvent.class);
    assertThat(recent.getLast().tick()).isEqualTo(MID_PLAN_TICK);
  }

  @Test
  void failureWritesAnIncidentThatReproduces() throws IOException {
    IncidentReport report = failedIncident();
    writer.shutdown();

    Path file = folder.resolve("incident-" + report.id() + ".json");

    assertThat(file).exists();
    TraceReplay.assertReproduces(new IncidentJson().read(Files.readString(file)));
  }

  @Test
  void failureWritesTheBlackBox() throws IOException {
    IncidentReport report = failedIncident();
    writer.shutdown();

    Path file = folder.resolve("incident-" + report.id() + "-blackbox.json");

    assertThat(file).exists();
    assertThat(Files.readString(file)).contains("\"DecisionEvent\"");
  }

  @Test
  void incidentKeepsTheCopyFromBeforeTheDecision() {
    GroupSnapshot snapshot = snapshotWithStranger();
    Observation observation = witness.before(group, snapshot);
    group.threat().recordDamage(ALICE, 2.0, MID_PLAN_TICK);

    IncidentReport report =
        witness.failed(group, observation, new IllegalStateException("failed half way"));
    writer.shutdown();

    assertThat(report.before()).isEqualTo(observation.before());
    assertThat(report.after()).isNotEqualTo(report.before());
  }

  private TraceDestinations destinations(FlightRecorder recorder) {
    LineFileWriter traceLines =
        new LineFileWriter(folder.resolve("trace.jsonl"), NOPLogger.NOP_LOGGER);
    LineFileWriter debugLines =
        new LineFileWriter(folder.resolve("debug.log"), NOPLogger.NOP_LOGGER);
    return new TraceDestinations(
        recorder,
        new TraceLevels(settings::debug),
        new TraceWriter(traceLines),
        new DebugLog(debugLines));
  }

  private IncidentReport failedIncident() {
    GroupSnapshot snapshot = snapshotWithStranger();
    Observation observation = witness.before(group, snapshot);
    return witness.failed(group, observation, failureOf(snapshot));
  }

  private RuntimeException failureOf(GroupSnapshot snapshot) {
    try {
      tickGroups.execute(snapshot);
    } catch (RuntimeException exception) {
      return exception;
    }
    throw new AssertionError("The decision was expected to fail at tick " + snapshot.tick());
  }

  private GroupSnapshot snapshotWithStranger() {
    List<MobSnapshot> withStranger = new ArrayList<>(mobs);
    withStranger.add(new MobSnapshotBuilder().withId(new MobId(new UUID(9, 9))).build());
    return BrainFixture.snapshot(MID_PLAN_TICK, withStranger, alice());
  }

  private void decide(long tick) {
    GroupSnapshot snapshot = BrainFixture.snapshot(tick, mobs, alice());
    Observation observation = witness.before(group, snapshot);
    BrainResult result = tickGroups.execute(snapshot).orElseThrow();
    witness.succeeded(group, observation, result);
  }

  private Group newGroup() {
    return new Group(
        GROUP_ID,
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
  }
}
