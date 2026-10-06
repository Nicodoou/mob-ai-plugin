package io.github.nicodoou.mobai.application;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionRepeatTest {
  private static final long SEED = 7;
  private static final long MID_PLAN_TICK = START_TICK + 20;
  private static final long PLAN_EXPIRED_TICK = START_TICK + 700;

  private final MobAiSettings settings = TestSettings.defaults();
  private final RegroupWindow window = new RegroupWindow(settings::retreat);
  private final RecordingRandomSource recorder =
      new RecordingRandomSource(new SeededRandomSource(SEED));
  private final Brain brain =
      new Brain(() -> settings, BrainParts.standard(() -> settings, recorder, window));
  private final GroupCaptureMapper mapper = new GroupCaptureMapper();
  private final List<MobSnapshot> mobs = BrainFixture.catalogMobs();
  private final Group group = newGroup();

  private GroupCapture before;
  private long windowTicks;

  @BeforeEach
  void decideTwiceAndTakeDamage() {
    mobs.forEach(mob -> group.roster().addMember(mob.id(), mob.kind()));
    brain.decide(group, BrainFixture.snapshot(START_TICK, mobs, alice()));
    brain.decide(group, BrainFixture.snapshot(START_TICK + 10, mobs, alice()));
    group.threat().recordDamage(ALICE, 3.0, START_TICK + 12);
  }

  @Test
  void repeatedDecisionMidPlanIsIdentical() {
    GroupSnapshot snapshot = BrainFixture.snapshot(MID_PLAN_TICK, mobs, alice());

    BrainResult original = decideRecording(snapshot);
    List<RecordedDraw> draws = recorder.draws();
    GroupCapture after = mapper.capture(group);
    Group restored = restoredGroup();
    ReplayRandomSource replay = new ReplayRandomSource(draws);
    BrainResult repeated = replayingBrain(replay).decide(restored, snapshot);

    assertThat(repeated).isEqualTo(original);
    assertThat(mapper.capture(restored)).isEqualTo(after);
    assertThat(replay.remaining()).isZero();
    assertThat(draws).isNotEmpty();
  }

  @Test
  void repeatedDecisionThatClosesThePlanIsIdentical() {
    GroupSnapshot snapshot = BrainFixture.snapshot(PLAN_EXPIRED_TICK, mobs, alice());

    BrainResult original = decideRecording(snapshot);
    List<RecordedDraw> draws = recorder.draws();
    GroupCapture after = mapper.capture(group);
    Group restored = restoredGroup();
    ReplayRandomSource replay = new ReplayRandomSource(draws);
    BrainResult repeated = replayingBrain(replay).decide(restored, snapshot);

    assertThat(repeated).isEqualTo(original);
    assertThat(mapper.capture(restored)).isEqualTo(after);
    assertThat(replay.remaining()).isZero();
    assertThat(draws).isEmpty();
    assertThat(original.closedPlan()).isPresent();
    assertThat(restored.drainEvents()).isEqualTo(group.drainEvents());
  }

  private BrainResult decideRecording(GroupSnapshot snapshot) {
    before = mapper.capture(group);
    windowTicks = window.currentTicks();
    recorder.clear();
    return brain.decide(group, snapshot);
  }

  private Group restoredGroup() {
    return mapper.restore(before, new SettingsHolder(settings));
  }

  private Brain replayingBrain(ReplayRandomSource replay) {
    RegroupWindow restoredWindow = new RegroupWindow(settings::retreat);
    restoredWindow.restore(windowTicks);
    return new Brain(() -> settings, BrainParts.standard(() -> settings, replay, restoredWindow));
  }

  private Group newGroup() {
    return new Group(
        BrainFixture.GROUP_ID,
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
  }
}
