package io.github.nicodoou.mobai.domain.snapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupSnapshotTest {
  @Test
  void builderGeneratesMobsWithDistinctIds() {
    GroupSnapshot snapshot =
        new GroupSnapshotBuilder().withZombies(2).withSkeletons(1).withSpiders(1).build();

    assertThat(snapshot.mobs()).hasSize(4);
    assertThat(snapshot.mobs().stream().map(MobSnapshot::kind))
        .containsExactly(MobKind.ZOMBIE, MobKind.ZOMBIE, MobKind.SKELETON, MobKind.SPIDER);
    assertThat(snapshot.mobs().stream().map(MobSnapshot::id).distinct()).hasSize(4);
    assertThat(snapshot.mobs().get(3).maxHealth()).isEqualTo(16.0);
  }

  @Test
  void rejectsDuplicateMobIds() {
    GroupSnapshotBuilder builder =
        new GroupSnapshotBuilder()
            .withMob(new MobSnapshotBuilder().build())
            .withMob(new MobSnapshotBuilder().build());

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GroupSnapshot.mobs has a duplicate id 00000000-0000-0000-0000-000000000001");
  }

  @Test
  void rejectsDuplicatePlayerIds() {
    GroupSnapshotBuilder builder =
        new GroupSnapshotBuilder()
            .withPlayer(new PlayerSnapshotBuilder().build())
            .withPlayer(new PlayerSnapshotBuilder().build());

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "GroupSnapshot.players has a duplicate id 00000000-0000-0000-0000-000000000002");
  }

  @Test
  void rejectsNegativeTick() {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder().withTick(-1);

    assertThatThrownBy(builder::build)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("GroupSnapshot.tick must be at least 0, got -1");
  }

  @Test
  void findsMobsAndPlayersById() {
    PlayerSnapshot player = new PlayerSnapshotBuilder().build();
    GroupSnapshot snapshot = new GroupSnapshotBuilder().withPlayer(player).withZombies(1).build();
    MobSnapshot zombie = snapshot.mobs().get(0);

    assertThat(snapshot.player(player.id())).containsSame(player);
    assertThat(snapshot.mob(zombie.id())).containsSame(zombie);
  }

  @Test
  void unknownIdsAreEmpty() {
    GroupSnapshot snapshot = new GroupSnapshotBuilder().withZombies(1).build();

    assertThat(snapshot.player(new PlayerId(new UUID(9, 9)))).isEmpty();
    assertThat(snapshot.mob(new MobId(new UUID(9, 9)))).isEmpty();
  }

  @Test
  void listsCannotBeChangedFromOutside() {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder().withZombies(1);
    GroupSnapshot snapshot = builder.build();
    List<MobSnapshot> mobs = snapshot.mobs();
    MobSnapshot extra = new MobSnapshotBuilder().build();

    builder.withSkeletons(1);

    assertThatThrownBy(() -> mobs.add(extra)).isInstanceOf(UnsupportedOperationException.class);
    assertThat(snapshot.mobs()).hasSize(1);
  }
}
