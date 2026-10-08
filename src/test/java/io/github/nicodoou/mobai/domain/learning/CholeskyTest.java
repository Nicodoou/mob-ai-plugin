package io.github.nicodoou.mobai.domain.learning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class CholeskyTest {

  @Test
  void factorsAKnownMatrix() {
    Cholesky cholesky = Cholesky.of(new double[][] {{4, 2}, {2, 3}});

    double[][] lower = cholesky.lower();

    assertThat(lower[0]).containsExactly(new double[] {2, 0}, within(1e-9));
    assertThat(lower[1]).containsExactly(new double[] {1, Math.sqrt(2)}, within(1e-9));
  }

  @Test
  void solveInvertsTheMatrix() {
    Cholesky cholesky = Cholesky.of(new double[][] {{3, 4}, {4, 9}});

    double[] solution = cholesky.solve(new double[] {2, 4});

    assertThat(solution).containsExactly(new double[] {2.0 / 11, 4.0 / 11}, within(1e-9));
  }

  @Test
  void solveTransposedIsTheBackSubstitution() {
    Cholesky cholesky = Cholesky.of(new double[][] {{3, 4}, {4, 9}});

    double[] solution = cholesky.solveTransposed(new double[] {0, 1});

    assertThat(solution)
        .containsExactly(new double[] {-0.6963106238227916, 0.5222329678670936}, within(1e-9));
  }

  @Test
  void aMatrixThatIsNotPositiveDefiniteIsRejected() {
    double[][] indefinite = {{1, 2}, {2, 1}};
    double[][] notSymmetric = {{2, 1}, {0, 2}};

    assertThatThrownBy(() -> Cholesky.of(indefinite))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cholesky.matrix must be symmetric positive definite");
    assertThatThrownBy(() -> Cholesky.of(notSymmetric))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Cholesky.matrix must be symmetric positive definite");
  }
}
