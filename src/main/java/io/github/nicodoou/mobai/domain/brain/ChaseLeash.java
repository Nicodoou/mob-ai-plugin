package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.strategy.ChaseAnchor;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Calls melee fighters back to their ground when the target ran further than the recipe chases
 * (CT-33).
 */
public final class ChaseLeash {
  private final Supplier<PackSettings> settings;

  public ChaseLeash(Supplier<PackSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "ChaseLeash.settings");
  }

  /**
   * The same orders, in the same order, with GATHER back to the ground for every PRESS or FLANK
   * fighter of the plan's target that is not yet fighting, once the target left the leash.
   */
  public List<RoleAssignment> reinIn(
      List<RoleAssignment> orders, GroupSnapshot snapshot, Plan plan) {
    Objects.requireNonNull(orders, "ChaseLeash.orders");
    Objects.requireNonNull(snapshot, "ChaseLeash.snapshot");
    Objects.requireNonNull(plan, "ChaseLeash.plan");
    Optional<Vec3> targetPosition =
        snapshot.player(plan.target()).map(player -> player.pose().position());
    if (plan.recipe().isEmpty() || targetPosition.isEmpty()) {
      return List.copyOf(orders);
    }
    RecipePlay play = plan.recipe().get();
    PackSettings pack = settings.get();
    if (!isTaut(play, targetPosition.get(), pack)) {
      return List.copyOf(orders);
    }
    Leash leash =
        new Leash(
            plan.target(),
            targetPosition.get(),
            play.chaseAnchor().ground(),
            pack.engagedDistanceBlocks());
    return orders.stream().map(order -> reinInOrder(order, snapshot, leash)).toList();
  }

  private static boolean isTaut(RecipePlay play, Vec3 targetPosition, PackSettings pack) {
    ChaseAnchor anchor = play.chaseAnchor();
    double distance = targetPosition.minus(anchor.ground()).horizontal().length();
    return distance > anchor.openingDistanceBlocks() + lengthOf(play, pack);
  }

  private static double lengthOf(RecipePlay play, PackSettings pack) {
    double range = pack.maxChaseBlocks() - pack.minChaseBlocks();
    return pack.minChaseBlocks() + play.recipe().chaseCommitment() * range;
  }

  private static RoleAssignment reinInOrder(
      RoleAssignment order, GroupSnapshot snapshot, Leash leash) {
    if (!PackCohesion.isInPack(order, leash.target())) {
      return order;
    }
    double distance = distanceOf(order, snapshot, leash.targetPosition());
    if (distance <= leash.engagedDistanceBlocks()) {
      return order;
    }
    return walkBackOrder(order, leash.ground());
  }

  private static double distanceOf(
      RoleAssignment order, GroupSnapshot snapshot, Vec3 targetPosition) {
    MobSnapshot mob =
        snapshot
            .mob(order.mob())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "ChaseLeash: order for mob " + order.mob().value() + " has no snapshot"));
    return mob.position().minus(targetPosition).horizontal().length();
  }

  private static RoleAssignment walkBackOrder(RoleAssignment order, Vec3 ground) {
    return new RoleAssignment(
        order.mob(),
        Role.GATHER,
        order.target(),
        Optional.empty(),
        false,
        Optional.of(ground),
        false);
  }

  private record Leash(
      PlayerId target, Vec3 targetPosition, Vec3 ground, double engagedDistanceBlocks) {}
}
