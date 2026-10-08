package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.github.nicodoou.mobai.domain.geometry.CombatGeometry;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VolleyStrategyTest {
  private final VolleyStrategy strategy = new VolleyStrategy();

  @Test
  void needsTwoSkeletonsAndTwoMelee() {
    var enough = new GroupSnapshotBuilder().withSkeletons(2).withZombies(2).build();
    var fewSkeletons = new GroupSnapshotBuilder().withSkeletons(1).withZombies(3).build();
    var fewMelee = new GroupSnapshotBuilder().withSkeletons(2).withSpiders(1).build();
    var mixedMelee =
        new GroupSnapshotBuilder().withSkeletons(2).withZombies(1).withSpiders(1).build();

    assertThat(strategy.isViable(enough)).isTrue();
    assertThat(strategy.isViable(fewSkeletons)).isFalse();
    assertThat(strategy.isViable(fewMelee)).isFalse();
    assertThat(strategy.isViable(mixedMelee)).isTrue();
  }

  @Test
  void meleePressAndSkeletonsShoot() {
    var player = new PlayerSnapshotBuilder().build();
    var snapshot =
        new GroupSnapshotBuilder()
            .withPlayer(player)
            .withZombies(1)
            .withSpiders(1)
            .withSkeletons(1)
            .build();

    var roles = strategy.assignRoles(snapshot, player.id());

    assertThat(roles)
        .containsExactly(
            entry(mobId(1), Role.PRESS), entry(mobId(2), Role.PRESS), entry(mobId(3), Role.SHOOT));
  }

  @Test
  void describesItsRequirement() {
    assertThat(strategy.id().value()).isEqualTo("VOLLEY");
    assertThat(strategy.requirement()).isEqualTo("at least 2 skeletons and 2 melee mobs");
  }

  @Test
  void catalogListsVolleyLast() {
    var catalog = new StrategyCatalog(new CombatGeometry());

    var last = catalog.all().getLast().id();

    assertThat(last).isEqualTo(VolleyStrategy.ID);
  }

  private static MobId mobId(int number) {
    return new MobId(new UUID(1, number));
  }
}
