package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.strategy.ChaseAnchor;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChaseLeashTest {
  private static final double GROUND_Y = 64;
  private static final Vec3 GROUND = new Vec3(0, GROUND_Y, 0);
  private static final double MID_CHASE = 0.5;
  private static final double NO_CHASE = 0;
  private static final double FULL_CHASE = 1;
  private static final double OPENING_BLOCKS = 10;

  private final ChaseLeash leash =
      new ChaseLeash(() -> new PackSettings(8.0, 4.0, 0.25, 1.0, 8.0, 32.0, 3, 60, 30));
  private final List<MobSnapshot> mobs = new ArrayList<>();
  private final List<RoleAssignment> orders = new ArrayList<>();

  @Test
  void beyondTheLeashFightersWalkBack() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result.getFirst().role()).isEqualTo(Role.GATHER);
  }

  @Test
  void theLeashOrderKeepsTheTargetAndDropsTheAttack() {
    RoleAssignment zombie = mob(MobKind.ZOMBIE, Role.PRESS, 20);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result)
        .containsExactly(
            new RoleAssignment(
                zombie.mob(),
                Role.GATHER,
                Optional.of(ALICE),
                Optional.empty(),
                false,
                Optional.of(GROUND),
                false));
  }

  @Test
  void exactlyAtTheLimitKeepsChasing() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);

    List<RoleAssignment> result = reinIn(30);

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void engagedFighterKeepsFighting() {
    mob(MobKind.ZOMBIE, Role.PRESS, 28);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void engagedAtTheEdgeKeepsFighting() {
    mob(MobKind.ZOMBIE, Role.PRESS, 27);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void flankersAreCalledBackToo() {
    mob(MobKind.ZOMBIE, Role.FLANK, 20);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result.getFirst().role()).isEqualTo(Role.GATHER);
  }

  @Test
  void noChaseMeansTheShortestLeash() {
    mob(MobKind.ZOMBIE, Role.PRESS, 5);
    Plan plan = recipePlan(NO_CHASE, OPENING_BLOCKS);

    List<RoleAssignment> atTheLimit = leash.reinIn(orders, snapshotWithAliceAt(18), plan);
    List<RoleAssignment> beyondIt = leash.reinIn(orders, snapshotWithAliceAt(19), plan);

    assertThat(atTheLimit).isEqualTo(orders);
    assertThat(beyondIt.getFirst().role()).isEqualTo(Role.GATHER);
  }

  @Test
  void fullChaseMeansTheLongestLeash() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);
    Plan plan = recipePlan(FULL_CHASE, OPENING_BLOCKS);

    List<RoleAssignment> atTheLimit = leash.reinIn(orders, snapshotWithAliceAt(42), plan);
    List<RoleAssignment> beyondIt = leash.reinIn(orders, snapshotWithAliceAt(43), plan);

    assertThat(atTheLimit).isEqualTo(orders);
    assertThat(beyondIt.getFirst().role()).isEqualTo(Role.GATHER);
  }

  @Test
  void theOpeningDistanceIsAdded() {
    mob(MobKind.ZOMBIE, Role.PRESS, 5);

    List<RoleAssignment> result =
        leash.reinIn(orders, snapshotWithAliceAt(25), recipePlan(NO_CHASE, 20));

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void heightDoesNotCount() {
    mob(MobKind.ZOMBIE, Role.PRESS, 10);
    GroupSnapshot snapshot = snapshotBuilder().withPlayer(aliceAt(new Vec3(0, 100, 30))).build();

    List<RoleAssignment> result = leash.reinIn(orders, snapshot, midChasePlan());

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void shootersReserveAndRetreatersAreNotCalled() {
    mob(MobKind.SKELETON, Role.SHOOT, 20);
    mob(MobKind.SKELETON, Role.VOLLEY, 20);
    mob(MobKind.SKELETON, Role.HOLD_FIRE, 20);
    mob(MobKind.ZOMBIE, Role.FALL_BACK, 20);
    mob(MobKind.ZOMBIE, Role.RETREAT, 20);

    List<RoleAssignment> result = reinIn(31);

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void aSpiderOnAnotherTargetIsNotCalled() {
    add(MobKind.SPIDER, new Vec3(0, GROUND_Y, 20), order(nextId(), Role.PRESS, BOB));
    GroupSnapshot snapshot =
        snapshotBuilder()
            .withPlayer(aliceAt(new Vec3(0, GROUND_Y, 31)))
            .withPlayer(
                new PlayerSnapshotBuilder()
                    .withId(BOB)
                    .withPosition(new Vec3(0, GROUND_Y, 40))
                    .build())
            .build();

    List<RoleAssignment> result = leash.reinIn(orders, snapshot, midChasePlan());

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void aPlanWithoutRecipeHasNoLeash() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);
    Plan strategyPlan =
        planOf(new PlanStart(new StrategyId("FLANK"), ALICE, Map.of(), 20, 0, Optional.empty()));

    List<RoleAssignment> result = leash.reinIn(orders, snapshotWithAliceAt(100), strategyPlan);

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void aTargetOutOfTheSnapshotChangesNothing() {
    mob(MobKind.ZOMBIE, Role.PRESS, 20);

    List<RoleAssignment> result = leash.reinIn(orders, snapshotBuilder().build(), midChasePlan());

    assertThat(result).isEqualTo(orders);
  }

  @Test
  void anOrderOfAMobOutOfTheSnapshotIsRejected() {
    RoleAssignment zombie = mob(MobKind.ZOMBIE, Role.PRESS, 20);
    mobs.clear();

    assertThatThrownBy(() -> reinIn(31))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChaseLeash: order for mob " + zombie.mob().value() + " has no snapshot");
  }

  private List<RoleAssignment> reinIn(double aliceZ) {
    return leash.reinIn(orders, snapshotWithAliceAt(aliceZ), midChasePlan());
  }

  private static Plan midChasePlan() {
    return recipePlan(MID_CHASE, OPENING_BLOCKS);
  }

  private static Plan recipePlan(double chase, double opening) {
    RecipePlay play =
        new RecipePlay(
            new PlanRecipe(new RoleSplit(1, 0, 0), new RoleSplit(0, 0, 0), false, 25, 0, 0, chase),
            new PlayerTraits(0, 0, 0),
            Collections.nCopies(ContextualFeatures.DIMENSION, 0.0),
            Map.of(),
            Set.of(),
            new ChaseAnchor(GROUND, opening));
    return planOf(
        new PlanStart(RecipePlanner.STRATEGY_ID, ALICE, Map.of(), 20, 0, Optional.of(play)));
  }

  private static Plan planOf(PlanStart start) {
    return Plan.start(new PlanId(new GroupId(new UUID(0, 3)), 1), start);
  }

  private RoleAssignment mob(MobKind kind, Role role, double z) {
    return add(kind, new Vec3(0, GROUND_Y, z), order(nextId(), role, ALICE));
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

  private static RoleAssignment order(MobId id, Role role, PlayerId target) {
    return new RoleAssignment(
        id,
        role,
        Optional.of(target),
        Optional.of(Attack.ZOMBIE_FRONT_STRIKE),
        false,
        Optional.empty(),
        false);
  }

  private GroupSnapshot snapshotWithAliceAt(double z) {
    return snapshotBuilder().withPlayer(aliceAt(new Vec3(0, GROUND_Y, z))).build();
  }

  private GroupSnapshotBuilder snapshotBuilder() {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder();
    mobs.forEach(builder::withMob);
    return builder;
  }

  private static PlayerSnapshot aliceAt(Vec3 position) {
    return new PlayerSnapshotBuilder().withId(ALICE).withPosition(position).build();
  }
}
