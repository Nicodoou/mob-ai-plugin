package io.github.nicodoou.mobai.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DecisionCadenceTest {
  private static final int INTERVAL = 10;
  private static final int FIRST_TICK = 1000;
  private static final int GROUP_COUNT = 20;
  private static final int EXPECTED_DISTINCT_TICKS = 10;

  @Test
  void eachGroupIsDueExactlyOncePerWindow() {
    List<GroupId> groups = groups();

    for (GroupId group : groups) {
      long dueTicks = dueTicksIn(group).size();

      assertThat(dueTicks).isEqualTo(1);
    }
  }

  @Test
  void aGroupIsDueAtTheSamePlaceInEveryWindow() {
    for (GroupId group : groups()) {
      long due = dueTicksIn(group).getFirst();

      assertThat(DecisionCadence.isDue(group, due + 10, INTERVAL)).isTrue();
      assertThat(DecisionCadence.isDue(group, due + 20, INTERVAL)).isTrue();
      assertThat(DecisionCadence.isDue(group, due + 1, INTERVAL)).isFalse();
    }
  }

  @Test
  void groupsAreSpreadOverTheWindow() {
    Set<Long> distinctTicks = new TreeSet<>();
    for (GroupId group : groups()) {
      distinctTicks.addAll(dueTicksIn(group));
    }

    assertThat(distinctTicks).hasSize(EXPECTED_DISTINCT_TICKS);
  }

  @Test
  void windowStartsAtMultiplesOfTheInterval() {
    assertThat(DecisionCadence.isWindowStart(1000, 10)).isTrue();
    assertThat(DecisionCadence.isWindowStart(1005, 10)).isFalse();
  }

  private static List<Long> dueTicksIn(GroupId group) {
    List<Long> due = new ArrayList<>();
    for (long tick = FIRST_TICK; tick < FIRST_TICK + INTERVAL; tick++) {
      if (DecisionCadence.isDue(group, tick, INTERVAL)) {
        due.add(tick);
      }
    }
    return due;
  }

  private static List<GroupId> groups() {
    List<GroupId> groups = new ArrayList<>();
    for (long n = 1; n <= GROUP_COUNT; n++) {
      groups.add(new GroupId(new UUID(0, n)));
    }
    return groups;
  }
}
