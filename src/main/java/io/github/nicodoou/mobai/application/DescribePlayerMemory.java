package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class DescribePlayerMemory {
  private static final Comparator<StrategyId> BY_STRATEGY = Comparator.comparing(StrategyId::value);

  private final ActiveGroups activeGroups;

  public DescribePlayerMemory(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DescribePlayerMemory.activeGroups");
  }

  public List<PlayerMemoryView> execute(PlayerId player, long tick) {
    return activeGroups.groups().stream()
        .filter(group -> remembers(group, player))
        .map(group -> view(group, player, tick))
        .toList();
  }

  private static boolean remembers(Group group, PlayerId player) {
    return group.memory().attackRecords().containsKey(player)
        || group.memory().strategyRecords().containsKey(player);
  }

  private static PlayerMemoryView view(Group group, PlayerId player, long tick) {
    GroupMemory memory = group.memory();
    return new PlayerMemoryView(
        group.id(),
        player,
        attackEstimates(memory, player, tick),
        strategyEstimates(memory, player, tick));
  }

  private static Map<Attack, SuccessEstimate> attackEstimates(
      GroupMemory memory, PlayerId player, long tick) {
    Map<Attack, SuccessEstimate> estimates = new LinkedHashMap<>();
    memory.attackRecords().getOrDefault(player, Map.of()).keySet().stream()
        .sorted()
        .forEach(attack -> estimates.put(attack, memory.attackEstimate(player, attack, tick)));
    return estimates;
  }

  private static Map<StrategyId, SuccessEstimate> strategyEstimates(
      GroupMemory memory, PlayerId player, long tick) {
    Map<StrategyId, SuccessEstimate> estimates = new LinkedHashMap<>();
    memory.strategyRecords().getOrDefault(player, Map.of()).keySet().stream()
        .sorted(BY_STRATEGY)
        .forEach(
            strategy -> estimates.put(strategy, memory.strategyEstimate(player, strategy, tick)));
    return estimates;
  }
}
