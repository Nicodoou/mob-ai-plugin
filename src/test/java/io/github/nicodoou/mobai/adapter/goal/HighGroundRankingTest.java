package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighGroundRankingTest {
  private static final double CURRENT_GROUND_Y = 64;

  private final HighGroundRanking ranking = new HighGroundRanking();

  @Test
  void onlyClearlyHigherSpotsCount() {
    Vec3 a = spot(1, 65);
    Vec3 b = spot(2, 66);
    Vec3 c = spot(3, 70);

    List<Vec3> ranked = ranking.rank(List.of(a, b, c), CURRENT_GROUND_Y);

    assertThat(ranked).containsExactly(c, b);
  }

  @Test
  void highestComesFirst() {
    Vec3 a = spot(1, 67);
    Vec3 b = spot(2, 72);
    Vec3 c = spot(3, 69);

    List<Vec3> ranked = ranking.rank(List.of(a, b, c), CURRENT_GROUND_Y);

    assertThat(ranked).containsExactly(b, c, a);
  }

  @Test
  void equalHeightsKeepTheSearchOrder() {
    Vec3 a = spot(1, 68);
    Vec3 b = spot(2, 68);

    List<Vec3> ranked = ranking.rank(List.of(a, b), CURRENT_GROUND_Y);

    assertThat(ranked).containsExactly(a, b);
  }

  @Test
  void noHigherSpotGivesNothing() {
    Vec3 a = spot(1, 64);
    Vec3 b = spot(2, 65.5);

    List<Vec3> ranked = ranking.rank(List.of(a, b), CURRENT_GROUND_Y);

    assertThat(ranked).isEmpty();
  }

  private static Vec3 spot(double x, double y) {
    return new Vec3(x, y, 0);
  }
}
