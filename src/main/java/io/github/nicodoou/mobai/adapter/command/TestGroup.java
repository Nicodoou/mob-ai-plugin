package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The test group of the MVP catalog and how much of it fits into an existing group. */
public final class TestGroup {
  // The test group of the MVP catalog: 4 zombies, 3 skeletons and 2 spiders.
  private static final int ZOMBIES = 4;
  private static final int SKELETONS = 3;
  private static final int SPIDERS = 2;

  private TestGroup() {}

  public static List<MobKind> kinds() {
    List<MobKind> kinds = new ArrayList<>();
    kinds.addAll(Collections.nCopies(ZOMBIES, MobKind.ZOMBIE));
    kinds.addAll(Collections.nCopies(SKELETONS, MobKind.SKELETON));
    kinds.addAll(Collections.nCopies(SPIDERS, MobKind.SPIDER));
    return List.copyOf(kinds);
  }

  public static int reinforcementSize(int members, int maxGroupSize) {
    requireValid(members, maxGroupSize);
    return Math.max(0, Math.min(kinds().size(), maxGroupSize - members));
  }

  private static void requireValid(int members, int maxGroupSize) {
    if (members < 0) {
      throw new IllegalArgumentException(
          "TestGroup.members must be zero or positive, got " + members);
    }
    if (maxGroupSize < 1) {
      throw new IllegalArgumentException(
          "TestGroup.maxGroupSize must be at least 1, got " + maxGroupSize);
    }
  }
}
