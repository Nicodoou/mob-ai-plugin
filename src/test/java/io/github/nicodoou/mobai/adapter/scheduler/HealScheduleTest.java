package io.github.nicodoou.mobai.adapter.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HealScheduleTest {
  private final HealSchedule schedule = new HealSchedule();

  @Test
  void firstHealComesAWholeIntervalAfterRecoveryStarts() {
    Set<MobId> recovering = Set.of(mob(1));

    assertThat(schedule.due(recovering, 1000)).isEmpty();
    assertThat(schedule.due(recovering, 1049)).isEmpty();
    assertThat(schedule.due(recovering, 1050)).containsExactly(mob(1));
  }

  @Test
  void healsEveryFiftyTicks() {
    Set<MobId> recovering = Set.of(mob(1));
    schedule.due(recovering, 1000);
    schedule.due(recovering, 1050);

    assertThat(schedule.due(recovering, 1099)).isEmpty();
    assertThat(schedule.due(recovering, 1100)).containsExactly(mob(1));
  }

  @Test
  void stoppingRecoveryResetsTheCount() {
    schedule.due(Set.of(mob(1)), 1000);

    assertThat(schedule.due(Set.of(), 1060)).isEmpty();
    assertThat(schedule.due(Set.of(mob(1)), 1070)).isEmpty();
    assertThat(schedule.due(Set.of(mob(1)), 1119)).isEmpty();
    assertThat(schedule.due(Set.of(mob(1)), 1120)).containsExactly(mob(1));
  }

  @Test
  void dueMobsComeInIdOrder() {
    Set<MobId> recovering = Set.of(mob(2), mob(1));
    schedule.due(recovering, 1000);

    assertThat(schedule.due(recovering, 1050)).containsExactly(mob(1), mob(2));
  }

  @Test
  void aLateTickHealsOnlyOnce() {
    Set<MobId> recovering = Set.of(mob(1));
    schedule.due(recovering, 1000);

    assertThat(schedule.due(recovering, 1200)).containsExactly(mob(1));
    assertThat(schedule.due(recovering, 1249)).isEmpty();
    assertThat(schedule.due(recovering, 1250)).containsExactly(mob(1));
  }

  private static MobId mob(long id) {
    return new MobId(new UUID(0, id));
  }
}
