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
import java.util.Optional;
import java.util.function.Supplier;

public final class GroupMemory {
  private static final double FULL_WEIGHT = 1.0;

  private final Supplier<MemorySettings> settings;
  private final Map<PlayerId, Map<Attack, AttackRecord>> attackRecords = new HashMap<>();
  private final Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords = new HashMap<>();
  private final Map<PlayerId, DangerRecord> dangerRecords = new HashMap<>();
  private final Map<PlayerId, RecipeModelRecord> recipeModels = new HashMap<>();

  public GroupMemory(Supplier<MemorySettings> settings) {
    this.settings = Objects.requireNonNull(settings, "GroupMemory.settings");
  }

  public static GroupMemory restore(Supplier<MemorySettings> settings, MemoryRecords records) {
    GroupMemory memory = new GroupMemory(settings);
    records
        .attackRecords()
        .forEach(
            (player, attacks) -> {
              Map<Attack, AttackRecord> copy = new EnumMap<>(Attack.class);
              copy.putAll(attacks);
              memory.attackRecords.put(player, copy);
            });
    records
        .strategyRecords()
        .forEach(
            (player, strategies) -> memory.strategyRecords.put(player, new HashMap<>(strategies)));
    memory.dangerRecords.putAll(records.dangerRecords());
    return memory;
  }

  public RecordChange recordAttack(AttackObservation observation) {
    Map<Attack, AttackRecord> records =
        attackRecords.computeIfAbsent(observation.player(), player -> new EnumMap<>(Attack.class));
    return applyObservation(
        records,
        new KeyedObservation<>(
            observation.attack(), observation.credit(), FULL_WEIGHT, observation.tick()));
  }

  public RecordChange recordStrategy(StrategyObservation observation) {
    Map<StrategyId, AttackRecord> records =
        strategyRecords.computeIfAbsent(observation.player(), player -> new HashMap<>());
    return applyObservation(
        records,
        new KeyedObservation<>(
            observation.strategy(),
            observation.credit(),
            observation.weight(),
            observation.tick()));
  }

  public void recordDanger(DangerObservation observation) {
    DangerRecord before = dangerRecord(observation.player(), observation.tick());
    dangerRecords.put(
        observation.player(), before.withPlan(observation.healthLost(), observation.damageDealt()));
  }

  public DangerRecord dangerRecord(PlayerId player, long tick) {
    DangerRecord stored = dangerRecords.getOrDefault(player, DangerRecord.empty(tick));
    return stored.decayedTo(tick, halfLifeTicks());
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
    return priorFromSettings().estimate(combinedKindRecord(player, kind, tick));
  }

  public void clearPlayer(PlayerId player) {
    attackRecords.remove(player);
    strategyRecords.remove(player);
    dangerRecords.remove(player);
    recipeModels.remove(player);
  }

  public void clear() {
    attackRecords.clear();
    strategyRecords.clear();
    dangerRecords.clear();
    recipeModels.clear();
  }

  public Map<PlayerId, Map<Attack, AttackRecord>> attackRecords() {
    return immutableCopy(attackRecords);
  }

  public Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords() {
    return immutableCopy(strategyRecords);
  }

  public Map<PlayerId, DangerRecord> dangerRecords() {
    return Map.copyOf(dangerRecords);
  }

  public Optional<RecipeModelRecord> recipeModel(PlayerId player) {
    return Optional.ofNullable(recipeModels.get(player));
  }

  public void storeRecipeModel(PlayerId player, RecipeModelRecord record) {
    Objects.requireNonNull(player, "GroupMemory.player");
    Objects.requireNonNull(record, "GroupMemory.record");
    recipeModels.put(player, record);
  }

  public Map<PlayerId, RecipeModelRecord> recipeModels() {
    return Map.copyOf(recipeModels);
  }

  private AttackRecord combinedKindRecord(PlayerId player, MobKind kind, long tick) {
    Map<Attack, AttackRecord> records = attackRecords.get(player);
    double successes = 0;
    double attempts = 0;
    for (Attack attack : Attack.forKind(kind)) {
      AttackRecord decayed = storedOrEmpty(records, attack, tick).decayedTo(tick, halfLifeTicks());
      successes += decayed.successes();
      attempts += decayed.attempts();
    }
    return new AttackRecord(successes, attempts, tick);
  }

  private <K> RecordChange applyObservation(
      Map<K, AttackRecord> records, KeyedObservation<K> observation) {
    long tick = observation.tick();
    AttackRecord before =
        storedOrEmpty(records, observation.key(), tick).decayedTo(tick, halfLifeTicks());
    AttackRecord after = before.withObservation(observation.credit(), observation.weight());
    records.put(observation.key(), after);
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

  private record KeyedObservation<K>(K key, double credit, double weight, long tick) {}
}
