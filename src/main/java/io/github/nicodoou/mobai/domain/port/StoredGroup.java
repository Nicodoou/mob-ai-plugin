package io.github.nicodoou.mobai.domain.port;

import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public record StoredGroup(
    GroupId id,
    SelectionPolicyType policy,
    long lastPlanSequence,
    List<Member> members,
    List<StoredAttackRecord> attackRecords,
    List<StoredStrategyRecord> strategyRecords) {
  public StoredGroup {
    Objects.requireNonNull(id, "StoredGroup.id");
    Objects.requireNonNull(policy, "StoredGroup.policy");
    if (lastPlanSequence < 0) {
      throw new IllegalArgumentException(
          "StoredGroup.lastPlanSequence must be zero or positive, got " + lastPlanSequence);
    }
    members = List.copyOf(members);
    attackRecords = List.copyOf(attackRecords);
    strategyRecords = List.copyOf(strategyRecords);
    requireDistinct(members, Member::id, "members");
    requireDistinct(members, Member::joinOrder, "join orders");
    requireDistinct(
        attackRecords, entry -> List.of(entry.player(), entry.attack()), "attackRecords");
    requireDistinct(
        strategyRecords, entry -> List.of(entry.player(), entry.strategy()), "strategyRecords");
  }

  private static <T> void requireDistinct(List<T> entries, Function<T, ?> key, String label) {
    Set<Object> seen = new HashSet<>();
    for (T entry : entries) {
      if (!seen.add(key.apply(entry))) {
        throw new IllegalArgumentException(
            "StoredGroup." + label + " has a duplicate entry " + key.apply(entry));
      }
    }
  }
}
