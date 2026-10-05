package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.RecordChange;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public final class RecordOutcome {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;

  public RecordOutcome(ActiveGroups activeGroups, SettingsHolder settings) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordOutcome.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecordOutcome.settings");
  }

  public Optional<RecordChange> execute(AttackResolution resolution) {
    return activeGroups.groupOf(resolution.mob()).flatMap(group -> record(group, resolution));
  }

  private Optional<RecordChange> record(Group group, AttackResolution resolution) {
    addPlanDamage(group, resolution);
    OptionalDouble credit =
        resolution.outcome().credit(settings.current().memory().partialHitWeight());
    if (credit.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        group
            .memory()
            .recordAttack(
                new AttackObservation(
                    resolution.target(),
                    resolution.attack(),
                    credit.getAsDouble(),
                    resolution.tick())));
  }

  // The plan rejects zero damage, and a blocked or missed attempt deals none.
  private static void addPlanDamage(Group group, AttackResolution resolution) {
    if (resolution.damageDealt() > 0) {
      group.lifecycle().recordPlanDamage(resolution.target(), resolution.damageDealt());
    }
  }
}
