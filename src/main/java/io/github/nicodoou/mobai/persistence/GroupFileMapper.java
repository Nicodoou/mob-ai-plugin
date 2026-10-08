package io.github.nicodoou.mobai.persistence;

import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.DangerRecord;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.port.StoredAttackRecord;
import io.github.nicodoou.mobai.domain.port.StoredDangerRecord;
import io.github.nicodoou.mobai.domain.port.StoredGroup;
import io.github.nicodoou.mobai.domain.port.StoredRecipeModel;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.port.StoredStrategyRecord;
import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.strategy.TraitSums;
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
        group.strategyRecords().stream().map(GroupFileMapper::toEntry).toList(),
        group.dangerRecords().stream().map(GroupFileMapper::toEntry).toList(),
        group.recipeModels().stream().map(GroupFileMapper::toEntry).toList());
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
            .toList(),
        required(file.dangerRecords(), "GroupFile.dangerRecords").stream()
            .map(GroupFileMapper::toDangerRecord)
            .toList(),
        required(file.recipeModels(), "GroupFile.recipeModels").stream()
            .map(GroupFileMapper::toRecipeModel)
            .toList());
  }

  StateFile toFile(StoredState state) {
    return new StateFile(
        SchemaMigrator.CURRENT_VERSION,
        state.serverTick(),
        state.regroupWindowTicks(),
        state.traits().stream().map(GroupFileMapper::toEntry).toList());
  }

  StoredState fromFile(StateFile file) {
    return new StoredState(
        file.serverTick(),
        file.regroupWindowTicks(),
        required(file.traits(), "StateFile.traits").stream()
            .map(GroupFileMapper::toTraits)
            .toList());
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

  private static DangerEntry toEntry(StoredDangerRecord stored) {
    DangerRecord record = stored.record();
    return new DangerEntry(
        stored.player().value().toString(),
        record.healthLost(),
        record.damageDealt(),
        record.lastUpdateTick());
  }

  private static RecipeModelEntry toEntry(StoredRecipeModel stored) {
    LinearPosterior model = stored.record().model();
    return new RecipeModelEntry(
        stored.player().value().toString(),
        model.precision(),
        model.information(),
        model.observations(),
        stored.record().lastTick());
  }

  private static TraitEntry toEntry(StoredTraits stored) {
    TraitSums sums = stored.sums();
    return new TraitEntry(
        stored.player().value().toString(),
        sums.shield(),
        sums.ranged(),
        sums.armor(),
        sums.weight(),
        sums.lastTick());
  }

  private static StoredRecipeModel toRecipeModel(RecipeModelEntry entry) {
    required(entry, "GroupFile.recipeModels[]");
    LinearPosterior model =
        LinearPosterior.of(
            required(entry.precision(), "RecipeModelEntry.precision"),
            required(entry.information(), "RecipeModelEntry.information"),
            entry.observations());
    return new StoredRecipeModel(
        toPlayer(entry.player(), "RecipeModelEntry.player"),
        new RecipeModelRecord(model, entry.lastTick()));
  }

  private static StoredTraits toTraits(TraitEntry entry) {
    required(entry, "StateFile.traits[]");
    TraitSums sums =
        new TraitSums(
            entry.shield(), entry.ranged(), entry.armor(), entry.weight(), entry.lastTick());
    return new StoredTraits(toPlayer(entry.player(), "TraitEntry.player"), sums);
  }

  private static PlayerId toPlayer(String text, String field) {
    return new PlayerId(UUID.fromString(required(text, field)));
  }

  private static StoredDangerRecord toDangerRecord(DangerEntry entry) {
    required(entry, "GroupFile.dangerRecords[]");
    PlayerId player =
        new PlayerId(UUID.fromString(required(entry.playerId(), "DangerEntry.playerId")));
    return new StoredDangerRecord(
        player, new DangerRecord(entry.healthLost(), entry.damageDealt(), entry.lastUpdateTick()));
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
