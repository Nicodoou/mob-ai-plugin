package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.UUID;

public final class MobSnapshotBuilder {
  private static final double FULL_HEALTH = 20.0;

  private MobId id = new MobId(new UUID(0, 1));
  private MobKind kind = MobKind.ZOMBIE;
  private Vec3 position = new Vec3(0, 64, 5);
  private double health = FULL_HEALTH;
  private double maxHealth = FULL_HEALTH;

  public MobSnapshotBuilder withId(MobId id) {
    this.id = id;
    return this;
  }

  public MobSnapshotBuilder withKind(MobKind kind) {
    this.kind = kind;
    return this;
  }

  public MobSnapshotBuilder withPosition(Vec3 position) {
    this.position = position;
    return this;
  }

  public MobSnapshotBuilder withHealth(double health) {
    this.health = health;
    return this;
  }

  public MobSnapshotBuilder withMaxHealth(double maxHealth) {
    this.maxHealth = maxHealth;
    return this;
  }

  public MobSnapshot build() {
    return new MobSnapshot(id, kind, position, health, maxHealth);
  }
}
