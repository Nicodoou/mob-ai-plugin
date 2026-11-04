package io.github.nicodoou.mobai.adapter.recording;

import com.destroystokyo.paper.entity.Pathfinder;
import io.github.nicodoou.mobai.adapter.debug.CombatSample;
import io.github.nicodoou.mobai.adapter.debug.PackShape;
import io.github.nicodoou.mobai.adapter.debug.RecordingSelection;
import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupStrength;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.PlanLifecycle;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.strategy.RecipePlay;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.Vector;

/** Writes what every recorded mob, group and player is doing, every few ticks (CT-36). */
public final class CombatSampler {
  private final SamplerParts parts;

  public CombatSampler(SamplerParts parts) {
    this.parts = Objects.requireNonNull(parts, "CombatSampler.parts");
  }

  public void tick(long now) {
    int interval = parts.settings().get().recordingIntervalTicks();
    if (!RecordingSelection.isSampleTick(now, interval)) {
      return;
    }
    RecordingSelection selection = RecordingSelection.of(parts.levels(), activeGroupIds());
    if (!selection.isActive()) {
      return;
    }
    Set<PlayerId> targets = recordGroups(selection, now);
    recordPlayers(targets, now);
  }

  private List<GroupId> activeGroupIds() {
    return parts.activeGroups().groups().stream().map(Group::id).toList();
  }

  private Set<PlayerId> recordGroups(RecordingSelection selection, long now) {
    Set<PlayerId> targets = new HashSet<>();
    for (GroupId id : selection.groups()) {
      parts
          .activeGroups()
          .group(id)
          .ifPresent(group -> targets.addAll(targetsOf(recordGroup(group, now))));
    }
    return targets;
  }

  private List<CombatSample.MobSample> recordGroup(Group group, long now) {
    List<CombatSample.MobSample> mobs = mobSamples(group, now);
    if (mobs.isEmpty()) {
      return mobs;
    }
    parts.writer().write(groupSample(group, mobs, now));
    mobs.forEach(parts.writer()::write);
    return mobs;
  }

  private static Set<PlayerId> targetsOf(List<CombatSample.MobSample> mobs) {
    Set<PlayerId> targets = new HashSet<>();
    for (CombatSample.MobSample mob : mobs) {
      mob.order().flatMap(order -> order.target()).ifPresent(targets::add);
    }
    return targets;
  }

  private List<CombatSample.MobSample> mobSamples(Group group, long now) {
    List<CombatSample.MobSample> samples = new ArrayList<>();
    for (Member member : group.roster().members()) {
      if (Bukkit.getEntity(member.id().value()) instanceof Mob mob
          && mob.isValid()
          && !mob.isDead()) {
        samples.add(mobSample(new LoadedMob(group.id(), member, mob), now));
      }
    }
    return samples;
  }

  private CombatSample.MobSample mobSample(LoadedMob loaded, long now) {
    Mob mob = loaded.mob();
    double maxHealth = parts.translator().maxHealth(mob);
    return new CombatSample.MobSample(
        now,
        loaded.group(),
        loaded.member().id(),
        loaded.member().kind(),
        PoseReader.positionOf(mob.getLocation()),
        vec3Of(mob.getVelocity()),
        clampHealth(mob.getHealth(), maxHealth),
        maxHealth,
        mob.isOnGround(),
        mob.getFireTicks(),
        parts.roles().assignmentOf(loaded.member().id()),
        runningGoalsOf(mob),
        pathEndOf(mob));
  }

  private static List<String> runningGoalsOf(Mob mob) {
    return Bukkit.getMobGoals().getRunningGoals(mob).stream()
        .map(goal -> goal.getKey().getNamespacedKey().getKey())
        .sorted()
        .toList();
  }

  private static Optional<Vec3> pathEndOf(Mob mob) {
    Pathfinder.PathResult path = mob.getPathfinder().getCurrentPath();
    if (path == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(path.getFinalPoint()).map(PoseReader::positionOf);
  }

  private CombatSample.GroupSample groupSample(
      Group group, List<CombatSample.MobSample> mobs, long now) {
    PlanLifecycle lifecycle = group.lifecycle();
    Optional<Plan> plan = lifecycle.plan();
    return new CombatSample.GroupSample(
        now,
        group.id(),
        lifecycle.state(),
        plan.map(Plan::id),
        targetOf(lifecycle),
        mobs.size(),
        PackShape.of(mobs),
        plan.flatMap(Plan::recipe).map(RecipePlay::recipe),
        plan.map(GroupStrength::ofPlan));
  }

  private static Optional<PlayerId> targetOf(PlanLifecycle lifecycle) {
    return lifecycle.plan().map(Plan::target).or(lifecycle::committedTarget);
  }

  private void recordPlayers(Set<PlayerId> targets, long now) {
    Collection<? extends Player> online = Bukkit.getOnlinePlayers();
    for (Player player : online) {
      PlayerId id = new PlayerId(player.getUniqueId());
      if (targets.contains(id) || parts.recipeBase().isTraining(id)) {
        parts.writer().write(playerSample(player, now));
      }
    }
  }

  private CombatSample.PlayerSample playerSample(Player player, long now) {
    PlayerId id = new PlayerId(player.getUniqueId());
    Location location = player.getLocation();
    PlayerInventory inventory = player.getInventory();
    double maxHealth = parts.translator().maxHealth(player);
    return new CombatSample.PlayerSample(
        now,
        id,
        player.getName(),
        PoseReader.positionOf(location),
        parts.movement().movementPerTick(id),
        location.getYaw(),
        location.getPitch(),
        clampHealth(player.getHealth(), maxHealth),
        player.getAbsorptionAmount(),
        maxHealth,
        parts.translator().armorPoints(player),
        isOnGround(player),
        player.isSprinting(),
        player.isBlocking(),
        parts.translator().itemName(inventory.getItemInMainHand()),
        parts.translator().itemName(inventory.getItemInOffHand()),
        itemInUseOf(player),
        player.getActiveItemUsedTime(),
        parts.recipeBase().isTraining(id));
  }

  private Optional<String> itemInUseOf(Player player) {
    if (!player.hasActiveItem()) {
      return Optional.empty();
    }
    return Optional.of(parts.translator().itemName(player.getActiveItem()));
  }

  // Paper deprecated it because the client reports it; a player has no server-side alternative.
  @SuppressWarnings("deprecation")
  private static boolean isOnGround(Player player) {
    return player.isOnGround();
  }

  // The server can report health above the maximum for a tick.
  private static double clampHealth(double health, double maxHealth) {
    return Math.clamp(health, 0, maxHealth);
  }

  private static Vec3 vec3Of(Vector vector) {
    return new Vec3(vector.getX(), vector.getY(), vector.getZ());
  }

  private record LoadedMob(GroupId group, Member member, Mob mob) {}
}
