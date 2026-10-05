package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.GroupDecision;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.event.DomainEvent;
import io.github.nicodoou.mobai.domain.event.LeaderDied;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.strategy.DirectAssaultStrategy;
import io.github.nicodoou.mobai.domain.strategy.FlankStrategy;
import io.github.nicodoou.mobai.domain.strategy.PinAndShootStrategy;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * A seeded fight of 500 decisions; the script draws from its own random source so the brain's
 * randomness stays exactly as it would be in game.
 */
class BrainInvariantsTest {
  private static final long BRAIN_SEED = 20_261_005L;
  private static final long SCRIPT_SEED = 7_919L;
  private static final int DECISIONS = 500;

  private static List<Step> fight() {
    return new Fight(BRAIN_SEED, SCRIPT_SEED).run(DECISIONS);
  }

  private static List<MobId> snapshotMobs(Step step) {
    return step.snapshot().mobs().stream().map(MobSnapshot::id).toList();
  }

  private static List<GroupDecision> decisions(List<Step> steps) {
    return steps.stream().map(step -> step.result().decision()).toList();
  }

  private static List<PlanEndReason> closeReasons(List<Step> steps) {
    return closedPlans(steps).stream().map(ClosedPlan::reason).toList();
  }

  private static List<ClosedPlan> closedPlans(List<Step> steps) {
    return steps.stream().flatMap(step -> step.result().closedPlan().stream()).toList();
  }

  @Test
  void everySnapshotMobGetsExactlyOneOrderWhenThereAreOrders() {
    List<Step> steps = fight();

    assertThat(steps).hasSize(DECISIONS);
    for (Step step : steps) {
      List<RoleAssignment> orders = step.result().decision().assignments();
      if (!orders.isEmpty()) {
        assertThat(orders)
            .as("orders at tick %d", step.snapshot().tick())
            .extracting(RoleAssignment::mob)
            .containsExactlyElementsOf(snapshotMobs(step));
      }
    }
  }

  @Test
  void retreatingMobsNeverGetAnAttack() {
    List<Step> steps = fight();

    for (Step step : steps) {
      for (RoleAssignment order : step.result().decision().assignments()) {
        if (order.role() == Role.RETREAT) {
          assertThat(order.suggestedAttack()).as("order %s", order).isEmpty();
        }
        if (order.recovering()) {
          assertThat(order.role()).as("order %s", order).isEqualTo(Role.RETREAT);
        }
      }
    }
  }

  @Test
  void theGroupNeverEndsADecisionInATransitState() {
    List<Step> steps = fight();

    for (Step step : steps) {
      GroupState state = step.result().decision().state();
      assertThat(state)
          .as("state at tick %d", step.snapshot().tick())
          .isNotIn(GroupState.PLANNING, GroupState.EVALUATING);
      assertThat(step.result().trace().stateAfter()).isEqualTo(state);
    }
  }

  @Test
  void everyPlanClosesAtMostOnce() {
    List<Step> steps = fight();

    List<PlanId> closed = closedPlans(steps).stream().map(ClosedPlan::id).toList();
    List<PlanId> executed =
        decisions(steps).stream().flatMap(decision -> decision.plan().stream()).distinct().toList();
    assertThat(closed).isNotEmpty().doesNotHaveDuplicates();
    for (int index = 0; index < closed.size(); index++) {
      assertThat(closed.get(index).sequence()).isEqualTo(index + 1L);
    }
    for (int index = 0; index < executed.size(); index++) {
      assertThat(executed.get(index).sequence()).isEqualTo(index + 1L);
    }
  }

  @Test
  void ordersAgreeWithTheGroupState() {
    List<Step> steps = fight();

    for (GroupDecision decision : decisions(steps)) {
      switch (decision.state()) {
        case EXECUTING -> {
          assertThat(decision.plan()).as("decision %s", decision).isPresent();
          assertThat(decision.strategy()).as("decision %s", decision).isPresent();
          assertThat(decision.target()).as("decision %s", decision).isPresent();
        }
        case OBSERVING -> assertThat(decision.assignments()).as("decision %s", decision).isEmpty();
        case REGROUPING ->
            assertThat(decision.assignments())
                .as("decision %s", decision)
                .allSatisfy(order -> assertThat(order.role()).isEqualTo(Role.RETREAT));
        case PLANNING, EVALUATING -> fail("decision left in a transit state: " + decision);
      }
    }
  }

