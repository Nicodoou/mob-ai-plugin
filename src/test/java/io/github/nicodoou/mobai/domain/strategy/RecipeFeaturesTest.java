package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class RecipeFeaturesTest {
  private static final RecipeBounds BOUNDS = new RecipeBounds(25, 400, 0.6);
  private static final RoleSplit NO_MOBS = new RoleSplit(0, 0, 0);

  @Test
  void knownRecipeGivesKnownFeatures() {
    var recipe = new PlanRecipe(new RoleSplit(2, 1, 1), new RoleSplit(1, 1, 0), true, 100, 0.3);

    double[] features = RecipeFeatures.of(recipe, 3, BOUNDS);

    assertThat(features)
        .containsExactly(
            new double[] {
              1, 0.25, 0.0625, 0.25, 0.0625, 0.5, 0.25, 0.5, 0.25, 0.5, 0.25, 1, 0.0625, 1.0 / 3,
              1.0 / 9
            },
            within(1e-9));
  }

  @Test
  void withoutReserveTheDelayIsIgnored() {
    var recipe = new PlanRecipe(new RoleSplit(4, 0, 0), NO_MOBS, false, 400, 0);

    double[] features = RecipeFeatures.of(recipe, 0, BOUNDS);

    assertThat(features[RecipeFeatures.DELAY_LINEAR]).isEqualTo(0, within(1e-9));
    assertThat(features[RecipeFeatures.DELAY_SQUARE]).isEqualTo(0, within(1e-9));
  }

  @Test
  void missingKindsGiveZeroFractions() {
    var recipe = new PlanRecipe(NO_MOBS, NO_MOBS, false, 25, 0);

    double[] features = RecipeFeatures.of(recipe, 3, BOUNDS);

    for (int index : new int[] {1, 2, 3, 4, 5, 6, 7, 8, 12, 13, 14}) {
      assertThat(features[index]).as("feature %d", index).isEqualTo(0, within(1e-9));
    }
  }

  @Test
  void invalidInputsAreRejected() {
    var noMobs = new PlanRecipe(NO_MOBS, NO_MOBS, false, 25, 0);
    var lateReserve = new PlanRecipe(new RoleSplit(0, 0, 1), NO_MOBS, false, 401, 0);
    var lateRetreat = new PlanRecipe(new RoleSplit(1, 0, 0), NO_MOBS, false, 25, 0.7);

    assertThatThrownBy(() -> RecipeFeatures.of(noMobs, 0, BOUNDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeFeatures needs at least one mob");
    assertThatThrownBy(() -> RecipeFeatures.of(noMobs, -1, BOUNDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeFeatures.skeletons must be zero or positive, got -1");
    assertThatThrownBy(() -> RecipeFeatures.of(lateReserve, 0, BOUNDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeFeatures.reserveDelayTicks must be between 25 and 400, got 401");
    assertThatThrownBy(() -> RecipeFeatures.of(lateRetreat, 0, BOUNDS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeFeatures.retreatHealthFraction must not exceed 0.6, got 0.7");
  }
}
