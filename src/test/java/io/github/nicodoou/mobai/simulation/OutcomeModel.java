package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** What happens when a mob attacks; empty means it waited and never attacked. */
final class OutcomeModel {
  private static final double SUM_TOLERANCE = 1e-9;
  private static final double TOTAL_PROBABILITY = 1.0;
  private static final List<Role> SPIDER_ROLES = List.of(Role.PRESS, Role.FLANK);

  private record OutcomeOdds(double hit, double partial, double miss, double noAttempt) {
    double sum() {
      return hit + partial + miss + noAttempt;
    }
  }

  private record OddsKey(PlayerArchetype archetype, MobKind kind, Role role, Attack attack) {}

  private final RandomSource world;
  private final Map<OddsKey, OutcomeOdds> odds = new HashMap<>();

  OutcomeModel(RandomSource world) {
    this.world = Objects.requireNonNull(world, "OutcomeModel.world");
    addBlockerOdds();
    addOpenOdds();
    requireEveryRowAddsUpToOne();
  }

  Optional<AttackOutcome> resolve(PlayerArchetype archetype, RoleAssignment order, MobKind kind) {
    Attack attack = order.suggestedAttack().orElseThrow();
    OddsKey key = new OddsKey(archetype, kind, order.role(), attack);
    OutcomeOdds row = odds.get(key);
    if (row == null) {
      throw new IllegalStateException("No odds for " + key);
    }
    return outcomeFor(row, world.nextUnit());
  }

  private static Optional<AttackOutcome> outcomeFor(OutcomeOdds row, double draw) {
    double hitLimit = row.hit();
    double partialLimit = hitLimit + row.partial();
    double missLimit = partialLimit + row.miss();
    if (draw < hitLimit) {
      return Optional.of(new AttackOutcome.Hit());
    }
    if (draw < partialLimit) {
      return Optional.of(new AttackOutcome.Partial());
    }
    if (draw < missLimit) {
      return Optional.of(new AttackOutcome.Miss());
    }
    return Optional.empty();
  }

  private void requireEveryRowAddsUpToOne() {
    odds.forEach(
        (key, row) -> {
          if (Math.abs(row.sum() - TOTAL_PROBABILITY) > SUM_TOLERANCE) {
            throw new IllegalStateException("Odds for " + key + " add up to " + row.sum());
          }
        });
  }

  private void put(OddsKey key, OutcomeOdds row) {
    odds.put(key, row);
  }

  private void addBlockerOdds() {
    PlayerArchetype who = PlayerArchetype.BLOCKER;
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_FRONT_STRIKE),
        new OutcomeOdds(0, 0.85, 0.15, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_FLANK_STRIKE),
        new OutcomeOdds(0.45, 0, 0.55, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_PATIENT_STRIKE),
        new OutcomeOdds(0.20, 0, 0, 0.80));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_EVASIVE_STRIKE),
        new OutcomeOdds(0.05, 0.60, 0.15, 0.20));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.FLANK, Attack.ZOMBIE_FLANK_STRIKE),
        new OutcomeOdds(0.70, 0, 0.30, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_DIRECT_SHOT),
        new OutcomeOdds(0.15, 0.60, 0.25, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_LEAD_SHOT),
        new OutcomeOdds(0.20, 0.55, 0.25, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_OPPORTUNISTIC_SHOT),
        new OutcomeOdds(0.40, 0, 0.20, 0.40));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_DIRECT_SHOT),
        new OutcomeOdds(0.25, 0.50, 0.25, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_LEAD_SHOT),
        new OutcomeOdds(0.30, 0.45, 0.25, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_OPPORTUNISTIC_SHOT),
        new OutcomeOdds(0.30, 0.45, 0.25, 0));
    put(
        new OddsKey(who, MobKind.SPIDER, Role.PRESS, Attack.SPIDER_BITE),
        new OutcomeOdds(0, 0.70, 0.30, 0));
    put(
        new OddsKey(who, MobKind.SPIDER, Role.FLANK, Attack.SPIDER_BITE),
        new OutcomeOdds(0.60, 0, 0.40, 0));
  }

  private void addOpenOdds() {
    PlayerArchetype who = PlayerArchetype.OPEN;
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_FRONT_STRIKE),
        new OutcomeOdds(0.70, 0, 0.30, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_FLANK_STRIKE),
        new OutcomeOdds(0.50, 0, 0.50, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_PATIENT_STRIKE),
        new OutcomeOdds(0.55, 0, 0.45, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.PRESS, Attack.ZOMBIE_EVASIVE_STRIKE),
        new OutcomeOdds(0.65, 0, 0.35, 0));
    put(
        new OddsKey(who, MobKind.ZOMBIE, Role.FLANK, Attack.ZOMBIE_FLANK_STRIKE),
        new OutcomeOdds(0.65, 0, 0.35, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_DIRECT_SHOT),
        new OutcomeOdds(0.35, 0, 0.65, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_LEAD_SHOT),
        new OutcomeOdds(0.60, 0, 0.40, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.SHOOT, Attack.SKELETON_OPPORTUNISTIC_SHOT),
        new OutcomeOdds(0.45, 0, 0.25, 0.30));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_DIRECT_SHOT),
        new OutcomeOdds(0.45, 0, 0.55, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_LEAD_SHOT),
        new OutcomeOdds(0.65, 0, 0.35, 0));
    put(
        new OddsKey(who, MobKind.SKELETON, Role.VOLLEY, Attack.SKELETON_OPPORTUNISTIC_SHOT),
        new OutcomeOdds(0.65, 0, 0.35, 0));
    for (Role role : SPIDER_ROLES) {
      put(
          new OddsKey(who, MobKind.SPIDER, role, Attack.SPIDER_BITE),
          new OutcomeOdds(0.60, 0, 0.40, 0));
    }
  }
}
