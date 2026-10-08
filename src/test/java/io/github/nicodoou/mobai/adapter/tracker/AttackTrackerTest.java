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
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttackTrackerTest {
  private static final Attack ATTACK = Attack.ZOMBIE_FRONT_STRIKE;
  private static final long TICK = 100;
  private static final TargetValidity VALID = (mob, target) -> true;

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
    activeGroups.join(groupId(1), mob(2), MobKind.SKELETON);
  }

  @Test
  void arrowThatHitsTheTargetIsAHit() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.TARGET);

    boolean accepted =
        tracker.recordProjectileHit(new ProjectileHit(arrow(1), player, 4.0, false, false));
    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(accepted).isTrue();
    assertThat(closures).hasSize(1);
    assertThat(closures.get(0).mob()).isEqualTo(mob(2));
    assertThat(closures.get(0).classification().outcome()).isInstanceOf(AttackOutcome.Hit.class);
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(6);
    assertThat(arrowRecord()).isEqualTo(new AttackRecord(1.0, 1.0, TICK + 6));
    assertThat(tracker.openAttempts()).isZero();
  }

  @Test
  void arrowIntoABlockIsAMiss() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.BLOCK);

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(closures.get(0).classification().outcome()).isInstanceOf(AttackOutcome.Miss.class);
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(8);
  }

  @Test
  void arrowIntoAnAllyIsNeutral() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.ALLY);

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(closures.get(0).classification().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.ALLY_HIT));
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(4);
    assertThat(group.memory().attackRecords()).doesNotContainKey(player);
  }

  @Test
  void arrowAgainstARaisedShieldIsPartial() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.TARGET);
    tracker.recordProjectileHit(new ProjectileHit(arrow(1), player, 0.0, true, false));

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(closures.get(0).classification().outcome())
        .isInstanceOf(AttackOutcome.Partial.class);
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(7);
  }

  @Test
  void arrowInFlightStaysOpenUntilTheTimeout() {
    tracker.openProjectile(shot(arrow(1), false));

    List<ProjectileClosure> early = tracker.closeProjectiles(TICK + 59, 60, VALID);
    int openAfterEarly = tracker.openAttempts();
    List<ProjectileClosure> onTime = tracker.closeProjectiles(TICK + 60, 60, VALID);

    assertThat(early).isEmpty();
    assertThat(openAfterEarly).isEqualTo(1);
    assertThat(onTime.get(0).classification().outcome()).isInstanceOf(AttackOutcome.Miss.class);
    assertThat(onTime.get(0).classification().trace().rule()).isEqualTo(8);
    assertThat(onTime.get(0).classification().trace().facts().timedOut()).isTrue();
  }

  @Test
  void lateLandingAfterTheTimeoutIsIgnored() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.closeProjectiles(TICK + 60, 60, VALID);

    boolean accepted = tracker.recordProjectileContact(arrow(1), ProjectileContact.TARGET);

    assertThat(accepted).isFalse();
    assertThat(tracker.targetOf(arrow(1))).isEmpty();
  }

  @Test
  void damageToAnotherPlayerIsNotRecorded() {
    tracker.openProjectile(shot(arrow(1), false));

    boolean accepted =
        tracker.recordProjectileHit(new ProjectileHit(arrow(1), otherPlayer, 4.0, false, false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.OTHER_ENTITY);
    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(accepted).isFalse();
    assertThat(closures.get(0).classification().outcome()).isInstanceOf(AttackOutcome.Miss.class);
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(8);
  }

  @Test
  void invalidTargetOfAnArrowIsNeutral() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.TARGET);
    tracker.recordProjectileHit(new ProjectileHit(arrow(1), player, 4.0, false, false));

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, (mob, id) -> false);

    assertThat(closures.get(0).classification().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.TARGET_INVALID));
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(1);
  }

  @Test
  void severalArrowsCloseInOpeningOrder() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.openProjectile(shot(arrow(2), false));
    tracker.recordProjectileContact(arrow(2), ProjectileContact.BLOCK);
    tracker.recordProjectileContact(arrow(1), ProjectileContact.BLOCK);

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(closures).hasSize(2);
    assertThat(closures.get(0).classification().trace().facts().attemptId().value())
        .isLessThan(closures.get(1).classification().trace().facts().attemptId().value());
  }

  @Test
  void onlyTheFirstContactCounts() {
    tracker.openProjectile(shot(arrow(1), false));
    tracker.recordProjectileContact(arrow(1), ProjectileContact.ALLY);
    tracker.recordProjectileContact(arrow(1), ProjectileContact.TARGET);
    tracker.recordProjectileHit(new ProjectileHit(arrow(1), player, 4.0, false, false));

    List<ProjectileClosure> closures = tracker.closeProjectiles(TICK + 6, 60, VALID);

    assertThat(closures.get(0).classification().outcome())
        .isEqualTo(new AttackOutcome.Neutral(NeutralCause.ALLY_HIT));
    assertThat(closures.get(0).classification().trace().rule()).isEqualTo(4);
  }

  @Test
  void sameArrowCannotOpenTwice() {
    tracker.openProjectile(shot(arrow(1), false));

    assertThatThrownBy(() -> tracker.openProjectile(shot(arrow(1), false)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Projectile 00000000-0000-0003-0000-000000000001 already has an open attempt");
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

  private static UUID arrow(long n) {
    return new UUID(3, n);
  }

  private ProjectileOpening shot(UUID arrow, boolean invulnerable) {
    return new ProjectileOpening(
        arrow, mob(2), player, Attack.SKELETON_DIRECT_SHOT, TICK, invulnerable);
  }

  private AttackRecord arrowRecord() {
    return group.memory().attackRecords().get(player).get(Attack.SKELETON_DIRECT_SHOT);
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
                new StrategyId("DIRECT_ASSAULT"),
                player,
                Map.of(mob(1), Role.PRESS),
                20.0,
                100,
                Optional.empty()));
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
