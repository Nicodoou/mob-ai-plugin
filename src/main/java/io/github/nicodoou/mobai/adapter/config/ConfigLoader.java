package io.github.nicodoou.mobai.adapter.config;

import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.AttackSettings;
import io.github.nicodoou.mobai.domain.settings.DebugSettings;
import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import io.github.nicodoou.mobai.domain.settings.LearningSettings;
import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.PersistenceSettings;
import io.github.nicodoou.mobai.domain.settings.PlanSettings;
import io.github.nicodoou.mobai.domain.settings.PlannerKind;
import io.github.nicodoou.mobai.domain.settings.RetreatSettings;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import io.github.nicodoou.mobai.domain.settings.SpiderSettings;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.settings.TargetSettings;
import io.github.nicodoou.mobai.domain.settings.TraceLevel;
import io.github.nicodoou.mobai.domain.settings.VolleySettings;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Turns config.yml into settings; only presence and type are checked here, ranges are the domain's.
 */
public final class ConfigLoader {
  public MobAiSettings load(ConfigurationSection root) {
    try {
      return new MobAiSettings(
          group(root),
          memory(root),
          selection(root),
          target(root),
          plan(root),
          attack(root),
          spider(root),
          persistence(root),
          debug(root),
          retreat(root),
          volley(root),
          success(root),
          learning(root));
    } catch (IllegalArgumentException exception) {
      throw new InvalidConfigException("config.yml: " + exception.getMessage(), exception);
    }
  }

  private GroupSettings group(ConfigurationSection root) {
    return new GroupSettings(
        Math.toIntExact(wholeNumber(root, "group.max-size")),
        Math.toIntExact(wholeNumber(root, "group.decision-interval-ticks")),
        number(root, "group.detection-radius-blocks"));
  }

  private MemorySettings memory(ConfigurationSection root) {
    return new MemorySettings(
        wholeNumber(root, "memory.half-life-ticks"),
        number(root, "memory.learning-speed"),
        number(root, "memory.partial-hit-weight"));
  }

  private SelectionSettings selection(ConfigurationSection root) {
    return new SelectionSettings(
        choice(root, "selection.default-policy", SelectionPolicyType.class),
        number(root, "selection.memory-multiplier-min"),
        number(root, "selection.memory-multiplier-max"),
        number(root, "selection.epsilon"),
        Math.toIntExact(wholeNumber(root, "selection.explore-first-attempts")));
  }

  private TargetSettings target(ConfigurationSection root) {
    return new TargetSettings(
        wholeNumber(root, "target.threat-window-ticks"),
        number(root, "target.commitment-bonus"),
        number(root, "target.base-threat"),
        number(root, "target.approach-speed-blocks-per-second"),
        number(root, "target.weakness-threat-multiplier-per-level"));
  }

  private PlanSettings plan(ConfigurationSection root) {
    return new PlanSettings(
        wholeNumber(root, "plan.max-duration-ticks"),
        number(root, "plan.target-lost-distance-blocks"),
        wholeNumber(root, "plan.target-lost-ticks"),
        number(root, "plan.retreat-health-fraction"),
        number(root, "plan.full-success-damage-fraction"));
  }

  private AttackSettings attack(ConfigurationSection root) {
    return new AttackSettings(
        wholeNumber(root, "attack.projectile-timeout-ticks"),
        wholeNumber(root, "attack.patient-strike-max-wait-ticks"),
        wholeNumber(root, "attack.opportunistic-shot-max-wait-ticks"),
        number(root, "attack.shoot-min-distance-blocks"),
        number(root, "attack.shoot-max-distance-blocks"),
        number(root, "attack.flank-margin-blocks"),
        number(root, "attack.retreat-distance-blocks"),
        number(root, "attack.evasive-margin-blocks"),
        wholeNumber(root, "attack.evasive-safety-ticks"),
        number(root, "attack.evasive-aim-margin-degrees"),
        number(root, "attack.perch-spacing-blocks"));
  }

