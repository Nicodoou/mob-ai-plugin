package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

/** An ally that could take an arrow: where its body is now and how far it moves each tick. */
public record MovingAlly(Vec3 center, Vec3 movementPerTick) {
  public MovingAlly {
    Objects.requireNonNull(center, "MovingAlly.center");
    Objects.requireNonNull(movementPerTick, "MovingAlly.movementPerTick");
  }
}
