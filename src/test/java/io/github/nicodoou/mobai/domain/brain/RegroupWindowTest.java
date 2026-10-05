package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RegroupWindowTest {
  private static final int WIPES_TO_REACH_THE_MINIMUM = 9;
  private static final int SURVIVALS_TO_REACH_THE_MAXIMUM = 25;

  private final AtomicReference<RetreatSettings> settings =
      new AtomicReference<>(TestSettings.defaults().retreat());
  private final RegroupWindow window = new RegroupWindow(settings::get);

  @Test
  void startsAtTheInitialValue() {
    assertThat(window.currentTicks()).isEqualTo(600);
  }

  @Test
  void wipesShortenAndSurvivalsLengthen() {
    window.recordWiped();
    assertThat(window.currentTicks()).isEqualTo(550);

    window.recordSurvived();
    window.recordSurvived();
    assertThat(window.currentTicks()).isEqualTo(650);
  }

  @Test
  void staysWithinItsBounds() {
    for (int wipe = 0; wipe < WIPES_TO_REACH_THE_MINIMUM; wipe++) {
      window.recordWiped();
    }
    assertThat(window.currentTicks()).isEqualTo(200);

    for (int survival = 0; survival < SURVIVALS_TO_REACH_THE_MAXIMUM; survival++) {
      window.recordSurvived();
    }
    assertThat(window.currentTicks()).isEqualTo(1200);
  }

  @Test
  void restoredValueIsBoundedOnRead() {
    window.restore(900);
    assertThat(window.currentTicks()).isEqualTo(900);

    window.restore(5000);
    assertThat(window.currentTicks()).isEqualTo(1200);
  }

  @Test
  void boundsFollowTheCurrentSettings() {
    window.restore(900);

    settings.set(new RetreatSettings(0.6, 12.0, 600, 200, 800, 50));

    assertThat(window.currentTicks()).isEqualTo(800);
  }

  @Test
  void rejectsInconsistentSettings() {
    assertThatThrownBy(() -> new RetreatSettings(0.6, 12.0, 600, 1300, 1200, 50))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "RetreatSettings.regroupMinTicks must not exceed RetreatSettings.regroupMaxTicks, got 1300.0 > 1200.0");
  }
}
