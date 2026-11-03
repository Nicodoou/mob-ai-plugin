package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChaseAnchorTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final MobSnapshot ZOMBIE =
      new MobSnapshotBuilder()
          .withId(new MobId(new UUID(1, 1)))
          .withPosition(new Vec3(0, 64, 0))
          .build();
  private static final MobSnapshot SKELETON =
      new MobSnapshotBuilder()
          .withId(new MobId(new UUID(1, 2)))
          .withKind(MobKind.SKELETON)
          .withPosition(new Vec3(4, 66, 0))
          .build();

  @Test
  void theGroundIsTheCenterOfAllTheMobs() {
    GroupSnapshot snapshot = snapshotWith(List.of(ZOMBIE, SKELETON), List.of(aliceAt(2, 65, 10)));

    ChaseAnchor anchor = ChaseAnchor.of(snapshot, ALICE);

    assertThat(anchor.ground().x()).isCloseTo(2, within(1e-9));
    assertThat(anchor.ground().y()).isCloseTo(65, within(1e-9));
    assertThat(anchor.ground().z()).isCloseTo(0, within(1e-9));
  }

  @Test
  void theOpeningDistanceIsHorizontal() {
    GroupSnapshot snapshot = snapshotWith(List.of(ZOMBIE, SKELETON), List.of(aliceAt(2, 90, 10)));

    ChaseAnchor anchor = ChaseAnchor.of(snapshot, ALICE);

    assertThat(anchor.openingDistanceBlocks()).isCloseTo(10, within(1e-9));
  }

  @Test
  void aTargetOutOfTheSnapshotIsRejected() {
    GroupSnapshot snapshot = snapshotWith(List.of(ZOMBIE, SKELETON), List.of());

    assertThatThrownBy(() -> ChaseAnchor.of(snapshot, ALICE))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChaseAnchor: target " + ALICE.value() + " is not in the snapshot");
  }

  @Test
  void aGroupWithNoMobsHasNoCenter() {
    GroupSnapshot snapshot = snapshotWith(List.of(), List.of(aliceAt(2, 65, 10)));

    assertThatThrownBy(() -> ChaseAnchor.of(snapshot, ALICE))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("GroupSnapshot.mobCenter: the group has no mobs");
  }

  @Test
  void aNegativeOpeningDistanceIsRejected() {
    assertThatThrownBy(() -> new ChaseAnchor(Vec3.ZERO, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ChaseAnchor.openingDistanceBlocks must be zero or positive, got -1.0");
  }

  private static GroupSnapshot snapshotWith(List<MobSnapshot> mobs, List<PlayerSnapshot> players) {
    return new GroupSnapshot(new GroupId(new UUID(0, 3)), 0, mobs, players);
  }

  private static PlayerSnapshot aliceAt(double x, double y, double z) {
    return new PlayerSnapshotBuilder().withId(ALICE).withPosition(new Vec3(x, y, z)).build();
  }
}