  @Test
  void sameSeedsGiveTheSameDecisions() {
    List<Step> first = fight();
    List<Step> second = fight();

    assertThat(decisions(second)).isEqualTo(decisions(first));
    assertThat(closeReasons(second)).isEqualTo(closeReasons(first));
  }

  @Test
  void theScenarioCoversEveryPath() {
    List<Step> steps = fight();

    Set<StrategyId> strategies = chosenStrategies(steps);
    List<PlanEndReason> reasons = closeReasons(steps);
    String coverage = coverage(steps);
    assertThat(strategies)
        .as(coverage)
        .contains(DirectAssaultStrategy.ID, FlankStrategy.ID, PinAndShootStrategy.ID);
    assertThat(reasons)
        .as(coverage)
        .contains(
            PlanEndReason.TARGET_LOST, PlanEndReason.TIMED_OUT, PlanEndReason.GROUP_RETREATED);
    assertThat(regroupEnds(steps)).as(coverage).isPositive();
    assertThat(returnsFromRetreat(steps)).as(coverage).isPositive();
    assertThat(leaderDeaths(steps)).as(coverage).isPositive();
  }

  private static Set<StrategyId> chosenStrategies(List<Step> steps) {
    return Set.copyOf(
        steps.stream()
            .flatMap(step -> step.result().trace().strategySelection().stream())
            .map(SelectionResult::chosen)
            .toList());
  }

  private static long regroupEnds(List<Step> steps) {
    return steps.stream().filter(step -> step.result().trace().regroupEnd().isPresent()).count();
  }

  private static long returnsFromRetreat(List<Step> steps) {
    return steps.stream()
        .mapToLong(step -> step.result().trace().returningFromRetreat().size())
        .sum();
  }

  private static long leaderDeaths(List<Step> steps) {
    return steps.stream()
        .flatMap(step -> step.events().stream())
        .filter(LeaderDied.class::isInstance)
        .count();
  }

  private static String coverage(List<Step> steps) {
    return "strategies "
        + chosenStrategies(steps)
        + ", plan ends "
        + closeReasons(steps)
        + ", regroup ends "
        + regroupEnds(steps)
        + ", returns from retreat "
        + returnsFromRetreat(steps)
        + ", leader deaths "
        + leaderDeaths(steps);
  }

  private record Step(GroupSnapshot snapshot, BrainResult result, List<DomainEvent> events) {}

  /**
   * The world around the group: players wander, Alice leaves for a while now and then, mobs get hit
   * near players in calm or fierce spells, heal while their order says so, die and are replaced.
   */
  private static final class Fight {
    private static final long TICKS_PER_DECISION = 10;
    private static final int FULL_GROUP = 9;
    private static final double MOB_STEP_BLOCKS = 3;
    private static final double MELEE_STAND_OFF_BLOCKS = 2;
    private static final double SHOOT_STAND_OFF_BLOCKS = 8;
    private static final double RETREAT_DISTANCE_BLOCKS = 20;
    private static final double DANGER_RADIUS_BLOCKS = 9;
    private static final double CALM_HIT_CHANCE = 0.02;
    private static final double FIERCE_HIT_CHANCE = 0.3;
    private static final int MIN_HIT_DAMAGE = 1;
    private static final int HIT_DAMAGE_SPREAD = 8;
    private static final double HEAL_PER_DECISION = 1;
    private static final double JOIN_CHANCE = 0.06;
    private static final double ABSENCE_CHANCE = 0.03;
    private static final int MIN_ABSENCE_DECISIONS = 10;
    private static final int ABSENCE_SPREAD_DECISIONS = 25;
    private static final double MOOD_SWITCH_CHANCE = 0.015;
    private static final int PLAYER_ROAM_BLOCKS = 8;
    private static final int WANDER_CHOICES = 3;
    private static final double ZOMBIE_MAX_HEALTH = 20;
    private static final double SKELETON_MAX_HEALTH = 20;
    private static final double SPIDER_MAX_HEALTH = 16;
    private static final long JOINED_MOB_ID_HIGH_BITS = 2;
    private static final Vec3 SPAWN = new Vec3(0, 64, 20);
    private static final Vec3 FALLBACK_AWAY = new Vec3(1, 0, 0);

