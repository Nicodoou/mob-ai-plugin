package io.github.nicodoou.mobai.domain.strategy;

/** How a player fights, each in [0, 1]: shield use, ranged weapon use and armor (CT-30). */
public record PlayerTraits(double shield, double ranged, double armor) {
  public PlayerTraits {
    if (!isUnit(shield) || !isUnit(ranged) || !isUnit(armor)) {
      throw new IllegalArgumentException(
          "PlayerTraits values must be in [0, 1], got " + shield + "/" + ranged + "/" + armor);
    }
  }

  private static boolean isUnit(double value) {
    return value >= 0 && value <= 1;
  }
}
