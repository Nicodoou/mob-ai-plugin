package io.github.nicodoou.mobai.domain.shared;

public final class MinecraftConstants {
  public static final int TICKS_PER_SECOND = 20;

  public static final int SHIELD_WARMUP_TICKS = 5;

  public static final double SHIELD_HALF_ARC_DEGREES = 90.0;

  public static final double ARROW_SPEED_BLOCKS_PER_TICK = 1.6;

  // Vanilla skeletons raise their aim by this fraction of the horizontal distance to make up for
  // the arrow's drop.
  public static final double ARROW_ARC_FACTOR = 0.2;

  // Damage per hit on normal difficulty. The zombie value was measured in the spike; spider and
  // arrow are vanilla values (an arrow deals 1.6 speed times 2.0 base damage, rounded up).
  public static final double ZOMBIE_HIT_DAMAGE = 3.0;
  public static final double SPIDER_HIT_DAMAGE = 2.0;
  public static final double SKELETON_ARROW_DAMAGE = 4.0;

  // Our goals attack at these vanilla rates; the kill time estimate assumes the same rates.
  public static final int MELEE_ATTACK_INTERVAL_TICKS = 20;
  public static final int SKELETON_ATTACK_INTERVAL_TICKS = 40;
  public static final double MELEE_REACH_BLOCKS = 2.0;

  // Vanilla damage reduction: armor, armor toughness, Protection and Resistance.
  public static final double ARMOR_TOUGHNESS_BASE = 2.0;
  public static final double ARMOR_TOUGHNESS_DIVISOR = 4.0;
  public static final double ARMOR_MIN_FRACTION = 0.2;
  public static final double ARMOR_MAX_POINTS = 20.0;
  public static final int PROTECTION_MAX_FACTOR = 20;
  public static final double DAMAGE_REDUCTION_SCALE = 25.0;
  public static final double RESISTANCE_REDUCTION_PER_LEVEL = 0.2;

  // Vanilla periodic effects heal or hurt one point every (base >> (level - 1)) ticks.
  public static final int REGENERATION_BASE_INTERVAL_TICKS = 50;
  public static final int POISON_BASE_INTERVAL_TICKS = 25;
  public static final int WITHER_BASE_INTERVAL_TICKS = 40;
  public static final double SLOWNESS_SPEED_REDUCTION_PER_LEVEL = 0.15;

  private MinecraftConstants() {}
}
