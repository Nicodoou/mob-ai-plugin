package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public final class SettingsHolder {
  private MobAiSettings current;

  public SettingsHolder(MobAiSettings initial) {
    this.current = Objects.requireNonNull(initial, "SettingsHolder.initial");
  }

  public MobAiSettings current() {
    return current;
  }

  public void replace(MobAiSettings settings) {
    current = Objects.requireNonNull(settings, "SettingsHolder.settings");
  }

  // Reads on every call, so a reload reaches everyone who holds the supplier.
  public <T> Supplier<T> section(Function<MobAiSettings, T> selector) {
    Objects.requireNonNull(selector, "SettingsHolder.selector");
    return () -> selector.apply(current);
  }
}
