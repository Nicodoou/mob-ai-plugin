package io.github.nicodoou.mobai.adapter.recording;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.adapter.debug.TraceLevels;
import io.github.nicodoou.mobai.adapter.goal.RoleRegistry;
import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RecordingScopeTest {
  private static final int RECORDER_EVENTS = 10;
  private static final int INTERVAL_TICKS = 5;
  private static final GroupId G1 = new GroupId(new UUID(0, 1));
  private static final GroupId G2 = new GroupId(new UUID(0, 2));
  private static final MobId M1 = new MobId(new UUID(1, 1));
  private static final MobId M2 = new MobId(new UUID(1, 2));
  private static final PlayerId ALICE = new PlayerId(new UUID(2, 1));
  private static final PlayerId BOB = new PlayerId(new UUID(2, 2));

  private final MobAiSettings settings = TestSettings.defaults();
  private final AtomicReference<TraceLevel> configured = new AtomicReference<>(TraceLevel.OFF);
  private final TraceLevels levels =
      new TraceLevels(() -> new DebugSettings(configured.get(), RECORDER_EVENTS, INTERVAL_TICKS));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final RoleRegistry roles = new RoleRegistry();
  private final RecipeBase recipeBase = new RecipeBase();
  private final RecordingScope scope =
      new RecordingScope(activeGroups, roles, new RecordingScope.ScopeRules(levels, recipeBase));

  @Test
  void nothingIsRecordedWhenOff() {
    addGroup(G1, M1);
    recipeBase.startTraining(ALICE);
    roles.assign(orderAgainst(M1, ALICE));

    assertThat(scope.isActive()).isFalse();
    assertThat(scope.recordedGroupOf(M1)).isEmpty();
    assertThat(scope.recordsPlayer(ALICE)).isFalse();
  }

  @Test
  void aMobOfAFullGroupIsRecorded() {
    addGroup(G1, M1);
    levels.set(G1, TraceLevel.FULL);

    assertThat(scope.recordedGroupOf(M1)).contains(G1);
  }

  @Test
  void aMobOutsideAnyGroupIsNotRecorded() {
    levels.setAll(TraceLevel.FULL);

    assertThat(scope.recordedGroupOf(M1)).isEmpty();
  }

  @Test
  void aMobOfAnotherLevelIsNotRecorded() {
    addGroup(G1, M1);
    addGroup(G2, M2);
    levels.set(G1, TraceLevel.FULL);
    levels.set(G2, TraceLevel.DECISIONS);

    assertThat(scope.recordedGroupOf(M2)).isEmpty();
  }

  @Test
  void aTargetOfARecordedGroupIsRecorded() {
    addGroup(G1, M1);
    levels.set(G1, TraceLevel.FULL);
    roles.assign(orderAgainst(M1, ALICE));

    assertThat(scope.recordsPlayer(ALICE)).isTrue();
  }

  @Test
  void aTargetOfAnUnrecordedGroupIsNotRecorded() {
    addGroup(G1, M1);
    addGroup(G2, M2);
    levels.set(G1, TraceLevel.FULL);
    levels.set(G2, TraceLevel.OFF);
    roles.assign(orderAgainst(M2, BOB));

    assertThat(scope.recordsPlayer(BOB)).isFalse();
  }

  @Test
  void aTrainingPlayerIsRecordedWhileActive() {
    levels.setAll(TraceLevel.FULL);
    recipeBase.startTraining(ALICE);

    assertThat(scope.recordsPlayer(ALICE)).isTrue();
  }

  @Test
  void aBystanderIsNotRecorded() {
    levels.setAll(TraceLevel.FULL);

    assertThat(scope.recordsPlayer(BOB)).isFalse();
  }

  private void addGroup(GroupId id, MobId mob) {
    Group group =
        new Group(
            id,
            SelectionPolicyType.THOMPSON_SAMPLING,
            new GroupKnowledge(
                new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
    group.roster().addMember(mob, MobKind.ZOMBIE);
    activeGroups.add(group);
  }

  private static RoleAssignment orderAgainst(MobId mob, PlayerId target) {
    return new RoleAssignment(
        mob, Role.PRESS, Optional.of(target), Optional.empty(), false, Optional.empty());
  }
}
