package io.github.nicodoou.mobai.domain.decision;

import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Objects;
import java.util.Optional;

/** The attack suggested to a mob; the selection is empty when no policy was asked (spiders). */
public record AttackChoice(MobId mob, Attack attack, Optional<SelectionResult<Attack>> selection) {
  public AttackChoice {
    Objects.requireNonNull(mob, "AttackChoice.mob");
    Objects.requireNonNull(attack, "AttackChoice.attack");
    Objects.requireNonNull(selection, "AttackChoice.selection");
  }
}
