package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.attack.NeutralCause;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
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

class RecordOutcomeTest {
  private static final Attack ATTACK = Attack.ZOMBIE_FRONT_STRIKE;

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final SettingsHolder settings = new SettingsHolder(TestSettings.defaults());
  private final RecordOutcome recordOutcome = new RecordOutcome(activeGroups, settings);
  private final Group group = newGroup(1);

  RecordOutcomeTest() {
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
  }

  @Test
  void hitRecordsFullCreditForTheTarget() {
    Optional<RecordChange> change =
        recordOutcome.execute(resolution(mob(1), player, new AttackOutcome.Hit(), 0.0));

    assertThat(change).isPresent();
    assertThat(change.get().after()).isEqualTo(new AttackRecord(1.0, 1.0, 100));
  }

  @Test
  void partialRecordsThePartialWeightInForce() {
    MobAiSettings defaults = TestSettings.defaults();
    settings.replace(
        new MobAiSettings(
            defaults.group(),
            new MemorySettings(12_000, 0.7, 0.25),
            defaults.selection(),
            defaults.target(),
            defaults.plan(),
            defaults.attack(),
            defaults.spider(),
            defaults.persistence(),
            defaults.debug(),
            defaults.retreat()));

    Optional<RecordChange> change =
        recordOutcome.execute(resolution(mob(1), player, new AttackOutcome.Partial(), 0.0));

    assertThat(change).isPresent();
    assertThat(change.get().after().successes()).isCloseTo(0.25, within(1e-9));
    assertThat(change.get().after().attempts()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void missRecordsAnAttemptWithoutSuccess() {
    Optional<RecordChange> change =
        recordOutcome.execute(resolution(mob(1), player, new AttackOutcome.Miss(), 0.0));

    assertThat(change).isPresent();
    assertThat(change.get().after()).isEqualTo(new AttackRecord(0.0, 1.0, 100));
  }

  @Test
  void neutralRecordsNothing() {
    AttackOutcome neutral = new AttackOutcome.Neutral(NeutralCause.INTERRUPTED);

    Optional<RecordChange> change = recordOutcome.execute(resolution(mob(1), player, neutral, 0.0));

    assertThat(change).isEmpty();
    assertThat(group.memory().attackRecords()).isEmpty();
  }

  @Test
  void outcomeOfALooseMobRecordsNothing() {
    Optional<RecordChange> change =
        recordOutcome.execute(resolution(mob(7), player, new AttackOutcome.Hit(), 0.0));

    assertThat(change).isEmpty();
  }

  @Test
  void damageAgainstThePlanTargetAddsToThePlan() {
    startPlanAgainstPlayer();

    recordOutcome.execute(resolution(mob(1), player, new AttackOutcome.Hit(), 6.0));

    assertThat(group.lifecycle().plan().orElseThrow().damageDealt()).isCloseTo(6.0, within(1e-9));
  }

  @Test
  void damageAgainstAnotherPlayerDoesNotTouchThePlan() {
    startPlanAgainstPlayer();

    recordOutcome.execute(resolution(mob(1), otherPlayer, new AttackOutcome.Hit(), 6.0));

    assertThat(group.lifecycle().plan().orElseThrow().damageDealt()).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void zeroDamageDoesNotTouchThePlan() {
    startPlanAgainstPlayer();

    recordOutcome.execute(resolution(mob(1), player, new AttackOutcome.Miss(), 0.0));

    assertThat(group.lifecycle().plan().orElseThrow().damageDealt()).isCloseTo(0.0, within(1e-9));
  }

  private void startPlanAgainstPlayer() {
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(
            new PlanStart(
                new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20.0, 100));
  }

  private static AttackResolution resolution(
      MobId mob, PlayerId target, AttackOutcome outcome, double damage) {
    return new AttackResolution(mob, target, ATTACK, outcome, damage, 100);
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