  private SpiderSettings spider(ConfigurationSection root) {
    return new SpiderSettings(
        wholeNumber(root, "spider.slowness-duration-ticks"),
        Math.toIntExact(wholeNumber(root, "spider.slowness-level")));
  }

  private PersistenceSettings persistence(ConfigurationSection root) {
    return new PersistenceSettings(wholeNumber(root, "persistence.save-interval-ticks"));
  }

  private DebugSettings debug(ConfigurationSection root) {
    return new DebugSettings(
        choice(root, "debug.default-trace-level", TraceLevel.class),
        Math.toIntExact(wholeNumber(root, "debug.flight-recorder-events")));
  }

  private RetreatSettings retreat(ConfigurationSection root) {
    return new RetreatSettings(
        number(root, "retreat.recovery-health-fraction"),
        number(root, "retreat.heal-safe-distance-blocks"),
        wholeNumber(root, "retreat.regroup-initial-ticks"),
        wholeNumber(root, "retreat.regroup-min-ticks"),
        wholeNumber(root, "retreat.regroup-max-ticks"),
        wholeNumber(root, "retreat.regroup-step-ticks"),
        number(root, "retreat.rally-distance-blocks"),
        number(root, "retreat.rally-arrival-blocks"));
  }

  private VolleySettings volley(ConfigurationSection root) {
    return new VolleySettings(
        wholeNumber(root, "volley.press-ticks"),
        wholeNumber(root, "volley.fall-back-ticks"),
        wholeNumber(root, "volley.fire-ticks"),
        number(root, "volley.fall-back-margin-blocks"));
  }

  private SuccessSettings success(ConfigurationSection root) {
    return new SuccessSettings(
        number(root, "success.damage-weight"),
        number(root, "success.speed-weight"),
        number(root, "success.survival-weight"),
        wholeNumber(root, "success.reference-kill-ticks"),
        number(root, "success.survival-weight-max"),
        number(root, "success.danger-ratio-low"),
        number(root, "success.danger-ratio-high"),
        number(root, "success.danger-prior-damage"));
  }

  private LearningSettings learning(ConfigurationSection root) {
    return new LearningSettings(
        choice(root, "learning.planner", PlannerKind.class),
        number(root, "learning.model-noise-variance"),
        number(root, "learning.prior-variance"),
        number(root, "learning.prior-success"),
        number(root, "learning.exploration-scale"),
        number(root, "learning.training-exploration-scale"),
        wholeNumber(root, "learning.min-reserve-delay-ticks"),
        wholeNumber(root, "learning.max-reserve-delay-ticks"),
        number(root, "learning.max-retreat-health-fraction"),
        wholeNumber(root, "learning.traits-half-life-ticks"),
        wholeNumber(root, "learning.base-weight-plans"));
  }

  private double number(ConfigurationSection root, String path) {
    Object value = requirePresent(root, path);
    if (!(value instanceof Number number)) {
      throw new InvalidConfigException("config.yml: " + path + " must be a number, got " + value);
    }
    return number.doubleValue();
  }

  private long wholeNumber(ConfigurationSection root, String path) {
    double value = number(root, path);
    if (value != Math.rint(value)) {
      throw new InvalidConfigException(
          "config.yml: " + path + " must be a whole number, got " + root.get(path));
    }
    return (long) value;
  }

  private <E extends Enum<E>> E choice(ConfigurationSection root, String path, Class<E> type) {
    Object value = requirePresent(root, path);
    if (value instanceof String text) {
      for (E constant : type.getEnumConstants()) {
        if (constant.name().equals(text)) {
          return constant;
        }
      }
    }
    String allowed =
        Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    throw new InvalidConfigException(
        "config.yml: " + path + " must be one of " + allowed + ", got " + value);
  }

  private Object requirePresent(ConfigurationSection root, String path) {
    Object value = root.get(path);
    if (value == null) {
      throw new InvalidConfigException("config.yml: missing " + path);
    }
    return value;
  }
}
