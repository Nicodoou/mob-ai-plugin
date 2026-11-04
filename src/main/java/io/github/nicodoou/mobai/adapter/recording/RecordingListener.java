package io.github.nicodoou.mobai.adapter.recording;

import io.github.nicodoou.mobai.adapter.debug.CombatSample;
import io.github.nicodoou.mobai.adapter.debug.TraceWriter;
import io.github.nicodoou.mobai.adapter.translate.VersionTranslator;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;

/** Writes what happens to the recorded entities, when it happens (CT-36). */
public final class RecordingListener implements Listener {
  private static final String UNKNOWN_ITEM = "unknown";

  private final RecordingParts parts;

  public RecordingListener(RecordingParts parts) {
    this.parts = Objects.requireNonNull(parts, "RecordingListener.parts");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageEvent event) {
    if (!parts.scope().isActive()) {
      return;
    }
    if (event.getEntity() instanceof LivingEntity victim) {
      recordedVictim(victim)
          .ifPresent(group -> parts.writer().write(damageSample(event, victim, group)));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    if (!parts.scope().isActive()) {
      return;
    }
    LivingEntity victim = event.getEntity();
    recordedVictim(victim).ifPresent(group -> parts.writer().write(deathSample(victim, group)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSwing(PlayerArmSwingEvent event) {
    if (!parts.scope().isActive()) {
      return;
    }
    Player player = event.getPlayer();
    ItemStack held = player.getInventory().getItemInMainHand();
    recordAction(
        player,
        CombatSample.PlayerAction.SWING,
        new ActionDetail(Optional.of(held), Optional.empty()));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onShoot(EntityShootBowEvent event) {
    if (!parts.scope().isActive()) {
      return;
    }
    if (event.getEntity() instanceof Player player) {
      Optional<Double> force = Optional.of((double) event.getForce());
      recordAction(
          player,
          CombatSample.PlayerAction.SHOOT,
          new ActionDetail(Optional.ofNullable(event.getBow()), force));
    }
  }

  private void recordAction(Player player, CombatSample.PlayerAction action, ActionDetail detail) {
    PlayerId id = new PlayerId(player.getUniqueId());
    if (!parts.scope().recordsPlayer(id)) {
      return;
    }
    parts
        .writer()
        .write(
            new CombatSample.ActionSample(
                parts.clock().currentTick(), id, action, itemName(detail.item()), detail.force()));
  }

  private String itemName(Optional<ItemStack> item) {
    return item.map(parts.translator()::itemName).orElse(UNKNOWN_ITEM);
  }

  // The outer optional says whether the victim is recorded; the inner one is its group, if any.
  private Optional<Optional<GroupId>> recordedVictim(Entity entity) {
    if (entity instanceof Player player) {
      boolean recorded = parts.scope().recordsPlayer(new PlayerId(player.getUniqueId()));
      return recorded ? Optional.of(Optional.empty()) : Optional.empty();
    }
    return parts.scope().recordedGroupOf(new MobId(entity.getUniqueId())).map(Optional::of);
  }

  private CombatSample.DamageSample damageSample(
      EntityDamageEvent event, LivingEntity victim, Optional<GroupId> group) {
    Attacker attacker = attackerOfDamage(event);
    return new CombatSample.DamageSample(
        parts.clock().currentTick(),
        group,
        victim.getUniqueId(),
        parts.translator().entityName(victim),
        event.getCause().name(),
        event.getDamage(),
        event.getFinalDamage(),
        victim.getHealth(),
        parts.translator().wasBlocked(event),
        event.isCancelled(),
        attacker.id(),
        attacker.type(),
        attacker.projectile());
  }

  private CombatSample.DeathSample deathSample(LivingEntity victim, Optional<GroupId> group) {
    EntityDamageEvent lastDamage = victim.getLastDamageCause();
    Attacker attacker = attackerOfDamage(lastDamage);
    return new CombatSample.DeathSample(
        parts.clock().currentTick(),
        group,
        victim.getUniqueId(),
        parts.translator().entityName(victim),
        Optional.ofNullable(lastDamage).map(damage -> damage.getCause().name()),
        attacker.id(),
        attacker.type());
  }

  private Attacker attackerOfDamage(EntityDamageEvent damage) {
    if (damage instanceof EntityDamageByEntityEvent byEntity) {
      return attackerOf(byEntity.getDamager());
    }
    return Attacker.NONE;
  }

  private Attacker attackerOf(Entity damager) {
    if (damager instanceof Projectile projectile) {
      Entity author = projectile.getShooter() instanceof Entity shooter ? shooter : damager;
      return attackerNamed(author, Optional.of(parts.translator().entityName(damager)));
    }
    return attackerNamed(damager, Optional.empty());
  }

  private Attacker attackerNamed(Entity author, Optional<String> projectile) {
    return new Attacker(
        Optional.of(author.getUniqueId()),
        Optional.of(parts.translator().entityName(author)),
        projectile);
  }

  private record Attacker(Optional<UUID> id, Optional<String> type, Optional<String> projectile) {
    static final Attacker NONE = new Attacker(Optional.empty(), Optional.empty(), Optional.empty());
  }

  private record ActionDetail(Optional<ItemStack> item, Optional<Double> force) {}

  public record RecordingParts(
      RecordingScope scope, TraceWriter writer, VersionTranslator translator, ServerClock clock) {
    public RecordingParts {
      Objects.requireNonNull(scope, "RecordingParts.scope");
      Objects.requireNonNull(writer, "RecordingParts.writer");
      Objects.requireNonNull(translator, "RecordingParts.translator");
      Objects.requireNonNull(clock, "RecordingParts.clock");
    }
  }
}
