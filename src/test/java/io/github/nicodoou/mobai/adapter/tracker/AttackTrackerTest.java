package io.github.nicodoou.mobai.adapter.tracker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.application.RecordOutcome;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.domain.attack.AttackClassifier;
import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.attack.NeutralCause;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
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

class AttackTrackerTest {
  private static final Attack ATTACK = Attack.ZOMBIE_FRONT_STRIKE;
  private static final long TICK = 100;

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final SettingsHolder settings = new SettingsHolder(TestSettings.defaults());
  private final Group group = newGroup(1);
  private final AttackTracker tracker =
      new AttackTracker(new RecordOutcome(activeGroups, settings), new AttackClassifier());

  AttackTrackerTest() {
    activeGroups.add(group);
    activeGroups.join(groupId(1), mob(1), MobKind.ZOMBIE);
  }

  @Test
  void hitRecordsAHit() {
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 3.0, false, false));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome()).isInstanceOf(AttackOutcome.Hit.class);
    assertThat(result.get().trace().rule()).isEqualTo(6);
    assertThat(recordOfPlayer()).isEqualTo(new AttackRecord(1.0, 1.0, 100));
  }

  @Test
  void blockedHitRecordsAPartial() {
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 0.0, true, false));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome()).isInstanceOf(AttackOutcome.Partial.class);
    assertThat(result.get().trace().rule()).isEqualTo(7);
    assertThat(recordOfPlayer().successes()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void noEventRecordsAMiss() {
    tracker.openMelee(opening(false));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome()).isInstanceOf(AttackOutcome.Miss.class);
    assertThat(result.get().trace().rule()).isEqualTo(8);
    assertThat(recordOfPlayer()).isEqualTo(new AttackRecord(0.0, 1.0, 100));
  }

  @Test
  void invulnerableTargetWithoutEventIsNeutral() {
    tracker.openMelee(opening(true));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.TARGET_INVULNERABLE));
    assertThat(result.get().trace().rule()).isEqualTo(3);
    assertThat(group.memory().attackRecords()).doesNotContainKey(player);
  }

  @Test
  void cancelledDamageIsNeutral() {
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 3.0, false, true));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.DAMAGE_CANCELLED));
    assertThat(result.get().trace().rule()).isEqualTo(2);
  }

  @Test
  void invalidTargetIsNeutral() {
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 3.0, false, false));

    Optional<Classification> result = tracker.closeMelee(mob(1), false, TICK);

    assertThat(result.orElseThrow().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.TARGET_INVALID));
    assertThat(result.get().trace().rule()).isEqualTo(1);
  }

  @Test
  void hitOnAnotherPlayerIsIgnored() {
    tracker.openMelee(opening(false));

    boolean accepted = tracker.recordHit(new MeleeHit(mob(1), otherPlayer, 3.0, false, false));
    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(accepted).isFalse();
    assertThat(result.orElseThrow().outcome()).isInstanceOf(AttackOutcome.Miss.class);
  }

  @Test
  void onlyTheFirstEventCounts() {
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 0.0, true, false));
    tracker.recordHit(new MeleeHit(mob(1), player, 3.0, false, false));

    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result.orElseThrow().outcome()).isInstanceOf(AttackOutcome.Partial.class);
  }

  @Test
  void secondOpeningIsRejected() {
    tracker.openMelee(opening(false));

    assertThatThrownBy(() -> tracker.openMelee(opening(false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Mob " + mob(1).shortId() + " already has an open melee attempt");
  }

  @Test
  void closingWithoutAnOpenAttemptReturnsEmpty() {
    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result).isEmpty();
    assertThat(group.memory().attackRecords()).doesNotContainKey(player);
  }

  @Test
  void cancelDiscardsWithoutRecording() {
    tracker.openMelee(opening(false));

    tracker.cancel(mob(1));
    Optional<Classification> result = tracker.closeMelee(mob(1), true, TICK);

    assertThat(result).isEmpty();
    assertThat(tracker.openAttempts()).isZero();
    assertThat(group.memory().attackRecords()).doesNotContainKey(player);
  }

  @Test
  void everyAttemptEndsExactlyOnce() {
    AttemptId first = tracker.openMelee(opening(false));

    Optional<Classification> firstClose = tracker.closeMelee(mob(1), true, TICK);
    Optional<Classification> secondClose = tracker.closeMelee(mob(1), true, TICK);
    AttemptId second = tracker.openMelee(opening(false));

    assertThat(first).isEqualTo(new AttemptId(1));
    assertThat(second).isEqualTo(new AttemptId(2));
    assertThat(firstClose).isPresent();
    assertThat(secondClose).isEmpty();
    assertThat(recordOfPlayer().attempts()).isCloseTo(1.0, within(1e-9));
  }

  @Test
  void planDamageCountsOnlyUncancelledHits() {
    startPlanAgainstPlayer();
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 3.0, false, false));
    tracker.closeMelee(mob(1), true, TICK);
    tracker.openMelee(opening(false));
    tracker.recordHit(new MeleeHit(mob(1), player, 4.0, false, true));
    tracker.closeMelee(mob(1), true, TICK);

    double damage = group.lifecycle().plan().orElseThrow().damageDealt();

    assertThat(damage).isCloseTo(3.0, within(1e-9));
  }

  private AttackRecord recordOfPlayer() {
    return group.memory().attackRecords().get(player).get(ATTACK);
  }

  private MeleeOpening opening(boolean targetInvulnerable) {
    return new MeleeOpening(mob(1), player, ATTACK, TICK, targetInvulnerable);
  }

  private void startPlanAgainstPlayer() {
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(
            new PlanStart(
                new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20.0, 100));
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
