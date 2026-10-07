package io.github.nicodoou.mobai.adapter.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpawnRingTest {
  private static final Vec3 CENTER = new Vec3(10, 64, -5);

  @Test
  void positionsAreOnTheRing() {
    List<Vec3> positions = SpawnRing.positions(CENTER, 9);

    assertThat(positions).hasSize(9);
    for (Vec3 position : positions) {
      assertThat(position.minus(CENTER).horizontal().length())
          .isCloseTo(SpawnRing.SPAWN_RING_BLOCKS, within(1e-9));
      assertThat(position.y()).isEqualTo(64);
    }
  }

  @Test
  void positionsAreEvenlySpaced() {
    List<Vec3> positions = SpawnRing.positions(CENTER, 4);

    assertThat(positions).hasSize(4);
    assertClose(positions.get(0), 14, 64, -5);
    assertClose(positions.get(1), 10, 64, -1);
    assertClose(positions.get(2), 6, 64, -5);
    assertClose(positions.get(3), 10, 64, -9);
  }

  @Test
  void countMustBePositive() {
    assertThatThrownBy(() -> SpawnRing.positions(CENTER, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("SpawnRing.count must be at least 1, got 0");
  }

  private static void assertClose(Vec3 actual, double x, double y, double z) {
    assertThat(actual.x()).isCloseTo(x, within(1e-9));
    assertThat(actual.y()).isCloseTo(y, within(1e-9));
    assertThat(actual.z()).isCloseTo(z, within(1e-9));
  }
}
