package io.github.nicodoou.mobai.domain.geometry;

/**
 * The shape of the surround: how far from the target the strikers stand, how far apart, and how far
 * out the rest wait for their turn.
 */
public record SurroundRing(
    double strikeRadiusBlocks, double spacingBlocks, double waitRadiusBlocks) {
  public SurroundRing {
    requirePositive("strikeRadiusBlocks", strikeRadiusBlocks);
    requirePositive("spacingBlocks", spacingBlocks);
    requirePositive("waitRadiusBlocks", waitRadiusBlocks);
  }

  private static void requirePositive(String field, double value) {
    if (!(value > 0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(
          "SurroundRing." + field + " must be a positive number, got " + value);
    }
  }
}
