package io.github.nicodoou.mobai.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.simulation.RecipeLearner.Episode;
import io.github.nicodoou.mobai.simulation.RecipeLearner.ModelKind;
import io.github.nicodoou.mobai.simulation.RecipeLearner.Opponent;
import io.github.nicodoou.mobai.simulation.RecipeLearner.Session;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import org.junit.jupiter.api.Test;

class RecipeLearnerTest {
  private static final double PLAY_EXPLORATION = 1;
  private static final double NO_JITTER = 0;
  private static final double TRAIT_JITTER = 0.05;

  @Test
  void priorsHaveTheirDimensions() {
    LinearPosterior flat = RecipeLearner.prior(ModelKind.FLAT);
    LinearPosterior contextual = RecipeLearner.prior(ModelKind.CONTEXTUAL);

    assertThat(flat.dimension()).isEqualTo(15);
    assertThat(contextual.dimension()).isEqualTo(60);
    for (LinearPosterior prior : new LinearPosterior[] {flat, contextual}) {
      double[] mean = prior.mean();
      assertThat(mean[0]).isEqualTo(0.5, within(1e-9));
      for (int i = 1; i < mean.length; i++) {
        assertThat(mean[i]).as("mean %d", i).isEqualTo(0, within(1e-9));
      }
    }
  }

  @Test
  void anEpisodeHasOneScorePerPlan() {
    var learner = new RecipeLearner(new SeededRandomSource(1));
    var opponent = new Opponent(PlayStyle.ARCHER, PlayStyle.ARCHER.traits());

    Episode episode =
        learner.play(
            RecipeLearner.prior(ModelKind.CONTEXTUAL),
            opponent,
            new Session(ModelKind.CONTEXTUAL, 12, PLAY_EXPLORATION, NO_JITTER));

    assertThat(episode.trueScores()).hasSize(12);
    assertThat(episode.model().observations()).isEqualTo(12, within(1e-9));
  }

  @Test
  void sameSeedGivesTheSameEpisode() {
    var opponent = new Opponent(PlayStyle.SHIELD_BLOCKER, PlayStyle.SHIELD_BLOCKER.traits());
    var session = new Session(ModelKind.CONTEXTUAL, 20, PLAY_EXPLORATION, TRAIT_JITTER);

    Episode first =
        new RecipeLearner(new SeededRandomSource(5))
            .play(RecipeLearner.prior(ModelKind.CONTEXTUAL), opponent, session);
    Episode second =
        new RecipeLearner(new SeededRandomSource(5))
            .play(RecipeLearner.prior(ModelKind.CONTEXTUAL), opponent, session);

    assertThat(second.trueScores()).isEqualTo(first.trueScores());
  }
}
