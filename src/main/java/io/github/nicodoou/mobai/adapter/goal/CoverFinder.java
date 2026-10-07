package io.github.nicodoou.mobai.adapter.goal;

import com.destroystokyo.paper.entity.Pathfinder.PathResult;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Finds a spot the danger cannot see and the mob can reach, among the cover candidates. */
final class CoverFinder {
  // Pathfinding is the costly part: a few paths per search keep many retreating mobs cheap.
  static final int MAX_PATHS_PER_SEARCH = 3;

  Optional<PathResult> find(Mob mob, Player danger, List<Vec3> candidates) {
    int paths = 0;
    for (Vec3 candidate : candidates) {
      if (paths == MAX_PATHS_PER_SEARCH) {
        return Optional.empty();
      }
      if (isSeenBy(danger, mob, candidate)) {
        continue;
      }
      paths++;
      Optional<PathResult> path = hiddenPath(mob, danger, candidate);
      if (path.isPresent()) {
        return path;
      }
    }
    return Optional.empty();
  }

  // The candidate can be inside a hill while the path ends on top of it, in plain sight.
  private static Optional<PathResult> hiddenPath(Mob mob, Player danger, Vec3 candidate) {
    PathResult path = mob.getPathfinder().findPath(locationIn(mob, candidate));
    if (path == null || !path.canReachFinalPoint() || path.getFinalPoint() == null) {
      return Optional.empty();
    }
    if (isSeenBy(danger, mob, PoseReader.positionOf(path.getFinalPoint()))) {
      return Optional.empty();
    }
    return Optional.of(path);
  }

  // The player would spot the mob's head there, not its feet.
  static boolean isSeenBy(Player danger, Mob mob, Vec3 spot) {
    return danger.hasLineOfSight(
        new Location(mob.getWorld(), spot.x(), spot.y() + mob.getEyeHeight(), spot.z()));
  }

  private static Location locationIn(Mob mob, Vec3 spot) {
    return new Location(mob.getWorld(), spot.x(), spot.y(), spot.z());
  }
}
