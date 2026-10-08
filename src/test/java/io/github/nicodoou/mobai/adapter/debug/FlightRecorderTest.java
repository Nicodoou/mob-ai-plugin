package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.ClosedPlan;
import io.github.nicodoou.mobai.domain.decision.PlanScores;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FlightRecorderTest {
  private static final int CAPACITY = 3;
  private static final GroupId FIRST = new GroupId(new UUID(0, 1));
  private static final GroupId SECOND = new GroupId(new UUID(0, 2));

  private final FlightRecorder recorder =
      new FlightRecorder(() -> new DebugSettings(TraceLevel.OFF, CAPACITY));

  @Test
  void keepsTheLastEventsOfEachGroup() {
    for (long tick = 1; tick <= 5; tick++) {
      recorder.record(event(FIRST, tick));
    }

    List<TraceEvent> recent = recorder.recent(FIRST);

    assertThat(recent).extracting(TraceEvent::tick).containsExactly(3L, 4L, 5L);
  }

  @Test
  void groupsAreKeptApart() {
    recorder.record(event(FIRST, 1));
    recorder.record(event(SECOND, 2));

    assertThat(recorder.recent(FIRST)).extracting(TraceEvent::tick).containsExactly(1L);
    assertThat(recorder.recent(SECOND)).extracting(TraceEvent::tick).containsExactly(2L);
  }

  @Test
  void forgetsTheLeastUsedGroupBeyondTheLimit() {
    int groups = FlightRecorder.MAX_GROUPS + 1;
    for (int index = 0; index < groups; index++) {
      recorder.record(event(new GroupId(new UUID(1, index)), index));
    }

    assertThat(recorder.recent(new GroupId(new UUID(1, 0)))).isEmpty();
    assertThat(recorder.recent(new GroupId(new UUID(1, groups - 1)))).hasSize(1);
  }

  private static TraceEvent event(GroupId group, long tick) {
    ClosedPlan plan =
        new ClosedPlan(
            new PlanId(group, 1),
            new StrategyId("SURROUND"),
            new PlayerId(new UUID(0, 10)),
            PlanEndReason.TIMED_OUT,
            0.5,
            new PlanScores(1, 1, 1),
            0,
            0,
            2.0,
            0,
            tick,
            Optional.empty());
    return new TraceEvent.PlanEvent(group, tick, plan);
  }
}
