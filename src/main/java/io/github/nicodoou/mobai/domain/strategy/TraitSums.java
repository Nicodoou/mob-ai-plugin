package io.github.nicodoou.mobai.domain.strategy;

/** The time-decayed sums behind one player's traits (CT-30). */
public record TraitSums(double shield, double ranged, double armor, double weight, long lastTick) {
  public TraitSums {
    if (!isZeroOrPositive(shield)
        || !isZeroOrPositive(ranged)
        || !isZeroOrPositive(armor)
        || !isZeroOrPositive(weight)
        || weight <= 0
        || lastTick < 0) {
      throw new IllegalArgumentException(
          "TraitSums values must be finite and zero or positive, got "
              + shield
              + "/"
              + ranged
              + "/"
              + armor
              + "/"
              + weight
              + "/"
              + lastTick);
    }
  }

  PlayerTraits traits() {
    return new PlayerTraits(shield / weight, ranged / weight, armor / weight);
  }

  private static boolean isZeroOrPositive(double value) {
    return Double.isFinite(value) && value >= 0;
  }
}
