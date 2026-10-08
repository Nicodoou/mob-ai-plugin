package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.DangerObservation;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.port.StoredAttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredMemoriesMapperTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final SettingsHolder settings = new SettingsHolder(TestSettings.defaults());
  private final StoredMemoriesMapper mapper = new StoredMemoriesMapper();

  @Test
  void toStoredKeepsMembersPolicyAndPlanSequence() {
    Group group = groupWithMemory(1);
    group.lifecycle().restorePlanSequence(5);

    StoredGroup stored = mapper.toStored(group);

    assertThat(stored.lastPlanSequence()).isEqualTo(5);
    assertThat(stored.policy()).isEqualTo(SelectionPolicyType.THOMPSON_SAMPLING);
    assertThat(stored.members())
        .containsExactly(
            new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.SKELETON, 2));
  }

  @Test
  void toStoredOrdersRecordsByPlayerThenAttack() {
    Group group = newGroup(1);
    group.memory().recordAttack(new AttackObservation(otherPlayer, Attack.SPIDER_BITE, 1.0, 100));
    group
        .memory()
        .recordAttack(new AttackObservation(player, Attack.SKELETON_DIRECT_SHOT, 0.0, 100));
    group
        .memory()
        .recordAttack(new AttackObservation(player, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 100));

    StoredGroup stored = mapper.toStored(group);

    assertThat(stored.attackRecords())
        .extracting(StoredAttackRecord::player, StoredAttackRecord::attack)
        .containsExactly(
            tuple(player, Attack.ZOMBIE_FRONT_STRIKE),
            tuple(player, Attack.SKELETON_DIRECT_SHOT),
            tuple(otherPlayer, Attack.SPIDER_BITE));
  }

  @Test
  void dangerRecordsSurviveTheMapper() {
    Group group = newGroup(1);
    group.memory().recordDanger(new DangerObservation(player, 40, 10, 1_000));

    Group restored = mapper.toGroup(mapper.toStored(group), settings);

    assertThat(restored.memory().dangerRecord(player, 1_000))
        .isEqualTo(new DangerRecord(40, 10, 1_000));
  }

  @Test
  void storedGroupRoundTripsThroughARestoredGroup() {
    Group group = groupWithMemory(1);
    group.lifecycle().restorePlanSequence(5);
    StoredGroup stored = mapper.toStored(group);

    StoredGroup roundTripped = mapper.toStored(mapper.toGroup(stored, settings));

    assertThat(roundTripped).isEqualTo(stored);
  }

  @Test
  void restoredGroupStartsObservingWithEmptyThreat() {
    Group source = groupWithMemory(1);
    source.lifecycle().restorePlanSequence(5);
    source.threat().recordDamage(player, 4.0, 100);

    Group restored = mapper.toGroup(mapper.toStored(source), settings);

    assertThat(restored.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(restored.threat().trackedPlayers()).isEmpty();
    restored.lifecycle().beginPlanning();
    long nextSequence = restored.lifecycle().startPlan(pressStart()).id().sequence();
    assertThat(nextSequence).isEqualTo(6);
  }

  private PlanStart pressStart() {
    return new PlanStart(
        new StrategyId("DIRECT_ASSAULT"),
        player,
        Map.of(mob(1), Role.PRESS),
        20,
        100,
        Optional.empty());
  }

  private Group groupWithMemory(long n) {
    Group group = newGroup(n);
    group.roster().addMember(mob(1), MobKind.ZOMBIE);
    group.roster().addMember(mob(2), MobKind.SKELETON);
    GroupMemory memory = group.memory();
    memory.recordAttack(new AttackObservation(player, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 100));
    memory.recordAttack(new AttackObservation(player, Attack.SKELETON_DIRECT_SHOT, 0.0, 100));
    memory.recordStrategy(new StrategyObservation(player, new StrategyId("FLANK"), 0.4, 1.0, 100));
    return group;
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
