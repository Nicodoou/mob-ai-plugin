package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** How many mobs of each kind fight in an arena. */
public record ArenaLineup(int zombies, int skeletons, int spiders) {
  private static final int COUNTS = 3;
  private static final int ZOMBIES_INDEX = 0;
  private static final int SKELETONS_INDEX = 1;
  private static final int SPIDERS_INDEX = 2;

  public static ArenaLineup standard() {
    List<MobKind> kinds = TestGroup.kinds();
    return new ArenaLineup(
        Collections.frequency(kinds, MobKind.ZOMBIE),
        Collections.frequency(kinds, MobKind.SKELETON),
        Collections.frequency(kinds, MobKind.SPIDER));
  }

  /** No counts mean the standard lineup; anything but three whole counts is not a lineup. */
  public static Optional<ArenaLineup> parse(List<String> counts) {
    if (counts.isEmpty()) {
      return Optional.of(standard());
    }
    if (counts.size() != COUNTS) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          new ArenaLineup(
              count(counts.get(ZOMBIES_INDEX)),
              count(counts.get(SKELETONS_INDEX)),
              count(counts.get(SPIDERS_INDEX))));
    } catch (NumberFormatException exception) {
      return Optional.empty();
    }
  }

  public int size() {
    return zombies + skeletons + spiders;
  }

  public List<MobKind> kinds() {
    List<MobKind> kinds = new ArrayList<>();
    kinds.addAll(Collections.nCopies(zombies, MobKind.ZOMBIE));
    kinds.addAll(Collections.nCopies(skeletons, MobKind.SKELETON));
    kinds.addAll(Collections.nCopies(spiders, MobKind.SPIDER));
    return List.copyOf(kinds);
  }

  private static int count(String text) {
    int count = Integer.parseInt(text);
    if (count < 0) {
      throw new NumberFormatException("a lineup count must not be negative, got " + text);
    }
    return count;
  }
}
