package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** How our goals hurt the target, and how far the target hurts back. */
public record Weapons(MeleeAttacker melee, BowShooter bow, Bodies bodies) {
  public Weapons {
    Objects.requireNonNull(melee, "Weapons.melee");
    Objects.requireNonNull(bow, "Weapons.bow");
    Objects.requireNonNull(bodies, "Weapons.bodies");
  }
}
