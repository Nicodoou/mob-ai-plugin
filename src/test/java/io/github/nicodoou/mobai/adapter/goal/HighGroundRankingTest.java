package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

class HighGroundRankingTest {
  private static final double CURRENT_GROUND_Y = 64;
  private static final double SPACING_BLOCKS = 3.0;

  private final HighGroundRanking ranking = new HighGroundRanking(() -> SPACING_BLOCKS);

  @Test
  void onlyClearlyHigherSpotsCount() {
    Vec3 a = spot(1, 65);
    Vec3 b = spot(2, 66);
    Vec3 c = spot(3, 70);

    List<Vec3> ranked = ranking.rank(List.of(a, b, c), CURRENT_GROUND_Y, List.of());

    assertThat(ranked).containsExactly(c, b);
  }

  @Test
  void highestComesFirst() {
    Vec3 a = spot(1, 67);
    Vec3 b = spot(2, 72);
    Vec3 c = spot(3, 69);

    List<Vec3> ranked = ranking.rank(List.of(a, b, c), CURRENT_GROUND_Y, List.of());

    assertThat(ranked).containsExactly(b, c, a);
  }

  @Test
  void equalHeightsKeepTheSearchOrder() {
    Vec3 a = spot(1, 68);
    Vec3 b = spot(2, 68);

    List<Vec3> ranked = ranking.rank(List.of(a, b), CURRENT_GROUND_Y, List.of());

    assertThat(ranked).containsExactly(a, b);
  }

  @Test
  void noHigherSpotGivesNothing() {
    Vec3 a = spot(1, 64);
    Vec3 b = spot(2, 65.5);

    List<Vec3> ranked = ranking.rank(List.of(a, b), CURRENT_GROUND_Y, List.of());

    assertThat(ranked).isEmpty();
  }

  @Test
  void aClaimedTopIsLeftToItsShooter() {
    Vec3 ground = spot(0, 64);
    Vec3 top = spot(10, 67);
    Vec3 lower = spot(16, 66);

    List<Vec3> ranked = ranking.rank(List.of(ground, top, lower), CURRENT_GROUND_Y, List.of(top));

    assertThat(ranked.getFirst()).isEqualTo(lower);
  }

  @Test
  void claimsFartherThanTheSpacingDoNotMatter() {
    Vec3 ground = spot(0, 64);
    Vec3 top = spot(10, 67);
    Vec3 lower = spot(16, 66);

    List<Vec3> ranked =
        ranking.rank(List.of(ground, top, lower), CURRENT_GROUND_Y, List.of(spot(30, 67)));

    assertThat(ranked.getFirst()).isEqualTo(top);
  }

  @Test
  void zeroSpacingKeepsEveryCandidate() {
    HighGroundRanking unspaced = new HighGroundRanking(() -> 0.0);
    Vec3 ground = spot(0, 64);
    Vec3 top = spot(10, 67);
    Vec3 lower = spot(16, 66);

    List<Vec3> ranked = unspaced.rank(List.of(ground, top, lower), CURRENT_GROUND_Y, List.of(top));

    assertThat(ranked.getFirst()).isEqualTo(top);
  }

  private static Vec3 spot(double x, double y) {
    return new Vec3(x, y, 0);
  }
}
