package io.github.nicodoou.mobai.adapter.goal;

import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.CombatTactic;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** The order each mob follows until the next decision; goals only read it. */
public final class RoleRegistry {
  private static final OrderSource NO_SOURCE =
      new OrderSource(new GroupId(new UUID(0, 0)), CombatTactic.NONE);

  private final Map<MobId, RoleAssignment> assignments = new HashMap<>();
  private final Map<MobId, OrderSource> sources = new HashMap<>();

  public void assign(RoleAssignment assignment) {
    assign(assignment, NO_SOURCE);
  }

  public void assign(RoleAssignment assignment, OrderSource source) {
    assignments.put(assignment.mob(), assignment);
    sources.put(assignment.mob(), Objects.requireNonNull(source, "RoleRegistry.source"));
  }

  public Optional<RoleAssignment> assignmentOf(MobId mob) {
    return Optional.ofNullable(assignments.get(mob));
  }

  /** NONE for a mob that has no order yet. */
  public CombatTactic tacticOf(MobId mob) {
    return sourceOf(mob).tactic();
  }

  /** The group of a mob that has an order; a mob without one belongs to none yet. */
  public GroupId groupOf(MobId mob) {
    return sourceOf(mob).group();
  }

  public Set<MobId> mobsWith(Role role, PlayerId target) {
    return assignments.values().stream()
        .filter(order -> order.role() == role && order.target().equals(Optional.of(target)))
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The mobs with this role on this target that share their places with the asking mob. */
  public Set<MobId> mobsWith(Role role, PlayerId target, MobId asking) {
    return mobsWith(role, target).stream()
        .filter(mob -> sourceOf(asking).sharesPlacesWith(sourceOf(mob)))
        .collect(Collectors.toUnmodifiableSet());
  }

  /** The same, without the fighters that hold an attack turn. */
  public Set<MobId> mobsWaitingWith(Role role, PlayerId target, MobId asking) {
    return mobsWith(role, target, asking).stream()
        .filter(mob -> !assignments.get(mob).holdsAttackTurn())
        .collect(Collectors.toUnmodifiableSet());
  }

  public Set<MobId> mobsTargeting(PlayerId target) {
    return assignments.values().stream()
        .filter(order -> order.target().equals(Optional.of(target)))
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }

  public Set<MobId> recoveringMobs() {
    return assignments.values().stream()
        .filter(RoleAssignment::recovering)
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }

  public void clear(MobId mob) {
    assignments.remove(mob);
    sources.remove(mob);
  }

  public void retainOnly(Set<MobId> members) {
    assignments.keySet().retainAll(members);
    sources.keySet().retainAll(members);
  }

  public int size() {
    return assignments.size();
  }

  private OrderSource sourceOf(MobId mob) {
    return sources.getOrDefault(mob, NO_SOURCE);
  }
}
