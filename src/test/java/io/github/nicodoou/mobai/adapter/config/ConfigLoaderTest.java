package io.github.nicodoou.mobai.adapter.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.PersistenceSettings;
import io.github.nicodoou.mobai.domain.settings.PlanSettings;
import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import io.github.nicodoou.mobai.domain.settings.SpiderSettings;
import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import java.io.IOException;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class ConfigLoaderTest {
  private final ConfigLoader loader = new ConfigLoader();

  @Test
  void bundledConfigLoadsTheCatalogValues() throws Exception {
    MobAiSettings loaded = loader.load(bundledConfig());

    assertThat(loaded).isEqualTo(catalogSettings());
  }

  @Test
  void missingKeyIsReported() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("memory.half-life-ticks", null);

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage("config.yml: missing memory.half-life-ticks");
  }

  @Test
  void textWhereANumberGoesIsReported() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("group.max-size", "doce");

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage("config.yml: group.max-size must be a number, got doce");
  }

  @Test
  void decimalWhereAWholeNumberGoesIsReported() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("group.max-size", 12.5);

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage("config.yml: group.max-size must be a whole number, got 12.5");
  }

  @Test
  void unknownPolicyIsReported() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("selection.default-policy", "SMART");

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage(
            "config.yml: selection.default-policy must be one of THOMPSON_SAMPLING, EXPLORE_FIRST,"
                + " EPSILON_GREEDY, RANDOM, got SMART");
  }

  @Test
  void unquotedOffIsReportedWithWhatYamlRead() throws Exception {
    String quoted = "default-trace-level: \"OFF\"";
    String unquoted = bundledText().replace(quoted, "default-trace-level: OFF");
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(unquoted);

    assertThat(unquoted).isNotEqualTo(bundledText());
    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessageEndingWith(", got false");
  }

  @Test
  void outOfRangeValuesUseTheDomainMessage() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("memory.learning-speed", 1.5);

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessage(
            "config.yml: MemorySettings.learningSpeed must be between 0.0 and 1.0, got 1.5");
  }

  @Test
  void crossSectionRulesUseTheDomainMessage() throws Exception {
    YamlConfiguration config = bundledConfig();
    config.set("retreat.recovery-health-fraction", 0.2);

    assertThatThrownBy(() -> loader.load(config))
        .isInstanceOf(InvalidConfigException.class)
        .hasMessageStartingWith(
            "config.yml: RetreatSettings.recoveryHealthFraction must exceed"
                + " PlanSettings.retreatHealthFraction");
  }

  private YamlConfiguration bundledConfig() throws IOException, InvalidConfigurationException {
    YamlConfiguration config = new YamlConfiguration();
    config.loadFromString(bundledText());
    return config;
  }

  private String bundledText() throws IOException {
    return new String(getClass().getResourceAsStream("/config.yml").readAllBytes(), UTF_8);
  }

  private MobAiSettings catalogSettings() {
    return new MobAiSettings(
        new GroupSettings(12, 10, 24.0),
        new MemorySettings(12_000, 1.0, 0.5),
        new SelectionSettings(SelectionPolicyType.THOMPSON_SAMPLING, 0.5, 1.5, 0.1, 10),
        new TargetSettings(600, 0.2, 1.0, 3.0, 0.5),
        new PlanSettings(600, 32.0, 200, 0.3, 0.5),
        new AttackSettings(60, 60, 60, 20.0, 30.0, 4.0, 16.0, 0.8, 3.5),
        new SpiderSettings(60, 1),
        new PersistenceSettings(6_000),
        new DebugSettings(TraceLevel.OFF, 200),
        new RetreatSettings(0.6, 12.0, 600, 200, 1200, 50));
  }
}
