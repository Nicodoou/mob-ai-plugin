package io.github.nicodoou.mobai.adapter.goal;

/**
 * What a retreating mob knows at one look around (CT-15).
 *
 * @param hidden the danger cannot see the mob
 * @param farEnough the mob is at the retreat distance or beyond, horizontally
 * @param coverStillHidden the mob is heading for a cover spot the danger still cannot see
 * @param searchDue the cover search interval has passed since the last search
 */
public record RetreatSituation(
    boolean hidden, boolean farEnough, boolean coverStillHidden, boolean searchDue) {

  public RetreatMove nextMove() {
    if (hidden && farEnough) {
      return RetreatMove.HOLD;
    }
    if (coverStillHidden) {
      return RetreatMove.KEEP_COVER;
    }
    if (searchDue) {
      return RetreatMove.SEARCH_COVER;
    }
    return RetreatMove.RETREAT_STRAIGHT;
  }
}
