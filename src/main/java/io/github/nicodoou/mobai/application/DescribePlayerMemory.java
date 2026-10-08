package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.DangerLevel;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.SuccessEstimate;
import io.github.nicodoou.mobai.domain.settings.SuccessSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class DescribePlayerMemory {
  private static final Comparator<StrategyId> BY_STRATEGY = Comparator.comparing(StrategyId::value);

  private final ActiveGroups activeGroups;
  private final Supplier<SuccessSettings> success;

  public DescribePlayerMemory(ActiveGroups activeGroups, Supplier<SuccessSettings> success) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DescribePlayerMemory.activeGroups");
    this.success = Objects.requireNonNull(success, "DescribePlayerMemory.success");
  }

  public List<PlayerMemoryView> execute(PlayerId player, long tick) {
    return activeGroups.groups().stream()
        .filter(group -> remembers(group, player))
        .map(group -> view(group, player, tick))
        .toList();
  }

  private static boolean remembers(Group group, PlayerId player) {
    return group.memory().attackRecords().containsKey(player)
        || group.memory().strategyRecords().containsKey(player)
        || group.memory().dangerRecords().containsKey(player);
  }

  private PlayerMemoryView view(Group group, PlayerId player, long tick) {
    GroupMemory memory = group.memory();
    DangerRecord record = memory.dangerRecord(player, tick);
    return new PlayerMemoryView(
        group.id(),
        player,
        attackEstimates(memory, player, tick),
        strategyEstimates(memory, player, tick),
        record,
        DangerLevel.of(record, success.get()));
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
