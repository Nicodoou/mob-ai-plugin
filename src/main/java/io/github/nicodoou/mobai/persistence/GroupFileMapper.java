package io.github.nicodoou.mobai.persistence;

import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredAttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.port.StoredStrategyRecord;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import java.util.UUID;

final class GroupFileMapper {
  GroupFile toFile(StoredGroup group) {
    return new GroupFile(
        SchemaMigrator.CURRENT_VERSION,
        group.id().value().toString(),
        group.policy().name(),
        group.lastPlanSequence(),
        group.members().stream().map(GroupFileMapper::toEntry).toList(),
        group.attackRecords().stream().map(GroupFileMapper::toEntry).toList(),
        group.strategyRecords().stream().map(GroupFileMapper::toEntry).toList());
  }

  StoredGroup fromFile(GroupFile file) {
    return new StoredGroup(
        new GroupId(UUID.fromString(required(file.groupId(), "GroupFile.groupId"))),
        SelectionPolicyType.valueOf(required(file.policy(), "GroupFile.policy")),
        file.lastPlanSequence(),
        required(file.members(), "GroupFile.members").stream()
            .map(GroupFileMapper::toMember)
            .toList(),
        required(file.attackRecords(), "GroupFile.attackRecords").stream()
            .map(GroupFileMapper::toAttackRecord)
            .toList(),
        required(file.strategyRecords(), "GroupFile.strategyRecords").stream()
            .map(GroupFileMapper::toStrategyRecord)
            .toList());
  }

  StateFile toFile(StoredState state) {
    return new StateFile(
        SchemaMigrator.CURRENT_VERSION, state.serverTick(), state.regroupWindowTicks());
  }

  StoredState fromFile(StateFile file) {
    return new StoredState(file.serverTick(), file.regroupWindowTicks());
  }

  private static MemberEntry toEntry(Member member) {
    return new MemberEntry(
        member.id().value().toString(), member.kind().name(), member.joinOrder());
  }

  private static RecordEntry toEntry(StoredAttackRecord stored) {
    return toEntry(stored.player(), stored.attack().id(), stored.record());
  }

  private static RecordEntry toEntry(StoredStrategyRecord stored) {
    return toEntry(stored.player(), stored.strategy().value(), stored.record());
  }

  private static RecordEntry toEntry(PlayerId player, String key, AttackRecord record) {
    return new RecordEntry(
        player.value().toString(),
        key,
        record.successes(),
        record.attempts(),
        record.lastUpdateTick());
  }

  private static Member toMember(MemberEntry entry) {
    required(entry, "GroupFile.members[]");
    return new Member(
        new MobId(UUID.fromString(required(entry.mobId(), "MemberEntry.mobId"))),
        MobKind.valueOf(required(entry.kind(), "MemberEntry.kind")),
        entry.joinOrder());
  }

  private static StoredAttackRecord toAttackRecord(RecordEntry entry) {
    required(entry, "GroupFile.attackRecords[]");
    String key = required(entry.key(), "RecordEntry.key");
    Attack attack =
        Attack.fromId(key)
            .orElseThrow(() -> new IllegalArgumentException("Unknown attack id '" + key + "'"));
    return new StoredAttackRecord(toPlayer(entry), attack, toRecord(entry));
  }

  private static StoredStrategyRecord toStrategyRecord(RecordEntry entry) {
    required(entry, "GroupFile.strategyRecords[]");
    StrategyId strategy = new StrategyId(required(entry.key(), "RecordEntry.key"));
    return new StoredStrategyRecord(toPlayer(entry), strategy, toRecord(entry));
  }

  private static PlayerId toPlayer(RecordEntry entry) {
    return new PlayerId(UUID.fromString(required(entry.playerId(), "RecordEntry.playerId")));
  }

  private static AttackRecord toRecord(RecordEntry entry) {
    return new AttackRecord(entry.successes(), entry.attempts(), entry.lastUpdateTick());
  }

  private static <T> T required(T value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is missing");
    }
    return value;
  }
}
