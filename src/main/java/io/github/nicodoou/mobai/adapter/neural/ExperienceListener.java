package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.application.ActiveGroups;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Tells the recorder what happens in a fight. It must be registered before DeathListener: a dying
 * mob has to be found in its group, and a dying target in its plan.
 */
public final class ExperienceListener implements Listener {
  private static final double NO_AMOUNT = 0;
  private static final int LAST_MEMBER = 1;

  private final ExperienceRecorder recorder;
  private final ActiveGroups activeGroups;
  private final ServerClock clock;

  public ExperienceListener(
      ExperienceRecorder recorder, ActiveGroups activeGroups, ServerClock clock) {
    this.recorder = Objects.requireNonNull(recorder, "ExperienceListener.recorder");
    this.activeGroups = Objects.requireNonNull(activeGroups, "ExperienceListener.activeGroups");
    this.clock = Objects.requireNonNull(clock, "ExperienceListener.clock");
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDamage(EntityDamageEvent event) {
    double amount = event.getFinalDamage();
    if (amount <= 0) {
      return;
    }
    Optional<Mob> attacker = attackingMob(event);
    if (event.getEntity() instanceof Player) {
      attacker.ifPresent(mob -> recordOfMob(mob, ExperienceEvent.Kind.DAMAGE_DEALT, amount));
      return;
    }
    if (event.getEntity() instanceof Mob victim) {
      recordOfMob(victim, ExperienceEvent.Kind.DAMAGE_TAKEN, amount);
      attacker
          .filter(mob -> isAlly(mob, victim))
          .ifPresent(mob -> recordOfMob(victim, ExperienceEvent.Kind.BOT_HIT_BY_ALLY, amount));
    }
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    LivingEntity victim = event.getEntity();
    if (victim instanceof Player player) {
      targetDied(new PlayerId(player.getUniqueId()), lastAttackerOf(player));
      return;
    }
    groupOf(victim).ifPresent(group -> memberDied(group, victim));
  }

  void targetDied(PlayerId player, Optional<MobId> killer) {
    long tick = clock.currentTick();
    for (Group group : activeGroups.groups()) {
      if (targetOf(group).filter(player::equals).isPresent()) {
        recorder.endFight(group.id(), tick, winnerOver(group, killer));
      }
    }
  }

  // A lava pool or a stray creeper is no victory for the network: the mobs did not earn it.
  private static FightWinner winnerOver(Group group, Optional<MobId> killer) {
    boolean isKilledByGroup = killer.flatMap(group.roster()::member).isPresent();
    return isKilledByGroup ? FightWinner.MOBS : FightWinner.NONE;
  }

  private static Optional<MobId> lastAttackerOf(Player player) {
    return Optional.ofNullable(player.getLastDamageCause())
        .flatMap(ExperienceListener::attackingMob)
        .map(mob -> new MobId(mob.getUniqueId()));
  }

  private void memberDied(Group group, LivingEntity victim) {
    long tick = clock.currentTick();
    GroupId id = group.id();
    Optional<MobId> mob = Optional.of(new MobId(victim.getUniqueId()));
    recorder.record(new ExperienceEvent(tick, id, mob, ExperienceEvent.Kind.MOB_DIED, NO_AMOUNT));
    if (group.roster().members().size() == LAST_MEMBER) {
      recorder.endFight(id, tick, FightWinner.PLAYER);
    }
  }

  // Between plans the group still fights the target it committed to.
  private static Optional<PlayerId> targetOf(Group group) {
    return group.lifecycle().plan().map(Plan::target).or(group.lifecycle()::committedTarget);
  }

  private void recordOfMob(Mob mob, ExperienceEvent.Kind kind, double amount) {
    groupOf(mob)
        .ifPresent(
            group ->
                recorder.record(
                    new ExperienceEvent(
                        clock.currentTick(),
                        group.id(),
                        Optional.of(new MobId(mob.getUniqueId())),
                        kind,
                        amount)));
  }

  private boolean isAlly(Mob attacker, Mob victim) {
    return !attacker.getUniqueId().equals(victim.getUniqueId())
        && groupOf(attacker).map(Group::id).equals(groupOf(victim).map(Group::id));
  }

  private Optional<Group> groupOf(Entity entity) {
    return activeGroups.groupOf(new MobId(entity.getUniqueId()));
  }

  private static Optional<Mob> attackingMob(EntityDamageEvent event) {
    if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
      return Optional.empty();
    }
    Entity damager = byEntity.getDamager();
    if (damager instanceof Projectile projectile
        && projectile.getShooter() instanceof Mob shooter) {
      return Optional.of(shooter);
    }
    return damager instanceof Mob mob ? Optional.of(mob) : Optional.empty();
  }
}
