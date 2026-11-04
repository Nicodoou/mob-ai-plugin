package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** One line of the combat recording (CT-36): what an entity was doing at a given tick. */
public sealed interface CombatSample {
  long tick();

  record MobSample(
      long tick,
      GroupId group,
      MobId mob,
      MobKind kind,
      Vec3 position,
      Vec3 velocityPerTick,
      double health,
      double maxHealth,
      boolean onGround,
      int fireTicks,
      Optional<RoleAssignment> order,
      List<String> runningGoals,
      Optional<Vec3> pathEnd)
      implements CombatSample {
    public MobSample {
      Objects.requireNonNull(group, "MobSample.group");
      Objects.requireNonNull(mob, "MobSample.mob");
      Objects.requireNonNull(kind, "MobSample.kind");
      Objects.requireNonNull(position, "MobSample.position");
      Objects.requireNonNull(velocityPerTick, "MobSample.velocityPerTick");
      Objects.requireNonNull(order, "MobSample.order");
      Objects.requireNonNull(runningGoals, "MobSample.runningGoals");
      Objects.requireNonNull(pathEnd, "MobSample.pathEnd");
      runningGoals = List.copyOf(runningGoals);
    }
  }

  record GroupSample(
      long tick,
      GroupId group,
      GroupState state,
      Optional<PlanId> plan,
      Optional<PlayerId> target,
      int loadedMembers,
      PackShape shape,
      Optional<PlanRecipe> recipe,
      Optional<Double> planStrength)
      implements CombatSample {
    public GroupSample {
      Objects.requireNonNull(group, "GroupSample.group");
      Objects.requireNonNull(state, "GroupSample.state");
      Objects.requireNonNull(plan, "GroupSample.plan");
      Objects.requireNonNull(target, "GroupSample.target");
      Objects.requireNonNull(shape, "GroupSample.shape");
      Objects.requireNonNull(recipe, "GroupSample.recipe");
      Objects.requireNonNull(planStrength, "GroupSample.planStrength");
    }
  }

  record PlayerSample(
      long tick,
      PlayerId player,
      String name,
      Vec3 position,
      Vec3 movementPerTick,
      float yaw,
      float pitch,
      double health,
      double absorption,
      double maxHealth,
      double armorPoints,
      boolean onGround,
      boolean sprinting,
      boolean blocking,
      String mainHand,
      String offHand,
      Optional<String> itemInUse,
      int itemInUseTicks,
      boolean training)
      implements CombatSample {
    public PlayerSample {
      Objects.requireNonNull(player, "PlayerSample.player");
      Objects.requireNonNull(name, "PlayerSample.name");
      Objects.requireNonNull(position, "PlayerSample.position");
      Objects.requireNonNull(movementPerTick, "PlayerSample.movementPerTick");
      Objects.requireNonNull(mainHand, "PlayerSample.mainHand");
      Objects.requireNonNull(offHand, "PlayerSample.offHand");
      Objects.requireNonNull(itemInUse, "PlayerSample.itemInUse");
    }
  }
}
