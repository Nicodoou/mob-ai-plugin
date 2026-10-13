package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/** Holds back the melee fighters that ran too far ahead of the rest of their pack (CT-32). */
public final class PackCohesion {
  private final Supplier<PackSettings> settings;

  public PackCohesion(Supplier<PackSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "PackCohesion.settings");
  }

  /**
   * The same orders, in the same order, with GATHER for every PRESS or FLANK fighter of the target
   * that is more than the allowed spread ahead of the slowest one and not yet fighting.
   */
  public List<RoleAssignment> closeRanks(
      List<RoleAssignment> orders, GroupSnapshot snapshot, PlayerId target) {
    Objects.requireNonNull(orders, "PackCohesion.orders");
    Objects.requireNonNull(snapshot, "PackCohesion.snapshot");
    Objects.requireNonNull(target, "PackCohesion.target");
    Optional<Vec3> targetPosition = snapshot.player(target).map(player -> player.pose().position());
    if (targetPosition.isEmpty()) {
      return List.copyOf(orders);
    }
    return closeRanksAround(orders, snapshot, new PackTarget(target, targetPosition.get()));
  }

  private List<RoleAssignment> closeRanksAround(
      List<RoleAssignment> orders, GroupSnapshot snapshot, PackTarget target) {
    PackSettings pack = settings.get();
    double rear = rearDistance(orders, snapshot, target);
    return orders.stream()
        .map(order -> closeRank(order, snapshot, new PackLine(target, rear, pack)))
        .toList();
  }

  private RoleAssignment closeRank(RoleAssignment order, GroupSnapshot snapshot, PackLine line) {
    if (!isInPack(order, line.target().id())) {
      return order;
    }
    double distance = distanceOf(order, snapshot, line.target().position());
    if (!shouldGather(distance, line.rearDistance(), line.settings())) {
      return order;
    }
    return gatherOrder(order);
  }

  private static boolean isInPack(RoleAssignment order, PlayerId target) {
    boolean isFighter = order.role() == Role.PRESS || order.role() == Role.FLANK;
    return isFighter && order.target().filter(target::equals).isPresent();
  }

  private static double distanceOf(
      RoleAssignment order, GroupSnapshot snapshot, Vec3 targetPosition) {
    MobSnapshot mob =
        snapshot
            .mob(order.mob())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "PackCohesion: order for mob " + order.mob().value() + " has no snapshot"));
    return mob.position().minus(targetPosition).horizontal().length();
  }

  private static double rearDistance(
      List<RoleAssignment> orders, GroupSnapshot snapshot, PackTarget target) {
    return orders.stream()
        .filter(order -> isInPack(order, target.id()))
        .mapToDouble(order -> distanceOf(order, snapshot, target.position()))
        .max()
        .orElse(0);
  }

  private static boolean shouldGather(double distance, double rear, PackSettings settings) {
    boolean isTooFarAhead = rear - distance > settings.maxSpreadBlocks();
    boolean isFighting = distance <= settings.engagedDistanceBlocks();
    return isTooFarAhead && !isFighting;
  }

  private static RoleAssignment gatherOrder(RoleAssignment order) {
    return new RoleAssignment(
        order.mob(), Role.GATHER, order.target(), Optional.empty(), false, Optional.empty());
  }

  private record PackTarget(PlayerId id, Vec3 position) {}

  private record PackLine(PackTarget target, double rearDistance, PackSettings settings) {}
}
