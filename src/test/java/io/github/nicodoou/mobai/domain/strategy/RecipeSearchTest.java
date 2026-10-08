package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RecipeSearchTest {
  private final RecipeSearch search = new RecipeSearch(() -> new RecipeBounds(25, 400, 0.6));

  @Test
  void allZeroWeightsKeepTheFirstCandidate() {
    double[] weights = new double[RecipeFeatures.DIMENSION];

    var recipe = search.best(weights, new GroupComposition(4, 3, 2));

    assertThat(recipe.zombies()).isEqualTo(new RoleSplit(4, 0, 0));
    assertThat(recipe.spiders()).isEqualTo(new RoleSplit(2, 0, 0));
    assertThat(recipe.volley()).isFalse();
    assertThat(recipe.reserveDelayTicks()).isEqualTo(25);
    assertThat(recipe.retreatHealthFraction()).isEqualTo(0, within(1e-9));
  }

  @Test
  void flankFractionFollowsThePeak() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[1] = 2;
    weights[2] = -2;
    weights[3] = -1;

    var recipe = search.best(weights, new GroupComposition(4, 0, 0));

    assertThat(recipe.zombies()).isEqualTo(new RoleSplit(2, 2, 0));
  }

  @Test
  void anInteriorPeakPicksTheBestPlayableSplit() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[1] = 0.8;
    weights[2] = -1;
    weights[3] = -1;

    var withFour = search.best(weights, new GroupComposition(4, 0, 0));
    var withFive = search.best(weights, new GroupComposition(5, 0, 0));

    assertThat(withFour.zombies()).isEqualTo(new RoleSplit(2, 2, 0));
    assertThat(withFive.zombies()).isEqualTo(new RoleSplit(3, 2, 0));
  }

  @Test
  void reserveDelayFollowsItsPeakInLogScale() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[3] = 1;
    weights[7] = 1;
    weights[8] = -1;

    var recipe = search.best(weights, new GroupComposition(4, 0, 0));

    assertThat(recipe.zombies()).isEqualTo(new RoleSplit(0, 0, 4));
    assertThat(recipe.reserveDelayTicks()).isEqualTo(100);
  }

  @Test
  void withoutReserveTheDelayIsTheMinimum() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[3] = -1;
    weights[7] = 1;
    weights[8] = -1;

    var recipe = search.best(weights, new GroupComposition(4, 0, 0));

    assertThat(recipe.zombies()).isEqualTo(new RoleSplit(4, 0, 0));
    assertThat(recipe.reserveDelayTicks()).isEqualTo(25);
  }

  @Test
  void retreatThresholdFollowsItsPeak() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[9] = 0.6;
    weights[10] = -1;

    var recipe = search.best(weights, new GroupComposition(4, 0, 0));

    assertThat(recipe.retreatHealthFraction()).isEqualTo(0.18, within(1e-9));
  }

  @Test
  void volleyOnlyWhenViable() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[11] = 1;

    var withoutSkeletons = search.best(weights, new GroupComposition(2, 0, 0));
    var withTwoSkeletons = search.best(weights, new GroupComposition(2, 2, 0));

    assertThat(withoutSkeletons.volley()).isFalse();
    assertThat(withTwoSkeletons.volley()).isTrue();
  }

  @Test
  void spiderFlankersFollowTheirPeak() {
    double[] weights = new double[RecipeFeatures.DIMENSION];
    weights[5] = 0.8;
    weights[6] = -1;

    var recipe = search.best(weights, new GroupComposition(0, 0, 3));

    assertThat(recipe.spiders()).isEqualTo(new RoleSplit(2, 1, 0));
  }

  @Test
  void wrongWeightCountIsRejected() {
    double[] weights = new double[14];

    assertThatThrownBy(() -> search.best(weights, new GroupComposition(4, 0, 0)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeSearch.weights must have 15 values, got 14");
  }
}
