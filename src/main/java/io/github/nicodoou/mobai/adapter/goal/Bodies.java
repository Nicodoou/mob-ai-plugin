package io.github.nicodoou.mobai.adapter.goal;

import java.util.Objects;

/** What the goals read of bodies on the field: how far the player hits, how fast a mob walks. */
public record Bodies(PlayerReach playerReach, MobSpeed mobSpeed) {
  public Bodies {
    Objects.requireNonNull(playerReach, "Bodies.playerReach");
    Objects.requireNonNull(mobSpeed, "Bodies.mobSpeed");
  }
}
