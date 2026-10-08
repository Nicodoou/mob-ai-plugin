package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.application.PlayerMemoryView;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** The lines /mobai memory sends for one player, best strategies and attacks first. */
final class MemoryReport {
  private static final double PERCENT = 100.0;

  private MemoryReport() {}

  static List<MessageLine> linesFor(String playerName, List<PlayerMemoryView> views) {
    if (views.isEmpty()) {
      return List.of(new MessageLine(MessageKey.MEMORY_EMPTY, Map.of("player", playerName)));
    }
    List<MessageLine> lines = new ArrayList<>();
    views.forEach(view -> lines.addAll(linesOf(view)));
    return List.copyOf(lines);
  }

  private static List<MessageLine> linesOf(PlayerMemoryView view) {
    List<MessageLine> lines = new ArrayList<>();
    lines.add(header(view));
    lines.addAll(
        estimateLines(
            view.strategies(),
            MessageKey.MEMORY_STRATEGY,
            "strategy",
            strategy -> strategy.value()));
    lines.addAll(
        estimateLines(view.attacks(), MessageKey.MEMORY_ATTACK, "attack", attack -> attack.id()));
    return lines;
  }

  private static MessageLine header(PlayerMemoryView view) {
    return new MessageLine(
        MessageKey.MEMORY_GROUP,
        Map.of(
            "group", view.group().shortId(),
            "danger", String.format(Locale.ROOT, "%.2f", view.danger()),
            "lost", String.format(Locale.ROOT, "%.1f", view.dangerRecord().healthLost()),
            "dealt", String.format(Locale.ROOT, "%.1f", view.dangerRecord().damageDealt())));
  }

  private static <K> List<MessageLine> estimateLines(
      Map<K, SuccessEstimate> estimates, MessageKey key, String nameKey, Function<K, String> name) {
    Comparator<Map.Entry<K, SuccessEstimate>> byMeanDescending =
        Comparator.comparingDouble((Map.Entry<K, SuccessEstimate> entry) -> entry.getValue().mean())
            .reversed();
    return estimates.entrySet().stream()
        .sorted(byMeanDescending.thenComparing(entry -> name.apply(entry.getKey())))
        .map(entry -> estimateLine(key, nameKey, name.apply(entry.getKey()), entry.getValue()))
        .toList();
  }

  private static MessageLine estimateLine(
      MessageKey key, String nameKey, String name, SuccessEstimate estimate) {
    return new MessageLine(
        key,
        Map.of(
            nameKey,
            name,
            "rate",
            String.valueOf(Math.round(estimate.mean() * PERCENT)),
            "support",
            String.format(Locale.ROOT, "%.1f", estimate.observedAttempts())));
  }
}
