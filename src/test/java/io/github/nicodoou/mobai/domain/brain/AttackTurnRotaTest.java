package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.AttackTurn;
import io.github.nicodoou.mobai.domain.group.AttackTurns;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttackTurnRotaTest {
  private static final double GROUND_Y = 64;
  private static final double FULL_HEALTH = 20;
  private static final long TICK = 1000;
  private static final long TIMEOUT_TICKS = 60;
  private static final long REST_TICKS = 30;
  private static final MobId NEAR = mob(1);
  private static final MobId MIDDLE = mob(2);
  private static final MobId FAR = mob(3);
  private static final MobId FURTHEST = mob(4);

  private final AttackTurnRota rota =
      new AttackTurnRota(
          () ->
              new PackSettings(
                  8.0, 4.0, 0.25, 1.0, 8.0, 32.0, 2, TIMEOUT_TICKS, REST_TICKS, 1.7, 1.7, 1.5));
  private final GroupSnapshotBuilder snapshot =
      new GroupSnapshotBuilder()
          .withTick(TICK)
          .withPlayer(new PlayerSnapshotBuilder().withId(ALICE).withPosition(at(0)).build());
  private final List<RoleAssignment> orders = new ArrayList<>();
  private Plan plan =
      Plan.start(
          new PlanId(GROUP_ID, 1),
          new PlanStart(
              new StrategyId("DIRECT_ASSAULT"), ALICE, Map.of(), 20.0, TICK, Optional.empty()));

  @Test
  void closestFightersTakeTheTurns() {
    fighters();

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(NEAR, MIDDLE);
  }

  @Test
  void ordersOfHoldersCarryTheTurnAndKeepTheirRole() {
    fighters();

    List<RoleAssignment> dealt = deal().orders();

    assertThat(dealt)
        .extracting(RoleAssignment::holdsAttackTurn)
        .containsExactly(true, true, false);
    assertThat(dealt).extracting(RoleAssignment::role).containsOnly(Role.PRESS);
  }

  @Test
  void aFlankerTakesATurnLikeAnyOtherFighter() {
    add(NEAR, Role.FLANK, 2);
    add(MIDDLE, Role.PRESS, 5);
    add(FAR, Role.PRESS, 6);

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(NEAR, MIDDLE);
    assertThat(dealt.orders().getFirst().role()).isEqualTo(Role.FLANK);
  }

  @Test
  void fightersOrderedAwayTakeNoTurn() {
    add(NEAR, Role.FALL_BACK, 1);
    add(MIDDLE, Role.GATHER, 2);
    add(FAR, Role.SHOOT, 3);
    add(FURTHEST, Role.PRESS, 9);

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(FURTHEST);
    assertThat(dealt.orders())
        .filteredOn(RoleAssignment::holdsAttackTurn)
        .extracting(RoleAssignment::mob)
        .containsExactly(FURTHEST);
  }

  @Test
  void aHolderKeepsItsTurnWhenAnotherComesCloser() {
    fighters();
    plan = plan.withAttackTurns(heldBy(FAR, MIDDLE));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(FAR, MIDDLE);
    assertThat(dealt.turns().holders().get(FAR).grantedTick()).isEqualTo(TICK - 10);
  }

  @Test
  void aStrikerRestsSoTheNextClosestTakesItsTurn() {
    fighters();
    plan = plan.withAttackTurns(heldBy(NEAR, MIDDLE).released(NEAR, TICK - 5));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE, FAR);
  }

  @Test
  void aRestingFighterStrikesAgainWhenNobodyElseIsFree() {
    add(NEAR, Role.PRESS, 2);
    plan = plan.withAttackTurns(heldBy(NEAR).released(NEAR, TICK - 5));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(NEAR);
  }

  @Test
  void restEndsAfterItsTicks() {
    fighters();
    plan = plan.withAttackTurns(heldBy(MIDDLE).released(NEAR, TICK - REST_TICKS));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE, NEAR);
  }

  @Test
  void aHitHolderLosesItsTurnToTheNextOne() {
    addHurt(NEAR, 2);
    add(MIDDLE, Role.PRESS, 3);
    add(FAR, Role.PRESS, 4);
    plan = plan.withAttackTurns(heldBy(NEAR, MIDDLE));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE, FAR);
    assertThat(dealt.turns().lastReleaseTicks()).containsEntry(NEAR, TICK);
  }

  @Test
  void aTurnRunsOutAfterItsTimeout() {
    fighters();
    plan =
        plan.withAttackTurns(
            AttackTurns.none()
                .granted(NEAR, new AttackTurn(TICK - TIMEOUT_TICKS, FULL_HEALTH))
                .granted(MIDDLE, new AttackTurn(TICK - TIMEOUT_TICKS + 1, FULL_HEALTH)));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE, FAR);
  }

  @Test
  void aHolderOrderedAwayGivesItsTurnUp() {
    add(NEAR, Role.FALL_BACK, 2);
    add(MIDDLE, Role.PRESS, 3);
    plan = plan.withAttackTurns(heldBy(NEAR));

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE);
  }

  @Test
  void fewerFightersThanTurnsAllHoldOne() {
    add(NEAR, Role.PRESS, 9);

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(NEAR);
  }

  @Test
  void aTieGoesToTheLowestId() {
    add(FAR, Role.PRESS, 5);
    add(MIDDLE, Role.PRESS, 5);
    add(NEAR, Role.PRESS, 5);

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(NEAR, MIDDLE);
  }

  @Test
  void aSpiderOnAnotherTargetAttacksWithoutTakingATurn() {
    orders.add(order(NEAR, Role.PRESS, BOB));
    snapshot.withMob(new MobSnapshotBuilder().withId(NEAR).withPosition(at(1)).build());
    add(MIDDLE, Role.PRESS, 5);
    add(FAR, Role.PRESS, 6);

    DealtTurns dealt = deal();

    assertThat(dealt.turns().holders()).containsOnlyKeys(MIDDLE, FAR);
    assertThat(dealt.orders().getFirst().holdsAttackTurn()).isTrue();
  }

  @Test
  void aTargetOutOfTheSnapshotChangesNothing() {
    GroupSnapshot withoutTarget = new GroupSnapshotBuilder().withTick(TICK).build();
    plan = plan.withAttackTurns(heldBy(NEAR));

    DealtTurns dealt = rota.deal(List.of(), withoutTarget, plan);

    assertThat(dealt.turns()).isEqualTo(plan.attackTurns());
  }

  @Test
  void theSameInputDealsTheSameTurns() {
    fighters();

    assertThat(deal()).isEqualTo(deal());
  }

  private void fighters() {
    add(NEAR, Role.PRESS, 2);
    add(MIDDLE, Role.PRESS, 3);
    add(FAR, Role.PRESS, 4);
  }

  private void add(MobId id, Role role, double distance) {
    snapshot.withMob(new MobSnapshotBuilder().withId(id).withPosition(at(distance)).build());
    orders.add(order(id, role, ALICE));
  }

  private void addHurt(MobId id, double distance) {
    snapshot.withMob(
        new MobSnapshotBuilder()
            .withId(id)
            .withPosition(at(distance))
            .withHealth(FULL_HEALTH - 1)
            .build());
    orders.add(order(id, Role.PRESS, ALICE));
  }

  private DealtTurns deal() {
    return rota.deal(orders, snapshot.build(), plan);
  }

  private static AttackTurns heldBy(MobId... holders) {
    AttackTurns turns = AttackTurns.none();
    for (MobId holder : holders) {
      turns = turns.granted(holder, new AttackTurn(TICK - 10, FULL_HEALTH));
    }
    return turns;
  }

  private static RoleAssignment order(MobId id, Role role, PlayerId target) {
    return new RoleAssignment(
        id, role, Optional.of(target), Optional.empty(), false, Optional.empty(), false);
  }

  private static Vec3 at(double distance) {
    return new Vec3(0, GROUND_Y, distance);
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(4, id));
  }
}
