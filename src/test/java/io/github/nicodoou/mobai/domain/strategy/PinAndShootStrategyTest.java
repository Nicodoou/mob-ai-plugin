package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PinAndShootStrategyTest {
  private final PinAndShootStrategy strategy = new PinAndShootStrategy();

  @Test
  void needsTwoZombiesAndTwoSkeletons() {
    var enough = new GroupSnapshotBuilder().withZombies(2).withSkeletons(2).build();
    var fewSkeletons = new GroupSnapshotBuilder().withZombies(2).withSkeletons(1).build();
    var fewZombies = new GroupSnapshotBuilder().withZombies(1).withSkeletons(3).build();
    var spidersDoNotCount =
        new GroupSnapshotBuilder().withZombies(1).withSpiders(2).withSkeletons(2).build();

    assertThat(strategy.isViable(enough)).isTrue();
    assertThat(strategy.isViable(fewSkeletons)).isFalse();
    assertThat(strategy.isViable(fewZombies)).isFalse();
    assertThat(strategy.isViable(spidersDoNotCount)).isFalse();
  }

  @Test
  void zombiesPinSkeletonsShootSpidersFlank() {
    var player = new PlayerSnapshotBuilder().build();
    var snapshot =
        new GroupSnapshotBuilder()
            .withPlayer(player)
            .withZombies(2)
            .withSkeletons(2)
            .withSpiders(1)
            .build();

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS),
            entry(mobId(2), Role.PRESS),
            entry(mobId(3), Role.SHOOT),
            entry(mobId(4), Role.SHOOT),
            entry(mobId(5), Role.FLANK));
  }

  @Test
  void describesItsRequirement() {
    assertThat(strategy.id().value()).isEqualTo("PIN_AND_SHOOT");
    assertThat(strategy.requirement()).isEqualTo("at least 2 zombies and 2 skeletons");
  }

  private static MobId mobId(int number) {
    return new MobId(new UUID(1, number));
  }
}
