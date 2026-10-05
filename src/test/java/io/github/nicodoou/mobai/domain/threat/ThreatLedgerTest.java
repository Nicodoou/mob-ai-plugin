package io.github.nicodoou.mobai.domain.threat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class ThreatLedgerTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 11));

  private final AtomicReference<TargetSettings> settings =
      new AtomicReference<>(TestSettings.defaults().target());
  private final ThreatLedger ledger = new ThreatLedger(settings::get);

  @Test
  void unknownPlayerHasNoThreat() {
    assertThat(ledger.threatOf(ALICE, 100)).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void damageAddsUpWithinTheWindow() {
    ledger.recordDamage(ALICE, 4.0, 100);
    ledger.recordDamage(ALICE, 2.5, 400);

    assertThat(ledger.threatOf(ALICE, 699)).isCloseTo(6.5, within(1e-9));
  }

  @Test
  void damageLeavesTheWindowAfterExactlyWindowTicks() {
    ledger.recordDamage(ALICE, 4.0, 100);
    ledger.recordDamage(ALICE, 2.5, 400);

    assertThat(ledger.threatOf(ALICE, 699)).isCloseTo(6.5, within(1e-9));
    assertThat(ledger.threatOf(ALICE, 700)).isCloseTo(2.5, within(1e-9));
    assertThat(ledger.threatOf(ALICE, 999)).isCloseTo(2.5, within(1e-9));
    assertThat(ledger.threatOf(ALICE, 1000)).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void threatIsKeptPerPlayer() {
    ledger.recordDamage(ALICE, 4.0, 100);
    ledger.recordDamage(BOB, 1.5, 200);

    assertThat(ledger.threatOf(ALICE, 300)).isCloseTo(4.0, within(1e-9));
    assertThat(ledger.threatOf(BOB, 300)).isCloseTo(1.5, within(1e-9));
  }

  @Test
  void usesTheCurrentWindowFromSettings() {
    ledger.recordDamage(ALICE, 4.0, 100);

    settings.set(new TargetSettings(200, 0.2, 1.0, 3.0, 0.5));

    assertThat(ledger.threatOf(ALICE, 350)).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void recordingDamageDropsExpiredEntries() {
    ledger.recordDamage(ALICE, 1.0, 0);
    ledger.recordDamage(ALICE, 1.0, 600);

    assertThat(ledger.entryCount()).isEqualTo(1);
  }

  @Test
  void pruneRemovesExpiredEntriesAndEmptyPlayers() {
    ledger.recordDamage(ALICE, 1.0, 0);
    ledger.recordDamage(BOB, 1.0, 500);

    ledger.prune(600);

    assertThat(ledger.trackedPlayers()).containsExactly(BOB);
    assertThat(ledger.entryCount()).isEqualTo(1);
  }

  @Test
  void forgetRemovesThePlayer() {
    ledger.recordDamage(ALICE, 4.0, 100);

    ledger.forget(ALICE);

    assertThat(ledger.threatOf(ALICE, 200)).isCloseTo(0.0, within(1e-9));
    assertThat(ledger.trackedPlayers()).isEmpty();
  }

  @Test
  void trackedPlayersKeepFirstDamageOrder() {
    ledger.recordDamage(BOB, 1.0, 10);
    ledger.recordDamage(ALICE, 1.0, 20);
    ledger.recordDamage(BOB, 1.0, 30);

    assertThat(ledger.trackedPlayers()).containsExactly(BOB, ALICE);
  }

  @Test
  void rejectsNonPositiveDamage() {
    assertThatThrownBy(() -> ledger.recordDamage(ALICE, 0, 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ThreatLedger.damage must be a positive number, got 0.0");
  }

  @Test
  void rejectsNegativeTick() {
    assertThatThrownBy(() -> ledger.recordDamage(ALICE, 1, -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ThreatLedger.tick must be zero or positive, got -1");
  }

  @Test
  void rejectsTicksThatGoBack() {
    ledger.recordDamage(ALICE, 1, 100);

    assertThatThrownBy(() -> ledger.recordDamage(BOB, 1, 99))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ThreatLedger.tick must not go back, got 99 after 100");
  }
}
