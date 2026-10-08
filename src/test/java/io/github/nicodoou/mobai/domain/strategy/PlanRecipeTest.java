package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlanRecipeTest {
  private static final RoleSplit NO_MOBS = new RoleSplit(0, 0, 0);

  @Test
  void negativeCountsAreRejected() {
    assertThatThrownBy(() -> new RoleSplit(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RoleSplit counts must be zero or positive, got -1/0/0");
  }

  @Test
  void spidersCannotKeepAReserve() {
    var spiders = new RoleSplit(1, 0, 1);

    assertThatThrownBy(() -> new PlanRecipe(NO_MOBS, spiders, false, 25, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlanRecipe.spiders cannot keep a reserve, got 1");
  }

  @Test
  void reserveDelayAndRetreatAreValidated() {
    assertThatThrownBy(() -> new PlanRecipe(NO_MOBS, NO_MOBS, false, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlanRecipe.reserveDelayTicks must be at least 1, got 0");
    assertThatThrownBy(() -> new PlanRecipe(NO_MOBS, NO_MOBS, false, 25, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("PlanRecipe.retreatHealthFraction must be in [0, 1), got 1.0");
  }

  @Test
  void boundsAreValidated() {
    assertThatThrownBy(() -> new RecipeBounds(0, 400, 0.6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeBounds.minReserveDelayTicks must be at least 1, got 0");
    assertThatThrownBy(() -> new RecipeBounds(25, 25, 0.6))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeBounds.maxReserveDelayTicks must exceed the minimum, got 25 <= 25");
    assertThatThrownBy(() -> new RecipeBounds(25, 400, 1.0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("RecipeBounds.maxRetreatHealthFraction must be in (0, 1), got 1.0");
  }
}
