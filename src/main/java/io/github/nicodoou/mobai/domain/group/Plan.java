package io.github.nicodoou.mobai.domain.group;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record Plan(
    PlanId id,
    StrategyId strategy,
    PlayerId target,
    Map<MobId, Role> roles,
    double targetMaxHealth,
    long startTick,
    long lastTargetSeenTick,
    double damageDealt,
    Map<MobId, Role> startingRoles) {

  public Plan {
    Objects.requireNonNull(id, "Plan.id");
    Objects.requireNonNull(strategy, "Plan.strategy");
    Objects.requireNonNull(target, "Plan.target");
    Objects.requireNonNull(roles, "Plan.roles");
    roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
    Objects.requireNonNull(startingRoles, "Plan.startingRoles");
    startingRoles = Collections.unmodifiableMap(new LinkedHashMap<>(startingRoles));
  }

  public static Plan start(PlanId id, PlanStart start) {
    return new Plan(
        id,
        start.strategy(),
        start.target(),
        start.roles(),
        start.targetMaxHealth(),
        start.tick(),
        start.tick(),
        0,
        start.roles());
  }

  public Plan withDamageDealt(double damage) {
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException("Plan.damage must be a positive number, got " + damage);
    }
    return copyWithDamage(damageDealt + damage);
  }

  public Plan withTargetSeenAt(long tick) {
    if (tick < lastTargetSeenTick) {
      throw new IllegalArgumentException(
          "Plan.lastTargetSeenTick must not go back, got " + tick + " after " + lastTargetSeenTick);
    }
    return copyWithLastSeen(tick);
  }

  public Plan withRole(MobId mob, Role role) {
    Objects.requireNonNull(mob, "Plan.mob");
    Objects.requireNonNull(role, "Plan.role");
    Map<MobId, Role> newRoles = new LinkedHashMap<>(roles);
    newRoles.put(mob, role);
    return copyWithRoles(newRoles);
  }

  public Plan withoutMember(MobId mob) {
    if (!roles.containsKey(mob)) {
      return this;
    }
    Map<MobId, Role> newRoles = new LinkedHashMap<>(roles);
    newRoles.remove(mob);
    return copyWithRoles(newRoles);
  }

  public Optional<Role> roleOf(MobId mob) {
    return Optional.ofNullable(roles.get(mob));
  }

  public int startingMembers() {
    return startingRoles.size();
  }

  public Optional<Role> startingRoleOf(MobId mob) {
    return Optional.ofNullable(startingRoles.get(mob));
  }

  public long ageTicks(long tick) {
    return tick - startTick;
  }

  public long ticksSinceTargetSeen(long tick) {
    return tick - lastTargetSeenTick;
  }

  public double successFraction(double fullSuccessDamageFraction) {
    return Math.min(1, damageDealt / (fullSuccessDamageFraction * targetMaxHealth));
  }

  private Plan copyWithRoles(Map<MobId, Role> newRoles) {
    return new Plan(
        id,
        strategy,
        target,
        newRoles,
        targetMaxHealth,
        startTick,
        lastTargetSeenTick,
        damageDealt,
        startingRoles);
  }

  private Plan copyWithDamage(double newDamage) {
    return new Plan(
        id,
        strategy,
        target,
        roles,
        targetMaxHealth,
        startTick,
        lastTargetSeenTick,
        newDamage,
        startingRoles);
  }

  private Plan copyWithLastSeen(long newTick) {
    return new Plan(
        id,
        strategy,
        target,
        roles,
        targetMaxHealth,
        startTick,
        newTick,
        damageDealt,
        startingRoles);
  }
}
