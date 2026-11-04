package io.github.nicodoou.mobai.domain.geometry;

/** How a flanker in reach hits, given where it stands relative to the player's shield. */
public enum FlankStrike {
  FROM_THE_SIDE,
  HEAD_ON,
  HOLD_FIRE;

  // A blocked hit knocks the attacker back, off its seat, so a flanker waits for the shield to
  // come down; with the shield down, the player's front is as open as its back.
  public static FlankStrike of(boolean isInShieldArc, boolean isBlocking) {
    if (!isInShieldArc) {
      return FROM_THE_SIDE;
    }
    return isBlocking ? HOLD_FIRE : HEAD_ON;
  }
}
