package io.github.nicodoou.mobai.domain.group;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GroupTacticTest {
  private final MobAiSettings settings = TestSettings.defaults();
  private final Group group =
      new Group(
          new GroupId(new UUID(0, 1)),
          SelectionPolicyType.RANDOM,
          new GroupKnowledge(
              new GroupMemory(settings::memory), new ThreatLedger(settings::target)));

  @Test
  void aGroupStartsWithoutATactic() {
    assertThat(group.tactic()).isEqualTo(CombatTactic.NONE);
  }

  @ParameterizedTest
  @CsvSource({
    "turnos-2, TURNS",
    "presion-1, PRESSURE",
    "cerco-3, ENCIRCLE",
    "relevo-4, RELAY",
    "default, NONE",
    "otra-1, NONE"
  })
  void theNetworksPrefixChoosesTheGroupsTactic(String network, CombatTactic tactic) {
    group.assignNeural(NeuralAssignment.forNetwork(network));

    assertThat(group.tactic()).isEqualTo(tactic);
  }

  @Test
  void aTacticWithoutANetworkLeavesTheGroupOutOfTheNetworks() {
    group.assignTactic(CombatTactic.PRESSURE);

    assertThat(group.tactic()).isEqualTo(CombatTactic.PRESSURE);
    assertThat(group.neuralAssignment()).isEmpty();
  }
}
