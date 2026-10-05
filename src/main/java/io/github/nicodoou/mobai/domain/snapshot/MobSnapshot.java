package io.github.nicodoou.mobai.domain.snapshot;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;

public record MobSnapshot(MobId id, MobKind kind, Vec3 position, double health, double maxHealth) {
  public MobSnapshot {
    Objects.requireNonNull(id, "MobSnapshot.id");
    Objects.requireNonNull(kind, "MobSnapshot.kind");
    Objects.requireNonNull(position, "MobSnapshot.position");
    SnapshotChecks.requirePositive("MobSnapshot.maxHealth", maxHealth);
    SnapshotChecks.requireBetween("MobSnapshot.health", health, maxHealth);
  }
}
