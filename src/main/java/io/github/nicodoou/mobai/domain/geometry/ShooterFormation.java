package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Spreads the shooters of one target evenly around it, as far apart as their number allows. */
public final class ShooterFormation {
  private static final double FULL_TURN_DEGREES = 360.0;

  public Vec3 pointFor(ShooterQuery query) {
    List<MobId> ring = aroundTheTarget(query);
    double anchorBearing = bearingOf(query.center(), query.shooters().get(ring.get(0)));
    double bearing = anchorBearing + ring.indexOf(query.self()) * FULL_TURN_DEGREES / ring.size();
    return query.center().plus(directionOf(bearing).times(query.radiusBlocks()));
  }

  // The lowest id anchors the ring where it stands; the rest follow round in the order they
  // already are, so nobody crosses in front of another.
  private static List<MobId> aroundTheTarget(ShooterQuery query) {
    MobId anchor =
        query.shooters().keySet().stream().min(Comparator.comparing(MobId::value)).orElseThrow();
    double anchorBearing = bearingOf(query.center(), query.shooters().get(anchor));
    return query.shooters().entrySet().stream()
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<MobId, Vec3> shooter) ->
                        turnFrom(anchorBearing, bearingOf(query.center(), shooter.getValue())))
                .thenComparing(shooter -> shooter.getKey().value()))
        .map(Map.Entry::getKey)
        .toList();
  }

  // 0° faces +Z and 90° faces +X; a shooter right on the target counts as 0°.
  private static double bearingOf(Vec3 center, Vec3 position) {
    Vec3 offset = position.minus(center).horizontal();
    if (offset.length() == 0) {
      return 0;
    }
    return Math.toDegrees(Math.atan2(offset.x(), offset.z()));
  }

  private static double turnFrom(double fromBearing, double toBearing) {
    double turn = (toBearing - fromBearing) % FULL_TURN_DEGREES;
    return turn < 0 ? turn + FULL_TURN_DEGREES : turn;
  }

  private static Vec3 directionOf(double bearingDegrees) {
    double radians = Math.toRadians(bearingDegrees);
    return new Vec3(Math.sin(radians), 0, Math.cos(radians));
  }
}
