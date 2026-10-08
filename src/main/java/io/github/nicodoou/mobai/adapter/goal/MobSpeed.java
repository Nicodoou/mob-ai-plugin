package io.github.nicodoou.mobai.adapter.goal;

import org.bukkit.entity.Mob;

/** The mob's movement speed attribute, potions and buffs included. */
@FunctionalInterface
public interface MobSpeed {
  double of(Mob mob);
}
