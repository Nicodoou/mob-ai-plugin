package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.application.AttackResolution;
import io.github.nicodoou.mobai.application.RecordOutcome;
import io.github.nicodoou.mobai.domain.attack.AttackClassifier;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.attack.ProjectileContact;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Opens, gathers and closes attack attempts; the domain decides what each one was worth. */
public final class AttackTracker {
  private final RecordOutcome recordOutcome;
  private final AttackClassifier classifier;
  private final Map<MobId, OpenAttempt> openMelee = new HashMap<>();
  private final Map<UUID, OpenProjectile> openProjectiles = new LinkedHashMap<>();
  private long lastAttemptNumber;

  public AttackTracker(RecordOutcome recordOutcome, AttackClassifier classifier) {
    this.recordOutcome = Objects.requireNonNull(recordOutcome, "AttackTracker.recordOutcome");
    this.classifier = Objects.requireNonNull(classifier, "AttackTracker.classifier");
  }

  public AttemptId openMelee(MeleeOpening opening) {
    if (openMelee.containsKey(opening.mob())) {
      throw new IllegalStateException(
          "Mob " + opening.mob().shortId() + " already has an open melee attempt");
    }
    AttemptId id = new AttemptId(++lastAttemptNumber);
    openMelee.put(opening.mob(), new OpenAttempt(id, opening));
    return id;
  }

  public boolean recordHit(MeleeHit hit) {
    OpenAttempt attempt = openMelee.get(hit.attacker());
    if (attempt == null || !attempt.opening().target().equals(hit.victim())) {
      return false;
    }
    attempt.record(hit);
    return true;
  }

  public Optional<Classification> closeMelee(MobId mob, boolean targetValid, long tick) {
    OpenAttempt attempt = openMelee.remove(mob);
    if (attempt == null) {
      return Optional.empty();
    }
    Classification classification = classifier.classify(attempt.facts(targetValid));
    recordOutcome.execute(resolution(attempt, classification, tick));
    return Optional.of(classification);
  }

  public void cancel(MobId mob) {
    openMelee.remove(mob);
  }

  public int openAttempts() {
    return openMelee.size() + openProjectiles.size();
  }

  public AttemptId openProjectile(ProjectileOpening opening) {
    if (openProjectiles.containsKey(opening.projectile())) {
      throw new IllegalStateException(
          "Projectile " + opening.projectile() + " already has an open attempt");
    }
    AttemptId id = new AttemptId(++lastAttemptNumber);
    openProjectiles.put(opening.projectile(), new OpenProjectile(id, opening));
    return id;
  }

  // Contacts of attempts already closed are ignored: an arrow can land after its timeout.
  public boolean recordProjectileContact(UUID projectile, ProjectileContact contact) {
    OpenProjectile attempt = openProjectiles.get(projectile);
    if (attempt == null) {
      return false;
    }
    attempt.touch(contact);
    return true;
  }

  public boolean recordProjectileHit(ProjectileHit hit) {
    OpenProjectile attempt = openProjectiles.get(hit.projectile());
    if (attempt == null || !attempt.opening().target().equals(hit.victim())) {
      return false;
    }
    attempt.record(hit);
    return true;
  }

  public Optional<PlayerId> targetOf(UUID projectile) {
    return Optional.ofNullable(openProjectiles.get(projectile))
        .map(attempt -> attempt.opening().target());
  }

  /** Closes, in opening order, the attempts whose arrow has landed and those past the timeout. */
  public List<ProjectileClosure> closeProjectiles(
      long now, long timeoutTicks, TargetValidity validity) {
    List<ProjectileClosure> closures = new ArrayList<>();
    for (OpenProjectile attempt : List.copyOf(openProjectiles.values())) {
      closeIfResolved(attempt, new Resolution(now, timeoutTicks, validity))
          .ifPresent(closures::add);
    }
    return closures;
  }

  private Optional<ProjectileClosure> closeIfResolved(
      OpenProjectile attempt, Resolution resolution) {
    boolean timedOut =
        !attempt.hasContact()
            && resolution.now() - attempt.opening().tick() >= resolution.timeoutTicks();
    if (!attempt.hasContact() && !timedOut) {
      return Optional.empty();
    }
    return Optional.of(closeProjectile(attempt, timedOut, resolution));
  }

  private ProjectileClosure closeProjectile(
      OpenProjectile attempt, boolean timedOut, Resolution resolution) {
    ProjectileOpening opening = attempt.opening();
    openProjectiles.remove(opening.projectile());
    boolean targetValid = resolution.validity().isValid(opening.mob(), opening.target());
    Classification classification = classifier.classify(attempt.facts(targetValid, timedOut));
    recordOutcome.execute(
        new AttackResolution(
            opening.mob(),
            opening.target(),
            opening.attack(),
            classification.outcome(),
            attempt.planDamage(),
            resolution.now()));
    return new ProjectileClosure(opening.mob(), classification);
  }

  private record Resolution(long now, long timeoutTicks, TargetValidity validity) {}

  private static AttackResolution resolution(
      OpenAttempt attempt, Classification classification, long tick) {
    MeleeOpening opening = attempt.opening();
    return new AttackResolution(
        opening.mob(),
        opening.target(),
        opening.attack(),
        classification.outcome(),
        attempt.planDamage(),
        tick);
  }
}
