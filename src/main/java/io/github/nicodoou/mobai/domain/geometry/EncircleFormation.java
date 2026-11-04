package io.github.nicodoou.mobai.domain.geometry;

import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gives each melee fighter of a target its own seat around the player: the pressers fan out from
 * straight ahead, the flankers from straight behind, both on the same lattice, so the two arcs
 * never meet. Inside each arc the seats keep the order the fighters already have around the player,
 * so nobody crosses an ally of its own role. Fighters that find no seat wait outside the player's
 * reach, spread evenly around it.
 */
public final class EncircleFormation {
  private static final double FULL_TURN_DEGREES = 360.0;
  private static final double HALF_TURN_DEGREES = 180.0;
  // A spacing equal to the radius gives a hair over 60° in floating point and must not lose a seat.
  private static final double SEAT_ANGLE_SLACK_DEGREES = 1e-9;
  // At most this much further round per leg: on a ring a little under reach, the straight line
  // between the two ends still passes well clear of the player's body.
  private static final double WALK_ROUND_STEP_DEGREES = 45.0;
  // Paths run over whole blocks, so a fighter can stop this short of its seat and still be there.
  private static final double SEAT_ARRIVAL_BLOCKS = 1.0;
  // Half a slot, so waiting fighters sit either side of the seam behind the player, not on it.
  private static final double HALF_SLOT = 0.5;

  private final CombatGeometry geometry;

