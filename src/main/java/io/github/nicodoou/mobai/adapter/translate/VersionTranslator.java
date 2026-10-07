package io.github.nicodoou.mobai.adapter.translate;

import io.github.nicodoou.mobai.domain.shared.EffectKind;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageModifier;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** The only class that reads Paper constants that change between Minecraft versions. */
public final class VersionTranslator {
  // No switch here: javac would put the EntityType table in a synthetic class outside this one.
  public Optional<MobKind> mobKindOf(EntityType type) {
    if (type == EntityType.ZOMBIE) {
      return Optional.of(MobKind.ZOMBIE);
    }
    if (type == EntityType.SKELETON) {
      return Optional.of(MobKind.SKELETON);
    }
    if (type == EntityType.SPIDER) {
      return Optional.of(MobKind.SPIDER);
    }
    return Optional.empty();
  }

  public EntityType entityTypeOf(MobKind kind) {
    return switch (kind) {
      case ZOMBIE -> EntityType.ZOMBIE;
      case SKELETON -> EntityType.SKELETON;
      case SPIDER -> EntityType.SPIDER;
    };
  }

  public PotionEffectType potionEffectOf(EffectKind kind) {
    return switch (kind) {
      case RESISTANCE -> PotionEffectType.RESISTANCE;
      case REGENERATION -> PotionEffectType.REGENERATION;
      case POISON -> PotionEffectType.POISON;
      case WITHER -> PotionEffectType.WITHER;
      case WEAKNESS -> PotionEffectType.WEAKNESS;
      case SLOWNESS -> PotionEffectType.SLOWNESS;
    };
  }

  public Map<EffectKind, Integer> effectLevels(LivingEntity entity) {
    Map<EffectKind, Integer> levels = new EnumMap<>(EffectKind.class);
    for (PotionEffect effect : entity.getActivePotionEffects()) {
      effectKindOf(effect.getType())
          .ifPresent(kind -> levels.merge(kind, effect.getAmplifier() + 1, Math::max));
    }
    return levels;
  }

  public double maxHealth(LivingEntity entity) {
    return attributeValue(entity, Attribute.MAX_HEALTH)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Entity " + entity.getUniqueId() + " has no max health attribute"));
  }

  public double armorPoints(LivingEntity entity) {
    return attributeValue(entity, Attribute.ARMOR).orElse(0);
  }

  public double armorToughness(LivingEntity entity) {
    return attributeValue(entity, Attribute.ARMOR_TOUGHNESS).orElse(0);
  }

  public int protectionFactor(LivingEntity entity) {
    EntityEquipment equipment = entity.getEquipment();
    if (equipment == null) {
      return 0;
    }
    int factor = 0;
    for (ItemStack piece : equipment.getArmorContents()) {
      if (piece != null) {
        factor += piece.getEnchantmentLevel(Enchantment.PROTECTION);
      }
    }
    return factor;
  }

  // Paper deprecated damage modifiers without a replacement; the spike showed they are the only
  // way to see damage eaten by absorption hearts.
  @SuppressWarnings("deprecation")
  public double absorbedDamage(EntityDamageEvent event) {
    return event.isApplicable(DamageModifier.ABSORPTION)
        ? -event.getDamage(DamageModifier.ABSORPTION)
        : 0;
  }

  // Same deprecated API: a blocked hit is not cancelled, only this modifier tells it apart.
  @SuppressWarnings("deprecation")
  public boolean wasBlocked(EntityDamageEvent event) {
    return event.isApplicable(DamageModifier.BLOCKING)
        && event.getDamage(DamageModifier.BLOCKING) != 0;
  }

  // Skeleton arrows cannot be picked up in vanilla either; launched arrows default to allowed.
  public void forbidPickup(AbstractArrow arrow) {
    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
  }

  private Optional<EffectKind> effectKindOf(PotionEffectType type) {
    for (EffectKind kind : EffectKind.values()) {
      if (potionEffectOf(kind).equals(type)) {
        return Optional.of(kind);
      }
    }
    return Optional.empty();
  }

  private static OptionalDouble attributeValue(LivingEntity entity, Attribute attribute) {
    AttributeInstance instance = entity.getAttribute(attribute);
    if (instance == null) {
      return OptionalDouble.empty();
    }
    return OptionalDouble.of(instance.getValue());
  }
}
