package io.github.nicodoou.mobai.adapter.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JdkRandomSourceTest {
  private static final int INDEX_BOUND = 10;
  private static final int SAMPLE_COUNT = 1_000;

  @Test
  void sameSeedGivesTheSameSequence() {
    JdkRandomSource first = new JdkRandomSource(42);
    JdkRandomSource second = new JdkRandomSource(42);

    assertThat(first.nextUnit()).isEqualTo(second.nextUnit());
    assertThat(first.nextGaussian()).isEqualTo(second.nextGaussian());
    assertThat(first.nextIndex(INDEX_BOUND)).isEqualTo(second.nextIndex(INDEX_BOUND));
    assertThat(first.nextUnit()).isEqualTo(second.nextUnit());
    assertThat(first.nextGaussian()).isEqualTo(second.nextGaussian());
  }

  @Test
  void unitsStayInsideZeroToOne() {
    JdkRandomSource source = new JdkRandomSource(7);

    for (int draw = 0; draw < SAMPLE_COUNT; draw++) {
      assertThat(source.nextUnit()).isGreaterThanOrEqualTo(0.0).isLessThan(1.0);
    }
  }

  @Test
  void indexRejectsANonPositiveBound() {
    JdkRandomSource source = new JdkRandomSource(1);

    assertThatThrownBy(() -> source.nextIndex(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("JdkRandomSource.bound must be positive, got 0");
  }
}
