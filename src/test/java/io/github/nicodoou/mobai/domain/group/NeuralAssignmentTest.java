package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NeuralAssignmentTest {
  @Test
  void theTacticComesFromThePrefixOfTheNetworkName() {
    assertThat(NeuralAssignment.forNetwork("turnos-1").tactic()).isEqualTo(CombatTactic.TURNS);
    assertThat(NeuralAssignment.forNetwork("presion-4").tactic()).isEqualTo(CombatTactic.PRESSURE);
    assertThat(NeuralAssignment.forNetwork("cerco-2").tactic()).isEqualTo(CombatTactic.ENCIRCLE);
    assertThat(NeuralAssignment.forNetwork("relevo-3").tactic()).isEqualTo(CombatTactic.RELAY);
  }

  @Test
  void aNameWithoutAKnownPrefixHasNoTactic() {
    assertThat(NeuralAssignment.forNetwork("default").tactic()).isEqualTo(CombatTactic.NONE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "..", "../otra", "a/b", "a\\b", "Mayus", "con espacio"})
  void aNameThatCouldLeaveItsFolderIsRejected(String name) {
    assertThatThrownBy(() -> NeuralAssignment.forNetwork(name))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
