package io.github.nicodoou.mobai.domain.shared;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Vec3Test {

  @Test
  void plusMinusAndTimesAreComponentWise() {
    Vec3 a = new Vec3(1, 2, 3);
    Vec3 b = new Vec3(4, 5, 6);

    assertThat(a.plus(b)).isEqualTo(new Vec3(5, 7, 9));
    assertThat(b.minus(a)).isEqualTo(new Vec3(3, 3, 3));
    assertThat(a.times(2)).isEqualTo(new Vec3(2, 4, 6));
  }

  @Test
  void dotProductOfKnownVectors() {
    Vec3 a = new Vec3(1, 2, 3);
    Vec3 b = new Vec3(4, 5, 6);

    assertThat(a.dot(b)).isEqualTo(32);
  }

  @Test
  void lengthOfThreeFourZeroIsFive() {
    Vec3 v = new Vec3(3, 4, 0);

    assertThat(v.length()).isEqualTo(5);
  }

  @Test
  void horizontalDropsTheVerticalComponent() {
    Vec3 v = new Vec3(1, 2, 3);

    assertThat(v.horizontal()).isEqualTo(new Vec3(1, 0, 3));
  }

  @Test
  void distanceBetweenKnownPoints() {
    Vec3 a = new Vec3(0, 0, 0);
    Vec3 b = new Vec3(3, 4, 12);

    assertThat(a.distanceTo(b)).isEqualTo(13);
  }

  @Test
  void normalizedVectorHasLengthOne() {
    Vec3 v = new Vec3(3, 4, 12);

    assertThat(v.normalized().length()).isCloseTo(1, within(1e-9));
  }

  @Test
  void normalizingZeroVectorFails() {
    assertThatThrownBy(() -> Vec3.ZERO.normalized())
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot normalize a zero-length vector");
  }

  @Test
  void angleBetweenPerpendicularVectorsIsNinety() {
    Vec3 a = new Vec3(1, 0, 0);
    Vec3 b = new Vec3(0, 0, 1);

    assertThat(a.angleDegreesTo(b)).isCloseTo(90, within(1e-9));
  }

  @Test
  void angleBetweenSameDirectionIsZero() {
    Vec3 a = new Vec3(1, 0, 0);
    Vec3 b = new Vec3(2, 0, 0);

    assertThat(a.angleDegreesTo(b)).isCloseTo(0, within(1e-9));
  }

  @Test
  void angleBetweenOppositeVectorsIsOneHundredEighty() {
    Vec3 a = new Vec3(1, 0, 0);
    Vec3 b = new Vec3(-1, 0, 0);

    assertThat(a.angleDegreesTo(b)).isCloseTo(180, within(1e-9));
  }

  @Test
  void angleWithZeroVectorFails() {
    Vec3 a = new Vec3(1, 0, 0);

    assertThatThrownBy(() -> a.angleDegreesTo(Vec3.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("cannot measure an angle with a zero-length vector");
  }

  @Test
  void rejectsNonFiniteComponents() {
    assertThatThrownBy(() -> new Vec3(Double.NaN, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Vec3 components must be finite, got (NaN, 0.0, 0.0)");

    assertThatThrownBy(() -> new Vec3(0, Double.POSITIVE_INFINITY, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
