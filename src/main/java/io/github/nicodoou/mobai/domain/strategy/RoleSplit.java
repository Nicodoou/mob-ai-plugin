package io.github.nicodoou.mobai.domain.strategy;

/** How many mobs of one kind take each role in a recipe. */
public record RoleSplit(int press, int flank, int reserve) {
  public RoleSplit {
    if (press < 0 || flank < 0 || reserve < 0) {
      throw new IllegalArgumentException(
          "RoleSplit counts must be zero or positive, got " + press + "/" + flank + "/" + reserve);
    }
  }

  public int total() {
    return press + flank + reserve;
  }
}
