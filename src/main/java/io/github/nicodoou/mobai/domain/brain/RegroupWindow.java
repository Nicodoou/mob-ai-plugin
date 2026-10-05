package io.github.nicodoou.mobai.domain.brain;

import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import java.util.Objects;
import java.util.function.Supplier;

/** The regroup time shared by every group; it learns from how regrouping ends. */
public final class RegroupWindow {
  private final Supplier<RetreatSettings> settings;
  private long ticks;

  public RegroupWindow(Supplier<RetreatSettings> settings) {
    this.settings = Objects.requireNonNull(settings, "RegroupWindow.settings");
    this.ticks = settings.get().regroupInitialTicks();
  }

  public long currentTicks() {
    return bounded(ticks);
  }

  public void recordWiped() {
    ticks = bounded(currentTicks() - settings.get().regroupStepTicks());
  }

  public void recordSurvived() {
    ticks = bounded(currentTicks() + settings.get().regroupStepTicks());
  }

  public void restore(long savedTicks) {
    ticks = savedTicks;
  }

  private long bounded(long value) {
    RetreatSettings current = settings.get();
    return Math.clamp(value, current.regroupMinTicks(), current.regroupMaxTicks());
  }
}
