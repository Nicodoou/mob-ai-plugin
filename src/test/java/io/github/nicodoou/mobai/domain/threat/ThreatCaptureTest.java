package io.github.nicodoou.mobai.domain.threat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ThreatCaptureTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final ThreatLedger ledger = newLedger();

  @Test
  void captureKeepsEveryRecordInOrder() {
    recordThreeHits();

    ThreatCapture capture = ledger.capture();

    assertThat(capture.records())
        .containsExactly(
            new ThreatRecord(player, 10, 2.0),
            new ThreatRecord(player, 15, 1.5),
            new ThreatRecord(otherPlayer, 12, 3.0));
    assertThat(capture.lastTick()).isEqualTo(15);
  }

  @Test
  void restoredLedgerHasTheSameThreat() {
    recordThreeHits();
    ThreatLedger restored = newLedger();

    restored.restore(ledger.capture());

    assertThat(restored.threatOf(player, 20)).isCloseTo(ledger.threatOf(player, 20), within(1e-9));
    assertThat(restored.threatOf(otherPlayer, 20))
        .isCloseTo(ledger.threatOf(otherPlayer, 20), within(1e-9));
    assertThat(restored.capture()).isEqualTo(ledger.capture());
  }

  @Test
  void restoredLedgerKeepsTheLastTick() {
    recordThreeHits();
    ThreatLedger restored = newLedger();

    restored.restore(ledger.capture());

    assertThatThrownBy(() -> restored.recordDamage(player, 1.0, 14))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("ThreatLedger.tick must not go back, got 14 after 15");
  }

  @Test
  void restoreRequiresAnEmptyLedger() {
    recordThreeHits();
    ThreatLedger used = newLedger();
    used.recordDamage(player, 1.0, 5);

    assertThatThrownBy(() -> used.restore(ledger.capture()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("ThreatLedger can only be restored while empty");
  }

  private void recordThreeHits() {
    ledger.recordDamage(player, 2.0, 10);
    ledger.recordDamage(otherPlayer, 3.0, 12);
    ledger.recordDamage(player, 1.5, 15);
  }

  private static ThreatLedger newLedger() {
    return new ThreatLedger(() -> TestSettings.defaults().target());
  }
}
