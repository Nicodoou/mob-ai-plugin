package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.domain.attack.AttackFacts;
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import java.util.Objects;
import java.util.Optional;

/** The facts of one projectile attempt, gathered while the arrow flies. */
final class OpenProjectile {
  private final AttemptId id;
  private final ProjectileOpening opening;
  private ProjectileContact contact = ProjectileContact.NONE;
  private Optional<ProjectileHit> hit = Optional.empty();

  OpenProjectile(AttemptId id, ProjectileOpening opening) {
    this.id = Objects.requireNonNull(id, "OpenProjectile.id");
    this.opening = Objects.requireNonNull(opening, "OpenProjectile.opening");
  }

  ProjectileOpening opening() {
    return opening;
  }

  boolean hasContact() {
    return contact != ProjectileContact.NONE;
  }

  // Only the first contact and the first damage of an arrow count.
  void touch(ProjectileContact first) {
    if (contact == ProjectileContact.NONE) {
      contact = first;
    }
  }

  void record(ProjectileHit firstHit) {
    if (hit.isEmpty()) {
      hit = Optional.of(firstHit);
    }
  }

  AttackFacts facts(boolean targetValid, boolean timedOut) {
    return new AttackFacts(
        id,
        opening.mob(),
        opening.target(),
        opening.attack(),
        opening.tick(),
        targetValid,
        opening.targetInvulnerable(),
        hit.isPresent(),
        hit.map(ProjectileHit::cancelled).orElse(false),
        hit.map(ProjectileHit::realDamage).orElse(0.0),
        hit.map(ProjectileHit::blocked).orElse(false),
        false,
        contact,
        false,
        timedOut);
  }

  double planDamage() {
    return hit.filter(found -> !found.cancelled()).map(ProjectileHit::realDamage).orElse(0.0);
  }
}