    private final BrainFixture fixture;
    private final SeededRandomSource script;
    private final Map<MobId, MobSnapshot> mobs = new LinkedHashMap<>();
    private Vec3 alicePosition = new Vec3(0, 64, 0);
    private Vec3 bobPosition = new Vec3(6, 64, -6);
    private int aliceAbsentDecisions = 0;
    private boolean fierce = false;
    private long tick = START_TICK;
    private long joinedMobs = 0;

    Fight(long brainSeed, long scriptSeed) {
      this.fixture = BrainFixture.seeded(brainSeed);
      this.script = new SeededRandomSource(scriptSeed);
      fixture.catalogGroup().forEach(mob -> mobs.put(mob.id(), mob));
    }

    List<Step> run(int decisions) {
      List<Step> steps = new ArrayList<>();
      for (int index = 0; index < decisions; index++) {
        steps.add(nextStep());
        tick += TICKS_PER_DECISION;
      }
      return steps;
    }

    private Step nextStep() {
      GroupSnapshot snapshot = snapshot();
      BrainResult result = fixture.decide(snapshot);
      follow(result.decision(), snapshot);
      removeDead();
      maybeJoin();
      moveWorld();
      return new Step(snapshot, result, fixture.group().drainEvents());
    }

    private GroupSnapshot snapshot() {
      List<PlayerSnapshot> players = new ArrayList<>();
      if (aliceAbsentDecisions == 0) {
        players.add(new PlayerSnapshotBuilder().withId(ALICE).withPosition(alicePosition).build());
      }
      players.add(
          new PlayerSnapshotBuilder()
              .withId(BOB)
              .withPosition(bobPosition)
              .fullDiamondProtectionFour()
              .build());
      return BrainFixture.snapshot(
          tick, List.copyOf(mobs.values()), players.toArray(PlayerSnapshot[]::new));
    }

    private void follow(GroupDecision decision, GroupSnapshot snapshot) {
      for (RoleAssignment order : decision.assignments()) {
        MobSnapshot mob = mobs.get(order.mob());
        mobs.put(mob.id(), afterOrder(mob, order, snapshot));
      }
    }

    private MobSnapshot afterOrder(MobSnapshot mob, RoleAssignment order, GroupSnapshot snapshot) {
      MobSnapshot moved = withPosition(mob, destination(mob, order, snapshot));
      MobSnapshot hit = hit(moved, snapshot);
      return order.recovering() ? healed(hit) : hit;
    }

    private Vec3 destination(MobSnapshot mob, RoleAssignment order, GroupSnapshot snapshot) {
      Optional<Vec3> target =
          order.target().flatMap(snapshot::player).map(player -> player.pose().position());
      if (target.isEmpty()) {
        return mob.position();
      }
      if (order.role() == Role.RETREAT) {
        return awayFrom(mob.position(), target.get());
      }
      return toward(mob.position(), target.get(), standOff(mob.kind()));
    }

    private static Vec3 toward(Vec3 position, Vec3 target, double standOffBlocks) {
      Vec3 offset = target.minus(position).horizontal();
      double distance = offset.length();
      if (distance <= standOffBlocks) {
        return position;
      }
      double step = Math.min(MOB_STEP_BLOCKS, distance - standOffBlocks);
      return position.plus(offset.normalized().times(step));
    }

