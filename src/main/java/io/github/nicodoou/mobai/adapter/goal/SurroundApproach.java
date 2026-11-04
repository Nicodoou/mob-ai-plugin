package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.snapshot.PoseReader;
import io.github.nicodoou.mobai.domain.geometry.SurroundFormation;
import io.github.nicodoou.mobai.domain.geometry.SurroundQuery;
import io.github.nicodoou.mobai.domain.geometry.SurroundRing;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.MinecraftConstants;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Where a pressing fighter without an attack turn waits: its place on the ring around the target.
 */
final class SurroundApproach {
  // A fifth of a block inside the zombie's reach, so it strikes from its slot without stepping in.
  private static final double STRIKE_SLOT_SLACK_BLOCKS = 0.2;

  private final GoalContext context;
  private final SurroundFormation formation = new SurroundFormation();

  SurroundApproach(GoalContext context) {
    this.context = Objects.requireNonNull(context, "SurroundApproach.context");
  }

  Vec3 ringSpotFor(Mob fighter, Player target) {
    Map<MobId, Mob> waiters = waitingFighters(fighter, target);
    Map<MobId, Vec3> positions = new HashMap<>();
    waiters.forEach((id, waiter) -> positions.put(id, PoseReader.positionOf(waiter.getLocation())));
    SurroundQuery query =
        new SurroundQuery(
            PoseReader.positionOf(target.getLocation()),
            new MobId(fighter.getUniqueId()),
            positions,
            ringFor(waiters, target));
    return formation.ringSpotFor(query);
  }

  // Every fighter waiting on this target loaded in its world, the asking one included.
  private Map<MobId, Mob> waitingFighters(Mob fighter, Player target) {
    Map<MobId, Mob> waiters = new HashMap<>();
    PlayerId targetId = new PlayerId(target.getUniqueId());
    for (MobId id : context.roles().mobsWaitingWith(Role.PRESS, targetId)) {
      if (Bukkit.getEntity(id.value()) instanceof Mob waiter
          && waiter.isValid()
          && waiter.getWorld().equals(target.getWorld())) {
        waiters.put(id, waiter);
      }
    }
    waiters.put(new MobId(fighter.getUniqueId()), fighter);
    return waiters;
  }

  private SurroundRing ringFor(Map<MobId, Mob> waiters, Player target) {
    PackSettings pack = context.tools().timing().pack().get();
    double widest = waiters.values().stream().mapToDouble(Mob::getWidth).max().orElseThrow();
    double reach = context.tools().weapons().bodies().playerReach().blocksOf(target);
    return new SurroundRing(
        MinecraftConstants.MELEE_REACH_BLOCKS - STRIKE_SLOT_SLACK_BLOCKS,
        formation.sweepSafeSpacingBlocks(widest, pack.surroundMarginBlocks()),
        reach + pack.waitMarginBlocks());
  }
}
