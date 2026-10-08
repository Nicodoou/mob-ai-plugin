package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.goal.GoalInstaller;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.application.RecruitMob;
import io.github.nicodoou.mobai.application.RecruitRequest;
import io.github.nicodoou.mobai.application.RecruitResult;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Spawns the test group around a player, as a new group or as reinforcements. */
public final class GroupSpawner {
  private final RecruitMob recruitMob;
  private final GoalInstaller installer;
  private final VersionTranslator translator;

  public GroupSpawner(
      RecruitMob recruitMob, GoalInstaller installer, VersionTranslator translator) {
    this.recruitMob = Objects.requireNonNull(recruitMob, "GroupSpawner.recruitMob");
    this.installer = Objects.requireNonNull(installer, "GroupSpawner.installer");
    this.translator = Objects.requireNonNull(translator, "GroupSpawner.translator");
  }

  public GroupId spawnTestGroup(Player player, SelectionPolicyType policy) {
    Location origin = player.getLocation();
    World world = origin.getWorld();
    List<MobKind> kinds = TestGroup.kinds();
    List<Vec3> positions = SpawnRing.positions(positionOf(player), kinds.size());
    GroupId groupId = found(spawn(world, kinds.get(0), positions.get(0)), kinds.get(0), policy);
    for (int index = 1; index < kinds.size(); index++) {
      Mob mob = spawn(world, kinds.get(index), positions.get(index));
      join(mob, kinds.get(index), groupId);
    }
    return groupId;
  }

  public int reinforce(Player player, GroupId groupId, int count) {
    if (count < 1) {
      throw new IllegalArgumentException("GroupSpawner.count must be at least 1, got " + count);
    }
    World world = player.getLocation().getWorld();
    List<MobKind> kinds = TestGroup.kinds().subList(0, count);
    List<Vec3> positions = SpawnRing.positions(positionOf(player), count);
    for (int index = 0; index < count; index++) {
      Mob mob = spawn(world, kinds.get(index), positions.get(index));
      reinforceWith(mob, kinds.get(index), groupId);
    }
    return count;
  }

  private static Vec3 positionOf(Player player) {
    Location location = player.getLocation();
    return new Vec3(location.getX(), location.getY(), location.getZ());
  }

  // Without these, a member that vanishes while the server is off stays in its saved group forever.
  private Mob spawn(World world, MobKind kind, Vec3 position) {
    Location location = new Location(world, position.x(), position.y(), position.z());
    Mob mob = (Mob) world.spawnEntity(location, translator.entityTypeOf(kind));
    mob.getEquipment().clear();
    if (kind == MobKind.SKELETON) {
      translator.armWithBow(mob);
    }
    mob.setCanPickupItems(false);
    mob.setRemoveWhenFarAway(false);
    return mob;
  }

  private GroupId found(Mob mob, MobKind kind, SelectionPolicyType policy) {
    RecruitRequest request = RecruitRequest.loose(new MobId(mob.getUniqueId()), kind);
    GroupId groupId = groupIdOf(recruitMob.foundWithPolicy(request, policy));
    installer.install(mob);
    return groupId;
  }

  private void join(Mob mob, MobKind kind, GroupId groupId) {
    RecruitRequest request = RecruitRequest.near(new MobId(mob.getUniqueId()), kind, groupId);
    groupIdOf(recruitMob.execute(request));
    installer.install(mob);
  }

  private void reinforceWith(Mob mob, MobKind kind, GroupId groupId) {
    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(new MobId(mob.getUniqueId()), kind, groupId));
    if (!(result instanceof RecruitResult.Joined)) {
      throw new IllegalStateException(
          "Reinforcement did not join group " + groupId.shortId() + ": " + result);
    }
    installer.install(mob);
  }

  private static GroupId groupIdOf(RecruitResult result) {
    return switch (result) {
      case RecruitResult.Founded founded -> founded.groupId();
      case RecruitResult.Joined joined -> joined.groupId();
      case RecruitResult.Rejected rejected ->
          throw new IllegalStateException("Test group recruit was rejected: " + rejected);
    };
  }
}
