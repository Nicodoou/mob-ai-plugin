package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import java.util.Objects;
import java.util.Optional;

/** The facts of one melee attempt, gathered between opening it and closing it. */
final class OpenAttempt {
  private final AttemptId id;
  private final MeleeOpening opening;
  private Optional<MeleeHit> hit = Optional.empty();

  OpenAttempt(AttemptId id, MeleeOpening opening) {
    this.id = Objects.requireNonNull(id, "OpenAttempt.id");
    this.opening = Objects.requireNonNull(opening, "OpenAttempt.opening");
  }

  MeleeOpening opening() {
    return opening;
  }

  // Only the first damage event of an attempt counts.
  void record(MeleeHit firstHit) {
    if (hit.isEmpty()) {
      hit = Optional.of(firstHit);
    }
  }

  AttackFacts facts(boolean targetValid) {
    return new AttackFacts(
        id,
        opening.mob(),
        opening.target(),
        opening.attack(),
        opening.tick(),
        targetValid,
        opening.targetInvulnerable(),
        hit.isPresent(),
        hit.map(MeleeHit::cancelled).orElse(false),
        hit.map(MeleeHit::realDamage).orElse(0.0),
        hit.map(MeleeHit::blocked).orElse(false),
        false,
        ProjectileContact.NONE,
        false,
        false);
  }

  double planDamage() {
    return hit.filter(found -> !found.cancelled()).map(MeleeHit::realDamage).orElse(0.0);
  }
}
