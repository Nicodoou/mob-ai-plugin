package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** How our goals hurt the target, and how far the target hurts back. */
public record Weapons(MeleeAttacker melee, BowShooter bow, PlayerReach playerReach) {
  public Weapons {
    Objects.requireNonNull(melee, "Weapons.melee");
    Objects.requireNonNull(bow, "Weapons.bow");
    Objects.requireNonNull(playerReach, "Weapons.playerReach");
  }
}
