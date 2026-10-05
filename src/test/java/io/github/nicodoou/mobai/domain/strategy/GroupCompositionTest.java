package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import org.junit.jupiter.api.Test;

class GroupCompositionTest {

  @Test
  void countsEachKindAndMelee() {
    var snapshot =
        new GroupSnapshotBuilder().withZombies(4).withSkeletons(3).withSpiders(2).build();

    var composition = GroupComposition.of(snapshot);

    assertThat(composition.zombies()).isEqualTo(4);
    assertThat(composition.skeletons()).isEqualTo(3);
    assertThat(composition.spiders()).isEqualTo(2);
    assertThat(composition.melee()).isEqualTo(6);
  }

  @Test
  void rejectsNegativeCounts() {
    assertThatThrownBy(() -> new GroupComposition(-1, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GroupComposition counts must be zero or positive, got -1/0/0");
  }
}
