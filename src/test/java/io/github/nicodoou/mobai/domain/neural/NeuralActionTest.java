package io.github.nicodoou.mobai.domain.neural;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import org.junit.jupiter.api.Test;

class NeuralActionTest {
  @Test
  void outputsKeepTheAgreedOrder() {
    assertThat(NeuralAction.values())
        .containsExactly(
            NeuralAction.PRESS,
            NeuralAction.FLANK,
            NeuralAction.FALL_BACK,
            NeuralAction.RETREAT,
            NeuralAction.SHOOT,
            NeuralAction.HOLD_FIRE);
  }

  @Test
  void meleeMobsChooseAmongTheFirstFourActions() {
    assertThat(NeuralAction.maskOf(MobKind.ZOMBIE))
        .containsExactly(true, true, true, true, false, false);
    assertThat(NeuralAction.maskOf(MobKind.SPIDER))
        .containsExactly(true, true, true, true, false, false);
  }

  @Test
  void skeletonsChooseAmongRetreatShootAndHoldFire() {
    assertThat(NeuralAction.maskOf(MobKind.SKELETON))
        .containsExactly(false, false, false, true, true, true);
  }

  @Test
  void rolesOutsideTheNetworkHaveNoAction() {
    assertThat(NeuralAction.ofRole(Role.HOLD_FIRE)).contains(NeuralAction.HOLD_FIRE);
    assertThat(NeuralAction.ofRole(Role.GATHER)).isEmpty();
  }
}
