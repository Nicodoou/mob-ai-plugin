package io.github.nicodoou.mobai.domain.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.LearningSettings;
import io.github.nicodoou.mobai.domain.settings.PlannerKind;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import io.github.nicodoou.mobai.testsupport.PlayerSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TraitLedgerTest {
  private static final double TOLERANCE = 1e-9;
  private static final GroupId GROUP = new GroupId(new UUID(0, 1));
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 2));

  private final TraitLedger ledger = new TraitLedger(() -> TestSettings.defaults().learning());

  @Test
  void anUnknownPlayerHasNoTraits() {
    PlayerTraits traits = ledger.traitsOf(ALICE);

    assertThat(traits).isEqualTo(new PlayerTraits(0, 0, 0));
  }

  @Test
  void theFirstObservationIsTheTraits() {
    ledger.observe(snapshotAt(1000, alice().withBlocking(true).withArmorPoints(10)));

    assertTraits(ledger, 1, 0, 0.5);
  }

  @Test
  void olderObservationsWeighLess() {
    ledger.observe(snapshotAt(1000, alice().withBlocking(true).withArmorPoints(20)));
    ledger.observe(snapshotAt(7000, alice().withBlocking(false).withArmorPoints(0)));

    PlayerTraits traits = ledger.traitsOf(ALICE);

    assertThat(traits.shield()).isCloseTo(1.0 / 3, within(TOLERANCE));
    assertThat(traits.armor()).isCloseTo(1.0 / 3, within(TOLERANCE));
  }

  @Test
  void theSameTickIsCountedOnce() {
    ledger.observe(snapshotAt(1000, alice().withBlocking(true)));
    ledger.observe(snapshotAt(1000, alice().withBlocking(false)));

    assertThat(ledger.traitsOf(ALICE).shield()).isCloseTo(1.0, within(TOLERANCE));
  }

  @Test
  void deadPlayersAreNotObserved() {
    ledger.observe(snapshotAt(1000, alice().withHealth(0).withBlocking(true)));

    assertThat(ledger.traitsOf(ALICE)).isEqualTo(new PlayerTraits(0, 0, 0));
  }

  @Test
  void armorIsCappedAtOne() {
    ledger.observe(snapshotAt(1000, alice().withArmorPoints(30)));

    assertThat(ledger.traitsOf(ALICE).armor()).isCloseTo(1.0, within(TOLERANCE));
  }

  @Test
  void theHalfLifeComesFromTheSettings() {
    LearningSettings base = TestSettings.defaults().learning();
    LearningSettings shortMemory =
        new LearningSettings(
            PlannerKind.STRATEGIES,
            base.modelNoiseVariance(),
            base.priorVariance(),
            base.priorSuccess(),
            base.explorationScale(),
            base.trainingExplorationScale(),
            base.minReserveDelayTicks(),
            base.maxReserveDelayTicks(),
            base.maxRetreatHealthFraction(),
            3000,
            base.baseWeightPlans());
    TraitLedger shortLedger = new TraitLedger(() -> shortMemory);
    shortLedger.observe(snapshotAt(1000, alice().withBlocking(true)));
    shortLedger.observe(snapshotAt(7000, alice().withBlocking(false)));

    assertThat(shortLedger.traitsOf(ALICE).shield()).isCloseTo(0.2, within(TOLERANCE));
  }

  @Test
  void captureAndRestoreKeepTheTraits() {
    ledger.observe(snapshotAt(1000, alice().withBlocking(true).withArmorPoints(10)));
    TraitLedger restored = new TraitLedger(() -> TestSettings.defaults().learning());

    Map<PlayerId, TraitSums> captured = ledger.capture();
    restored.restore(captured);

    assertTraits(restored, 1, 0, 0.5);
    assertThat(captured).isUnmodifiable();
  }

  private static void assertTraits(TraitLedger target, double shield, double ranged, double armor) {
    PlayerTraits traits = target.traitsOf(ALICE);

    assertThat(traits.shield()).isCloseTo(shield, within(TOLERANCE));
    assertThat(traits.ranged()).isCloseTo(ranged, within(TOLERANCE));
    assertThat(traits.armor()).isCloseTo(armor, within(TOLERANCE));
  }

  private static PlayerSnapshotBuilder alice() {
    return new PlayerSnapshotBuilder().withId(ALICE);
  }

  private static GroupSnapshot snapshotAt(long tick, PlayerSnapshotBuilder player) {
    PlayerSnapshot built = player.build();
    return new GroupSnapshot(GROUP, tick, List.of(), List.of(built));
  }
}
