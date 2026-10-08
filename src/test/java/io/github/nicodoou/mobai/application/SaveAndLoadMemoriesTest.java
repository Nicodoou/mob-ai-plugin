package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredMemories;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.InMemoryMemoryRepository;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SaveAndLoadMemoriesTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final StoredState state = new StoredState(500, 650);
  private final SettingsHolder settings = new SettingsHolder(TestSettings.defaults());
  private final ActiveGroups savedGroups = new ActiveGroups();
  private final InMemoryMemoryRepository inner = new InMemoryMemoryRepository();
  private final GuardedMemoryRepository repository = new GuardedMemoryRepository(inner);
  private final SaveMemories saveMemories = new SaveMemories(savedGroups, repository);

  @Test
  void captureCopiesEveryActiveGroupAndTheState() {
    addGroupWithMemory(savedGroups, 1);
    addGroupWithMemory(savedGroups, 2);

    StoredMemories captured = saveMemories.capture(state);

    assertThat(captured.state()).isEqualTo(state);
    assertThat(captured.groups())
        .extracting(StoredGroup::id)
        .containsExactly(groupId(1), groupId(2));
  }

  @Test
  void writeHandsTheMemoriesToTheRepository() {
    addGroupWithMemory(savedGroups, 1);
    StoredMemories captured = saveMemories.capture(state);
    repository.load();

    saveMemories.write(captured);

    assertThat(inner.load().groups()).isEqualTo(captured.groups());
  }

  @Test
  void loadRebuildsTheSavedGroups() {
    addGroupWithMemory(savedGroups, 1);
    addGroupWithMemory(savedGroups, 2);
    inner.save(saveMemories.capture(state));
    ActiveGroups loadedGroups = new ActiveGroups();

    LoadReport report = new LoadMemories(loadedGroups, settings, inner).execute();

    assertThat(report.loadedGroups()).containsExactly(groupId(1), groupId(2));
    Group restored = loadedGroups.groupOf(mob(1)).orElseThrow();
    assertThat(restored.id()).isEqualTo(groupId(1));
    Group original = savedGroups.groupOf(mob(1)).orElseThrow();
    assertThat(restored.memory().attackRecords()).isEqualTo(original.memory().attackRecords());
  }

  @Test
  void loadReturnsTheStoredStateWithoutApplyingIt() {
    addGroupWithMemory(savedGroups, 1);
    inner.save(saveMemories.capture(state));

    LoadReport report = new LoadMemories(new ActiveGroups(), settings, inner).execute();

    assertThat(report.state()).isEqualTo(Optional.of(new StoredState(500, 650)));
  }

  @Test
  void loadSkipsAGroupThatCannotBeRestored() {
    inner.save(
        new StoredMemories(state, List.of(storedGroupOf(1, mob(1)), storedGroupOf(2, mob(1)))));
    ActiveGroups loadedGroups = new ActiveGroups();

    LoadReport report = new LoadMemories(loadedGroups, settings, inner).execute();

    assertThat(report.loadedGroups()).containsExactly(groupId(1));
    assertThat(report.skippedGroups()).containsExactly(groupId(2));
  }

  @Test
  void loadRefusesToRunWithActiveGroups() {
    ActiveGroups busyGroups = new ActiveGroups();
    busyGroups.add(newGroup(1));
    LoadMemories loadMemories = new LoadMemories(busyGroups, settings, inner);

    assertThatThrownBy(loadMemories::execute)
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("LoadMemories must run before any group exists, found 1");
  }

  private static StoredGroup storedGroupOf(long n, MobId mobId) {
    return new StoredGroup(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        0,
        List.of(new Member(mobId, MobKind.ZOMBIE, 1)),
        List.of(),
        List.of(),
        List.of());
  }

  private void addGroupWithMemory(ActiveGroups groups, long n) {
    groups.add(newGroup(n));
    groups.join(groupId(n), mob(2 * n - 1), MobKind.ZOMBIE);
    groups.join(groupId(n), mob(2 * n), MobKind.SKELETON);
    GroupMemory memory = groups.group(groupId(n)).orElseThrow().memory();
    memory.recordAttack(new AttackObservation(player, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 100));
    memory.recordAttack(new AttackObservation(player, Attack.SKELETON_DIRECT_SHOT, 0.0, 100));
    memory.recordStrategy(new StrategyObservation(player, new StrategyId("FLANK"), 0.4, 1.0, 100));
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static Group newGroup(long n) {
    return new Group(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }
}
