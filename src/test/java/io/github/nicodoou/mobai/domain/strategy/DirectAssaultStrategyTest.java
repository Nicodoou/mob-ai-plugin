package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DirectAssaultStrategyTest {
  private final DirectAssaultStrategy strategy = new DirectAssaultStrategy();
  private final PlayerSnapshot targetPlayer = new PlayerSnapshotBuilder().build();

  @Test
  void isAlwaysViable() {
    var empty = new GroupSnapshotBuilder().build();
    var lonelySkeleton = new GroupSnapshotBuilder().withSkeletons(1).build();

    assertThat(strategy.isViable(empty)).isTrue();
    assertThat(strategy.isViable(lonelySkeleton)).isTrue();
  }

  @Test
  void meleePressAndSkeletonsShoot() {
    var snapshot =
        new GroupSnapshotBuilder()
            .withPlayer(targetPlayer)
            .withZombies(2)
            .withSkeletons(1)
            .withSpiders(1)
            .build();

    var roles = strategy.assignRoles(snapshot, targetPlayer.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS),
            entry(mobId(2), Role.PRESS),
            entry(mobId(3), Role.SHOOT),
            entry(mobId(4), Role.PRESS));
  }

  @Test
  void describesItsRequirement() {
    assertThat(strategy.id().value()).isEqualTo("DIRECT_ASSAULT");
    assertThat(strategy.requirement()).isEqualTo("none");
  }

  private static MobId mobId(int number) {
    return new MobId(new UUID(1, number));
  }
}
