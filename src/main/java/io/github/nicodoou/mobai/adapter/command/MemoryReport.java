package io.github.nicodoou.mobai.adapter.command;

import io.github.nicodoou.mobai.adapter.config.MessageKey;
import io.github.nicodoou.mobai.application.PlayerMemoryView;
import io.github.nicodoou.mobai.application.RecipeAdvice;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.strategy.PlanRecipe;
import io.github.nicodoou.mobai.domain.strategy.PlayerTraits;
import io.github.nicodoou.mobai.domain.strategy.RecipeEstimate;
import io.github.nicodoou.mobai.domain.strategy.RoleSplit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** The lines /mobai memory sends for one player, best strategies and attacks first. */
final class MemoryReport {
  private static final double PERCENT = 100.0;
  private static final LineKind STRATEGY_LINE =
      new LineKind(MessageKey.MEMORY_STRATEGY, "strategy");
  private static final LineKind ATTACK_LINE = new LineKind(MessageKey.MEMORY_ATTACK, "attack");

  private record LineKind(MessageKey key, String nameKey) {}

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
    view.recipes().ifPresent(advice -> lines.addAll(recipeLines(advice)));
    lines.addAll(estimateLines(view.strategies(), STRATEGY_LINE, strategy -> strategy.value()));
    lines.addAll(estimateLines(view.attacks(), ATTACK_LINE, attack -> attack.id()));
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

  private static List<MessageLine> recipeLines(RecipeAdvice advice) {
    List<MessageLine> lines = new ArrayList<>();
    lines.add(recipesHeader(advice));
    for (int index = 0; index < advice.best().size(); index++) {
      lines.add(recipeLine(index + 1, advice.best().get(index)));
    }
    return lines;
  }

  private static MessageLine recipesHeader(RecipeAdvice advice) {
    PlayerTraits traits = advice.traits();
    return new MessageLine(
        MessageKey.MEMORY_RECIPES,
        Map.of(
            "shield", String.format(Locale.ROOT, "%.2f", traits.shield()),
            "ranged", String.format(Locale.ROOT, "%.2f", traits.ranged()),
            "armor", String.format(Locale.ROOT, "%.2f", traits.armor()),
            "plans", String.format(Locale.ROOT, "%.0f", advice.observations())));
  }

  private static MessageLine recipeLine(int rank, RecipeEstimate estimate) {
    PlanRecipe recipe = estimate.recipe();
    MessageKey key = recipe.volley() ? MessageKey.MEMORY_RECIPE_VOLLEY : MessageKey.MEMORY_RECIPE;
    return new MessageLine(
        key,
        Map.of(
            "rank", String.valueOf(rank),
            "zombies", zombieSplit(recipe.zombies()),
            "spiders", recipe.spiders().press() + "/" + recipe.spiders().flank(),
            "delay", String.valueOf(recipe.reserveDelayTicks()),
            "retreat", String.valueOf(Math.round(recipe.retreatHealthFraction() * PERCENT)),
            "rate", String.valueOf(clampedPercent(estimate.predictedSuccess()))));
  }

  private static String zombieSplit(RoleSplit split) {
    return split.press() + "/" + split.flank() + "/" + split.reserve();
  }

  private static long clampedPercent(double fraction) {
    return Math.max(0, Math.min((long) PERCENT, Math.round(fraction * PERCENT)));
  }

  private static <K> List<MessageLine> estimateLines(
      Map<K, SuccessEstimate> estimates, LineKind kind, Function<K, String> name) {
    Comparator<Map.Entry<K, SuccessEstimate>> byMeanDescending =
        Comparator.comparingDouble((Map.Entry<K, SuccessEstimate> entry) -> entry.getValue().mean())
            .reversed();
    return estimates.entrySet().stream()
        .sorted(byMeanDescending.thenComparing(entry -> name.apply(entry.getKey())))
        .map(entry -> estimateLine(kind, name.apply(entry.getKey()), entry.getValue()))
        .toList();
  }

  private static MessageLine estimateLine(LineKind kind, String name, SuccessEstimate estimate) {
    return new MessageLine(
        kind.key(),
        Map.of(
            kind.nameKey(),
            name,
            "rate",
            String.valueOf(Math.round(estimate.mean() * PERCENT)),
            "support",
            String.format(Locale.ROOT, "%.1f", estimate.observedAttempts())));
  }
}
