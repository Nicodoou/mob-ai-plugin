package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SettingsHolderTest {
  @Test
  void currentReturnsTheInitialSettings() {
    MobAiSettings initial = TestSettings.defaults();

    SettingsHolder holder = new SettingsHolder(initial);

    assertThat(holder.current()).isSameAs(initial);
  }

  @Test
  void currentReturnsTheLastReplacedSettings() {
    SettingsHolder holder = new SettingsHolder(TestSettings.defaults());
    MobAiSettings other = withMaxGroupSize(3);

    holder.replace(other);

    assertThat(holder.current()).isSameAs(other);
  }

  @Test
  void sectionReadsTheCurrentValueAfterReplace() {
    SettingsHolder holder = new SettingsHolder(TestSettings.defaults());
    Supplier<GroupSettings> section = holder.section(MobAiSettings::group);

    holder.replace(withMaxGroupSize(3));

    assertThat(section.get().maxGroupSize()).isEqualTo(3);
  }

  private static MobAiSettings withMaxGroupSize(int size) {
    MobAiSettings base = TestSettings.defaults();
    return new MobAiSettings(
        new GroupSettings(size, 10, 24.0),
        base.memory(),
        base.selection(),
        base.target(),
        base.plan(),
        base.attack(),
        base.spider(),
        base.persistence(),
        base.debug(),
        base.retreat(),
        base.volley());
  }
}
