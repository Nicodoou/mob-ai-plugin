package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Spreads the zombies pressing one target over an arc in front of their pack, far enough apart that
 * a sword's sweep reaches only one; the ones that do not fit wait out of reach for their turn.
 */
public final class SurroundFormation {
  private static final double HALF_TURN_DEGREES = 180.0;
  private static final double FULL_TURN_DEGREES = 360.0;
  private static final int SINGLE_SLOT = 1;

  public double sweepSafeSpacingBlocks(double mobWidthBlocks, double marginBlocks) {
    if (!(mobWidthBlocks > 0) || !Double.isFinite(mobWidthBlocks)) {
      throw new IllegalArgumentException(
          "SurroundFormation.mobWidthBlocks must be a positive number, got " + mobWidthBlocks);
    }
    if (!(marginBlocks >= 0) || !Double.isFinite(marginBlocks)) {
      throw new IllegalArgumentException(
          "SurroundFormation.marginBlocks must be zero or positive, got " + marginBlocks);
    }
    // Two boxes touch when their centers are closer than w + sweep reach on both axes at once, and
    // on the diagonal that is √2 × (w + sweep reach).
    return Math.sqrt(2) * (mobWidthBlocks + MinecraftConstants.SWEEP_REACH_BLOCKS) + marginBlocks;
  }

  public int strikeSlots(SurroundRing ring) {
    if (ring.spacingBlocks() > 2 * ring.strikeRadiusBlocks()) {
      return SINGLE_SLOT;
    }
    return (int) Math.floor(HALF_TURN_DEGREES / slotAngleDegrees(ring)) + 1;
  }

  public SurroundSpot spotFor(SurroundQuery query) {
    List<MobId> strikers = strikers(query);
    if (!strikers.contains(query.self())) {
      return new SurroundSpot(waitSpot(query), false);
    }
    return new SurroundSpot(strikeSpot(query, strikers), true);
  }

  private double slotAngleDegrees(SurroundRing ring) {
    return Math.toDegrees(2 * Math.asin(ring.spacingBlocks() / (2 * ring.strikeRadiusBlocks())));
  }

  private List<MobId> strikers(SurroundQuery query) {
    return query.pressers().entrySet().stream()
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<MobId, Vec3> presser) ->
                        distanceFrom(query.center(), presser.getValue()))
                .thenComparing(presser -> presser.getKey().value()))
        .limit(strikeSlots(query.ring()))
        .map(Map.Entry::getKey)
        .toList();
  }

  private Vec3 strikeSpot(SurroundQuery query, List<MobId> strikers) {
    double centerBearing = arcCenterBearing(query);
    List<MobId> leftToRight = leftToRight(query, strikers, centerBearing);
    double offsetSlots = leftToRight.indexOf(query.self()) - (leftToRight.size() - 1) / 2.0;
    double bearing = centerBearing + offsetSlots * slotAngleDegrees(query.ring());
    return pointAt(query, bearing, query.ring().strikeRadiusBlocks());
  }

  private List<MobId> leftToRight(SurroundQuery query, List<MobId> strikers, double centerBearing) {
    return strikers.stream()
        .sorted(
            Comparator.comparingDouble(
                    (MobId id) ->
                        signedTurn(
                            centerBearing, bearingOf(query.center(), query.pressers().get(id))))
                .thenComparing(MobId::value))
        .toList();
  }

  private Vec3 waitSpot(SurroundQuery query) {
    double bearing = bearingOf(query.center(), query.pressers().get(query.self()));
    return pointAt(query, bearing, query.ring().waitRadiusBlocks());
  }

  private double arcCenterBearing(SurroundQuery query) {
    Vec3 sum = Vec3.ZERO;
    for (Vec3 position : query.pressers().values()) {
      sum = sum.plus(position);
    }
    return bearingOf(query.center(), sum.times(1.0 / query.pressers().size()));
  }

  private Vec3 pointAt(SurroundQuery query, double bearing, double radiusBlocks) {
    Vec3 point = query.center().plus(directionOf(bearing).times(radiusBlocks));
    return new Vec3(point.x(), query.center().y(), point.z());
  }

  private static double distanceFrom(Vec3 center, Vec3 position) {
    return position.minus(center).horizontal().length();
  }

  // 0° faces +Z and 90° faces +X; a mob right on the target counts as 0°.
  private static double bearingOf(Vec3 center, Vec3 position) {
    Vec3 offset = position.minus(center).horizontal();
    if (offset.length() == 0) {
      return 0;
    }
    return Math.toDegrees(Math.atan2(offset.x(), offset.z()));
  }

  private static double signedTurn(double fromBearing, double toBearing) {
    double turn = (toBearing - fromBearing + HALF_TURN_DEGREES) % FULL_TURN_DEGREES;
    if (turn < 0) {
      turn += FULL_TURN_DEGREES;
    }
    return turn - HALF_TURN_DEGREES;
  }

  private static Vec3 directionOf(double bearingDegrees) {
    double radians = Math.toRadians(bearingDegrees);
    return new Vec3(Math.sin(radians), 0, Math.cos(radians));
  }
}
