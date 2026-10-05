package io.github.nicodoou.mobai.domain.settings;

import static org.assertj.core.api.Assertions.*;

import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SettingsValidationTest {

  @Test
  void catalogDefaultsAreValid() {
    MobAiSettings settings = TestSettings.defaults();

    assertThat(settings.group().maxGroupSize()).isEqualTo(12);
    assertThat(settings.memory().halfLifeTicks()).isEqualTo(12_000);
    assertThat(settings.memory().learningSpeed()).isEqualTo(0.7);
    assertThat(settings.selection().defaultPolicy())
        .isEqualTo(SelectionPolicyType.THOMPSON_SAMPLING);
    assertThat(settings.attack().projectileTimeoutTicks()).isEqualTo(60);
    assertThat(settings.persistence().saveIntervalTicks()).isEqualTo(6_000);
    assertThat(settings.debug().defaultTraceLevel()).isEqualTo(TraceLevel.OFF);
  }

  @Test
  void boundaryValuesAreAccepted() {
    new MemorySettings(1, 0.0, 0.0);
    new MemorySettings(1, 1.0, 1.0);
    new SelectionSettings(SelectionPolicyType.RANDOM, 1.0, 1.0, 0.0, 0);
    new SelectionSettings(SelectionPolicyType.RANDOM, 1.0, 1.0, 1.0, 0);
    new AttackSettings(1, 1, 1, 8.0, 8.0, 1.0, 1.0);
    new PlanSettings(1, 1.0, 1, 0.0, 1.0);
    new TargetSettings(1, 0.0, 1.0, 1.0, 1.0);
  }

  @ParameterizedTest
  @MethodSource("outOfRangeValuesProvider")
  void outOfRangeValuesAreRejectedWithTheirFieldName(
      String expectedMessage, Runnable construction) {
    assertThatThrownBy(construction::run)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(expectedMessage);
  }

  static Stream<org.junit.jupiter.params.provider.Arguments> outOfRangeValuesProvider() {
    return Stream.of(
        org.junit.jupiter.params.provider.Arguments.of(
            "GroupSettings.maxGroupSize must be at least 1, got 0",
            (Runnable) () -> new GroupSettings(0, 10, 24.0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "GroupSettings.decisionIntervalTicks must be at least 1, got 0",
            (Runnable) () -> new GroupSettings(12, 0, 24.0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "GroupSettings.detectionRadiusBlocks must be a positive number, got 0.0",
            (Runnable) () -> new GroupSettings(12, 10, 0.0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "MemorySettings.halfLifeTicks must be at least 1, got 0",
            (Runnable) () -> new MemorySettings(0, 0.7, 0.5)),
        org.junit.jupiter.params.provider.Arguments.of(
            "MemorySettings.learningSpeed must be between 0.0 and 1.0, got 1.1",
            (Runnable) () -> new MemorySettings(12_000, 1.1, 0.5)),
        org.junit.jupiter.params.provider.Arguments.of(
            "MemorySettings.learningSpeed must be between 0.0 and 1.0, got NaN",
            (Runnable) () -> new MemorySettings(12_000, Double.NaN, 0.5)),
        org.junit.jupiter.params.provider.Arguments.of(
            "MemorySettings.partialHitWeight must be between 0.0 and 1.0, got -0.1",
            (Runnable) () -> new MemorySettings(12_000, 0.7, -0.1)),
        org.junit.jupiter.params.provider.Arguments.of(
            "SelectionSettings.memoryMultiplierMin must not exceed SelectionSettings.memoryMultiplierMax, got 1.6 > 1.5",
            (Runnable)
                () ->
                    new SelectionSettings(
                        SelectionPolicyType.THOMPSON_SAMPLING, 1.6, 1.5, 0.1, 10)),
        org.junit.jupiter.params.provider.Arguments.of(
            "SelectionSettings.epsilon must be between 0.0 and 1.0, got 2.0",
            (Runnable)
                () ->
                    new SelectionSettings(
                        SelectionPolicyType.THOMPSON_SAMPLING, 0.5, 1.5, 2.0, 10)),
        org.junit.jupiter.params.provider.Arguments.of(
            "SelectionSettings.exploreFirstAttempts must be at least 0, got -1",
            (Runnable)
                () ->
                    new SelectionSettings(
                        SelectionPolicyType.THOMPSON_SAMPLING, 0.5, 1.5, 0.1, -1)),
        org.junit.jupiter.params.provider.Arguments.of(
            "TargetSettings.commitmentBonus must be zero or positive, got -0.2",
            (Runnable) () -> new TargetSettings(600, -0.2, 1.0, 3.0, 0.5)),
        org.junit.jupiter.params.provider.Arguments.of(
            "TargetSettings.weaknessThreatMultiplierPerLevel must be a positive number, got 0.0",
            (Runnable) () -> new TargetSettings(600, 0.2, 1.0, 3.0, 0.0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "PlanSettings.retreatHealthFraction must be between 0.0 and 1.0, got 1.5",
            (Runnable) () -> new PlanSettings(600, 32.0, 200, 1.5, 0.5)),
        org.junit.jupiter.params.provider.Arguments.of(
            "AttackSettings.shootMinDistanceBlocks must not exceed AttackSettings.shootMaxDistanceBlocks, got 16.0 > 15.0",
            (Runnable) () -> new AttackSettings(60, 60, 60, 16.0, 15.0, 3.0, 16.0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "SpiderSettings.slownessLevel must be at least 1, got 0",
            (Runnable) () -> new SpiderSettings(60, 0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "PersistenceSettings.saveIntervalTicks must be at least 1, got 0",
            (Runnable) () -> new PersistenceSettings(0)),
        org.junit.jupiter.params.provider.Arguments.of(
            "DebugSettings.flightRecorderEvents must be at least 1, got 0",
            (Runnable) () -> new DebugSettings(TraceLevel.OFF, 0)));
  }

  @Test
  void missingSectionIsRejected() {
    MobAiSettings defaults = TestSettings.defaults();
    assertThatThrownBy(
            () ->
                new MobAiSettings(
                    defaults.group(),
                    null,
                    defaults.selection(),
                    defaults.target(),
                    defaults.plan(),
                    defaults.attack(),
                    defaults.spider(),
                    defaults.persistence(),
                    defaults.debug(),
                    defaults.retreat()))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("MobAiSettings.memory");
  }

  @Test
  void recoveryMustExceedTheRetreatThreshold() {
    MobAiSettings defaults = TestSettings.defaults();

    assertThatThrownBy(
            () ->
                new MobAiSettings(
                    defaults.group(),
                    defaults.memory(),
                    defaults.selection(),
                    defaults.target(),
                    defaults.plan(),
                    defaults.attack(),
                    defaults.spider(),
                    defaults.persistence(),
                    defaults.debug(),
                    new RetreatSettings(0.3, 12.0, 600, 200, 1200, 50)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "RetreatSettings.recoveryHealthFraction must exceed PlanSettings.retreatHealthFraction, got 0.3 <= 0.3");
  }
}
