package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.Objects;
import java.util.Optional;

/**
 * What one mob does until the next decision.
 *
 * @param recovering the plugin heals this mob until the next decision
 */
public record RoleAssignment(
    MobId mob,
    Role role,
    Optional<PlayerId> target,
    Optional<Attack> suggestedAttack,
    boolean recovering) {
  public RoleAssignment {
    Objects.requireNonNull(mob, "RoleAssignment.mob");
    Objects.requireNonNull(role, "RoleAssignment.role");
    Objects.requireNonNull(target, "RoleAssignment.target");
    Objects.requireNonNull(suggestedAttack, "RoleAssignment.suggestedAttack");
  }
}