  public EncircleFormation(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "EncircleFormation.geometry");
  }

  public int strikeSeats(EncircleRing ring) {
    double seatAngle =
        Math.toDegrees(2 * Math.asin(ring.seatSpacingBlocks() / (2 * ring.strikeRadiusBlocks())));
    return (int) Math.floor(FULL_TURN_DEGREES / (seatAngle - SEAT_ANGLE_SLACK_DEGREES));
  }

  public EncircleStep stepFor(EncircleQuery query) {
    Seat seat = seatOf(query);
    return new EncircleStep(waypointTo(query, seat), moveFor(query, seat), seat.striking());
  }

  private Seat seatOf(EncircleQuery query) {
    Set<MobId> strikers = closestFighters(query);
    if (!strikers.contains(query.self())) {
      return waitingSeat(query, strikers);
    }
    StrikeArc arc = new StrikeArc(strikers, FULL_TURN_DEGREES / strikeSeats(query.ring()));
    double angle = query.isFlanker() ? flankerSeatAngle(query, arc) : presserSeatAngle(query, arc);
    return new Seat(angle, query.ring().strikeRadiusBlocks(), true);
  }

  private Set<MobId> closestFighters(EncircleQuery query) {
    return query.fighters().entrySet().stream()
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<MobId, Vec3> fighter) ->
                        horizontalDistance(fighter.getValue(), query.pose().position()))
                .thenComparing(fighter -> fighter.getKey().value()))
        .limit(strikeSeats(query.ring()))
        .map(Map.Entry::getKey)
        .collect(Collectors.toUnmodifiableSet());
  }

  private static double presserSeatAngle(EncircleQuery query, StrikeArc arc) {
    List<MobId> order =
        roundOrder(query, arc.ofRole(query.pressers().keySet()), query.pose().facing());
    return slotOffset(order, query.self()) * arc.latticeDegrees();
  }

  private static double flankerSeatAngle(EncircleQuery query, StrikeArc arc) {
    List<MobId> order =
        roundOrder(query, arc.ofRole(query.flankers().keySet()), query.pose().facing().times(-1));
    return HALF_TURN_DEGREES + slotOffset(order, query.self()) * arc.latticeDegrees();
  }

  private static double slotOffset(List<MobId> order, MobId self) {
    return order.indexOf(self) - (order.size() - 1) / 2.0;
  }

  private static Seat waitingSeat(EncircleQuery query, Set<MobId> strikers) {
    Set<MobId> waiting = new HashSet<>(query.fighters().keySet());
    waiting.removeAll(strikers);
    List<MobId> order = roundOrder(query, waiting, query.pose().facing());
    double slotDegrees = FULL_TURN_DEGREES / order.size();
    double angle = (order.indexOf(query.self()) + HALF_SLOT) * slotDegrees - HALF_TURN_DEGREES;
    return new Seat(angle, query.ring().waitRadiusBlocks(), false);
  }

  // Sorted by signed angle from the reference: the seam of the order is opposite the reference.
  private static List<MobId> roundOrder(EncircleQuery query, Set<MobId> fighters, Vec3 reference) {
    Map<MobId, Vec3> positions = query.fighters();
    return fighters.stream()
        .sorted(
            Comparator.comparingDouble(
                    (MobId id) ->
                        signedAngleDegrees(
                            reference, positions.get(id).minus(query.pose().position())))
                .thenComparing(MobId::value))
        .toList();
  }

  private static Vec3 waypointTo(EncircleQuery query, Seat seat) {
    Vec3 offset = query.selfPosition().minus(query.pose().position()).horizontal();
    double ownAngle = signedAngleDegrees(query.pose().facing(), offset);
    if (offset.length() > query.ring().engagedDistanceBlocks()) {
      return pointAt(query.pose(), ownAngle, seat.radiusBlocks());
    }
    double turn = wrappedTurn(ownAngle, seat.angleDegrees());
    if (Math.abs(turn) <= WALK_ROUND_STEP_DEGREES) {
      return seatPoint(query, seat);
    }
    return pointAt(
        query.pose(),
        ownAngle + Math.signum(turn) * WALK_ROUND_STEP_DEGREES,
        walkRoundRadius(offset.length(), seat));
  }

  // The foot of the perpendicular on the next direction: closing in while going round, never
  // inside the seat's ring.
  private static double walkRoundRadius(double distanceBlocks, Seat seat) {
    return Math.max(
        seat.radiusBlocks(), distanceBlocks * Math.cos(Math.toRadians(WALK_ROUND_STEP_DEGREES)));
  }

  private EncircleMove moveFor(EncircleQuery query, Seat seat) {
    Vec3 position = query.selfPosition();
    boolean isAtSeat = horizontalDistance(position, seatPoint(query, seat)) <= SEAT_ARRIVAL_BLOCKS;
    if (!seat.striking()) {
      return isAtSeat ? EncircleMove.HOLD : EncircleMove.WALK;
    }
    boolean isInReach =
        position.distanceTo(query.pose().position()) <= MinecraftConstants.MELEE_REACH_BLOCKS;
    if (isInReach && !isCrowded(query) && (isAtSeat || isPlaceRight(query))) {
      return EncircleMove.HOLD;
    }
    return isAtSeat && !isInReach ? EncircleMove.STEP_IN : EncircleMove.WALK;
  }

  // A fighter already hitting from a good place stays there: walking every time the player turns
  // would take a presser off the player's back and into its shield.
  private boolean isPlaceRight(EncircleQuery query) {
    return !query.isFlanker() || geometry.isOutOfSight(query.pose(), query.selfPosition());
  }

  private static boolean isCrowded(EncircleQuery query) {
    Vec3 position = query.selfPosition();
    return query.fighters().entrySet().stream()
        .filter(fighter -> !fighter.getKey().equals(query.self()))
        .anyMatch(
            fighter ->
                horizontalDistance(fighter.getValue(), position) < query.ring().crowdingBlocks());
  }

  private static Vec3 seatPoint(EncircleQuery query, Seat seat) {
    return pointAt(query.pose(), seat.angleDegrees(), seat.radiusBlocks());
  }

  private static Vec3 pointAt(PlayerPose pose, double angleDegrees, double radiusBlocks) {
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), angleDegrees);
    return pose.position().plus(direction.times(radiusBlocks));
  }

  // Positive towards CombatGeometry's positive side; a fighter right on the player counts as 0°.
  private static double signedAngleDegrees(Vec3 reference, Vec3 offset) {
    double determinant = reference.x() * offset.z() - reference.z() * offset.x();
    double dot = reference.x() * offset.x() + reference.z() * offset.z();
    return Math.toDegrees(Math.atan2(determinant, dot));
  }

  private static double wrappedTurn(double fromDegrees, double toDegrees) {
    double turn = (toDegrees - fromDegrees + HALF_TURN_DEGREES) % FULL_TURN_DEGREES;
    if (turn < 0) {
      turn += FULL_TURN_DEGREES;
    }
    return turn - HALF_TURN_DEGREES;
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return from.minus(to).horizontal().length();
  }

  private record Seat(double angleDegrees, double radiusBlocks, boolean striking) {}

  private record StrikeArc(Set<MobId> strikers, double latticeDegrees) {
    Set<MobId> ofRole(Set<MobId> role) {
      return role.stream().filter(strikers::contains).collect(Collectors.toUnmodifiableSet());
    }
  }
}
