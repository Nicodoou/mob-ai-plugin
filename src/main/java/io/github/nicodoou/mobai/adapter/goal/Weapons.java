package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** How our goals hurt the target: melee hits and arrows, both through the attack tracker. */
public record Weapons(MeleeAttacker melee, BowShooter bow) {
  public Weapons {
    Objects.requireNonNull(melee, "Weapons.melee");
    Objects.requireNonNull(bow, "Weapons.bow");
  }
}
