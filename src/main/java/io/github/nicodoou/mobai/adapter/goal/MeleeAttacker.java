package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.adapter.tracker.AttackTracker;
import io.github.nicodoou.mobai.adapter.tracker.MeleeOpening;
import io.github.nicodoou.mobai.adapter.tracker.TargetChecks;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.port.ServerClock;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/** The only place that lands melee hits; every hit goes through the attack tracker. */
public final class MeleeAttacker {
  private final AttackTracker tracker;
  private final ServerClock clock;
  private final StrikeFollowUps followUps;

  public MeleeAttacker(AttackTracker tracker, ServerClock clock, StrikeFollowUps followUps) {
    this.tracker = Objects.requireNonNull(tracker, "MeleeAttacker.tracker");
    this.clock = Objects.requireNonNull(clock, "MeleeAttacker.clock");
    this.followUps = Objects.requireNonNull(followUps, "MeleeAttacker.followUps");
  }

  public Optional<Classification> strike(Mob mob, Player target, Attack attack) {
    MobId mobId = new MobId(mob.getUniqueId());
    long tick = clock.currentTick();
    tracker.openMelee(
        new MeleeOpening(
            mobId,
            new PlayerId(target.getUniqueId()),
            attack,
            tick,
            TargetChecks.isInvulnerable(target)));
    attackOrCancel(mob, target, mobId);
    Optional<Classification> classification =
        tracker.closeMelee(mobId, TargetChecks.isValidTarget(target, mob), tick);
    classification.ifPresent(found -> followUps.hub().attacked(mobId, tick, found));
    classification.ifPresent(found -> followUps.effects().apply(target, attack, found.outcome()));
    return classification;
  }

  // The damage event arrives inside attack(); if attack() fails, no attempt may stay open.
  private void attackOrCancel(Mob mob, Player target, MobId mobId) {
    try {
      mob.attack(target);
    } catch (RuntimeException exception) {
      tracker.cancel(mobId);
      throw exception;
    }
  }
}
