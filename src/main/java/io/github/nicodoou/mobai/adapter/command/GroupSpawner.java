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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Spawns the test group around a player and enrolls it as one group. */
public final class GroupSpawner {
  // The test group of the MVP catalog: 4 zombies, 3 skeletons and 2 spiders.
  private static final int TEST_ZOMBIES = 4;
  private static final int TEST_SKELETONS = 3;
  private static final int TEST_SPIDERS = 2;

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
    List<MobKind> kinds = testGroupKinds();
    List<Vec3> positions =
        SpawnRing.positions(new Vec3(origin.getX(), origin.getY(), origin.getZ()), kinds.size());
    GroupId groupId = found(spawn(world, kinds.get(0), positions.get(0)), kinds.get(0), policy);
    for (int index = 1; index < kinds.size(); index++) {
      Mob mob = spawn(world, kinds.get(index), positions.get(index));
      join(mob, kinds.get(index), groupId);
    }
    return groupId;
  }

  private static List<MobKind> testGroupKinds() {
    List<MobKind> kinds = new ArrayList<>();
    kinds.addAll(Collections.nCopies(TEST_ZOMBIES, MobKind.ZOMBIE));
    kinds.addAll(Collections.nCopies(TEST_SKELETONS, MobKind.SKELETON));
    kinds.addAll(Collections.nCopies(TEST_SPIDERS, MobKind.SPIDER));
    return kinds;
  }

  // Without these, a member that vanishes while the server is off stays in its saved group forever.
  private Mob spawn(World world, MobKind kind, Vec3 position) {
    Location location = new Location(world, position.x(), position.y(), position.z());
    Mob mob = (Mob) world.spawnEntity(location, translator.entityTypeOf(kind));
    mob.getEquipment().clear();
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

  private static GroupId groupIdOf(RecruitResult result) {
    return switch (result) {
      case RecruitResult.Founded founded -> founded.groupId();
      case RecruitResult.Joined joined -> joined.groupId();
      case RecruitResult.Rejected rejected ->
          throw new IllegalStateException("Test group recruit was rejected: " + rejected);
    };
  }
}
