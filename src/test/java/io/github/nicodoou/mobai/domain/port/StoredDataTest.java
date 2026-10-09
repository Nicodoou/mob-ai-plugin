package io.github.nicodoou.mobai.domain.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.memory.AttackRecord;
import io.github.nicodoou.mobai.domain.memory.RecipeModelRecord;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.strategy.TraitSums;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StoredDataTest {
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static StoredGroup group(
      long n, List<Member> members, List<StoredAttackRecord> attackRecords, long planSequence) {
    return new StoredGroup(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        planSequence,
        members,
        attackRecords,
        List.of(),
        List.of(),
        List.of());
  }

  private static List<Member> twoMembers() {
    return List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.SKELETON, 2));
  }

  @Test
  void storedGroupRejectsADuplicateMember() {
    var members =
        List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(1), MobKind.ZOMBIE, 2));

    assertThatThrownBy(() -> group(1, members, List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("StoredGroup.members has a duplicate entry ");
  }

  @Test
  void storedGroupRejectsADuplicateJoinOrder() {
    var members =
        List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.ZOMBIE, 1));

    assertThatThrownBy(() -> group(1, members, List.of(), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("StoredGroup.join orders has a duplicate entry ");
  }

  @Test
  void storedGroupRejectsADuplicateAttackRecord() {
    var record = new StoredAttackRecord(PLAYER, Attack.ZOMBIE_FRONT_STRIKE, AttackRecord.empty(0));

    assertThatThrownBy(() -> group(1, twoMembers(), List.of(record, record), 0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageStartingWith("StoredGroup.attackRecords has a duplicate entry ");
  }

  @Test
  void storedGroupRejectsANegativePlanSequence() {
    assertThatThrownBy(() -> group(1, twoMembers(), List.of(), -1))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StoredGroup.lastPlanSequence must be zero or positive, got -1");
  }

  @Test
  void storedGroupCopiesItsLists() {
    var members = new ArrayList<>(twoMembers());
    var stored = group(1, members, List.of(), 0);

    members.add(new Member(mob(3), MobKind.SPIDER, 3));

    assertThat(stored.members()).hasSize(2);
  }

  @Test
  void storedMemoriesRejectsADuplicateGroup() {
    var state = new StoredState(0, 600, List.of());
    var groups =
        List.of(group(1, twoMembers(), List.of(), 0), group(1, twoMembers(), List.of(), 0));

    assertThatThrownBy(() -> new StoredMemories(state, groups))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StoredMemories.groups has a duplicate group " + groupId(1).shortId());
  }

  @Test
  void duplicateRecipeModelsAreRejected() {
    var model = new StoredRecipeModel(PLAYER, new RecipeModelRecord(prior(), 100));
    var models = List.of(model, model);

    assertThatThrownBy(
            () ->
                new StoredGroup(
                    groupId(1),
                    SelectionPolicyType.THOMPSON_SAMPLING,
                    0,
                    twoMembers(),
                    List.of(),
                    List.of(),
                    List.of(),
                    models))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("recipeModels");
  }

  @Test
  void duplicateTraitsAreRejected() {
    var traits = new StoredTraits(PLAYER, new TraitSums(0.5, 0, 0.25, 1.5, 7000));
    var duplicated = List.of(traits, traits);

    assertThatThrownBy(() -> new StoredState(0, 600, duplicated))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("StoredState.traits has a duplicate entry " + PLAYER);
  }

  private static LinearPosterior prior() {
    return LinearPosterior.prior(new double[] {0.5, 0}, 1.0);
  }
}
