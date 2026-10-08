package io.github.nicodoou.mobai.domain.learning;

/** L·Lᵀ factor of a symmetric positive definite matrix, and the solves the posterior needs. */
final class Cholesky {
  // Absorbs floating-point rounding in matrices that are symmetric by construction.
  private static final double SYMMETRY_TOLERANCE = 1e-9;
  private static final String NOT_POSITIVE_DEFINITE =
      "Cholesky.matrix must be symmetric positive definite";

  private final double[][] lower;

  private Cholesky(double[][] lower) {
    this.lower = lower;
  }

  static Cholesky of(double[][] matrix) {
    requireSquare(matrix);
    requireSymmetric(matrix);
    return new Cholesky(factor(matrix));
  }

  /** x with L·Lᵀ·x = b. */
  double[] solve(double[] b) {
    return solveTransposed(solveForward(b));
  }

  /** x with Lᵀ·x = z (back substitution). */
  double[] solveTransposed(double[] z) {
    int size = lower.length;
    double[] x = new double[size];
    for (int i = size - 1; i >= 0; i--) {
      double sum = z[i];
      for (int k = i + 1; k < size; k++) {
        sum -= lower[k][i] * x[k];
      }
      x[i] = sum / lower[i][i];
    }
    return x;
  }

  double[][] lower() {
    double[][] copy = new double[lower.length][];
    for (int i = 0; i < lower.length; i++) {
      copy[i] = lower[i].clone();
    }
    return copy;
  }

  private double[] solveForward(double[] b) {
    int size = lower.length;
    double[] y = new double[size];
    for (int i = 0; i < size; i++) {
      double sum = b[i];
      for (int k = 0; k < i; k++) {
        sum -= lower[i][k] * y[k];
      }
      y[i] = sum / lower[i][i];
    }
    return y;
  }

  private static void requireSquare(double[][] matrix) {
    if (matrix.length == 0) {
      throw new IllegalArgumentException("Cholesky.matrix must not be empty");
    }
    for (double[] row : matrix) {
      if (row.length != matrix.length) {
        throw new IllegalArgumentException(
            "Cholesky.matrix must be square, got a row of "
                + row.length
                + " values in a matrix of "
                + matrix.length
                + " rows");
      }
    }
  }

  private static void requireSymmetric(double[][] matrix) {
    for (int i = 0; i < matrix.length; i++) {
      for (int j = 0; j < i; j++) {
        double difference = Math.abs(matrix[i][j] - matrix[j][i]);
        if (difference > SYMMETRY_TOLERANCE * Math.max(1, Math.abs(matrix[i][j]))) {
          throw new IllegalArgumentException(NOT_POSITIVE_DEFINITE);
        }
      }
    }
  }

  private static double[][] factor(double[][] matrix) {
    int size = matrix.length;
    double[][] lower = new double[size][size];
    for (int i = 0; i < size; i++) {
      for (int j = 0; j <= i; j++) {
        double sum = matrix[i][j] - rowProduct(lower, i, j);
        lower[i][j] = i == j ? diagonalEntry(sum) : sum / lower[j][j];
      }
    }
    return lower;
  }

  private static double rowProduct(double[][] lower, int i, int j) {
    double sum = 0;
    for (int k = 0; k < j; k++) {
      sum += lower[i][k] * lower[j][k];
    }
    return sum;
  }

  private static double diagonalEntry(double sum) {
    if (!(sum > 0) || !Double.isFinite(sum)) {
      throw new IllegalArgumentException(NOT_POSITIVE_DEFINITE);
    }
    return Math.sqrt(sum);
  }
}
