package io.github.nicodoou.mobai.domain.snapshot;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record GroupSnapshot(
    GroupId groupId, long tick, List<MobSnapshot> mobs, List<PlayerSnapshot> players) {

  public GroupSnapshot {
    Objects.requireNonNull(groupId, "GroupSnapshot.groupId");
    Objects.requireNonNull(mobs, "GroupSnapshot.mobs");
    Objects.requireNonNull(players, "GroupSnapshot.players");
    SnapshotChecks.requireAtLeast("GroupSnapshot.tick", tick, 0);
    mobs = List.copyOf(mobs);
    players = List.copyOf(players);
    requireDistinctMobIds(mobs);
    requireDistinctPlayerIds(players);
  }

  public Optional<MobSnapshot> mob(MobId id) {
    return mobs.stream().filter(mob -> mob.id().equals(id)).findFirst();
  }

  public Optional<PlayerSnapshot> player(PlayerId id) {
    return players.stream().filter(player -> player.id().equals(id)).findFirst();
  }

  private static void requireDistinctMobIds(List<MobSnapshot> mobs) {
    Set<MobId> seen = new HashSet<>();
    for (MobSnapshot mob : mobs) {
      if (!seen.add(mob.id())) {
        throw new IllegalArgumentException(
            "GroupSnapshot.mobs has a duplicate id " + mob.id().value());
      }
    }
  }

  private static void requireDistinctPlayerIds(List<PlayerSnapshot> players) {
    Set<PlayerId> seen = new HashSet<>();
    for (PlayerSnapshot player : players) {
      if (!seen.add(player.id())) {
        throw new IllegalArgumentException(
            "GroupSnapshot.players has a duplicate id " + player.id().value());
      }
    }
  }
}
