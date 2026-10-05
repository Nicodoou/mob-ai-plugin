package io.github.nicodoou.mobai.domain.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GroupMemoryTest {
  private static final PlayerId PLAYER_1 = new PlayerId(new UUID(0, 1));
  private static final PlayerId PLAYER_2 = new PlayerId(new UUID(0, 2));
  private static final StrategyId FLANK = new StrategyId("FLANK");
  private static final long HALF_LIFE = 12_000;

  private final MemorySettings settings = TestSettings.defaults().memory();
  private final GroupMemory memory = new GroupMemory(() -> settings);

  private void hit(PlayerId player, Attack attack, long tick) {
    memory.recordAttack(new AttackObservation(player, attack, 1.0, tick));
  }

  private void miss(PlayerId player, Attack attack, long tick) {
    memory.recordAttack(new AttackObservation(player, attack, 0.0, tick));
  }

  @Test
  void unknownPlayerHasThePriorEstimate() {
    SuccessEstimate estimate = memory.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);

    assertThat(estimate.alpha()).isCloseTo(8.2, within(1e-9));
    assertThat(estimate.beta()).isCloseTo(8.2, within(1e-9));
    assertThat(estimate.observedAttempts()).isZero();
  }

  @Test
  void recordedHitRaisesTheEstimate() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);

    SuccessEstimate estimate = memory.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);

    assertThat(estimate.alpha()).isCloseTo(9.2, within(1e-9));
    assertThat(estimate.beta()).isCloseTo(8.2, within(1e-9));
    assertThat(estimate.mean()).isCloseTo(9.2 / 17.4, within(1e-9));
  }

  @Test
  void recordAttackReturnsTheChangeForTracing() {
    memory.recordAttack(new AttackObservation(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 0));

    RecordChange change =
        memory.recordAttack(
            new AttackObservation(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 1.0, HALF_LIFE));

    assertThat(change.before()).isEqualTo(new AttackRecord(0.5, 0.5, HALF_LIFE));
    assertThat(change.after()).isEqualTo(new AttackRecord(1.5, 1.5, HALF_LIFE));
  }

  @Test
  void oneHalfLifeWithoutFightingHalvesEffectiveCounts() {
    for (int i = 0; i < 10; i++) {
      hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    }

    SuccessEstimate estimate =
        memory.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, HALF_LIFE);

    assertThat(estimate.observedAttempts()).isCloseTo(5, within(1e-9));
    assertThat(estimate.alpha()).isCloseTo(5 + 8.2, within(1e-9));
  }

  @Test
  void readingDoesNotStoreDecay() {
    for (int i = 0; i < 4; i++) {
      hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    }

    memory.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, HALF_LIFE);

    assertThat(memory.attackRecords().get(PLAYER_1).get(Attack.ZOMBIE_FRONT_STRIKE))
        .isEqualTo(new AttackRecord(4, 4, 0));
  }

  @Test
  void partialCreditCountsAsHalfASuccess() {
    memory.recordAttack(new AttackObservation(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0.5, 7));

    assertThat(memory.attackRecords().get(PLAYER_1).get(Attack.ZOMBIE_FRONT_STRIKE))
        .isEqualTo(new AttackRecord(0.5, 1, 7));
  }

  @Test
  void strategyObservationUsesItsWeight() {
    memory.recordStrategy(new StrategyObservation(PLAYER_1, FLANK, 0.73, 0.5, 0));

    AttackRecord record = memory.strategyRecords().get(PLAYER_1).get(FLANK);

    assertThat(record.successes()).isCloseTo(0.365, within(1e-9));
    assertThat(record.attempts()).isCloseTo(0.5, within(1e-9));
  }

  @Test
  void playersAreIndependent() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);

    SuccessEstimate other = memory.attackEstimate(PLAYER_2, Attack.ZOMBIE_FRONT_STRIKE, 0);

    assertThat(other.mean()).isCloseTo(0.5, within(1e-9));
    assertThat(other.observedAttempts()).isZero();
  }

  @Test
  void kindEstimateCombinesAllAttacksOfTheKindAndAppliesThePriorOnce() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    miss(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    hit(PLAYER_1, Attack.ZOMBIE_FLANK_STRIKE, 0);
    hit(PLAYER_1, Attack.ZOMBIE_FLANK_STRIKE, 0);
    miss(PLAYER_1, Attack.SKELETON_DIRECT_SHOT, 0);

    SuccessEstimate estimate = memory.kindEstimate(PLAYER_1, MobKind.ZOMBIE, 0);

    assertThat(estimate.alpha()).isCloseTo(3 + 8.2, within(1e-9));
    assertThat(estimate.beta()).isCloseTo(1 + 8.2, within(1e-9));
    assertThat(estimate.observedAttempts()).isCloseTo(4, within(1e-9));
  }

  @Test
  void learningSpeedIsReadOnEveryOperation() {
    AtomicReference<MemorySettings> reference = new AtomicReference<>(settings);
    GroupMemory live = new GroupMemory(reference::get);
    live.recordAttack(new AttackObservation(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 0));
    double slow = live.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0).mean();

    reference.set(new MemorySettings(HALF_LIFE, 1.0, settings.partialHitWeight()));
    double fast = live.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0).mean();

    assertThat(slow).isCloseTo(9.2 / 17.4, within(1e-9));
    assertThat(fast).isCloseTo(2.0 / 3.0, within(1e-9));
  }

  @Test
  void clearPlayerForgetsOnlyThatPlayer() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    hit(PLAYER_2, Attack.ZOMBIE_FRONT_STRIKE, 0);
    memory.recordStrategy(new StrategyObservation(PLAYER_1, FLANK, 1.0, 1.0, 0));

    memory.clearPlayer(PLAYER_1);

    assertThat(memory.attackEstimate(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0).observedAttempts())
        .isZero();
    assertThat(memory.strategyRecords()).doesNotContainKey(PLAYER_1);
    assertThat(memory.attackEstimate(PLAYER_2, Attack.ZOMBIE_FRONT_STRIKE, 0).observedAttempts())
        .isCloseTo(1, within(1e-9));
  }

  @Test
  void clearForgetsEverything() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    memory.recordStrategy(new StrategyObservation(PLAYER_2, FLANK, 1.0, 1.0, 0));

    memory.clear();

    assertThat(memory.attackRecords()).isEmpty();
    assertThat(memory.strategyRecords()).isEmpty();
  }

  @Test
  void snapshotsAreUnmodifiable() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 0);
    AttackRecord record = AttackRecord.empty(0);

    assertThatThrownBy(() -> memory.attackRecords().put(PLAYER_2, Map.of()))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> memory.attackRecords().get(PLAYER_1).put(Attack.SPIDER_BITE, record))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void restoreKeepsTheRecords() {
    hit(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 10);
    memory.recordStrategy(new StrategyObservation(PLAYER_2, FLANK, 0.5, 1.0, 20));

    GroupMemory restored =
        GroupMemory.restore(() -> settings, memory.attackRecords(), memory.strategyRecords());

    assertThat(restored.attackRecords()).isEqualTo(memory.attackRecords());
    assertThat(restored.strategyRecords()).isEqualTo(memory.strategyRecords());
  }

  @Test
  void observationsRejectInvalidCredit() {
    assertThatThrownBy(() -> new AttackObservation(PLAYER_1, Attack.ZOMBIE_FRONT_STRIKE, 1.5, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("AttackObservation.credit must be between 0.0 and 1.0, got 1.5");
    assertThatThrownBy(() -> new StrategyObservation(PLAYER_1, FLANK, 0.5, 0.0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StrategyObservation.weight must be greater than 0.0 and at most 1.0, got 0.0");
  }
}
