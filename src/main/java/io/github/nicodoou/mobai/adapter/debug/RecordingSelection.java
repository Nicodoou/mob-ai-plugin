package io.github.nicodoou.mobai.adapter.debug;

import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.List;
import java.util.Objects;

/** Which groups are being recorded right now, and whether anything is (CT-36). */
public record RecordingSelection(List<GroupId> groups, boolean isActive) {
  public RecordingSelection {
    Objects.requireNonNull(groups, "RecordingSelection.groups");
    groups = List.copyOf(groups);
  }

  public static RecordingSelection of(TraceLevels levels, List<GroupId> candidates) {
    Objects.requireNonNull(levels, "RecordingSelection.levels");
    Objects.requireNonNull(candidates, "RecordingSelection.candidates");
    List<GroupId> recorded = candidates.stream().filter(levels::isFull).toList();
    return new RecordingSelection(recorded, !recorded.isEmpty() || levels.isFullForAll());
  }

  /** The recording samples on the ticks that are multiples of the interval. */
  public static boolean isSampleTick(long tick, int intervalTicks) {
    return tick % intervalTicks == 0;
  }
}
