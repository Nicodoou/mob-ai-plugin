package io.github.nicodoou.mobai.adapter.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import org.junit.jupiter.api.Test;

class TestGroupTest {
  private static final int MAX_GROUP_SIZE = 12;

  @Test
  void testGroupIsFourZombiesThreeSkeletonsAndTwoSpiders() {
    assertThat(TestGroup.kinds())
        .containsExactly(
            MobKind.ZOMBIE,
            MobKind.ZOMBIE,
            MobKind.ZOMBIE,
            MobKind.ZOMBIE,
            MobKind.SKELETON,
            MobKind.SKELETON,
            MobKind.SKELETON,
            MobKind.SPIDER,
            MobKind.SPIDER);
  }

  @Test
  void aSmallGroupGetsTheWholeTestGroup() {
    assertThat(TestGroup.reinforcementSize(2, MAX_GROUP_SIZE)).isEqualTo(9);
  }

  @Test
  void reinforcementIsCutAtTheFreeSlots() {
    assertThat(TestGroup.reinforcementSize(4, MAX_GROUP_SIZE)).isEqualTo(8);
  }

  @Test
  void aFullGroupGetsNothing() {
    assertThat(TestGroup.reinforcementSize(12, MAX_GROUP_SIZE)).isZero();
  }

  @Test
  void aGroupOverTheMaximumGetsNothing() {
    assertThat(TestGroup.reinforcementSize(15, MAX_GROUP_SIZE)).isZero();
  }

  @Test
  void negativeMembersAreRejected() {
    assertThatThrownBy(() -> TestGroup.reinforcementSize(-1, MAX_GROUP_SIZE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("TestGroup.members must be zero or positive, got -1");
  }
}
