package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.Pathfinder.PathResult;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Finds a higher spot near a shooter's place, with sight, a clear line of fire and a way up. */
final class HighGroundFinder {
  // Pathfinding is the costly part: a few paths per search keep the shooters cheap.
  static final int MAX_PATHS_PER_SEARCH = 3;
  // A path that ends this far below the spot did not climb it.
  private static final double CLIMB_TOLERANCE_BLOCKS = 1.0;

  private final Waypoints waypoints;

  HighGroundFinder(Waypoints waypoints) {
    this.waypoints = Objects.requireNonNull(waypoints, "HighGroundFinder.waypoints");
  }

  Optional<Vec3> find(Mob shooter, Player target, PerchRequest request) {
    Vec3 targetPosition = PoseReader.positionOf(target.getLocation());
    List<Vec3> ranked =
        waypoints.rankPerches(
            grounded(shooter, waypoints.perchCandidates(request.spot(), targetPosition)),
            groundYAt(shooter, request.spot()));
    Aim aim = new Aim(target, request.allies());
    int paths = 0;
    for (Vec3 perch : ranked) {
      if (paths == MAX_PATHS_PER_SEARCH) {
        return Optional.empty();
      }
      if (!canShootFrom(shooter, perch, aim)) {
        continue;
      }
      paths++;
      if (canClimb(shooter, perch)) {
        return Optional.of(perch);
      }
    }
    return Optional.empty();
  }

  private static List<Vec3> grounded(Mob shooter, List<Vec3> candidates) {
    return candidates.stream()
        .map(candidate -> new Vec3(candidate.x(), groundYAt(shooter, candidate), candidate.z()))
        .toList();
  }

  private static double groundYAt(Mob shooter, Vec3 spot) {
    // A mob stands on top of the highest block of the column.
    return shooter
            .getWorld()
            .getHighestBlockYAt((int) Math.floor(spot.x()), (int) Math.floor(spot.z()))
        + 1;
  }

  private boolean canShootFrom(Mob shooter, Vec3 perch, Aim aim) {
    Vec3 eyes = new Vec3(perch.x(), perch.y() + shooter.getEyeHeight(), perch.z());
    return aim.target()
            .hasLineOfSight(new Location(shooter.getWorld(), eyes.x(), eyes.y(), eyes.z()))
        && waypoints.isLineOfFireClear(eyes, PoseReader.bodyCenterOf(aim.target()), aim.allies());
  }

  private static boolean canClimb(Mob shooter, Vec3 perch) {
    PathResult path =
        shooter
            .getPathfinder()
            .findPath(new Location(shooter.getWorld(), perch.x(), perch.y(), perch.z()));
    return path != null
        && path.canReachFinalPoint()
        && path.getFinalPoint() != null
        && path.getFinalPoint().getY() >= perch.y() - CLIMB_TOLERANCE_BLOCKS;
  }

  private record Aim(Player target, List<Vec3> allies) {}
}
