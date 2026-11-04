package io.github.nicodoou.mobai.adapter.debug;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RecordingSelectionTest {
  private static final int RECORDER_EVENTS = 10;
  private static final int INTERVAL_TICKS = 5;

  private final AtomicReference<TraceLevel> configured = new AtomicReference<>(TraceLevel.OFF);
  private final TraceLevels levels =
      new TraceLevels(() -> new DebugSettings(configured.get(), RECORDER_EVENTS, INTERVAL_TICKS));
  private final List<GroupId> candidates = List.of(groupId(1), groupId(2));

  @Test
  void offRecordsNothing() {
    RecordingSelection selection = RecordingSelection.of(levels, candidates);

    assertThat(selection.groups()).isEmpty();
    assertThat(selection.isActive()).isFalse();
  }

  @Test
  void decisionsDoNotRecord() {
    levels.setAll(TraceLevel.DECISIONS);

    RecordingSelection selection = RecordingSelection.of(levels, candidates);

    assertThat(selection.groups()).isEmpty();
    assertThat(selection.isActive()).isFalse();
  }

  @Test
  void fullForAllRecordsEveryGroup() {
    levels.setAll(TraceLevel.FULL);

    RecordingSelection selection = RecordingSelection.of(levels, candidates);

    assertThat(selection.groups()).containsExactly(groupId(1), groupId(2));
    assertThat(selection.isActive()).isTrue();
  }

  @Test
  void fullForAllIsActiveWithoutGroups() {
    levels.setAll(TraceLevel.FULL);

    RecordingSelection selection = RecordingSelection.of(levels, List.of());

    assertThat(selection.groups()).isEmpty();
    assertThat(selection.isActive()).isTrue();
  }

  @Test
  void aGroupAtFullRecordsOnlyThatGroup() {
    levels.set(groupId(2), TraceLevel.FULL);

    RecordingSelection selection = RecordingSelection.of(levels, candidates);

    assertThat(selection.groups()).containsExactly(groupId(2));
    assertThat(selection.isActive()).isTrue();
  }

  @Test
  void theConfiguredDefaultCounts() {
    configured.set(TraceLevel.FULL);

    RecordingSelection selection = RecordingSelection.of(levels, candidates);

    assertThat(selection.groups()).containsExactly(groupId(1), groupId(2));
    assertThat(selection.isActive()).isTrue();
  }

  @Test
  void samplesOnMultiplesOfTheInterval() {
    assertThat(RecordingSelection.isSampleTick(10, INTERVAL_TICKS)).isTrue();
    assertThat(RecordingSelection.isSampleTick(11, INTERVAL_TICKS)).isFalse();
    assertThat(RecordingSelection.isSampleTick(14, INTERVAL_TICKS)).isFalse();
    assertThat(RecordingSelection.isSampleTick(15, INTERVAL_TICKS)).isTrue();
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }
}
