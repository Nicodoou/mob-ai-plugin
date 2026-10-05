package io.github.nicodoou.mobai.domain.memory;

import io.github.nicodoou.mobai.domain.settings.MemorySettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

public final class GroupMemory {
  private final Supplier<MemorySettings> settings;
  private final Map<PlayerId, Map<Attack, AttackRecord>> attackRecords = new HashMap<>();
  private final Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords = new HashMap<>();

  public GroupMemory(Supplier<MemorySettings> settings) {
    this.settings = Objects.requireNonNull(settings, "GroupMemory.settings");
  }

  public static GroupMemory restore(
      Supplier<MemorySettings> settings,
      Map<PlayerId, Map<Attack, AttackRecord>> attackRecords,
      Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords) {
    GroupMemory memory = new GroupMemory(settings);
    attackRecords.forEach(
        (player, records) -> {
          Map<Attack, AttackRecord> copy = new EnumMap<>(Attack.class);
          copy.putAll(records);
          memory.attackRecords.put(player, copy);
        });
    strategyRecords.forEach(
        (player, records) -> memory.strategyRecords.put(player, new HashMap<>(records)));
    return memory;
  }

  public RecordChange recordAttack(AttackObservation observation) {
    Map<Attack, AttackRecord> records =
        attackRecords.computeIfAbsent(observation.player(), player -> new EnumMap<>(Attack.class));
    return applyObservation(
        records, observation.attack(), observation.credit(), 1.0, observation.tick());
  }

  public RecordChange recordStrategy(StrategyObservation observation) {
    Map<StrategyId, AttackRecord> records =
        strategyRecords.computeIfAbsent(observation.player(), player -> new HashMap<>());
    return applyObservation(
        records,
        observation.strategy(),
        observation.credit(),
        observation.weight(),
        observation.tick());
  }

  public SuccessEstimate attackEstimate(PlayerId player, Attack attack, long tick) {
    AttackRecord record = storedOrEmpty(attackRecords.get(player), attack, tick);
    return priorFromSettings().estimate(record.decayedTo(tick, halfLifeTicks()));
  }

  public SuccessEstimate strategyEstimate(PlayerId player, StrategyId strategy, long tick) {
    AttackRecord record = storedOrEmpty(strategyRecords.get(player), strategy, tick);
    return priorFromSettings().estimate(record.decayedTo(tick, halfLifeTicks()));
  }

  public SuccessEstimate kindEstimate(PlayerId player, MobKind kind, long tick) {
    Map<Attack, AttackRecord> records = attackRecords.get(player);
    double successes = 0;
    double attempts = 0;
    for (Attack attack : Attack.forKind(kind)) {
      AttackRecord decayed = storedOrEmpty(records, attack, tick).decayedTo(tick, halfLifeTicks());
      successes += decayed.successes();
      attempts += decayed.attempts();
    }
    return priorFromSettings().estimate(new AttackRecord(successes, attempts, tick));
  }

  public void clearPlayer(PlayerId player) {
    attackRecords.remove(player);
    strategyRecords.remove(player);
  }

  public void clear() {
    attackRecords.clear();
    strategyRecords.clear();
  }

  public Map<PlayerId, Map<Attack, AttackRecord>> attackRecords() {
    return immutableCopy(attackRecords);
  }

  public Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords() {
    return immutableCopy(strategyRecords);
  }

  private <K> RecordChange applyObservation(
      Map<K, AttackRecord> records, K key, double credit, double weight, long tick) {
    AttackRecord before = storedOrEmpty(records, key, tick).decayedTo(tick, halfLifeTicks());
    AttackRecord after = before.withObservation(credit, weight);
    records.put(key, after);
    return new RecordChange(before, after);
  }

  private static <K> AttackRecord storedOrEmpty(Map<K, AttackRecord> records, K key, long tick) {
    if (records == null) {
      return AttackRecord.empty(tick);
    }
    return records.getOrDefault(key, AttackRecord.empty(tick));
  }

  private static <K, V> Map<PlayerId, Map<K, V>> immutableCopy(Map<PlayerId, Map<K, V>> records) {
    Map<PlayerId, Map<K, V>> copy = new HashMap<>();
    records.forEach((player, inner) -> copy.put(player, Map.copyOf(inner)));
    return Map.copyOf(copy);
  }

  private long halfLifeTicks() {
    return settings.get().halfLifeTicks();
  }

  private LearningPrior priorFromSettings() {
    return LearningPrior.fromLearningSpeed(settings.get().learningSpeed());
  }
}
