package io.github.nicodoou.mobai.domain.geometry;

/**
 * The shape of the encirclement: where the strike seats are, how far apart, where the rest wait,
 * from what distance a fighter walks round the target instead of straight at it, and how close an
 * ally counts as being in the way.
 */
public record EncircleRing(
    double strikeRadiusBlocks,
    double seatSpacingBlocks,
    double waitRadiusBlocks,
    double engagedDistanceBlocks,
    double crowdingBlocks) {
  public EncircleRing {
    requirePositive("strikeRadiusBlocks", strikeRadiusBlocks);
    requirePositive("seatSpacingBlocks", seatSpacingBlocks);
    requirePositive("waitRadiusBlocks", waitRadiusBlocks);
    requirePositive("engagedDistanceBlocks", engagedDistanceBlocks);
    requirePositive("crowdingBlocks", crowdingBlocks);
    if (seatSpacingBlocks > 2 * strikeRadiusBlocks) {
      throw new IllegalArgumentException(
          "EncircleRing.seatSpacingBlocks must fit across the ring, got "
              + seatSpacingBlocks
              + " on a radius of "
              + strikeRadiusBlocks);
    }
  }

  private static void requirePositive(String field, double value) {
    if (!(value > 0) || !Double.isFinite(value)) {
      throw new IllegalArgumentException(
          "EncircleRing." + field + " must be a positive number, got " + value);
    }
  }
}
