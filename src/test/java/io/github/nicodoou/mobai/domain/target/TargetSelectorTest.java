package io.github.nicodoou.mobai.domain.target;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TargetSelectorTest {
  private static final double TOLERANCE = 1e-9;
  private static final long DAMAGE_TICK = 900;
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 11));
  private static final PlayerId CAROL = new PlayerId(new UUID(0, 12));

  private final ThreatLedger ledger = new ThreatLedger(() -> TestSettings.defaults().target());
  private final GroupMemory memory = new GroupMemory(() -> TestSettings.defaults().memory());
  private final KillTimeEstimator estimator =
      new KillTimeEstimator(
          () -> TestSettings.defaults().target(), () -> TestSettings.defaults().attack());
  private final TargetSelector selector =
      new TargetSelector(() -> TestSettings.defaults().target(), estimator);

  @Test
  void noPlayersMeansNoTarget() {
    TargetSelection selection = select(Optional.empty());

    assertThat(selection.target()).isEmpty();
    assertThat(selection.scores()).isEmpty();
  }

  @Test
  void deadPlayersAreNotCandidates() {
    TargetSelection selection = select(Optional.empty(), player(ALICE).withHealth(0).build());

    assertThat(selection.target()).isEmpty();
    assertThat(selection.scores()).isEmpty();
  }

  @Test
  void threatHasAFloorOfBaseThreat() {
    TargetSelection selection = select(Optional.empty(), player(ALICE).build());

    TargetScore score = selection.scores().get(0);
    assertThat(score.rawThreat()).isCloseTo(0.0, within(TOLERANCE));
    assertThat(score.threat()).isCloseTo(1.0, within(TOLERANCE));
    assertThat(score.priority()).isCloseTo(0.075, within(TOLERANCE));
  }

  @Test
  void higherThreatWins() {
    ledger.recordDamage(ALICE, 10.0, DAMAGE_TICK);

    TargetSelection selection =
        select(Optional.empty(), player(ALICE).build(), player(BOB).build());

    assertThat(selection.target()).contains(ALICE);
    assertThat(scoreOf(selection, ALICE).priority()).isCloseTo(0.75, within(TOLERANCE));
  }

  @Test
  void easierKillWinsWhenThreatIsEqual() {
    TargetSelection selection =
        select(
            Optional.empty(),
            player(ALICE).fullDiamondProtectionFour().build(),
            player(BOB).build());

    assertThat(selection.target()).contains(BOB);
    assertThat(scoreOf(selection, ALICE).priority()).isCloseTo(0.00621, within(TOLERANCE));
  }

  @Test
  void weaknessHalvesThreatPerLevel() {
    ledger.recordDamage(ALICE, 8.0, DAMAGE_TICK);

    TargetSelection selection =
        select(Optional.empty(), player(ALICE).withEffect(EffectKind.WEAKNESS, 2).build());

    assertThat(selection.scores().get(0).threat()).isCloseTo(2.0, within(TOLERANCE));
  }

  @Test
  void weaknessAppliesToTheThreatFloorToo() {
    ledger.recordDamage(ALICE, 2.0, DAMAGE_TICK);

    TargetSelection selection =
        select(Optional.empty(), player(ALICE).withEffect(EffectKind.WEAKNESS, 2).build());

    assertThat(selection.scores().get(0).threat()).isCloseTo(0.5, within(TOLERANCE));
  }

  @Test
  void commitmentKeepsTheTargetAgainstASmallDifference() {
    ledger.recordDamage(ALICE, 10.0, DAMAGE_TICK);
    ledger.recordDamage(BOB, 11.0, DAMAGE_TICK);

    TargetSelection selection =
        select(Optional.of(ALICE), player(ALICE).build(), player(BOB).build());

    assertThat(selection.target()).contains(ALICE);
    assertThat(scoreOf(selection, ALICE).priority()).isCloseTo(0.9, within(TOLERANCE));
    assertThat(scoreOf(selection, BOB).priority()).isCloseTo(0.825, within(TOLERANCE));
    assertThat(scoreOf(selection, ALICE).committed()).isTrue();
    assertThat(scoreOf(selection, BOB).committed()).isFalse();
  }

  @Test
  void commitmentYieldsToAClearlyBetterTarget() {
    ledger.recordDamage(ALICE, 10.0, DAMAGE_TICK);
    ledger.recordDamage(BOB, 13.0, DAMAGE_TICK);

    TargetSelection selection =
        select(Optional.of(ALICE), player(ALICE).build(), player(BOB).build());

    assertThat(selection.target()).contains(BOB);
  }

  @Test
  void tiesGoToTheFirstPlayerInTheSnapshot() {
    TargetSelection selection =
        select(Optional.empty(), player(BOB).build(), player(ALICE).build());

    assertThat(selection.target()).contains(BOB);
  }

  @Test
  void unkillableTargetHasZeroPriority() {
    ledger.recordDamage(ALICE, 10.0, DAMAGE_TICK);

    TargetSelection selection =
        select(
            Optional.empty(),
            player(ALICE).withEffect(EffectKind.REGENERATION, 3).build(),
            player(BOB).build());

    assertThat(scoreOf(selection, ALICE).priority()).isCloseTo(0.0, within(TOLERANCE));
    assertThat(selection.target()).contains(BOB);
  }

  @Test
  void scoresExplainEveryCandidateInSnapshotOrder() {
    TargetSelection selection =
        select(
            Optional.empty(),
            player(BOB).withHealth(0).build(),
            player(ALICE).build(),
            player(CAROL).build());

    assertThat(selection.scores()).extracting(TargetScore::player).containsExactly(ALICE, CAROL);
    assertThat(selection.scores())
        .allSatisfy(
            score ->
                assertThat(score.killTime().killTimeSeconds())
                    .isCloseTo(13.333333333333334, within(TOLERANCE)));
  }

  private TargetSelection select(Optional<PlayerId> committed, PlayerSnapshot... players) {
    GroupSnapshotBuilder builder =
        new GroupSnapshotBuilder()
            .withMob(
                new MobSnapshotBuilder()
                    .withId(new MobId(new UUID(1, 1)))
                    .withPosition(new Vec3(0, 64, 2))
                    .build());
    for (PlayerSnapshot player : players) {
      builder.withPlayer(player);
    }
    GroupSnapshot snapshot = builder.build();
    return selector.select(new TargetQuery(snapshot, memory, ledger, committed));
  }

  private static PlayerSnapshotBuilder player(PlayerId id) {
    return new PlayerSnapshotBuilder().withId(id);
  }

  private static TargetScore scoreOf(TargetSelection selection, PlayerId id) {
    return selection.scores().stream()
        .filter(score -> score.player().equals(id))
        .findFirst()
        .orElseThrow();
  }
}
