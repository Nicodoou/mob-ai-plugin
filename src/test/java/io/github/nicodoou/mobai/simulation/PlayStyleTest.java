package io.github.nicodoou.mobai.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import org.junit.jupiter.api.Test;

class PlayStyleTest {
  @Test
  void trueScoresOfKnownRecipes() {
    var flank =
        new PlanRecipe(new RoleSplit(2, 2, 0), new RoleSplit(1, 1, 0), true, 20, 0.3, 0.3, 0.5);
    var assault =
        new PlanRecipe(new RoleSplit(4, 0, 0), new RoleSplit(2, 0, 0), false, 20, 0, 0.3, 0.5);

    assertThat(PlayStyle.SHIELD_BLOCKER.trueScore(flank)).isEqualTo(0.748, within(1e-9));
    assertThat(PlayStyle.BERSERKER.trueScore(flank)).isEqualTo(0.17175, within(1e-9));
    assertThat(PlayStyle.ARCHER.trueScore(flank)).isEqualTo(0.4485, within(1e-9));
    assertThat(PlayStyle.SHIELD_BLOCKER.trueScore(assault)).isEqualTo(0.163, within(1e-9));
    assertThat(PlayStyle.BERSERKER.trueScore(assault)).isEqualTo(0.50675, within(1e-9));
    assertThat(PlayStyle.ARCHER.trueScore(assault)).isEqualTo(-0.5615, within(1e-9));
  }

  @Test
  void aReserveAddsTheDelayTerm() {
    var recipe =
        new PlanRecipe(new RoleSplit(2, 1, 1), new RoleSplit(1, 1, 0), true, 89, 0.3, 0.3, 0.5);

    double score = PlayStyle.SHIELD_BLOCKER.trueScore(recipe);

    assertThat(score).isEqualTo(0.722999176931682, within(1e-9));
  }

  @Test
  void optimaAreTheKnownValues() {
    assertThat(PlayStyle.SHIELD_BLOCKER.optimum()).isEqualTo(0.809999176931682, within(1e-9));
    assertThat(PlayStyle.BERSERKER.optimum()).isEqualTo(0.68125, within(1e-9));
    assertThat(PlayStyle.ARCHER.optimum()).isEqualTo(0.78, within(1e-9));
  }

  @Test
  void legacyRegretsAreTheKnownValues() {
    assertThat(PlayStyle.SHIELD_BLOCKER.legacyRegret()).isEqualTo(0.1453325102650154, within(1e-9));
    assertThat(PlayStyle.BERSERKER.legacyRegret()).isEqualTo(0.16061111111111115, within(1e-9));
    assertThat(PlayStyle.ARCHER.legacyRegret()).isEqualTo(0.3298333333333333, within(1e-9));
  }
}
