package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.settings.VolleySettings;
import org.junit.jupiter.api.Test;

class VolleyCycleTest {
  private final VolleyCycle cycle = new VolleyCycle(() -> new VolleySettings(60, 20, 30, 1.5));

  @Test
  void startsPressing() {
    VolleyPhase first = cycle.phaseAt(0);
    VolleyPhase last = cycle.phaseAt(59);

    assertThat(first).isEqualTo(VolleyPhase.PRESSING);
    assertThat(last).isEqualTo(VolleyPhase.PRESSING);
  }

  @Test
  void thenFallsBack() {
    VolleyPhase first = cycle.phaseAt(60);
    VolleyPhase last = cycle.phaseAt(79);

    assertThat(first).isEqualTo(VolleyPhase.FALLING_BACK);
    assertThat(last).isEqualTo(VolleyPhase.FALLING_BACK);
  }

  @Test
  void thenFires() {
    VolleyPhase first = cycle.phaseAt(80);
    VolleyPhase last = cycle.phaseAt(109);

    assertThat(first).isEqualTo(VolleyPhase.FIRING);
    assertThat(last).isEqualTo(VolleyPhase.FIRING);
  }

  @Test
  void theCycleRepeats() {
    VolleyPhase pressing = cycle.phaseAt(110);
    VolleyPhase fallingBack = cycle.phaseAt(170);
    VolleyPhase firing = cycle.phaseAt(190);

    assertThat(pressing).isEqualTo(VolleyPhase.PRESSING);
    assertThat(fallingBack).isEqualTo(VolleyPhase.FALLING_BACK);
    assertThat(firing).isEqualTo(VolleyPhase.FIRING);
  }

  @Test
  void phasesFollowTheSettings() {
    VolleyCycle shortCycle = new VolleyCycle(() -> new VolleySettings(10, 10, 10, 0));

    VolleyPhase lastPressing = shortCycle.phaseAt(9);
    VolleyPhase fallingBack = shortCycle.phaseAt(10);
    VolleyPhase firing = shortCycle.phaseAt(20);
    VolleyPhase pressingAgain = shortCycle.phaseAt(30);

    assertThat(lastPressing).isEqualTo(VolleyPhase.PRESSING);
    assertThat(fallingBack).isEqualTo(VolleyPhase.FALLING_BACK);
    assertThat(firing).isEqualTo(VolleyPhase.FIRING);
    assertThat(pressingAgain).isEqualTo(VolleyPhase.PRESSING);
  }
}
