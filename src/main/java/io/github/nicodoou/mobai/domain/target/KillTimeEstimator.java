package io.github.nicodoou.mobai.domain.target;

import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.ARMOR_MAX_POINTS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.ARMOR_MIN_FRACTION;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.ARMOR_TOUGHNESS_BASE;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.ARMOR_TOUGHNESS_DIVISOR;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.DAMAGE_REDUCTION_SCALE;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.MELEE_REACH_BLOCKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.POISON_BASE_INTERVAL_TICKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.PROTECTION_MAX_FACTOR;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.REGENERATION_BASE_INTERVAL_TICKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.RESISTANCE_REDUCTION_PER_LEVEL;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.SKELETON_ARROW_DAMAGE;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.SKELETON_ATTACK_INTERVAL_TICKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.SLOWNESS_SPEED_REDUCTION_PER_LEVEL;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.SPIDER_HIT_DAMAGE;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.TICKS_PER_SECOND;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.WITHER_BASE_INTERVAL_TICKS;
import static io.github.nicodoou.mobai.domain.shared.MinecraftConstants.ZOMBIE_HIT_DAMAGE;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.PlayerSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public final class KillTimeEstimator {
  // Java masks int shifts to five bits; capping keeps huge effect levels at a one-tick interval.
  private static final int MAX_INTERVAL_SHIFT = 30;

  private final Supplier<TargetSettings> targetSettings;
  private final Supplier<AttackSettings> attackSettings;

  public KillTimeEstimator(
      Supplier<TargetSettings> targetSettings, Supplier<AttackSettings> attackSettings) {
    this.targetSettings =
        Objects.requireNonNull(targetSettings, "KillTimeEstimator.targetSettings");
    this.attackSettings =
        Objects.requireNonNull(attackSettings, "KillTimeEstimator.attackSettings");
  }

  public KillTimeEstimate estimate(
      PlayerSnapshot player, GroupSnapshot snapshot, GroupMemory memory) {
    double effectiveHealth = player.health() + player.absorption();
    double damagePerSecond =
        groupDamagePerSecond(player, snapshot, memory) + effectDamagePerSecond(player);
    double arrivalSeconds = arrivalSeconds(player, snapshot.mobs());
    double killTimeSeconds = killTime(effectiveHealth, damagePerSecond, arrivalSeconds);
    return new KillTimeEstimate(effectiveHealth, damagePerSecond, arrivalSeconds, killTimeSeconds);
  }

  private double groupDamagePerSecond(
      PlayerSnapshot player, GroupSnapshot snapshot, GroupMemory memory) {
    return snapshot.mobs().stream()
        .mapToDouble(
            mob ->
                mobDamagePerSecond(
                    mob,
                    player,
                    memory.kindEstimate(player.id(), mob.kind(), snapshot.tick()).mean()))
        .sum();
  }

  private double mobDamagePerSecond(MobSnapshot mob, PlayerSnapshot player, double successRate) {
    return successRate * reducedHit(hitDamage(mob.kind()), player) * hitsPerSecond(mob.kind());
  }

  private static double hitDamage(MobKind kind) {
    return switch (kind) {
      case ZOMBIE -> ZOMBIE_HIT_DAMAGE;
      case SKELETON -> SKELETON_ARROW_DAMAGE;
      case SPIDER -> SPIDER_HIT_DAMAGE;
    };
  }

  private static double hitsPerSecond(MobKind kind) {
    return (double) TICKS_PER_SECOND / attackIntervalTicks(kind);
  }

  private static int attackIntervalTicks(MobKind kind) {
    return switch (kind) {
      case SKELETON -> SKELETON_ATTACK_INTERVAL_TICKS;
      case ZOMBIE, SPIDER -> MELEE_ATTACK_INTERVAL_TICKS;
    };
  }

  private static double reducedHit(double damage, PlayerSnapshot player) {
    return damage
        * armorFactor(damage, player)
        * protectionFactor(player)
        * resistanceFactor(player);
  }

  private static double armorFactor(double damage, PlayerSnapshot player) {
    double toughnessDivisor =
        ARMOR_TOUGHNESS_BASE + player.armorToughness() / ARMOR_TOUGHNESS_DIVISOR;
    double effectiveArmor =
        Math.clamp(
            player.armorPoints() - damage / toughnessDivisor,
            player.armorPoints() * ARMOR_MIN_FRACTION,
            ARMOR_MAX_POINTS);
    return 1 - effectiveArmor / DAMAGE_REDUCTION_SCALE;
  }

  private static double protectionFactor(PlayerSnapshot player) {
    return 1 - Math.min(player.protectionFactor(), PROTECTION_MAX_FACTOR) / DAMAGE_REDUCTION_SCALE;
  }

  private static double resistanceFactor(PlayerSnapshot player) {
    return Math.max(
        0, 1 - RESISTANCE_REDUCTION_PER_LEVEL * player.effectLevel(EffectKind.RESISTANCE));
  }

  private static double effectDamagePerSecond(PlayerSnapshot player) {
    return pointsPerSecond(POISON_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.POISON))
        + pointsPerSecond(WITHER_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.WITHER))
        - pointsPerSecond(
            REGENERATION_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.REGENERATION));
  }

  private static double pointsPerSecond(int baseIntervalTicks, int level) {
    if (level == 0) {
      return 0;
    }
    int interval = Math.max(1, baseIntervalTicks >> Math.min(level - 1, MAX_INTERVAL_SHIFT));
    return (double) TICKS_PER_SECOND / interval;
  }

  private double arrivalSeconds(PlayerSnapshot player, List<MobSnapshot> mobs) {
    if (mobs.isEmpty()) {
      return 0;
    }
    return mobs.stream().mapToDouble(mob -> mobArrivalSeconds(mob, player)).average().orElse(0)
        * slownessFactor(player);
  }

  private double mobArrivalSeconds(MobSnapshot mob, PlayerSnapshot player) {
    return Math.max(
            0, mob.position().distanceTo(player.pose().position()) - engageDistance(mob.kind()))
        / targetSettings.get().approachSpeedBlocksPerSecond();
  }

  private double engageDistance(MobKind kind) {
    return switch (kind) {
      case SKELETON -> attackSettings.get().shootMaxDistanceBlocks();
      case ZOMBIE, SPIDER -> MELEE_REACH_BLOCKS;
    };
  }

  private static double slownessFactor(PlayerSnapshot player) {
    return Math.max(
        0, 1 - SLOWNESS_SPEED_REDUCTION_PER_LEVEL * player.effectLevel(EffectKind.SLOWNESS));
  }

  private static double killTime(
      double effectiveHealth, double damagePerSecond, double arrivalSeconds) {
    if (damagePerSecond <= 0) {
      return Double.POSITIVE_INFINITY;
    }
    return arrivalSeconds + effectiveHealth / damagePerSecond;
  }
}
