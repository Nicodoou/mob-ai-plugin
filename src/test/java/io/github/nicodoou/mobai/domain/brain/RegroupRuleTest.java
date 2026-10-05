package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RegroupRuleTest {
  private static final long REGROUP_START_TICK = 1000;

  private final Supplier<RetreatSettings> settings = () -> TestSettings.defaults().retreat();
  private final RegroupRule rule = new RegroupRule(settings, new RegroupWindow(settings));

  @Test
  void recoveredMajorityEndsRegrouping() {
    GroupSnapshot snapshot = snapshotAt(1100, 12, 12, 5);

    assertThat(rule.detect(snapshot, REGROUP_START_TICK)).contains(RegroupEndReason.RECOVERED);
  }

  @Test
  void halfRecoveredIsNotEnough() {
    GroupSnapshot snapshot = snapshotAt(1100, 12, 5);

    assertThat(rule.detect(snapshot, REGROUP_START_TICK)).isEmpty();
  }

  @Test
  void windowExpiresAfterItsTicks() {
    assertThat(rule.detect(snapshotAt(1599, 5, 5), REGROUP_START_TICK)).isEmpty();
    assertThat(rule.detect(snapshotAt(1600, 5, 5), REGROUP_START_TICK))
        .contains(RegroupEndReason.WINDOW_EXPIRED);
  }

  @Test
  void recoveryWinsOverTheWindow() {
    GroupSnapshot snapshot = snapshotAt(1600, 12, 12);

    assertThat(rule.detect(snapshot, REGROUP_START_TICK)).contains(RegroupEndReason.RECOVERED);
  }

  @Test
  void withoutMobsOnlyTheWindowCanEndIt() {
    assertThat(rule.detect(snapshotAt(1100), REGROUP_START_TICK)).isEmpty();
    assertThat(rule.detect(snapshotAt(1600), REGROUP_START_TICK))
        .contains(RegroupEndReason.WINDOW_EXPIRED);
  }

  private static GroupSnapshot snapshotAt(long tick, double... mobHealths) {
    GroupSnapshotBuilder builder = new GroupSnapshotBuilder().withTick(tick);
    for (int index = 0; index < mobHealths.length; index++) {
      builder.withMob(
          new MobSnapshotBuilder()
              .withId(new MobId(new UUID(1, index + 1)))
              .withHealth(mobHealths[index])
              .build());
    }
    return builder.build();
  }
}
