package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GroupSnapshotBuilder {
  private static final double ZOMBIE_MAX_HEALTH = 20.0;
  private static final double SKELETON_MAX_HEALTH = 20.0;
  private static final double SPIDER_MAX_HEALTH = 16.0;
  private static final long GENERATED_MOB_ID_HIGH_BITS = 1;
  private static final double GENERATED_MOB_Y = 64;
  private static final double GENERATED_MOB_Z = 5;

  private GroupId groupId = new GroupId(new UUID(0, 3));
  private long tick = 1000;
  private final List<MobSnapshot> mobs = new ArrayList<>();
  private final List<PlayerSnapshot> players = new ArrayList<>();
  private int generatedMobs = 0;

  public GroupSnapshotBuilder withGroupId(GroupId groupId) {
    this.groupId = groupId;
    return this;
  }

  public GroupSnapshotBuilder withTick(long tick) {
    this.tick = tick;
    return this;
  }

  public GroupSnapshotBuilder withMob(MobSnapshot mob) {
    mobs.add(mob);
    return this;
  }

  public GroupSnapshotBuilder withPlayer(PlayerSnapshot player) {
    players.add(player);
    return this;
  }

  public GroupSnapshotBuilder withZombies(int count) {
    for (int index = 0; index < count; index++) {
      addGeneratedMob(MobKind.ZOMBIE, ZOMBIE_MAX_HEALTH);
    }
    return this;
  }

  public GroupSnapshotBuilder withSkeletons(int count) {
    for (int index = 0; index < count; index++) {
      addGeneratedMob(MobKind.SKELETON, SKELETON_MAX_HEALTH);
    }
    return this;
  }

  public GroupSnapshotBuilder withSpiders(int count) {
    for (int index = 0; index < count; index++) {
      addGeneratedMob(MobKind.SPIDER, SPIDER_MAX_HEALTH);
    }
    return this;
  }

  public GroupSnapshot build() {
    return new GroupSnapshot(groupId, tick, mobs, players);
  }

  private void addGeneratedMob(MobKind kind, double maxHealth) {
    generatedMobs++;
    mobs.add(
        new MobSnapshot(
            new MobId(new UUID(GENERATED_MOB_ID_HIGH_BITS, generatedMobs)),
            kind,
            new Vec3(generatedMobs, GENERATED_MOB_Y, GENERATED_MOB_Z),
            maxHealth,
            maxHealth));
  }
}
