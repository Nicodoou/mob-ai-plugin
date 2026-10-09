package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.GroupComposition;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeBase;
import io.github.nicodoou.mobai.domain.strategy.RecipeEstimate;
import io.github.nicodoou.mobai.domain.strategy.RecipePlanner;
import io.github.nicodoou.mobai.domain.strategy.RecipeQuery;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipeAdvisorTest {
  private static final long TICK = 1000;
  private static final int OBSERVED_PLANS = 6;
  private static final double FEATURE_VALUE = 0.3;
  private static final PlayerId ALICE = new PlayerSnapshotBuilder().build().id();

  private final MobAiSettings settings = TestSettings.defaults();
  private final RecipePlanner planner =
      new RecipePlanner(() -> settings, new ScriptedRandomSource(), new RecipeBase());
  private final TraitLedger traits = new TraitLedger(settings::learning);
  private final RecipeAdvisor advisor = new RecipeAdvisor(planner, traits);

  @Test
  void noRecipeModelGivesNoAdvice() {
    Group group = groupWith(2, 1);

    assertThat(advisor.adviceFor(group, ALICE, TICK)).isEmpty();
  }

  @Test
  void adviceUsesTheGroupComposition() {
    Group group = groupWith(2, 1);
    group.memory().storeRecipeModel(ALICE, new RecipeModelRecord(planner.prior(), TICK));

    RecipeAdvice advice = advisor.adviceFor(group, ALICE, TICK).orElseThrow();

    assertThat(advice.best()).hasSize(RecipeAdvisor.SHOWN_RECIPES);
    assertThat(advice.best())
        .allSatisfy(
            estimate -> {
              assertThat(estimate.recipe().zombies().total()).isEqualTo(2);
              assertThat(estimate.recipe().spiders().total()).isEqualTo(1);
            });
  }

  @Test
  void adviceCarriesTraitsAndPlans() {
    Group group = groupWith(2, 1);
    traits.observe(blockingAlice());
    LinearPosterior model = observedPlans(OBSERVED_PLANS);
    group.memory().storeRecipeModel(ALICE, new RecipeModelRecord(model, TICK));

    RecipeAdvice advice = advisor.adviceFor(group, ALICE, TICK).orElseThrow();

    PlayerTraits expectedTraits = traits.traitsOf(ALICE);
    assertThat(expectedTraits).isNotEqualTo(new PlayerTraits(0, 0, 0));
    assertThat(advice.traits()).isEqualTo(expectedTraits);
    assertThat(advice.observations()).isCloseTo(OBSERVED_PLANS, within(1e-9));
    List<RecipeEstimate> expected =
        planner.estimates(
            new RecipeQuery(model, expectedTraits, new GroupComposition(2, 0, 1)),
            RecipeAdvisor.SHOWN_RECIPES);
    assertThat(advice.best()).isEqualTo(expected);
  }

  private LinearPosterior observedPlans(int plans) {
    double[] features = new double[ContextualFeatures.DIMENSION];
    Arrays.fill(features, FEATURE_VALUE);
    LinearPosterior model = planner.prior();
    for (int plan = 0; plan < plans; plan++) {
      model = model.withObservation(features, 1.0, settings.learning().modelNoiseVariance());
    }
    return model;
  }

  private static GroupSnapshot blockingAlice() {
    return new GroupSnapshotBuilder()
        .withTick(TICK)
        .withPlayer(new PlayerSnapshotBuilder().withBlocking(true).build())
        .build();
  }

  private Group groupWith(int zombies, int spiders) {
    GroupId id = new GroupId(new UUID(0, 1));
    Group group =
        new Group(
            id,
            SelectionPolicyType.THOMPSON_SAMPLING,
            new GroupKnowledge(
                new GroupMemory(settings::memory), new ThreatLedger(settings::target)));
    long next = 1;
    for (int index = 0; index < zombies; index++) {
      group.roster().addMember(new MobId(new UUID(1, next++)), MobKind.ZOMBIE);
    }
    for (int index = 0; index < spiders; index++) {
      group.roster().addMember(new MobId(new UUID(1, next++)), MobKind.SPIDER);
    }
    return group;
  }
}
