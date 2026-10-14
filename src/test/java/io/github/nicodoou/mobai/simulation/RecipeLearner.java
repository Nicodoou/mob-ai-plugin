package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.strategy.ContextualFeatures;
import io.github.nicodoou.mobai.domain.strategy.GroupComposition;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeBounds;
import io.github.nicodoou.mobai.domain.strategy.RecipeFeatures;
import io.github.nicodoou.mobai.domain.strategy.RecipePrior;
import io.github.nicodoou.mobai.domain.strategy.RecipeSearch;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Plays recipes against a synthetic opponent with the real model and search. */
final class RecipeLearner {
  static final GroupComposition GROUP = new GroupComposition(4, 3, 2);
  static final RecipeBounds BOUNDS = new RecipeBounds(20, 400, 0.6, 0.6);
  static final double MODEL_NOISE_VARIANCE = 0.01;
  static final double PRIOR_VARIANCE = 1.0;
  static final double PRIOR_SUCCESS = 0.5;
  static final double REWARD_NOISE = 0.15;
  static final double MID_KNOB_PRIOR = 0.0;

  enum ModelKind {
    FLAT,
    CONTEXTUAL
  }

  record Session(ModelKind kind, int plans, double explorationScale, double traitJitter) {
    Session {
      Objects.requireNonNull(kind, "Session.kind");
      if (plans < 1) {
        throw new IllegalArgumentException("Session.plans must be at least 1, got " + plans);
      }
      if (!(explorationScale > 0)) {
        throw new IllegalArgumentException(
            "Session.explorationScale must be positive, got " + explorationScale);
      }
      if (!(traitJitter >= 0)) {
        throw new IllegalArgumentException(
            "Session.traitJitter must be zero or positive, got " + traitJitter);
      }
    }
  }

  record Opponent(PlayStyle style, PlayerTraits traits) {
    Opponent {
      Objects.requireNonNull(style, "Opponent.style");
      Objects.requireNonNull(traits, "Opponent.traits");
    }
  }

  record Episode(LinearPosterior model, List<Double> trueScores, List<PlanRecipe> recipes) {
    Episode {
      Objects.requireNonNull(model, "Episode.model");
      trueScores = List.copyOf(trueScores);
      recipes = List.copyOf(recipes);
    }
  }

  private final RandomSource random;
  private final RecipeSearch search;

  RecipeLearner(RandomSource random) {
    this.random = Objects.requireNonNull(random, "RecipeLearner.random");
    this.search = new RecipeSearch(() -> BOUNDS);
  }

  static LinearPosterior prior(ModelKind kind) {
    return LinearPosterior.prior(meanOf(kind), PRIOR_VARIANCE);
  }

  Episode play(LinearPosterior start, Opponent opponent, Session session) {
    LinearPosterior model = start;
    List<Double> trueScores = new ArrayList<>();
    List<PlanRecipe> recipes = new ArrayList<>();
    for (int plan = 0; plan < session.plans(); plan++) {
      PlayerTraits traits = planTraits(opponent, session);
      PlanRecipe recipe = chosenRecipe(model, session, traits);
      recipes.add(recipe);
      double trueScore = opponent.style().trueScore(recipe);
      trueScores.add(trueScore);
      double success = observedSuccess(trueScore);
      double[] features = featuresOf(recipe, session.kind(), traits);
      model = model.withObservation(features, success, MODEL_NOISE_VARIANCE);
    }
    return new Episode(model, trueScores, recipes);
  }

  private PlayerTraits planTraits(Opponent opponent, Session session) {
    if (session.traitJitter() > 0) {
      return PlayStyle.perturbed(opponent.traits(), random, session.traitJitter());
    }
    return opponent.traits();
  }

  private PlanRecipe chosenRecipe(LinearPosterior model, Session session, PlayerTraits traits) {
    double[] sampled = model.sample(random, session.explorationScale());
    return search.best(recipeWeightsOf(sampled, session.kind(), traits), GROUP);
  }

  private double observedSuccess(double trueScore) {
    return PlayStyle.clampToUnit(trueScore + REWARD_NOISE * random.nextGaussian());
  }

  private static double[] recipeWeightsOf(double[] sampled, ModelKind kind, PlayerTraits traits) {
    return switch (kind) {
      case FLAT -> sampled;
      case CONTEXTUAL -> ContextualFeatures.weightsFor(sampled, traits);
    };
  }

  private static double[] featuresOf(PlanRecipe recipe, ModelKind kind, PlayerTraits traits) {
    double[] recipeFeatures = RecipeFeatures.of(recipe, GROUP.skeletons(), BOUNDS);
    return switch (kind) {
      case FLAT -> recipeFeatures;
      case CONTEXTUAL -> ContextualFeatures.of(recipeFeatures, traits);
    };
  }

  private static double[] meanOf(ModelKind kind) {
    return switch (kind) {
      case FLAT -> RecipePrior.recipeMean(PRIOR_SUCCESS, MID_KNOB_PRIOR);
      case CONTEXTUAL -> RecipePrior.contextualMean(PRIOR_SUCCESS, MID_KNOB_PRIOR);
    };
  }
}
