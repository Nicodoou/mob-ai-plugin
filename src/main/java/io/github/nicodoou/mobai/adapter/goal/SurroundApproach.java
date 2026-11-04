package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.geometry.SurroundFormation;
import io.github.nicodoou.mobai.domain.geometry.SurroundQuery;
import io.github.nicodoou.mobai.domain.geometry.SurroundRing;
import io.github.nicodoou.mobai.domain.geometry.SurroundSpot;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Where a pressing zombie walks: its slot in the arc in front of its pack, or its turn (CT-32).
 * With attack turns, where a pressing fighter without one waits: its place on the ring round the
 * target.
 */
final class SurroundApproach {
  // A fifth of a block inside the zombie's reach, so it strikes from its slot without stepping in.
  private static final double STRIKE_SLOT_SLACK_BLOCKS = 0.2;

  private final GoalContext context;
  private final SurroundFormation formation = new SurroundFormation();

  SurroundApproach(GoalContext context) {
    this.context = Objects.requireNonNull(context, "SurroundApproach.context");
  }

  SurroundSpot spotFor(Mob zombie, Player target) {
    Map<MobId, Mob> zombies = pressingZombies(zombie, target);
    return formation.spotFor(queryFor(zombie, target, zombies));
  }

  Vec3 ringSpotFor(Mob fighter, Player target) {
    Map<MobId, Mob> waiters = waitingFighters(fighter, target);
    return formation.ringSpotFor(queryFor(fighter, target, waiters));
  }

  private SurroundQuery queryFor(Mob asking, Player target, Map<MobId, Mob> fighters) {
    Map<MobId, Vec3> positions = new HashMap<>();
    fighters.forEach(
        (id, fighter) -> positions.put(id, PoseReader.positionOf(fighter.getLocation())));
    return new SurroundQuery(
        PoseReader.positionOf(target.getLocation()),
        new MobId(asking.getUniqueId()),
        positions,
        ringFor(fighters, target));
  }

  // Every zombie pressing this target loaded in its world, the asking one included.
  private Map<MobId, Mob> pressingZombies(Mob zombie, Player target) {
    Set<MobId> pressers =
        context
            .roles()
            .mobsWith(
                Role.PRESS, new PlayerId(target.getUniqueId()), new MobId(zombie.getUniqueId()));
    return fightersAmong(pressers, zombie, this::isZombie);
  }

  // Every fighter waiting on this target loaded in its world, the asking one included.
  private Map<MobId, Mob> waitingFighters(Mob fighter, Player target) {
    Set<MobId> waiters =
        context
            .roles()
            .mobsWaitingWith(
                Role.PRESS, new PlayerId(target.getUniqueId()), new MobId(fighter.getUniqueId()));
    return fightersAmong(waiters, fighter, mob -> true);
  }

  private Map<MobId, Mob> fightersAmong(Set<MobId> ids, Mob asking, Predicate<Mob> isFighter) {
    Map<MobId, Mob> fighters = new HashMap<>();
    for (MobId id : ids) {
      if (Bukkit.getEntity(id.value()) instanceof Mob fighter
          && fighter.isValid()
          && fighter.getWorld().equals(asking.getWorld())
          && isFighter.test(fighter)) {
        fighters.put(id, fighter);
      }
    }
    fighters.put(new MobId(asking.getUniqueId()), asking);
    return fighters;
  }

  private boolean isZombie(Mob mob) {
    return context
        .tools()
        .weapons()
        .bodies()
        .mobKinds()
        .of(mob)
        .equals(Optional.of(MobKind.ZOMBIE));
  }

  private SurroundRing ringFor(Map<MobId, Mob> fighters, Player target) {
    PackSettings pack = context.tools().timing().pack().get();
    double widest = fighters.values().stream().mapToDouble(Mob::getWidth).max().orElseThrow();
    double reach = context.tools().weapons().bodies().playerReach().blocksOf(target);
    return new SurroundRing(
        MinecraftConstants.MELEE_REACH_BLOCKS - STRIKE_SLOT_SLACK_BLOCKS,
        formation.sweepSafeSpacingBlocks(widest, pack.surroundMarginBlocks()),
        reach + pack.waitMarginBlocks());
  }
}
