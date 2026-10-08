package io.github.nicodoou.mobai.testsupport;

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
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.settings.VolleySettings;

public final class TestSettings {
  private TestSettings() {}

  public static MobAiSettings defaults() {
    return new MobAiSettings(
        new GroupSettings(12, 10, 24.0),
        new MemorySettings(12_000, 0.7, 0.5),
        new SelectionSettings(SelectionPolicyType.THOMPSON_SAMPLING, 0.5, 1.5, 0.1, 10),
        new TargetSettings(600, 0.2, 1.0, 3.0, 0.5),
        new PlanSettings(600, 32.0, 200, 0.3, 0.5),
        new AttackSettings(60, 60, 60, 8.0, 15.0, 1.0, 16.0, 0.5, 4, 15.0),
        new SpiderSettings(60, 1),
        new PersistenceSettings(6_000),
        new DebugSettings(TraceLevel.OFF, 200),
        new RetreatSettings(0.6, 12.0, 600, 200, 1200, 50),
        new VolleySettings(60, 20, 30, 1.5),
        new SuccessSettings(0.4, 0.4, 0.2, 600));
  }
}
