package io.github.nicodoou.mobai.adapter.tracker;

import io.github.nicodoou.mobai.application.AttackResolution;
import io.github.nicodoou.mobai.application.RecordOutcome;
import io.github.nicodoou.mobai.domain.attack.AttackClassifier;
import io.github.nicodoou.mobai.domain.attack.Classification;
import io.github.nicodoou.mobai.domain.shared.AttemptId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Opens, gathers and closes attack attempts; the domain decides what each one was worth. */
public final class AttackTracker {
  private final RecordOutcome recordOutcome;
  private final AttackClassifier classifier;
  private final Map<MobId, OpenAttempt> openMelee = new HashMap<>();
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
    return openMelee.size();
  }

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
