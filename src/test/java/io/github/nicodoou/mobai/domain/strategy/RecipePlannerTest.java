package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.ScriptedRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecipePlannerTest {
  private static final double TOLERANCE = 1e-9;
  private static final PlayerTraits NO_TRAITS = new PlayerTraits(0, 0, 0);
  private static final PlayerTraits SHIELD_USER = new PlayerTraits(1, 0, 0);
  private static final PlayerSnapshot TARGET = new PlayerSnapshotBuilder().build();
  private static final MobId FRONT = mobId(1);
  private static final MobId RIGHT = mobId(2);
  private static final MobId BEHIND = mobId(3);
  private static final MobId LEFT = mobId(4);
  private static final MobId SKELETON = mobId(5);

  private final MobAiSettings settings = TestSettings.defaults();

  @Test
  void priorHasSixtyNumbersAndThePriorSuccess() {
    LinearPosterior prior = plannerWithNeutralDraws(0).prior();

    assertThat(prior.dimension()).isEqualTo(60);
    assertThat(prior.mean()[0]).isCloseTo(0.5, within(TOLERANCE));
    assertThat(Arrays.stream(prior.mean()).skip(1)).allSatisfy(value -> assertThat(value).isZero());
  }

  @Test
  void withoutAStoredModelTheCurrentIsThePrior() {
    RecipePlanner planner = plannerWithNeutralDraws(0);

    LinearPosterior current = planner.current(Optional.empty(), 5000);

    assertThat(current.mean()).containsExactly(planner.prior().mean(), within(TOLERANCE));
  }

  @Test
  void aStoredModelForgetsTowardThePriorWithTheMemoryHalfLife() {
    RecipePlanner planner = plannerWithNeutralDraws(0);
    double[] features = new double[ContextualFeatures.DIMENSION];
    Arrays.fill(features, 0.1);
    LinearPosterior stored = planner.prior().withObservation(features, 1.0, 0.01);
    LinearPosterior expected = stored.shrunkToward(planner.prior(), 0.5);

    LinearPosterior current =
        planner.current(Optional.of(new RecipeModelRecord(stored, 0)), 12_000);

    assertThat(current.mean()).containsExactly(expected.mean(), within(TOLERANCE));
    assertThat(current.observations()).isCloseTo(0.5, within(TOLERANCE));
  }

  @Test
  void zombiesFlankTheMostSideways() {
    RecipePlay play = plannerWithNeutralDraws(1).plan(fourZombiesFlankRequest(NO_TRAITS));

    assertThat(play.recipe().zombies()).isEqualTo(new RoleSplit(2, 2, 0));
    assertThat(play.roles())
        .containsExactly(
            Map.entry(FRONT, Role.PRESS),
            Map.entry(RIGHT, Role.FLANK),
            Map.entry(BEHIND, Role.FLANK),
            Map.entry(LEFT, Role.PRESS));
    assertThat(play.reserve()).isEmpty();
  }

  @Test
  void theReserveIsTheFarthestFromTheTarget() {
    LinearPosterior model = modelWith(Map.of(1, -1.0, 3, 1.0, 4, -1.0));
    GroupSnapshot snapshot =
        snapshotOf(
            zombie(FRONT, new Vec3(0, 64, 3)),
            zombie(RIGHT, new Vec3(6, 64, 0)),
            zombie(BEHIND, new Vec3(0, 64, -9)),
            zombie(LEFT, new Vec3(-4, 64, 1)));

    RecipePlay play =
        plannerWithNeutralDraws(1).plan(new RecipeRequest(model, NO_TRAITS, snapshot, TARGET.id()));

    assertThat(play.recipe().zombies()).isEqualTo(new RoleSplit(2, 0, 2));
    assertThat(play.recipe().reserveDelayTicks()).isEqualTo(20);
    assertThat(play.reserve()).containsExactlyInAnyOrder(BEHIND, RIGHT);
    assertThat(play.roles().values()).containsOnly(Role.PRESS).hasSize(4);
  }

  @Test
  void spidersFlankTheMostSideways() {
    LinearPosterior model = modelWith(Map.of(5, 0.8, 6, -1.0));
    GroupSnapshot snapshot =
        snapshotOf(spider(RIGHT, new Vec3(5, 64, 0)), spider(FRONT, new Vec3(0, 64, 5)));

    RecipePlay play =
        plannerWithNeutralDraws(1).plan(new RecipeRequest(model, NO_TRAITS, snapshot, TARGET.id()));

    assertThat(play.recipe().spiders()).isEqualTo(new RoleSplit(1, 1, 0));
    assertThat(play.roles())
        .containsExactly(Map.entry(RIGHT, Role.FLANK), Map.entry(FRONT, Role.PRESS));
  }

  @Test
  void skeletonsShoot() {
    LinearPosterior model = modelWith(Map.of(0, 0.5));
    GroupSnapshot snapshot =
        snapshotOf(
            zombie(FRONT, new Vec3(0, 64, 5)),
            zombie(RIGHT, new Vec3(5, 64, 0)),
            new MobSnapshotBuilder()
                .withId(SKELETON)
                .withKind(MobKind.SKELETON)
                .withPosition(new Vec3(0, 64, 12))
                .build());

    RecipePlay play =
        plannerWithNeutralDraws(1).plan(new RecipeRequest(model, NO_TRAITS, snapshot, TARGET.id()));

    assertThat(play.roles()).containsEntry(SKELETON, Role.SHOOT);
  }

  @Test
  void theTargetTraitsFoldTheWeights() {
    LinearPosterior model = modelWith(Map.of(16, 2.0, 17, -2.0, 18, -1.0));
    RecipePlanner planner = plannerWithNeutralDraws(2);
    GroupSnapshot snapshot = fourZombiesFlankRequest(NO_TRAITS).snapshot();

    RecipePlay withShield =
        planner.plan(new RecipeRequest(model, SHIELD_USER, snapshot, TARGET.id()));
    RecipePlay withoutShield =
        planner.plan(new RecipeRequest(model, NO_TRAITS, snapshot, TARGET.id()));

    assertThat(withShield.recipe().zombies()).isEqualTo(new RoleSplit(2, 2, 0));
    assertThat(withoutShield.recipe().zombies()).isEqualTo(new RoleSplit(4, 0, 0));
  }

  @Test
  void thePlayCarriesTheFeaturesItLearnsFrom() {
    RecipePlay play = plannerWithNeutralDraws(1).plan(fourZombiesFlankRequest(NO_TRAITS));

    double[] expected =
        ContextualFeatures.of(
            RecipeFeatures.of(play.recipe(), 0, new RecipeBounds(20, 400, 0.5)), NO_TRAITS);

    assertThat(play.features()).containsExactlyElementsOf(boxed(expected));
  }

  @Test
  void learningAddsTheOutcome() {
    RecipePlanner planner = plannerWithNeutralDraws(1);
    RecipePlay play = planner.plan(fourZombiesFlankRequest(NO_TRAITS));
    double[] features = play.features().stream().mapToDouble(Double::doubleValue).toArray();
    LinearPosterior expected = planner.prior().withObservation(features, 0.7, 0.01);

    RecipeModelRecord learned = planner.learned(planner.prior(), new RecipeOutcome(play, 0.7, 500));

    assertThat(learned.lastTick()).isEqualTo(500);
    assertThat(learned.model().observations()).isCloseTo(1, within(TOLERANCE));
    assertThat(learned.model().mean()).containsExactly(expected.mean(), within(TOLERANCE));
  }

  private RecipePlanner plannerWithNeutralDraws(int plans) {
    double[] draws = new double[ContextualFeatures.DIMENSION * plans];
    return new RecipePlanner(
        () -> settings, new ScriptedRandomSource().withGaussians(draws), new CombatGeometry());
  }

  private static RecipeRequest fourZombiesFlankRequest(PlayerTraits traits) {
    LinearPosterior model = modelWith(Map.of(1, 2.0, 2, -2.0, 3, -1.0));
    GroupSnapshot snapshot =
        snapshotOf(
            zombie(FRONT, new Vec3(0, 64, 5)),
            zombie(RIGHT, new Vec3(5, 64, 0)),
            zombie(BEHIND, new Vec3(0, 64, -5)),
            zombie(LEFT, new Vec3(-5, 64, 1)));
    return new RecipeRequest(model, traits, snapshot, TARGET.id());
  }

  private static LinearPosterior modelWith(Map<Integer, Double> means) {
    double[] mean = new double[ContextualFeatures.DIMENSION];
    means.forEach((index, value) -> mean[index] = value);
    return LinearPosterior.prior(mean, 1.0);
  }

  private static GroupSnapshot snapshotOf(MobSnapshot... mobs) {
    return new GroupSnapshot(new GroupId(new UUID(0, 9)), 0, List.of(mobs), List.of(TARGET));
  }

  private static MobSnapshot zombie(MobId id, Vec3 position) {
    return new MobSnapshotBuilder().withId(id).withPosition(position).build();
  }

  private static MobSnapshot spider(MobId id, Vec3 position) {
    return new MobSnapshotBuilder()
        .withId(id)
        .withKind(MobKind.SPIDER)
        .withPosition(position)
        .build();
  }

  private static MobId mobId(long value) {
    return new MobId(new UUID(0, value));
  }

  private static List<Double> boxed(double[] values) {
    return Arrays.stream(values).boxed().toList();
  }
}
