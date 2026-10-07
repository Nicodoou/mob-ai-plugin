package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

public record ShotRequest(Attack attack, Vec3 eye, Vec3 targetCenter, Vec3 movementPerTick) {
  public ShotRequest {
    Objects.requireNonNull(attack, "ShotRequest.attack");
    Objects.requireNonNull(eye, "ShotRequest.eye");
    Objects.requireNonNull(targetCenter, "ShotRequest.targetCenter");
    Objects.requireNonNull(movementPerTick, "ShotRequest.movementPerTick");
  }
}
