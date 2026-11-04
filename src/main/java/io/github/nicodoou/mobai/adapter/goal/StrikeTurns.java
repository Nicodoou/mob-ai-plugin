package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.attack.StrikeRelay;
import io.github.nicodoou.mobai.domain.attack.WindowReading;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.settings.RelaySettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.function.Supplier;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Asks the strike relay about live entities, reading the target's protection as it stands. */
public final class StrikeTurns {
  private final StrikeRelay relay;
  private final ServerClock clock;
  private final Supplier<RelaySettings> settings;

  public StrikeTurns(StrikeRelay relay, ServerClock clock, Supplier<RelaySettings> settings) {
    this.relay = Objects.requireNonNull(relay, "StrikeTurns.relay");
    this.clock = Objects.requireNonNull(clock, "StrikeTurns.clock");
    this.settings = Objects.requireNonNull(settings, "StrikeTurns.settings");
  }

  public boolean mayStrike(Mob mob, Player target) {
    return relay.mayStrike(new MobId(mob.getUniqueId()), readingOf(target));
  }

  public void struck(Mob mob, Player target) {
    relay.struck(new MobId(mob.getUniqueId()), new PlayerId(target.getUniqueId()));
  }

  public boolean isShotSlotFree(Player target, long flightTicks) {
    return relay.isShotSlotFree(readingOf(target), flightTicks);
  }

  public void shotLoosed(Player target, long flightTicks) {
    relay.shotLoosed(readingOf(target), flightTicks);
  }

  public long maxShotHoldTicks() {
    return settings.get().maxShotHoldTicks();
  }

  private WindowReading readingOf(Player target) {
    return new WindowReading(
        new PlayerId(target.getUniqueId()),
        clock.currentTick(),
        TargetChecks.protectedTicksLeft(target),
        TargetChecks.protectionWindowTicks(target));
  }
}
