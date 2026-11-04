package io.github.nicodoou.mobai.domain.attack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.settings.RelaySettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.testsupport.FakeServerClock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StrikeRelayTest {
  private static final long WINDOW_TICKS = 10;
  private static final int GOAL_INTERVAL_TICKS = 2;
  private static final long MELEE_INTERVAL_TICKS = 20;

  private final FakeServerClock clock = new FakeServerClock(1_000);
  private final StrikeRelay relay = new StrikeRelay(() -> new RelaySettings(1, 2, 2, 20, 3));
  private final GroupId group = new GroupId(UUID.randomUUID());
  private final PlayerId target = new PlayerId(UUID.randomUUID());
  private final MobId first = new MobId(UUID.randomUUID());
  private final MobId second = new MobId(UUID.randomUUID());

  @Test
  void aMobStrikesWhenTheTargetIsOpen() {
    assertThat(relay.mayStrike(first, open())).isTrue();
  }

  @Test
  void aMobHoldsWhileTheTargetIsProtected() {
    assertThat(relay.mayStrike(first, protectedFor(1))).isFalse();
  }

  @Test
  void theMobThatGotReadyFirstStrikesFirst() {
    relay.mayStrike(first, protectedFor(4));
    relay.mayStrike(second, protectedFor(4));

    assertThat(relay.mayStrike(second, open())).isFalse();
    assertThat(relay.mayStrike(first, open())).isTrue();
  }

  @Test
  void theNextInLineStrikesOnceTheFirstHasStruck() {
    relay.mayStrike(first, open());
    relay.mayStrike(second, open());

    relay.struck(first, open());

    assertThat(relay.mayStrike(second, open())).isTrue();
  }

  @Test
  void aMobThatStopsAskingLosesItsPlace() {
    relay.mayStrike(first, protectedFor(9));
    relay.mayStrike(second, protectedFor(9));

    clock.advance(2);
    relay.mayStrike(second, protectedFor(7));
    clock.advance(2);

    assertThat(relay.mayStrike(second, open())).isTrue();
  }

  @Test
  void aMobThatKeepsAskingKeepsItsPlace() {
    relay.mayStrike(first, protectedFor(9));
    relay.mayStrike(second, protectedFor(9));

    clock.advance(3);
    relay.mayStrike(first, protectedFor(6));

    assertThat(relay.mayStrike(second, open())).isFalse();
    assertThat(relay.mayStrike(first, open())).isTrue();
  }

  @Test
  void linesOfDifferentTargetsDoNotMix() {
    PlayerId other = new PlayerId(UUID.randomUUID());
    relay.mayStrike(first, open());

    WindowReading otherOpen = new WindowReading(group, other, clock.currentTick(), 0, WINDOW_TICKS);

    assertThat(relay.mayStrike(second, otherOpen)).isTrue();
  }

  @Test
  void aMobWaitsForNeitherTheMobsNorTheArrowsOfAnotherGroupOnTheSameTarget() {
    WindowReading otherOpen = openForAnotherGroup();
    relay.mayStrike(first, otherOpen);
    relay.shotLoosed(otherOpen, 5);

    assertThat(relay.mayStrike(second, open())).isTrue();
  }

  @Test
  void anArrowWaitsForNeitherTheMobsNorTheArrowsOfAnotherGroupOnTheSameTarget() {
    WindowReading otherOpen = openForAnotherGroup();
    relay.mayStrike(first, otherOpen);
    relay.shotLoosed(otherOpen, 6);

    assertThat(relay.isShotSlotFree(open(), 6)).isTrue();
  }

  private WindowReading openForAnotherGroup() {
    GroupId otherGroup = new GroupId(UUID.randomUUID());
    return new WindowReading(otherGroup, target, clock.currentTick(), 0, WINDOW_TICKS);
  }

  @Test
  void aMobHoldsForAnArrowThatWouldLandInsideItsWindow() {
    relay.shotLoosed(open(), 9);

    assertThat(relay.mayStrike(first, open())).isFalse();
  }

  @Test
  void aMobStrikesWhenTheArrowLandsAfterItsWindow() {
    relay.shotLoosed(open(), WINDOW_TICKS);

    assertThat(relay.mayStrike(first, open())).isTrue();
  }

  @Test
  void anArrowThatMissedStopsHoldingOnceItsGraceIsOver() {
    relay.shotLoosed(open(), 5);

    clock.advance(7);
    assertThat(relay.mayStrike(first, open())).isFalse();

    clock.advance(1);
    assertThat(relay.mayStrike(first, open())).isTrue();
  }

  @Test
  void anArrowArrivingBeforeTheCloseWaits() {
    assertThat(relay.isShotSlotFree(protectedFor(8), 8)).isFalse();
  }

  @Test
  void anArrowLeavesToArriveJustAfterTheClose() {
    assertThat(relay.isShotSlotFree(protectedFor(8), 9)).isTrue();
  }

  @Test
  void anArrowLeavesAtOnceWhenNothingElseIsComing() {
    assertThat(relay.isShotSlotFree(open(), 6)).isTrue();
  }

  @Test
  void anArrowLetsAReadyMobTakeTheOpenTargetFirst() {
    relay.mayStrike(first, open());

    assertThat(relay.isShotSlotFree(open(), 6)).isFalse();
  }

  @Test
  void anArrowArrivingRightAtTheCloseGoesAheadOfAReadyMob() {
    relay.mayStrike(first, protectedFor(5));

    assertThat(relay.isShotSlotFree(protectedFor(5), 8)).isTrue();
  }

  @Test
  void anArrowTooLateForTheCloseWaitsWhenAMobIsReady() {
    relay.mayStrike(first, protectedFor(1));

    assertThat(relay.isShotSlotFree(protectedFor(1), 5)).isFalse();
  }

  @Test
  void anArrowFlyingLongerThanAWindowIsNotHeldForAReadyMob() {
    relay.mayStrike(first, open());

    assertThat(relay.isShotSlotFree(open(), WINDOW_TICKS + 1)).isTrue();
  }

  @Test
  void twoArrowsDoNotShareAWindow() {
    relay.shotLoosed(open(), 6);

    clock.advance(3);
    assertThat(relay.isShotSlotFree(open(), 12)).isFalse();

    clock.advance(1);
    assertThat(relay.isShotSlotFree(open(), 12)).isTrue();
  }

  @Test
  void aReadingWithNegativeProtectionIsRejected() {
    assertThatThrownBy(() -> new WindowReading(group, target, 0, -1, WINDOW_TICKS))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("-1");
  }

  @Test
  void aReadingWithoutWindowIsRejected() {
    assertThatThrownBy(() -> new WindowReading(group, target, 0, 0, 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("got 0");
  }

  @Test
  void fourMobsInReachLandOneUsefulHitEveryWindowWithoutGaps() {
    List<Long> hits = hitsOfAFightAmong(4);

    assertThat(hits).hasSize(20);
    assertThat(gapsBetween(hits)).containsOnly(WINDOW_TICKS);
  }

  @Test
  void aMobAndAnArrowAlternateWithoutWastingEitherOne() {
    MobId mob = new MobId(UUID.randomUUID());
    assertThat(relay.mayStrike(mob, open())).isTrue();
    relay.struck(mob, open());

    clock.advance(4);
    assertThat(relay.isShotSlotFree(protectedFor(6), 6)).isFalse();

    clock.advance(1);
    assertThat(relay.isShotSlotFree(protectedFor(5), 6)).isTrue();
    relay.shotLoosed(protectedFor(5), 6);

    clock.advance(5);
    assertThat(relay.mayStrike(second, open())).isFalse();
  }

  // Goals run every other tick, and a hit protects the target until the window ends.
  private List<Long> hitsOfAFightAmong(int mobCount) {
    List<MobId> mobs = new ArrayList<>();
    List<Long> nextReadyTicks = new ArrayList<>();
    for (int mob = 0; mob < mobCount; mob++) {
      mobs.add(new MobId(UUID.randomUUID()));
      nextReadyTicks.add(Long.MIN_VALUE);
    }
    List<Long> hits = new ArrayList<>();
    for (int step = 0; step < 100; step++) {
      for (int mob = 0; mob < mobCount; mob++) {
        if (clock.currentTick() >= nextReadyTicks.get(mob)
            && relay.mayStrike(mobs.get(mob), readingAfter(hits))) {
          hits.add(clock.currentTick());
          relay.struck(mobs.get(mob), open());
          nextReadyTicks.set(mob, clock.currentTick() + MELEE_INTERVAL_TICKS);
        }
      }
      clock.advance(GOAL_INTERVAL_TICKS);
    }
    return hits;
  }

  private WindowReading readingAfter(List<Long> hits) {
    if (hits.isEmpty()) {
      return open();
    }
    long elapsed = clock.currentTick() - hits.getLast();
    return protectedFor(Math.max(0, WINDOW_TICKS - elapsed));
  }

  private static List<Long> gapsBetween(List<Long> hits) {
    List<Long> gaps = new ArrayList<>();
    for (int hit = 1; hit < hits.size(); hit++) {
      gaps.add(hits.get(hit) - hits.get(hit - 1));
    }
    return gaps;
  }

  private WindowReading open() {
    return protectedFor(0);
  }

  private WindowReading protectedFor(long protectedTicks) {
    return new WindowReading(group, target, clock.currentTick(), protectedTicks, WINDOW_TICKS);
  }
}
