package io.github.nicodoou.mobai.testsupport;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;

import io.github.nicodoou.mobai.application.GroupCapture;
import io.github.nicodoou.mobai.application.GroupCaptureMapper;
import io.github.nicodoou.mobai.application.IncidentFailure;
import io.github.nicodoou.mobai.application.IncidentLocation;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.application.RecordingRandomSource;
import io.github.nicodoou.mobai.application.TraitCaptureMapper;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Builds real incidents by recording one decision of the catalog group. */
public final class IncidentFixture {
  private static final long SEED = 7;
  private static final long SECOND_DECISION_OFFSET = 10;
  private static final long DAMAGE_OFFSET = 12;
  private static final double DAMAGE = 3.0;
  private static final long MID_PLAN_OFFSET = 20;
  private static final double LEARNED_FEATURE_VALUE = 0.5;
  private static final double LEARNED_REWARD = 1.0;
  private static final int UNKILLABLE_REGENERATION_LEVEL = 10;
  private static final IncidentLocation LOCATION =
      new IncidentLocation("domain", "Brain", "decide", "TickGroups");

  private final MobAiSettings settings;
  private final RegroupWindow window;
  private final RecordingRandomSource recorder =
      new RecordingRandomSource(new SeededRandomSource(SEED));
  private final BrainParts parts;
  private final Brain brain;
  private final GroupCaptureMapper mapper = new GroupCaptureMapper();
  private final TraitCaptureMapper traits = new TraitCaptureMapper();
  private final List<MobSnapshot> mobs = BrainFixture.catalogMobs();
  private final Group group;

  private IncidentFixture(MobAiSettings settings) {
    this.settings = settings;
    this.window = new RegroupWindow(settings::retreat);
    this.parts = BrainParts.standard(() -> settings, recorder, window);
    this.brain = new Brain(() -> settings, parts);
    this.group = newGroup();
    mobs.forEach(mob -> group.roster().addMember(mob.id(), mob.kind()));
  }

  private static IncidentFixture afterPreviousDecisions(MobAiSettings settings) {
    IncidentFixture fixture = new IncidentFixture(settings);
    fixture.decidePrevious();
    fixture.group.threat().recordDamage(ALICE, DAMAGE, START_TICK + DAMAGE_OFFSET);
    return fixture;
  }

  public static IncidentReport recordedDecision(long tick) {
    IncidentFixture fixture = afterPreviousDecisions(TestSettings.defaults());
    return fixture.record(BrainFixture.snapshot(tick, fixture.mobs, BrainFixture.alice()));
  }

  public static IncidentReport recordedRecipeDecision(long tick) {
    IncidentFixture fixture = new IncidentFixture(TestSettings.withRecipes());
    fixture.group.memory().storeRecipeModel(ALICE, fixture.learnedRecord());
    fixture.decidePrevious();
    fixture.group.threat().recordDamage(ALICE, DAMAGE, START_TICK + DAMAGE_OFFSET);
    return fixture.record(BrainFixture.snapshot(tick, fixture.mobs, BrainFixture.alice()));
  }

  public static IncidentReport provokedFailure() {
    IncidentFixture fixture = afterPreviousDecisions(TestSettings.defaults());
    List<MobSnapshot> withStranger = new ArrayList<>(fixture.mobs);
    withStranger.add(new MobSnapshotBuilder().withId(new MobId(new UUID(9, 9))).build());
    long tick = START_TICK + MID_PLAN_OFFSET;
    return fixture.record(BrainFixture.snapshot(tick, withStranger, BrainFixture.alice()));
  }

  public static IncidentReport unkillablePlayer() {
    IncidentFixture fixture = new IncidentFixture(TestSettings.defaults());
    PlayerSnapshot unkillable =
        new PlayerSnapshotBuilder()
            .withId(ALICE)
            .fullDiamondProtectionFour()
            .withEffect(EffectKind.REGENERATION, UNKILLABLE_REGENERATION_LEVEL)
            .build();
    return fixture.record(BrainFixture.snapshot(START_TICK, fixture.mobs, unkillable));
  }

  private RecipeModelRecord learnedRecord() {
    LinearPosterior prior = parts.recipePlanner().prior();
    double[] features = new double[prior.dimension()];
    Arrays.fill(features, LEARNED_FEATURE_VALUE);
    double noise = settings.learning().modelNoiseVariance();
    LinearPosterior learned = prior.withObservation(features, LEARNED_REWARD, noise);
    return new RecipeModelRecord(learned, START_TICK);
  }

  private void decidePrevious() {
    brain.decide(group, BrainFixture.snapshot(START_TICK, mobs, BrainFixture.alice()));
    long second = START_TICK + SECOND_DECISION_OFFSET;
    brain.decide(group, BrainFixture.snapshot(second, mobs, BrainFixture.alice()));
  }

  private IncidentReport record(GroupSnapshot snapshot) {
    Start start =
        new Start(
            mapper.capture(group),
            window.currentTicks(),
            traits.forSnapshot(parts.traitLedger(), snapshot));
    recorder.clear();
    return report(start, snapshot, decideCatching(snapshot));
  }

  private Decided decideCatching(GroupSnapshot snapshot) {
    try {
      return new Decided(Optional.empty(), Optional.of(brain.decide(group, snapshot)));
    } catch (IllegalArgumentException exception) {
      return new Decided(Optional.of(IncidentFailure.of(exception)), Optional.empty());
    }
  }

  private IncidentReport report(Start start, GroupSnapshot snapshot, Decided decided) {
    return new IncidentReport(
        "test-" + snapshot.tick(),
        snapshot.tick(),
        LOCATION,
        decided.failure(),
        start.before(),
        start.windowTicks(),
        snapshot,
        settings,
        recorder.draws(),
        decided.result(),
        mapper.capture(group),
        window.currentTicks(),
        start.traitsBefore(),
        traits.forSnapshot(parts.traitLedger(), snapshot));
  }

  private Group newGroup() {
    return new Group(
        BrainFixture.GROUP_ID,
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
  }

  private record Start(GroupCapture before, long windowTicks, List<StoredTraits> traitsBefore) {}

  private record Decided(Optional<IncidentFailure> failure, Optional<BrainResult> result) {}
}
