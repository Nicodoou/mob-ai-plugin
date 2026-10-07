package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RetreatSituationTest {
  @Test
  void hiddenAndFarHolds() {
    RetreatSituation situation = new RetreatSituation(true, true, false, true);

    assertThat(situation.nextMove()).isEqualTo(RetreatMove.HOLD);
  }

  @Test
  void hiddenButCloseKeepsLooking() {
    RetreatSituation situation = new RetreatSituation(true, false, false, true);

    assertThat(situation.nextMove()).isEqualTo(RetreatMove.SEARCH_COVER);
  }

  @Test
  void coverStillHiddenKeepsItsPath() {
    RetreatSituation situation = new RetreatSituation(false, false, true, true);

    assertThat(situation.nextMove()).isEqualTo(RetreatMove.KEEP_COVER);
  }

  @Test
  void seenWithASearchDueLooksForCover() {
    RetreatSituation situation = new RetreatSituation(false, true, false, true);

    assertThat(situation.nextMove()).isEqualTo(RetreatMove.SEARCH_COVER);
  }

  @Test
  void seenBetweenSearchesRetreatsStraight() {
    RetreatSituation situation = new RetreatSituation(false, false, false, false);

    assertThat(situation.nextMove()).isEqualTo(RetreatMove.RETREAT_STRAIGHT);
  }
}
