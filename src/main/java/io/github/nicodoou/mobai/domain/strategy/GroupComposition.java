package io.github.nicodoou.mobai.domain.strategy;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;

public record GroupComposition(int zombies, int skeletons, int spiders) {
  public GroupComposition {
    if (zombies < 0 || skeletons < 0 || spiders < 0) {
      throw new IllegalArgumentException(
          "GroupComposition counts must be zero or positive, got "
              + zombies
              + "/"
              + skeletons
              + "/"
              + spiders);
    }
  }

  public static GroupComposition of(GroupSnapshot snapshot) {
    return new GroupComposition(
        count(snapshot, MobKind.ZOMBIE),
        count(snapshot, MobKind.SKELETON),
        count(snapshot, MobKind.SPIDER));
  }

  public int melee() {
    return zombies + spiders;
  }

  private static int count(GroupSnapshot snapshot, MobKind kind) {
    return (int) snapshot.mobs().stream().filter(mob -> mob.kind() == kind).count();
  }
}
