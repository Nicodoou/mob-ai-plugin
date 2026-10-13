package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PackCohesionTest {
  private static final double GROUND_Y = 64;
  private static final Vec3 ALICE_POSITION = new Vec3(0, GROUND_Y, 0);
  private static final Vec3 BOB_POSITION = new Vec3(0, GROUND_Y, 40);

  private final PackCohesion cohesion = new PackCohesion(() -> new PackSettings(8.0, 4.0));
  private final List<MobSnapshot> mobs = new ArrayList<>();
  private final List<RoleAssignment> orders = new ArrayList<>();

  @Test
  void leaderTooFarAheadGathers() {
    RoleAssignment zombie = mob(MobKind.ZOMBIE, Role.PRESS, 20);
    RoleAssignment spider = mob(MobKind.SPIDER, Role.PRESS, 10);

    List<RoleAssignment> result = closeRanks();

    assertThat(result.get(0)).isEqualTo(zombie);
    assertThat(result.get(1).role()).isEqualTo(Role.GATHER);
    assertThat(result.get(1).mob()).isEqualTo(spider.mob());
  }

  @Test
  void withinTheSpreadEveryonePresses() {
    mob(MobKind.ZOMBIE, Role.PRESS, 17);
    mob(MobKind.SPIDER, Role.PRESS, 10);

    List<RoleAssignment> result = closeRanks();

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void exactlyTheSpreadDoesNotGather() {
    mob(MobKind.ZOMBIE, Role.PRESS, 18);
    mob(MobKind.SPIDER, Role.PRESS, 10);

    List<RoleAssignment> result = closeRanks();

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void engagedFighterKeepsFighting() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);
    mob(MobKind.SPIDER, Role.PRESS, 3);

    List<RoleAssignment> result = closeRanks();

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void engagedAtTheEdgeKeepsFighting() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);
    mob(MobKind.SPIDER, Role.PRESS, 4);

    List<RoleAssignment> result = closeRanks();

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void flankersCountInThePack() {
    RoleAssignment rear = mob(MobKind.ZOMBIE, Role.FLANK, 20);
    mob(MobKind.SPIDER, Role.PRESS, 10);
    mob(MobKind.ZOMBIE, Role.FLANK, 11);

    List<RoleAssignment> result = closeRanks();

    assertThat(result.get(0)).isEqualTo(rear);
    assertThat(result.get(1).role()).isEqualTo(Role.GATHER);
    assertThat(result.get(2).role()).isEqualTo(Role.GATHER);
  }

  @Test
  void reserveRetreatAndShootersDoNotHoldThePackBack() {
    mob(MobKind.ZOMBIE, Role.FALL_BACK, 30);
    mob(MobKind.ZOMBIE, Role.RETREAT, 30);
    mob(MobKind.SKELETON, Role.SHOOT, 30);
    mob(MobKind.ZOMBIE, Role.PRESS, 10);
    mob(MobKind.SPIDER, Role.PRESS, 12);

    List<RoleAssignment> result = closeRanks();

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void spiderOnAnotherTargetIsIgnored() {
    mobAgainst(MobKind.SPIDER, BOB, new Vec3(0, GROUND_Y, 30));
    mob(MobKind.ZOMBIE, Role.PRESS, 10);
    mob(MobKind.ZOMBIE, Role.PRESS, 12);

    List<RoleAssignment> result = closeRanks(snapshotWithBob());

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void heightDoesNotCount() {
    mobAt(MobKind.SPIDER, Role.PRESS, new Vec3(0, 84, 10));
    mobAt(MobKind.ZOMBIE, Role.PRESS, new Vec3(0, GROUND_Y, 20));

    List<RoleAssignment> result = closeRanks();

    assertThat(result.get(0).role()).isEqualTo(Role.GATHER);
  }

  @Test
  void gatherDropsTheAttackAndKeepsTheRest() {
    RoleAssignment zombie = mob(MobKind.ZOMBIE, Role.PRESS, 20);
    RoleAssignment spider = mob(MobKind.SPIDER, Role.PRESS, 10);

    List<RoleAssignment> result = closeRanks();

    assertThat(result)
        .containsExactly(
            zombie,
            new RoleAssignment(
                spider.mob(),
                Role.GATHER,
                Optional.of(ALICE),
                Optional.empty(),
                false,
                Optional.empty()));
  }

  @Test
  void targetOutOfTheSnapshotChangesNothing() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);
    mob(MobKind.SPIDER, Role.PRESS, 10);

    List<RoleAssignment> result = closeRanks(snapshotWithoutPlayers());

    assertThat(result).isEqualTo(orders);
  }

  private RoleAssignment mob(MobKind kind, Role role, double z) {
    return mobAt(kind, role, new Vec3(0, GROUND_Y, z));
  }

  private RoleAssignment mobAt(MobKind kind, Role role, Vec3 position) {
    return add(kind, position, order(nextId(), role, ALICE, attackOf(kind)));
  }

  private RoleAssignment mobAgainst(MobKind kind, PlayerId target, Vec3 position) {
    return add(kind, position, order(nextId(), Role.PRESS, target, attackOf(kind)));
  }

  private RoleAssignment add(MobKind kind, Vec3 position, RoleAssignment order) {
    mobs.add(
        new MobSnapshotBuilder().withId(order.mob()).withKind(kind).withPosition(position).build());
    orders.add(order);
    return order;
  }

  private MobId nextId() {
    return new MobId(new UUID(2, mobs.size() + 1));
  }

  private static RoleAssignment order(MobId id, Role role, PlayerId target, Attack attack) {
    return new RoleAssignment(
        id, role, Optional.of(target), Optional.of(attack), false, Optional.empty());
  }

  private static Attack attackOf(MobKind kind) {
    return switch (kind) {
      case ZOMBIE -> Attack.ZOMBIE_FRONT_STRIKE;
      case SPIDER -> Attack.SPIDER_BITE;
      case SKELETON -> Attack.SKELETON_DIRECT_SHOT;
    };
  }

  private List<RoleAssignment> closeRanks() {
    return closeRanks(snapshotWithAlice());
  }

  private List<RoleAssignment> closeRanks(GroupSnapshot snapshot) {
    return cohesion.closeRanks(List.copyOf(orders), snapshot, ALICE);
  }

  private GroupSnapshot snapshotWithAlice() {
    return snapshotBuilder().withPlayer(alice()).build();
  }

  private GroupSnapshot snapshotWithBob() {
    return snapshotBuilder()
        .withPlayer(alice())
        .withPlayer(new PlayerSnapshotBuilder().withId(BOB).withPosition(BOB_POSITION).build())
        .build();
  }

  private GroupSnapshot snapshotWithoutPlayers() {
    return snapshotBuilder().build();
  }

  private GroupSnapshotBuilder snapshotBuilder() {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder();
    mobs.forEach(builder::withMob);
    return builder;
  }

  private static PlayerSnapshot alice() {
    return new PlayerSnapshotBuilder().withId(ALICE).withPosition(ALICE_POSITION).build();
  }
}
