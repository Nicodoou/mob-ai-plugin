package io.github.nicodoou.mobai.simulation;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.attack.AttackOutcome;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class OutcomeModelTest {
  private static final int DRAWS = 20_000;
  private static final double FREQUENCY_TOLERANCE = 0.01;

  private final OutcomeModel model = new OutcomeModel(new SeededRandomSource(7));

  private static RoleAssignment order(Role role, Attack attack) {
    return new RoleAssignment(
        new MobId(new UUID(0, 1)), role, Optional.of(ALICE), Optional.of(attack), false);
  }

  private double share(
      RoleAssignment order, MobKind kind, Predicate<Optional<AttackOutcome>> wanted) {
    long matching =
        IntStream.range(0, DRAWS)
            .mapToObj(draw -> model.resolve(PlayerArchetype.BLOCKER, order, kind))
            .filter(wanted)
            .count();
    return (double) matching / DRAWS;
  }

  @Test
  void everyRowAddsUpToOne() {
    assertThatCode(() -> new OutcomeModel(new SeededRandomSource(1))).doesNotThrowAnyException();
  }

  @Test
  void frequenciesMatchTheTable() {
    RoleAssignment front = order(Role.PRESS, Attack.ZOMBIE_FRONT_STRIKE);
    RoleAssignment patient = order(Role.PRESS, Attack.ZOMBIE_PATIENT_STRIKE);

    double partials =
        share(
            front,
            MobKind.ZOMBIE,
            outcome -> outcome.orElseThrow() instanceof AttackOutcome.Partial);
    double hits =
        share(
            front,
            MobKind.ZOMBIE,
            outcome -> outcome.isPresent() && outcome.get() instanceof AttackOutcome.Hit);
    double waits = share(patient, MobKind.ZOMBIE, Optional::isEmpty);

    assertThat(partials).isCloseTo(0.85, within(FREQUENCY_TOLERANCE));
    assertThat(hits).isZero();
    assertThat(waits).isCloseTo(0.80, within(FREQUENCY_TOLERANCE));
  }

  @Test
  void flankRoleChangesTheOdds() {
    RoleAssignment flanking = order(Role.FLANK, Attack.ZOMBIE_FLANK_STRIKE);

    double hits =
        share(
            flanking,
            MobKind.ZOMBIE,
            outcome -> outcome.orElseThrow() instanceof AttackOutcome.Hit);

    assertThat(hits).isCloseTo(0.70, within(FREQUENCY_TOLERANCE));
  }

  @Test
  void impossibleCombinationsFail() {
    RoleAssignment impossible = order(Role.SHOOT, Attack.ZOMBIE_FRONT_STRIKE);

    assertThatThrownBy(() -> model.resolve(PlayerArchetype.BLOCKER, impossible, MobKind.SKELETON))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("No odds for");
  }
}
