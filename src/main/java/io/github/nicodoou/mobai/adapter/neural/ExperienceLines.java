package io.github.nicodoou.mobai.adapter.neural;

import io.github.nicodoou.mobai.domain.event.NeuralDecided;
import io.github.nicodoou.mobai.domain.neural.NeuralAction;
import io.github.nicodoou.mobai.domain.neural.NeuralStep;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * Formats the JSON lines the trainer reads. Written by hand because it runs for every mob on every
 * decision, and every value is a number or an identifier with nothing to escape.
 */
final class ExperienceLines {
  // Six decimals are far more than the network can tell apart, and keep the files much smaller.
  private static final double ROUNDING_SCALE = 1e6;
  private static final String LINE_SEPARATOR = "\n";

  String steps(NeuralDecided decision) {
    return decision.steps().stream()
        .map(step -> step(decision, step))
        .collect(Collectors.joining(LINE_SEPARATOR));
  }

  String event(ExperienceEvent event) {
    return "{\"t\":\"event\",\"tick\":"
        + event.tick()
        + ",\"group\":\""
        + idOf(event.group())
        + "\",\"mob\":"
        + event.mob().map(ExperienceLines::quotedId).orElse("null")
        + ",\"kind\":\""
        + event.kind().name()
        + "\",\"amount\":"
        + rounded(event.amount())
        + "}";
  }

  String end(long tick, GroupId group, FightWinner winner) {
    return "{\"t\":\"end\",\"tick\":"
        + tick
        + ",\"group\":\""
        + idOf(group)
        + "\",\"winner\":\""
        + winner.name()
        + "\"}";
  }

  private static String step(NeuralDecided decision, NeuralStep step) {
    return "{\"t\":\"step\",\"tick\":"
        + decision.tick()
        + ",\"group\":\""
        + idOf(decision.groupId())
        + "\",\"mob\":"
        + quotedId(step.mob())
        + ",\"obs\":"
        + observation(step.observation())
        + ",\"mask\":"
        + mask(step)
        + ",\"action\":"
        + step.action().ordinal()
        + ",\"logp\":"
        + rounded(step.logProbability())
        + "}";
  }

  private static String observation(List<Double> values) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    values.forEach(value -> joiner.add(String.valueOf(rounded(value))));
    return joiner.toString();
  }

  private static String mask(NeuralStep step) {
    StringJoiner joiner = new StringJoiner(",", "[", "]");
    for (boolean allowed : NeuralAction.maskOf(step.kind())) {
      joiner.add(allowed ? "1" : "0");
    }
    return joiner.toString();
  }

  private static double rounded(double value) {
    return Math.round(value * ROUNDING_SCALE) / ROUNDING_SCALE;
  }

  private static String idOf(GroupId group) {
    return group.shortId();
  }

  private static String quotedId(MobId mob) {
    return "\"" + mob.value() + "\"";
  }
}
