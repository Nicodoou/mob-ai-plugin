package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.attack.StrikeRelay;
import io.github.nicodoou.mobai.domain.attack.WindowReading;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Asks the strike relay about live entities, reading the target's protection as it stands. Only the
 * mobs of a RELAY group wait for it, each in its own group's line; every other mob is always free.
 */
public final class StrikeTurns {
  private final StrikeRelay relay;
  private final ServerClock clock;
  private final RoleRegistry roles;

  public StrikeTurns(StrikeRelay relay, ServerClock clock, RoleRegistry roles) {
    this.relay = Objects.requireNonNull(relay, "StrikeTurns.relay");
    this.clock = Objects.requireNonNull(clock, "StrikeTurns.clock");
    this.roles = Objects.requireNonNull(roles, "StrikeTurns.roles");
  }

  public boolean isRelaying(Mob mob) {
    return roles.tacticOf(idOf(mob)) == CombatTactic.RELAY;
  }

  public boolean mayStrike(Mob mob, Player target) {
    return !isRelaying(mob) || relay.mayStrike(idOf(mob), readingOf(mob, target));
  }

  public void struck(Mob mob, Player target) {
    if (isRelaying(mob)) {
      relay.struck(idOf(mob), readingOf(mob, target));
    }
  }

  public boolean isShotSlotFree(Mob shooter, Player target, long flightTicks) {
    return !isRelaying(shooter) || relay.isShotSlotFree(readingOf(shooter, target), flightTicks);
  }

  public void shotLoosed(Mob shooter, Player target, long flightTicks) {
    if (isRelaying(shooter)) {
      relay.shotLoosed(readingOf(shooter, target), flightTicks);
    }
  }

  public long maxShotHoldTicks() {
    return relay.maxShotHoldTicks();
  }

  private WindowReading readingOf(Mob mob, Player target) {
    return new WindowReading(
        roles.groupOf(idOf(mob)),
        new PlayerId(target.getUniqueId()),
        clock.currentTick(),
        TargetChecks.protectedTicksLeft(target),
        TargetChecks.protectionWindowTicks(target));
  }

  private static MobId idOf(Mob mob) {
    return new MobId(mob.getUniqueId());
  }
}
