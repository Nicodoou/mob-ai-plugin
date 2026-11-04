package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.AttackTurn;
import io.github.nicodoou.mobai.domain.group.AttackTurns;
import io.github.nicodoou.mobai.domain.group.Plan;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.settings.PackSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Deals the group's attack turns among its melee fighters. A turn ends when its holder strikes (see
 * {@code PlanLifecycle.recordMeleeStrike}), takes a hit or runs out of time; the closest fighter
 * that did not just have one takes it next, so the pressure never stops and never piles up.
 */
public final class AttackTurnRota {
  private final Supplier<PackSettings> settings;

  public AttackTurnRota(Supplier<PackSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "AttackTurnRota.settings");
  }

  /**
   * The turns after this decision and the same orders, in the same order, each marked with its
   * turn. Runs last, so that only fighters still ordered to attack take part.
   */
  public DealtTurns deal(List<RoleAssignment> orders, GroupSnapshot snapshot, Plan plan) {
    Objects.requireNonNull(orders, "AttackTurnRota.orders");
    Objects.requireNonNull(snapshot, "AttackTurnRota.snapshot");
    Objects.requireNonNull(plan, "AttackTurnRota.plan");
    Optional<Vec3> targetPosition =
        snapshot.player(plan.target()).map(player -> player.pose().position());
    if (targetPosition.isEmpty()) {
      return new DealtTurns(plan.attackTurns(), orders);
    }
    Table table = tableOf(orders, snapshot, new TurnTarget(plan.target(), targetPosition.get()));
    AttackTurns turns = grantFreeTurns(releaseSpentTurns(plan.attackTurns(), table), table);
    return new DealtTurns(turns, marked(orders, turns, plan.target()));
  }

  private static Table tableOf(
      List<RoleAssignment> orders, GroupSnapshot snapshot, TurnTarget target) {
    List<Candidate> candidates =
        orders.stream()
            .filter(order -> PackCohesion.isInPack(order, target.id()))
            .map(order -> candidateOf(order, snapshot, target.position()))
            .toList();
    return new Table(snapshot.tick(), candidates);
  }

  private static Candidate candidateOf(
      RoleAssignment order, GroupSnapshot snapshot, Vec3 targetPosition) {
    MobSnapshot mob =
        snapshot
            .mob(order.mob())
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "AttackTurnRota: order for mob "
                            + order.mob().value()
                            + " has no snapshot"));
    double distance = mob.position().minus(targetPosition).horizontal().length();
    return new Candidate(mob.id(), distance, mob.health());
  }

  private AttackTurns releaseSpentTurns(AttackTurns turns, Table table) {
    AttackTurns kept = turns;
    for (Map.Entry<MobId, AttackTurn> holder : turns.holders().entrySet()) {
      if (isSpent(holder.getKey(), holder.getValue(), table)) {
        kept = kept.released(holder.getKey(), table.tick());
      }
    }
    return kept;
  }

  private boolean isSpent(MobId mob, AttackTurn turn, Table table) {
    Optional<Candidate> candidate = table.candidate(mob);
    if (candidate.isEmpty()) {
      return true;
    }
    boolean isExpired =
        table.tick() - turn.grantedTick() >= settings.get().attackTurnTimeoutTicks();
    boolean wasHit = candidate.get().health() < turn.grantedHealth();
    return isExpired || wasHit;
  }

  private AttackTurns grantFreeTurns(AttackTurns turns, Table table) {
    long freeTurns = settings.get().attackTurns() - turns.holders().size();
    List<Candidate> nextUp =
        table.candidates().stream()
            .filter(candidate -> !turns.holds(candidate.mob()))
            .sorted(nextUpOrder(turns, table.tick()))
            .limit(Math.max(0, freeTurns))
            .toList();
    AttackTurns granted = turns;
    for (Candidate candidate : nextUp) {
      granted = granted.granted(candidate.mob(), new AttackTurn(table.tick(), candidate.health()));
    }
    return granted;
  }

  // A resting fighter goes last, not out: with nobody else free it strikes again at once.
  private Comparator<Candidate> nextUpOrder(AttackTurns turns, long tick) {
    return Comparator.comparing((Candidate candidate) -> isResting(candidate.mob(), turns, tick))
        .thenComparingDouble(Candidate::distanceBlocks)
        .thenComparing(candidate -> candidate.mob().value());
  }

  private boolean isResting(MobId mob, AttackTurns turns, long tick) {
    Long releaseTick = turns.lastReleaseTicks().get(mob);
    return releaseTick != null && tick - releaseTick < settings.get().attackTurnRestTicks();
  }

  private static List<RoleAssignment> marked(
      List<RoleAssignment> orders, AttackTurns turns, PlayerId target) {
    return orders.stream().map(order -> marked(order, turns, target)).toList();
  }

  // A spider biting someone else is alone over there: it has no pack to take turns with.
  private static RoleAssignment marked(RoleAssignment order, AttackTurns turns, PlayerId target) {
    boolean isFighter = order.role() == Role.PRESS || order.role() == Role.FLANK;
    if (!isFighter) {
      return order;
    }
    boolean isOnOtherTarget = !PackCohesion.isInPack(order, target);
    if (isOnOtherTarget || turns.holds(order.mob())) {
      return order.withAttackTurn();
    }
    return order;
  }

  private record TurnTarget(PlayerId id, Vec3 position) {}

  private record Candidate(MobId mob, double distanceBlocks, double health) {}

  private record Table(long tick, List<Candidate> candidates) {
    Optional<Candidate> candidate(MobId mob) {
      return candidates.stream().filter(candidate -> candidate.mob().equals(mob)).findFirst();
    }
  }
}
