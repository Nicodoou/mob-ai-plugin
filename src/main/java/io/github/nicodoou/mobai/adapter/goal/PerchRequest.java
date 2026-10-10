package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.List;
import java.util.Objects;

/** Where a shooter was going, and who stands between it and the target. */
public record PerchRequest(Vec3 spot, List<Vec3> allies, List<Vec3> taken) {
  public PerchRequest {
    Objects.requireNonNull(spot, "PerchRequest.spot");
    Objects.requireNonNull(allies, "PerchRequest.allies");
    Objects.requireNonNull(taken, "PerchRequest.taken");
    allies = List.copyOf(allies);
    taken = List.copyOf(taken);
  }
}
