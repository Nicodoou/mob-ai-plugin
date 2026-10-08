package io.github.nicodoou.mobai.testsupport;

import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.EnumMap;
import java.util.UUID;

public final class PlayerSnapshotBuilder {
  private static final double FULL_HEALTH = 20.0;
  private static final double FULL_DIAMOND_ARMOR_POINTS = 20.0;
  private static final double FULL_DIAMOND_ARMOR_TOUGHNESS = 8.0;
  private static final int FULL_PROTECTION_FOUR_FACTOR = 16;

  private PlayerId id = new PlayerId(new UUID(0, 2));
  private Vec3 position = new Vec3(0, 64, 0);
  private Vec3 facing = new Vec3(0, 0, 1);
  private Vec3 movementPerTick = Vec3.ZERO;
  private double health = FULL_HEALTH;
  private double maxHealth = FULL_HEALTH;
  private double absorption = 0.0;
  private double armorPoints = 0.0;
  private double armorToughness = 0.0;
  private int protectionFactor = 0;
  private final EnumMap<EffectKind, Integer> effectLevels = new EnumMap<>(EffectKind.class);
  private boolean blocking = false;
  private boolean holdingRanged = false;

  public PlayerSnapshotBuilder withId(PlayerId id) {
    this.id = id;
    return this;
  }

  public PlayerSnapshotBuilder withPosition(Vec3 position) {
    this.position = position;
    return this;
  }

  public PlayerSnapshotBuilder withFacing(Vec3 facing) {
    this.facing = facing;
    return this;
  }

  public PlayerSnapshotBuilder withMovementPerTick(Vec3 movementPerTick) {
    this.movementPerTick = movementPerTick;
    return this;
  }

  public PlayerSnapshotBuilder withHealth(double health) {
    this.health = health;
    return this;
  }

  public PlayerSnapshotBuilder withMaxHealth(double maxHealth) {
    this.maxHealth = maxHealth;
    return this;
  }

  public PlayerSnapshotBuilder withAbsorption(double absorption) {
    this.absorption = absorption;
    return this;
  }

  public PlayerSnapshotBuilder withArmorPoints(double armorPoints) {
    this.armorPoints = armorPoints;
    return this;
  }

  public PlayerSnapshotBuilder withArmorToughness(double armorToughness) {
    this.armorToughness = armorToughness;
    return this;
  }

  public PlayerSnapshotBuilder withProtectionFactor(int protectionFactor) {
    this.protectionFactor = protectionFactor;
    return this;
  }

  public PlayerSnapshotBuilder withEffect(EffectKind kind, int level) {
    effectLevels.put(kind, level);
    return this;
  }

  public PlayerSnapshotBuilder withBlocking(boolean blocking) {
    this.blocking = blocking;
    return this;
  }

  public PlayerSnapshotBuilder withHoldingRanged(boolean holdingRanged) {
    this.holdingRanged = holdingRanged;
    return this;
  }

  public PlayerSnapshotBuilder fullDiamondProtectionFour() {
    this.armorPoints = FULL_DIAMOND_ARMOR_POINTS;
    this.armorToughness = FULL_DIAMOND_ARMOR_TOUGHNESS;
    this.protectionFactor = FULL_PROTECTION_FOUR_FACTOR;
    return this;
  }

  public PlayerSnapshot build() {
    return new PlayerSnapshot(
        id,
        new PlayerPose(position, facing),
        movementPerTick,
        health,
        absorption,
        maxHealth,
        armorPoints,
        armorToughness,
        protectionFactor,
        effectLevels,
        blocking,
        holdingRanged);
  }
}
