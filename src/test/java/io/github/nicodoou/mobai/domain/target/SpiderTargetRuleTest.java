package io.github.nicodoou.mobai.domain.target;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SpiderTargetRuleTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 11));

  private final SpiderTargetRule rule =
      new SpiderTargetRule(() -> TestSettings.defaults().target());
  private final MobSnapshot spider =
      new MobSnapshotBuilder().withKind(MobKind.SPIDER).withPosition(new Vec3(0, 64, 0)).build();

  @Test
  void noPlayersMeansNoTarget() {
    assertThat(rule.choose(spider, List.of(), Optional.empty())).isEmpty();
  }

  @Test
  void picksTheNearestPlayer() {
    List<PlayerSnapshot> players = List.of(playerAt(ALICE, 6), playerAt(BOB, 4));

    assertThat(rule.choose(spider, players, Optional.empty())).contains(BOB);
  }

  @Test
  void keepsTheCurrentTargetWithinTheCommitmentMargin() {
    List<PlayerSnapshot> players = List.of(playerAt(ALICE, 5), playerAt(BOB, 4.5));

    assertThat(rule.choose(spider, players, Optional.of(ALICE))).contains(ALICE);
  }

  @Test
  void switchesWhenAnotherPlayerIsClearlyCloser() {
    List<PlayerSnapshot> players = List.of(playerAt(ALICE, 5), playerAt(BOB, 4));

    assertThat(rule.choose(spider, players, Optional.of(ALICE))).contains(BOB);
  }

  @Test
  void missingCurrentTargetFallsBackToTheNearest() {
    List<PlayerSnapshot> players = List.of(playerAt(ALICE, 6), playerAt(BOB, 4));
    PlayerId missing = new PlayerId(new UUID(9, 9));

    assertThat(rule.choose(spider, players, Optional.of(missing))).contains(BOB);
  }

  @Test
  void ignoresDeadPlayers() {
    PlayerSnapshot deadAlice =
        new PlayerSnapshotBuilder()
            .withId(ALICE)
            .withPosition(new Vec3(0, 64, 2))
            .withHealth(0)
            .build();
    List<PlayerSnapshot> players = List.of(deadAlice, playerAt(BOB, 4));

    assertThat(rule.choose(spider, players, Optional.empty())).contains(BOB);
  }

  @Test
  void tiesGoToTheFirstPlayer() {
    List<PlayerSnapshot> players = List.of(playerAt(ALICE, 3), playerAt(BOB, 3));

    assertThat(rule.choose(spider, players, Optional.empty())).contains(ALICE);
  }

  private static PlayerSnapshot playerAt(PlayerId id, double z) {
    return new PlayerSnapshotBuilder().withId(id).withPosition(new Vec3(0, 64, z)).build();
  }
}
