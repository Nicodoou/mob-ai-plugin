package io.github.nicodoou.mobai.domain.attack;

import io.github.nicodoou.mobai.domain.settings.RelaySettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Spreads a group's hits over time. A hit inside the target's post-hit protection is lost, so each
 * melee mob and arrow gets its own window: the first mob to get ready strikes as the protection
 * closes, and an arrow leaves so as to arrive just then.
 */
public final class StrikeRelay {
  private final Supplier<RelaySettings> settings;
  private final Map<PlayerId, RelayLane> lanes = new HashMap<>();

  public StrikeRelay(Supplier<RelaySettings> settings) {
    this.settings = Objects.requireNonNull(settings, "StrikeRelay.settings");
  }

  /** Puts the mob in the target's line and says whether its hit is the one due now. */
  public boolean mayStrike(MobId mob, WindowReading reading) {
    RelayLane lane = freshLane(reading);
    lane.markReady(mob, reading.tick());
    return reading.protectedTicks() == 0
        // Its hit would protect the target as the arrow arrives, and an arrow cannot be recalled.
        && !lane.hasLandingBefore(reading.windowEndIfHitNow())
        && lane.isNextToStrike(mob);
  }

  public void struck(MobId mob, PlayerId target) {
    RelayLane lane = lanes.get(target);
    if (lane != null) {
      lane.forgetReady(mob);
    }
  }

  /** Whether an arrow loosed now, flying this long, would arrive on a window of its own. */
  public boolean isShotSlotFree(WindowReading reading, long flightTicks) {
    RelayLane lane = freshLane(reading);
    long landing = reading.tick() + flightTicks;
    return landing >= earliestLanding(reading)
        && !lane.hasLandingNear(landing, reading.windowTicks())
        && !(lane.hasReadyMob() && landsTooLateForTheClose(reading, flightTicks));
  }

  public void shotLoosed(WindowReading reading, long flightTicks) {
    freshLane(reading).addLanding(reading.tick() + flightTicks);
  }

  private long earliestLanding(WindowReading reading) {
    return reading.closingTick() + settings.get().arrowMarginTicks();
  }

  // A ready mob would have to hold until this late arrow arrives, leaving a gap, so the arrow waits
  // for the next close. An arrow flying longer than a window cannot be timed to one.
  private boolean landsTooLateForTheClose(WindowReading reading, long flightTicks) {
    long latestLanding = earliestLanding(reading) + settings.get().arrowSlackTicks();
    return flightTicks <= reading.windowTicks() && reading.tick() + flightTicks > latestLanding;
  }

  private RelayLane freshLane(WindowReading reading) {
    RelaySettings current = settings.get();
    lanes.values().forEach(lane -> lane.prune(reading.tick(), current));
    lanes.values().removeIf(RelayLane::isEmpty);
    return lanes.computeIfAbsent(reading.target(), target -> new RelayLane());
  }
}
