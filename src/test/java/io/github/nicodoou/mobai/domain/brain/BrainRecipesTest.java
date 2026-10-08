package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withHealth;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BrainRecipesTest {
  private static final long SEED = 7;
  private static final double CERTAINTY = 1e6;
  private static final double PRIOR_SUCCESS = 0.5;
  // Negative weights that break ties: no zombie flank, no reserve, no spider flank, no retreat
  // threshold and no volley, unless a test says otherwise.
  private static final Map<Integer, Double> TIE_BREAKERS =
      Map.of(1, -1.0, 3, -1.0, 5, -1.0, 9, -1.0, 11, -1.0);
  private static final List<Integer> SKELETONS = List.of(4, 5, 6);
  private static final int FIRST_ZOMBIE = 0;
  private static final int SECOND_ZOMBIE = 1;
  private static final int THIRD_ZOMBIE = 2;
  private static final int FOURTH_ZOMBIE = 3;
  private static final long BEFORE_THE_DELAY_TICKS = 80;
  private static final long AFTER_THE_DELAY_TICKS = 90;
  private static final double WOUNDED_HEALTH = 4;
  private static final double WOUNDED_FRACTION = 0.2;

  private final BrainFixture fixture = BrainFixture.seededWithRecipes(SEED);
  private final List<MobSnapshot> mobs = fixture.catalogGroup();

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
    storeModel(Map.of(3, 1.0, 4, -1.0, 7, 1.0, 8, -1.0));

    List<RoleAssignment> atStart =
        fixture.decide(START_TICK, mobs, alice()).decision().assignments();
    List<RoleAssignment> beforeTheDelay =
        fixture.decide(START_TICK + BEFORE_THE_DELAY_TICKS, mobs, alice()).decision().assignments();
    List<RoleAssignment> afterTheDelay =
        fixture.decide(START_TICK + AFTER_THE_DELAY_TICKS, mobs, alice()).decision().assignments();

    RecipePlay play = currentPlan().recipe().orElseThrow();
    assertThat(play.recipe().zombies()).isEqualTo(new RoleSplit(2, 0, 2));
    assertThat(play.recipe().reserveDelayTicks()).isEqualTo(89);
    assertHeldBack(atStart);
    assertHeldBack(beforeTheDelay);
    assertThat(zombieRoles(afterTheDelay)).containsOnly(Role.PRESS);
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

    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    assertThat(currentPlan().recipe().orElseThrow().recipe().volley()).isTrue();
    for (int index : SKELETONS) {
      assertThat(result.decision().assignments().get(index).role()).isEqualTo(Role.HOLD_FIRE);
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
