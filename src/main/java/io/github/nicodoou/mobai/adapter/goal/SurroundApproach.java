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
import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Where a pressing zombie walks: its slot in the arc in front of its pack, or its turn (CT-32). */
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
    Map<MobId, Vec3> positions = new HashMap<>();
    zombies.forEach(
        (id, presser) -> positions.put(id, PoseReader.positionOf(presser.getLocation())));
    SurroundQuery query =
        new SurroundQuery(
            PoseReader.positionOf(target.getLocation()),
            new MobId(zombie.getUniqueId()),
            positions,
            ringFor(zombies, target));
    return formation.spotFor(query);
  }

  // Every zombie pressing this target loaded in its world, the asking one included.
  private Map<MobId, Mob> pressingZombies(Mob zombie, Player target) {
    Map<MobId, Mob> zombies = new HashMap<>();
    for (MobId id : context.roles().mobsWith(Role.PRESS, new PlayerId(target.getUniqueId()))) {
      if (Bukkit.getEntity(id.value()) instanceof Mob presser
          && presser.isValid()
          && presser.getWorld().equals(target.getWorld())
          && isZombie(presser)) {
        zombies.put(id, presser);
      }
    }
    zombies.put(new MobId(zombie.getUniqueId()), zombie);
    return zombies;
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

  private SurroundRing ringFor(Map<MobId, Mob> zombies, Player target) {
    PackSettings pack = context.tools().timing().pack().get();
    double widest = zombies.values().stream().mapToDouble(Mob::getWidth).max().orElseThrow();
    double reach = context.tools().weapons().bodies().playerReach().blocksOf(target);
    return new SurroundRing(
        MinecraftConstants.MELEE_REACH_BLOCKS - STRIKE_SLOT_SLACK_BLOCKS,
        formation.sweepSafeSpacingBlocks(widest, pack.surroundMarginBlocks()),
        reach + pack.waitMarginBlocks());
  }
}
