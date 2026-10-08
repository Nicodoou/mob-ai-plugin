package io.github.nicodoou.mobai.domain.snapshot;

import io.github.nicodoou.mobai.domain.geometry.PlayerPose;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * @param holdingRanged either hand holds a bow or a crossbow
 */
public record PlayerSnapshot(
    PlayerId id,
    PlayerPose pose,
    Vec3 movementPerTick,
    double health,
    double absorption,
    double maxHealth,
    double armorPoints,
    double armorToughness,
    int protectionFactor,
    Map<EffectKind, Integer> effectLevels,
    boolean blocking,
    boolean holdingRanged) {

  public PlayerSnapshot {
    Objects.requireNonNull(id, "PlayerSnapshot.id");
    Objects.requireNonNull(pose, "PlayerSnapshot.pose");
    Objects.requireNonNull(movementPerTick, "PlayerSnapshot.movementPerTick");
    Objects.requireNonNull(effectLevels, "PlayerSnapshot.effectLevels");
    SnapshotChecks.requirePositive("PlayerSnapshot.maxHealth", maxHealth);
    SnapshotChecks.requireBetween("PlayerSnapshot.health", health, maxHealth);
    SnapshotChecks.requireNonNegative("PlayerSnapshot.absorption", absorption);
    SnapshotChecks.requireNonNegative("PlayerSnapshot.armorPoints", armorPoints);
    SnapshotChecks.requireNonNegative("PlayerSnapshot.armorToughness", armorToughness);
    SnapshotChecks.requireAtLeast("PlayerSnapshot.protectionFactor", protectionFactor, 0);
    requireValidLevels(effectLevels);
    effectLevels = copyEffectLevels(effectLevels);
  }

  public int effectLevel(EffectKind kind) {
    return effectLevels.getOrDefault(kind, 0);
  }

  private static void requireValidLevels(Map<EffectKind, Integer> effectLevels) {
    for (Map.Entry<EffectKind, Integer> entry : effectLevels.entrySet()) {
      String field = "PlayerSnapshot.effectLevels." + entry.getKey();
      Integer level = Objects.requireNonNull(entry.getValue(), field);
      SnapshotChecks.requireAtLeast(field, level, 1);
    }
  }

  private static Map<EffectKind, Integer> copyEffectLevels(Map<EffectKind, Integer> effectLevels) {
    EnumMap<EffectKind, Integer> copy = new EnumMap<>(EffectKind.class);
    copy.putAll(effectLevels);
    return Collections.unmodifiableMap(copy);
  }
}
