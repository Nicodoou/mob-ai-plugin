package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The high ground each shooter has reserved, so two shooters do not climb the same hilltop. */
public final class PerchClaims {
  private final Map<MobId, Vec3> claims = new HashMap<>();

  public void claim(MobId shooter, Vec3 perch) {
    Objects.requireNonNull(shooter, "PerchClaims.shooter");
    Objects.requireNonNull(perch, "PerchClaims.perch");
    claims.put(shooter, perch);
  }

  public void release(MobId shooter) {
    Objects.requireNonNull(shooter, "PerchClaims.shooter");
    claims.remove(shooter);
  }

  /** The perches reserved by these shooters, leaving out the one asking. */
  public List<Vec3> takenBy(Set<MobId> shooters, MobId self) {
    Objects.requireNonNull(shooters, "PerchClaims.shooters");
    Objects.requireNonNull(self, "PerchClaims.self");
    return shooters.stream()
        .filter(shooter -> !shooter.equals(self))
        .map(claims::get)
        .filter(Objects::nonNull)
        .toList();
  }
}