    private static Vec3 awayFrom(Vec3 position, Vec3 danger) {
      Vec3 offset = position.minus(danger).horizontal();
      double distance = offset.length();
      if (distance >= RETREAT_DISTANCE_BLOCKS) {
        return position;
      }
      Vec3 direction = distance == 0 ? FALLBACK_AWAY : offset.normalized();
      return position.plus(
          direction.times(Math.min(MOB_STEP_BLOCKS, RETREAT_DISTANCE_BLOCKS - distance)));
    }

    private static double standOff(MobKind kind) {
      return kind == MobKind.SKELETON ? SHOOT_STAND_OFF_BLOCKS : MELEE_STAND_OFF_BLOCKS;
    }

    private MobSnapshot hit(MobSnapshot mob, GroupSnapshot snapshot) {
      if (!isInDanger(mob, snapshot) || script.nextUnit() >= hitChance()) {
        return mob;
      }
      double damage = MIN_HIT_DAMAGE + script.nextIndex(HIT_DAMAGE_SPREAD);
      return withHealth(mob, Math.max(0, mob.health() - damage));
    }

    private static boolean isInDanger(MobSnapshot mob, GroupSnapshot snapshot) {
      return snapshot.players().stream()
          .anyMatch(
              player ->
                  player.pose().position().distanceTo(mob.position()) <= DANGER_RADIUS_BLOCKS);
    }

    private double hitChance() {
      return fierce ? FIERCE_HIT_CHANCE : CALM_HIT_CHANCE;
    }

    private static MobSnapshot healed(MobSnapshot mob) {
      return withHealth(mob, Math.min(mob.maxHealth(), mob.health() + HEAL_PER_DECISION));
    }

    private void removeDead() {
      List<MobId> dead =
          mobs.values().stream().filter(mob -> mob.health() == 0).map(MobSnapshot::id).toList();
      for (MobId mob : dead) {
        mobs.remove(mob);
        fixture.group().removeMember(mob, tick);
      }
    }

    private void maybeJoin() {
      if (mobs.size() >= FULL_GROUP || script.nextUnit() >= JOIN_CHANCE) {
        return;
      }
      MobKind kind = MobKind.values()[script.nextIndex(MobKind.values().length)];
      joinedMobs++;
      MobId id = new MobId(new UUID(JOINED_MOB_ID_HIGH_BITS, joinedMobs));
      double maxHealth = maxHealth(kind);
      fixture.group().roster().addMember(id, kind);
      mobs.put(id, new MobSnapshot(id, kind, SPAWN, maxHealth, maxHealth));
    }

    private static double maxHealth(MobKind kind) {
      return switch (kind) {
        case ZOMBIE -> ZOMBIE_MAX_HEALTH;
        case SKELETON -> SKELETON_MAX_HEALTH;
        case SPIDER -> SPIDER_MAX_HEALTH;
      };
    }

    private void moveWorld() {
      alicePosition = wander(alicePosition);
      bobPosition = wander(bobPosition);
      updateAliceAbsence();
      if (script.nextUnit() < MOOD_SWITCH_CHANCE) {
        fierce = !fierce;
      }
    }

    private Vec3 wander(Vec3 position) {
      return new Vec3(wanderAxis(position.x()), position.y(), wanderAxis(position.z()));
    }

    private double wanderAxis(double value) {
      double moved = value + script.nextIndex(WANDER_CHOICES) - 1;
      return Math.clamp(moved, -PLAYER_ROAM_BLOCKS, PLAYER_ROAM_BLOCKS);
    }

    private void updateAliceAbsence() {
      if (aliceAbsentDecisions > 0) {
        aliceAbsentDecisions--;
        return;
      }
      if (script.nextUnit() < ABSENCE_CHANCE) {
        aliceAbsentDecisions = MIN_ABSENCE_DECISIONS + script.nextIndex(ABSENCE_SPREAD_DECISIONS);
      }
    }

    private static MobSnapshot withPosition(MobSnapshot mob, Vec3 position) {
      return new MobSnapshot(mob.id(), mob.kind(), position, mob.health(), mob.maxHealth());
    }

    private static MobSnapshot withHealth(MobSnapshot mob, double health) {
      return new MobSnapshot(mob.id(), mob.kind(), mob.position(), health, mob.maxHealth());
    }
  }
}
