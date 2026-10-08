package io.github.nicodoou.mobai.adapter.goal;

import org.bukkit.entity.Player;

/** How far the player can hit right now, with the weapon in hand. */
@FunctionalInterface
public interface PlayerReach {
  double blocksOf(Player player);
}
