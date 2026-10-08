package io.github.nicodoou.mobai.application;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.MemoryRecords;
import io.github.nicodoou.mobai.domain.port.StoredAttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredDangerRecord;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredStrategyRecord;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StoredMemoriesMapper {
  private static final Comparator<PlayerId> BY_PLAYER = Comparator.comparing(PlayerId::value);
  private static final Comparator<StrategyId> BY_STRATEGY = Comparator.comparing(StrategyId::value);

  public StoredGroup toStored(Group group) {
    return new StoredGroup(
        group.id(),
        group.policy(),
        group.lifecycle().planSequence(),
        group.roster().members(),
        storedAttackRecords(group.memory()),
        storedStrategyRecords(group.memory()),
        storedDangerRecords(group.memory()));
  }

  public Group toGroup(StoredGroup stored, SettingsHolder settings) {
    Group group = new Group(stored.id(), stored.policy(), restoredKnowledge(stored, settings));
    stored.members().forEach(group.roster()::restoreMember);
    group.lifecycle().restorePlanSequence(stored.lastPlanSequence());
    return group;
  }

  private static List<StoredAttackRecord> storedAttackRecords(GroupMemory memory) {
    Map<PlayerId, Map<Attack, AttackRecord>> records = memory.attackRecords();
    return records.keySet().stream()
        .sorted(BY_PLAYER)
        .flatMap(
            player ->
                records.get(player).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .map(entry -> new StoredAttackRecord(player, entry.getKey(), entry.getValue())))
        .toList();
  }

  private static List<StoredStrategyRecord> storedStrategyRecords(GroupMemory memory) {
    Map<PlayerId, Map<StrategyId, AttackRecord>> records = memory.strategyRecords();
    return records.keySet().stream()
        .sorted(BY_PLAYER)
        .flatMap(
            player ->
                records.get(player).entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(BY_STRATEGY))
                    .map(
                        entry ->
                            new StoredStrategyRecord(player, entry.getKey(), entry.getValue())))
        .toList();
  }

  private static List<StoredDangerRecord> storedDangerRecords(GroupMemory memory) {
    Map<PlayerId, DangerRecord> records = memory.dangerRecords();
    return records.keySet().stream()
        .sorted(BY_PLAYER)
        .map(player -> new StoredDangerRecord(player, records.get(player)))
        .toList();
  }

  private static GroupKnowledge restoredKnowledge(StoredGroup stored, SettingsHolder settings) {
    return new GroupKnowledge(
        GroupMemory.restore(
            settings.section(MobAiSettings::memory),
            new MemoryRecords(attackMaps(stored), strategyMaps(stored), dangerMap(stored))),
        new ThreatLedger(settings.section(MobAiSettings::target)));
  }

  private static Map<PlayerId, Map<Attack, AttackRecord>> attackMaps(StoredGroup stored) {
    Map<PlayerId, Map<Attack, AttackRecord>> maps = new LinkedHashMap<>();
    for (StoredAttackRecord entry : stored.attackRecords()) {
      maps.computeIfAbsent(entry.player(), player -> new LinkedHashMap<>())
          .put(entry.attack(), entry.record());
    }
    return maps;
  }

  private static Map<PlayerId, Map<StrategyId, AttackRecord>> strategyMaps(StoredGroup stored) {
    Map<PlayerId, Map<StrategyId, AttackRecord>> maps = new LinkedHashMap<>();
    for (StoredStrategyRecord entry : stored.strategyRecords()) {
      maps.computeIfAbsent(entry.player(), player -> new LinkedHashMap<>())
          .put(entry.strategy(), entry.record());
    }
    return maps;
  }

  private static Map<PlayerId, DangerRecord> dangerMap(StoredGroup stored) {
    Map<PlayerId, DangerRecord> map = new LinkedHashMap<>();
    for (StoredDangerRecord entry : stored.dangerRecords()) {
      map.put(entry.player(), entry.record());
    }
    return map;
  }
}
