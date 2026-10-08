package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Objects;
import java.util.Optional;

/**
 * What one mob does until the next decision.
 *
 * @param recovering the plugin heals this mob until the next decision
 * @param rallyPoint where a regrouping mob meets the rest of its group (CT-29)
 */
public record RoleAssignment(
    MobId mob,
    Role role,
    Optional<PlayerId> target,
    Optional<Attack> suggestedAttack,
    boolean recovering,
    Optional<Vec3> rallyPoint) {
  public RoleAssignment {
    Objects.requireNonNull(mob, "RoleAssignment.mob");
    Objects.requireNonNull(role, "RoleAssignment.role");
    Objects.requireNonNull(target, "RoleAssignment.target");
    Objects.requireNonNull(suggestedAttack, "RoleAssignment.suggestedAttack");
    Objects.requireNonNull(rallyPoint, "RoleAssignment.rallyPoint");
  }
}
