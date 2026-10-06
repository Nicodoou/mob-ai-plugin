package io.github.nicodoou.mobai.adapter.tracker;

import org.bukkit.GameMode;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** Target conditions the tracker needs, read from the live entities. */
public final class TargetChecks {
  private TargetChecks() {}

  // Inside the post-hit window a weaker hit raises no event at all (spike finding 4).
  public static boolean isInvulnerable(Player target) {
    GameMode mode = target.getGameMode();
    return mode == GameMode.CREATIVE
        || mode == GameMode.SPECTATOR
        || target.getNoDamageTicks() > target.getMaximumNoDamageTicks() / 2.0;
  }

  public static boolean isValidTarget(Player target, Mob attacker) {
    return target.isOnline() && !target.isDead() && target.getWorld().equals(attacker.getWorld());
  }
}
