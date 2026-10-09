package io.github.nicodoou.mobai.application;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.bobAt;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.strategy.TraitLedger;
import io.github.nicodoou.mobai.domain.strategy.TraitSums;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TraitCaptureMapperTest {
  private final TraitCaptureMapper mapper = new TraitCaptureMapper();
  private final TraitLedger ledger = new TraitLedger(() -> TestSettings.defaults().learning());

  @Test
  void forSnapshotKeepsOnlyItsPlayers() {
    ledger.observe(both());
    GroupSnapshot onlyAlice = BrainFixture.snapshot(START_TICK, List.of(), alice());

    List<StoredTraits> stored = mapper.forSnapshot(ledger, onlyAlice);

    assertThat(stored).extracting(StoredTraits::player).containsExactly(ALICE);
  }

  @Test
  void storedTraitsAreSortedByPlayer() {
    ledger.observe(both());

    List<StoredTraits> stored = mapper.toStored(ledger.capture());

    assertThat(stored).extracting(StoredTraits::player).containsExactly(lower(), higher());
  }

  @Test
  void sumsSurviveTheRoundTrip() {
    ledger.observe(both());
    Map<PlayerId, TraitSums> sums = ledger.capture();

    Map<PlayerId, TraitSums> restored = mapper.toSums(mapper.toStored(sums));

    assertThat(restored).isEqualTo(sums);
  }

  private static GroupSnapshot both() {
    return BrainFixture.snapshot(START_TICK, List.of(), alice(), bobAt(new Vec3(3, 64, 0)));
  }

  private static PlayerId lower() {
    return ALICE.value().compareTo(BOB.value()) < 0 ? ALICE : BOB;
  }

  private static PlayerId higher() {
    return lower().equals(ALICE) ? BOB : ALICE;
  }
}
