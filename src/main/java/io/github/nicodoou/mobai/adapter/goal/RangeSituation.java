package io.github.nicodoou.mobai.adapter.goal;

/**
 * What a shooter knows about its range at one look around.
 *
 * @param distanceBlocks horizontal distance to the target
 * @param targetInSight the shooter has a line of sight to the target
 */
public record RangeSituation(double distanceBlocks, boolean targetInSight) {
  public RangeMove nextMove(double minBlocks, double maxBlocks) {
    if (!targetInSight || distanceBlocks > maxBlocks) {
      return RangeMove.APPROACH;
    }
    if (distanceBlocks < minBlocks) {
      return RangeMove.BACK_OFF;
    }
    return RangeMove.HOLD;
  }
}
