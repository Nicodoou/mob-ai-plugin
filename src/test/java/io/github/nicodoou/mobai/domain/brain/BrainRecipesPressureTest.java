package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withHealth;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withPosition;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrainRecipesPressureTest {
  private static final long SEED = 7;
  private static final double CERTAINTY = 1e6;
  private static final double PRIOR_SUCCESS = 0.5;
  // Negative weights that break ties: no zombie flank, no reserve, no spider flank, no retreat
  // threshold and no volley, unless a test says otherwise.
  private static final Map<Integer, Double> TIE_BREAKERS =
      Map.of(1, -1.0, 3, -1.0, 5, -1.0, 9, -1.0, 11, -1.0);
  // A split of two pressing zombies and two in reserve, delayed 89 ticks.
  private static final Map<Integer, Double> RESERVE_OF_TWO =
      Map.of(3, 1.0, 4, -1.0, 7, 1.0, 8, -1.0);
  private static final List<Integer> SKELETONS = List.of(4, 5, 6);
  private static final int FIRST_ZOMBIE = 0;
  private static final int SECOND_ZOMBIE = 1;
  private static final int THIRD_ZOMBIE = 2;
  private static final int FOURTH_ZOMBIE = 3;
  // The recipe's 89-tick delay, at the settings' quarter.
  private static final long BEFORE_THE_DELAY_TICKS = 20;
  private static final long AFTER_THE_DELAY_TICKS = 30;
  private static final double FAR_AWAY_Z = 30;
  private static final double WITHIN_ENGAGE_Z = 2;
  private static final long VOLLEY_SYNC_AGE_TICKS = 70;
  private static final long VOLLEY_FIRE_AGE_TICKS = 80;
  private static final double WOUNDED_HEALTH = 4;
  private static final double WOUNDED_FRACTION = 0.2;
  // The peak is -linear / (2 * quadratic), and the group retreat knob scales it by 0.6.
  private static final Map<Integer, Double> LOW_GROUP_RETREAT = Map.of(15, 0.6, 16, -1.0);
  private static final Map<Integer, Double> HIGH_GROUP_RETREAT = Map.of(15, 1.0, 16, -0.6);
  private static final Map<Integer, Double> NO_CHASE = Map.of(17, -1.0);
  private static final List<Integer> SPIDERS = List.of(7, 8);
  private static final double GROUND_Y = 64;
  private static final double ORIGIN_Z = 0;

  private final BrainFixture fixture = BrainFixture.seededWithRecipes(SEED);
  private final List<MobSnapshot> mobs = fixture.catalogGroup();

  @BeforeEach
  void fightUnderPressure() {
    fixture.group().assignTactic(CombatTactic.PRESSURE);
  }

  @Test
  void withRecipesThePlanIsARecipe() {
    fixture.decide(START_TICK, mobs, alice());

    Plan plan = currentPlan();
    assertThat(plan.strategy()).isEqualTo(RecipePlanner.STRATEGY_ID);
    assertThat(plan.recipe()).isPresent();
    for (int index : SKELETONS) {
      assertThat(plan.roleOf(mobs.get(index).id())).contains(Role.SHOOT);
    }
  }

  @Test
  void theReserveFallsBackUntilTheDelay() {
    storeModel(RESERVE_OF_TWO);
    List<MobSnapshot> far = farFromAlice(mobs);

    List<RoleAssignment> atStart =
        fixture.decide(START_TICK, far, alice()).decision().assignments();
    List<RoleAssignment> beforeTheDelay =
        fixture.decide(START_TICK + BEFORE_THE_DELAY_TICKS, far, alice()).decision().assignments();
    List<RoleAssignment> afterTheDelay =
        fixture.decide(START_TICK + AFTER_THE_DELAY_TICKS, far, alice()).decision().assignments();

    RecipePlay play = currentPlan().recipe().orElseThrow();
    assertThat(play.recipe().zombies()).isEqualTo(new RoleSplit(2, 0, 2));
    assertThat(play.recipe().reserveDelayTicks()).isEqualTo(89);
    assertHeldBack(atStart);
    assertHeldBack(beforeTheDelay);
    assertThat(zombieRoles(afterTheDelay)).containsOnly(Role.PRESS);
  }

  @Test
  void aReserveWithTheTargetAtHandJoinsInAtOnce() {
    storeModel(RESERVE_OF_TWO);
    List<MobSnapshot> far = farFromAlice(mobs);
    fixture.decide(START_TICK, far, alice());
    Vec3 close = new Vec3(mobs.get(THIRD_ZOMBIE).position().x(), GROUND_Y, WITHIN_ENGAGE_Z);
    List<MobSnapshot> reserveClose = withPosition(far, THIRD_ZOMBIE, close);

    List<RoleAssignment> orders =
        fixture.decide(START_TICK + 10, reserveClose, alice()).decision().assignments();

    assertThat(orders.get(THIRD_ZOMBIE).role()).isEqualTo(Role.PRESS);
    assertThat(orders.get(FOURTH_ZOMBIE).role()).isEqualTo(Role.FALL_BACK);
  }

  @Test
  void theRecipeThresholdDecidesIndividualRetreats() {
    storeModel(Map.of(9, 0.6, 10, -1.0));
    List<MobSnapshot> wounded = withHealth(mobs, FIRST_ZOMBIE, WOUNDED_HEALTH);

    BrainResult result = fixture.decide(START_TICK, wounded, alice());

    RecipePlay play = currentPlan().recipe().orElseThrow();
    assertThat(play.recipe().retreatHealthFraction()).isLessThan(WOUNDED_FRACTION);
    assertThat(TestSettings.withRecipes().plan().retreatHealthFraction())
        .isGreaterThan(WOUNDED_FRACTION);
    assertThat(result.decision().assignments().get(FIRST_ZOMBIE).role()).isNotEqualTo(Role.RETREAT);
  }

  @Test
  void aVolleyRecipeUsesTheVolleyPhases() {
    storeModel(Map.of(11, 1.0));

    BrainResult atStart = fixture.decide(START_TICK, mobs, alice());
    BrainResult beforeTheShot = fixture.decide(START_TICK + VOLLEY_SYNC_AGE_TICKS, mobs, alice());
    BrainResult atTheShot = fixture.decide(START_TICK + VOLLEY_FIRE_AGE_TICKS, mobs, alice());

    assertThat(currentPlan().recipe().orElseThrow().recipe().volley()).isTrue();
    for (int index : SKELETONS) {
      assertThat(atStart.decision().assignments().get(index).role()).isEqualTo(Role.SHOOT);
      assertThat(beforeTheShot.decision().assignments().get(index).role())
          .isEqualTo(Role.HOLD_FIRE);
      assertThat(atTheShot.decision().assignments().get(index).role()).isEqualTo(Role.VOLLEY);
    }
  }

  @Test
  void traitsAreObservedEveryDecision() {
    fixture.decide(
        START_TICK, mobs, new PlayerSnapshotBuilder().withId(ALICE).withBlocking(true).build());

    assertThat(fixture.parts().traitLedger().traitsOf(ALICE).shield()).isCloseTo(1, within(1e-9));
  }

  @Test
  void theRecipeTargetsTheChosenPlayer() {
    fixture.decide(START_TICK, mobs, alice());

    Plan plan = currentPlan();
    RecipePlay play = plan.recipe().orElseThrow();
    assertThat(plan.target()).isEqualTo(ALICE);
    assertThat(play.recipe().zombies().total()).isEqualTo(4);
    assertThat(play.recipe().spiders().total()).isEqualTo(2);
    assertThat(play.traits()).isEqualTo(new PlayerTraits(0, 0, 0));
  }

  @Test
  void strategiesRemainTheDefault() {
    BrainFixture strategies = BrainFixture.seeded(SEED);
    List<MobSnapshot> strategyMobs = strategies.catalogGroup();

    strategies.decide(START_TICK, strategyMobs, alice());

    Plan plan = strategies.group().lifecycle().plan().orElseThrow();
    assertThat(plan.recipe()).isEmpty();
    assertThat(plan.strategy()).isNotEqualTo(RecipePlanner.STRATEGY_ID);
  }

  @Test
  void aLowRecipeStrengthFightsBelowTheConfiguredOne() {
    storeModel(LOW_GROUP_RETREAT);
    fixture.decide(START_TICK, mobs, alice());
    assertThat(currentRecipe().groupRetreatStrength()).isCloseTo(0.18, within(0.01));

    BrainResult result = fixture.decide(START_TICK + 10, atHealthFraction(mobs, 0.3), alice());

    assertThat(result.closedPlan()).isEmpty();
    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.EXECUTING);
  }

  @Test
  void aHighRecipeStrengthRetreatsAboveTheConfiguredOne() {
    BrainResult result = retreatAtHighStrength();

    assertThat(result.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.GROUP_RETREATED);
    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.REGROUPING);
  }

  @Test
  void theGroupStaysRetreatedByItsLastPlanStrength() {
    retreatAtHighStrength();

    fixture.decide(
        START_TICK + 10 + fixture.regroupWindow().currentTicks(),
        atHealthFraction(mobs, 0.45),
        alice());

    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.REGROUPING);
  }

  @Test
  void theGroupRejoinsByItsLastPlanStrength() {
    storeModel(LOW_GROUP_RETREAT);
    fixture.decide(START_TICK, mobs, alice());
    assertThat(currentRecipe().groupRetreatStrength()).isCloseTo(0.18, within(0.01));
    BrainResult closed = fixture.decide(START_TICK + 10, atHealthFraction(mobs, 0.1), alice());
    assertThat(closed.closedPlan().orElseThrow().reason()).isEqualTo(PlanEndReason.GROUP_RETREATED);

    fixture.decide(
        START_TICK + 10 + fixture.regroupWindow().currentTicks(),
        atHealthFraction(mobs, 0.3),
        alice());

    assertThat(fixture.group().lifecycle().state()).isEqualTo(GroupState.OBSERVING);
  }

  @Test
  void theLeashCallsFightersBackAndTheyReturnWhenTheTargetComesBack() {
    storeModel(NO_CHASE);
    fixture.decide(START_TICK, placed(ORIGIN_Z), aliceAtZ(10));
    RecipePlay play = currentPlan().recipe().orElseThrow();
    assertThat(play.recipe().chaseCommitment()).isCloseTo(0, within(0.01));
    assertThat(play.chaseAnchor().ground().x()).isCloseTo(0, within(1e-9));
    assertThat(play.chaseAnchor().ground().z()).isCloseTo(0, within(1e-9));
    assertThat(play.chaseAnchor().openingDistanceBlocks()).isCloseTo(10, within(1e-9));

    List<RoleAssignment> leashed =
        fixture.decide(START_TICK + 10, placed(14), aliceAtZ(19)).decision().assignments();
    List<RoleAssignment> back =
        fixture.decide(START_TICK + 20, placed(12), aliceAtZ(18)).decision().assignments();

    for (RoleAssignment order : fightersOf(leashed)) {
      assertThat(order.role()).isEqualTo(Role.GATHER);
      assertThat(order.rallyPoint()).contains(play.chaseAnchor().ground());
    }
    for (int index : SKELETONS) {
      assertThat(leashed.get(index).role()).isEqualTo(Role.SHOOT);
    }
    assertThat(zombieRoles(back)).containsOnly(Role.PRESS);
    for (int index : SPIDERS) {
      assertThat(back.get(index).role()).isEqualTo(Role.GATHER);
      assertThat(back.get(index).rallyPoint()).isEmpty();
    }
  }

  private static List<RoleAssignment> fightersOf(List<RoleAssignment> orders) {
    List<RoleAssignment> fighters =
        new ArrayList<>(orders.subList(FIRST_ZOMBIE, FOURTH_ZOMBIE + 1));
    SPIDERS.forEach(index -> fighters.add(orders.get(index)));
    return fighters;
  }

  private List<MobSnapshot> placed(double spiderZ) {
    List<MobSnapshot> placed = mobs;
    for (int index = 0; index < mobs.size(); index++) {
      double z = SPIDERS.contains(index) ? spiderZ : ORIGIN_Z;
      placed = BrainFixture.withPosition(placed, index, new Vec3(0, GROUND_Y, z));
    }
    return placed;
  }

  private static PlayerSnapshot aliceAtZ(double z) {
    return new PlayerSnapshotBuilder().withId(ALICE).withPosition(new Vec3(0, GROUND_Y, z)).build();
  }

  private BrainResult retreatAtHighStrength() {
    storeModel(HIGH_GROUP_RETREAT);
    fixture.decide(START_TICK, mobs, alice());
    assertThat(currentRecipe().groupRetreatStrength()).isCloseTo(0.5, within(0.01));
    return fixture.decide(START_TICK + 10, atHealthFraction(mobs, 0.45), alice());
  }

  private PlanRecipe currentRecipe() {
    return currentPlan().recipe().orElseThrow().recipe();
  }

  private static List<MobSnapshot> atHealthFraction(List<MobSnapshot> mobs, double fraction) {
    return mobs.stream()
        .map(
            mob ->
                new MobSnapshot(
                    mob.id(),
                    mob.kind(),
                    mob.position(),
                    mob.maxHealth() * fraction,
                    mob.maxHealth()))
        .toList();
  }

  // Beyond the engage distance, each keeping its offset along x so the farthest stay the farthest.
  private static List<MobSnapshot> farFromAlice(List<MobSnapshot> mobs) {
    return mobs.stream()
        .map(
            mob ->
                new MobSnapshot(
                    mob.id(),
                    mob.kind(),
                    new Vec3(mob.position().x(), mob.position().y(), FAR_AWAY_Z),
                    mob.health(),
                    mob.maxHealth()))
        .toList();
  }

  private void assertHeldBack(List<RoleAssignment> orders) {
    assertThat(orders.get(FIRST_ZOMBIE).role()).isEqualTo(Role.PRESS);
    assertThat(orders.get(SECOND_ZOMBIE).role()).isEqualTo(Role.PRESS);
    assertThat(orders.get(THIRD_ZOMBIE).role()).isEqualTo(Role.FALL_BACK);
    assertThat(orders.get(FOURTH_ZOMBIE).role()).isEqualTo(Role.FALL_BACK);
  }

  private static List<Role> zombieRoles(List<RoleAssignment> orders) {
    return orders.subList(FIRST_ZOMBIE, FOURTH_ZOMBIE + 1).stream()
        .map(RoleAssignment::role)
        .toList();
  }

  private Plan currentPlan() {
    return fixture.group().lifecycle().plan().orElseThrow();
  }

  // A model this certain samples within thousandths of its mean, so the recipe does not depend on
  // the draw.
  private void storeModel(Map<Integer, Double> means) {
    double[] mean = new double[ContextualFeatures.DIMENSION];
    mean[0] = PRIOR_SUCCESS;
    TIE_BREAKERS.forEach((index, value) -> mean[index] = value);
    means.forEach((index, value) -> mean[index] = value);
    double[][] precision = new double[mean.length][mean.length];
    double[] information = new double[mean.length];
    for (int i = 0; i < mean.length; i++) {
      precision[i][i] = CERTAINTY;
      information[i] = CERTAINTY * mean[i];
    }
    fixture
        .group()
        .memory()
        .storeRecipeModel(
            ALICE,
            new RecipeModelRecord(LinearPosterior.of(precision, information, 0), START_TICK));
  }
}
