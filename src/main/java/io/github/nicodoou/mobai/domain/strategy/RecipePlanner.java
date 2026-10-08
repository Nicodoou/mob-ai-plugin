package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.settings.LearningSettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Plans with learned recipes (CT-30): the current model, the recipe and its roles, and learning.
 */
public final class RecipePlanner {
  private static final int BIAS_INDEX = 0;
  private static final double HALF = 0.5;

  private final Supplier<MobAiSettings> settings;
  private final RandomSource random;
  private final CombatGeometry geometry;
  private final RecipeSearch search;

  public RecipePlanner(
      Supplier<MobAiSettings> settings, RandomSource random, CombatGeometry geometry) {
    this.settings = Objects.requireNonNull(settings, "RecipePlanner.settings");
    this.random = Objects.requireNonNull(random, "RecipePlanner.random");
    this.geometry = Objects.requireNonNull(geometry, "RecipePlanner.geometry");
    this.search = new RecipeSearch(this::bounds);
  }

  public LinearPosterior prior() {
    LearningSettings learning = learning();
    double[] mean = new double[ContextualFeatures.DIMENSION];
    mean[BIAS_INDEX] = learning.priorSuccess();
    return LinearPosterior.prior(mean, learning.priorVariance());
  }

  public LinearPosterior current(Optional<RecipeModelRecord> stored, long tick) {
    Objects.requireNonNull(stored, "RecipePlanner.stored");
    if (stored.isEmpty()) {
      return prior();
    }
    RecipeModelRecord record = stored.get();
    return record.model().shrunkToward(prior(), keepAfter(record.lastTick(), tick));
  }

  public RecipePlay plan(RecipeRequest request) {
    Objects.requireNonNull(request, "RecipePlanner.request");
    GroupComposition composition = GroupComposition.of(request.snapshot());
    PlanRecipe recipe = search.best(foldedWeights(request), composition);
    Set<MobId> flankers = flankersOf(recipe, request);
    return new RecipePlay(
        recipe,
        request.traits(),
        featuresOf(recipe, composition, request.traits()),
        rolesOf(request.snapshot(), flankers),
        reserveOf(recipe, request, flankers));
  }

  public RecipeModelRecord learned(LinearPosterior current, RecipeOutcome outcome) {
    Objects.requireNonNull(current, "RecipePlanner.current");
    Objects.requireNonNull(outcome, "RecipePlanner.outcome");
    double[] features =
        outcome.play().features().stream().mapToDouble(Double::doubleValue).toArray();
    LinearPosterior model =
        current.withObservation(features, outcome.success(), learning().modelNoiseVariance());
    return new RecipeModelRecord(model, outcome.tick());
  }

  // The same half-life as the rest of the group memory, so every record forgets alike.
  private double keepAfter(long lastTick, long tick) {
    double halfLives =
        Math.max(0, tick - lastTick) / (double) settings.get().memory().halfLifeTicks();
    return Math.pow(HALF, halfLives);
  }

  private double[] foldedWeights(RecipeRequest request) {
    double[] sampled = request.model().sample(random, learning().explorationScale());
    return ContextualFeatures.weightsFor(sampled, request.traits());
  }

  private List<Double> featuresOf(
      PlanRecipe recipe, GroupComposition composition, PlayerTraits traits) {
    double[] recipeFeatures = RecipeFeatures.of(recipe, composition.skeletons(), bounds());
    return Arrays.stream(ContextualFeatures.of(recipeFeatures, traits)).boxed().toList();
  }

  private Set<MobId> flankersOf(PlanRecipe recipe, RecipeRequest request) {
    Optional<PlayerPose> pose = targetOf(request).map(PlayerSnapshot::pose);
    List<MobSnapshot> zombies = mobsOfKind(request.snapshot(), MobKind.ZOMBIE);
    List<MobSnapshot> spiders = mobsOfKind(request.snapshot(), MobKind.SPIDER);
    Set<MobId> flankers = new HashSet<>(mostSideways(zombies, pose, recipe.zombies().flank()));
    flankers.addAll(mostSideways(spiders, pose, recipe.spiders().flank()));
    return flankers;
  }

  private List<MobId> mostSideways(List<MobSnapshot> mobs, Optional<PlayerPose> pose, int count) {
    return SidewaysOrder.of(geometry, mobs, pose).stream()
        .limit(count)
        .map(MobSnapshot::id)
        .toList();
  }

  // The farthest leave the target's reach soonest, so they make the cheapest reserve.
  private static Set<MobId> reserveOf(
      PlanRecipe recipe, RecipeRequest request, Set<MobId> flankers) {
    List<MobSnapshot> candidates =
        mobsOfKind(request.snapshot(), MobKind.ZOMBIE).stream()
            .filter(mob -> !flankers.contains(mob.id()))
            .toList();
    return farthestFirst(candidates, targetOf(request)).stream()
        .limit(recipe.zombies().reserve())
        .map(MobSnapshot::id)
        .collect(Collectors.toSet());
  }

  private static List<MobSnapshot> farthestFirst(
      List<MobSnapshot> mobs, Optional<PlayerSnapshot> target) {
    if (target.isEmpty()) {
      return mobs;
    }
    Vec3 targetPosition = target.get().pose().position();
    List<MobSnapshot> sorted = new ArrayList<>(mobs);
    sorted.sort(
        Comparator.comparingDouble((MobSnapshot mob) -> horizontalDistance(mob, targetPosition))
            .reversed());
    return sorted;
  }

  private static double horizontalDistance(MobSnapshot mob, Vec3 targetPosition) {
    return mob.position().minus(targetPosition).horizontal().length();
  }

  private static Map<MobId, Role> rolesOf(GroupSnapshot snapshot, Set<MobId> flankers) {
    Map<MobId, Role> roles = new LinkedHashMap<>();
    for (MobSnapshot mob : snapshot.mobs()) {
      roles.put(mob.id(), roleFor(mob, flankers));
    }
    return roles;
  }

  private static Role roleFor(MobSnapshot mob, Set<MobId> flankers) {
    if (mob.kind() == MobKind.SKELETON) {
      return Role.SHOOT;
    }
    if (flankers.contains(mob.id())) {
      return Role.FLANK;
    }
    return Role.PRESS;
  }

  private static Optional<PlayerSnapshot> targetOf(RecipeRequest request) {
    return request.snapshot().player(request.target());
  }

  private static List<MobSnapshot> mobsOfKind(GroupSnapshot snapshot, MobKind kind) {
    return snapshot.mobs().stream().filter(mob -> mob.kind() == kind).toList();
  }

  private RecipeBounds bounds() {
    LearningSettings learning = learning();
    return new RecipeBounds(
        learning.minReserveDelayTicks(),
        learning.maxReserveDelayTicks(),
        learning.maxRetreatHealthFraction());
  }

  private LearningSettings learning() {
    return settings.get().learning();
  }
}
