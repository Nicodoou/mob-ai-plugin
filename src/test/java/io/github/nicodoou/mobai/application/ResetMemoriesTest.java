package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ResetMemoriesTest {
  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final PlayerId otherPlayer = new PlayerId(new UUID(2, 2));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final ResetMemories resetMemories = new ResetMemories(activeGroups);

  @Test
  void resetPlayerClearsOnlyThatPlayer() {
    Group group = addGroupRemembering(1, player);
    remember(group, otherPlayer);

    int changed = resetMemories.resetPlayer(player);

    assertThat(changed).isEqualTo(1);
    assertThat(group.memory().attackRecords()).containsOnlyKeys(otherPlayer);
    assertThat(group.memory().strategyRecords()).containsOnlyKeys(otherPlayer);
  }

  @Test
  void resetPlayerCountsOnlyGroupsThatRememberThem() {
    addGroupRemembering(1, player);
    addGroupRemembering(2, otherPlayer);

    int changed = resetMemories.resetPlayer(player);

    assertThat(changed).isEqualTo(1);
  }

  @Test
  void resetAllClearsEveryGroup() {
    Group first = addGroupRemembering(1, player);
    Group second = addGroupRemembering(2, otherPlayer);

    int changed = resetMemories.resetAll();

    assertThat(changed).isEqualTo(2);
    assertThat(first.memory().attackRecords()).isEmpty();
    assertThat(first.memory().strategyRecords()).isEmpty();
    assertThat(second.memory().attackRecords()).isEmpty();
    assertThat(second.memory().strategyRecords()).isEmpty();
  }

  private Group addGroupRemembering(long n, PlayerId remembered) {
    Group group = newGroup(n);
    activeGroups.add(group);
    remember(group, remembered);
    return group;
  }

  private static void remember(Group group, PlayerId remembered) {
    GroupMemory memory = group.memory();
    memory.recordAttack(new AttackObservation(remembered, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 100));
    memory.recordAttack(new AttackObservation(remembered, Attack.SKELETON_DIRECT_SHOT, 0.0, 100));
    memory.recordStrategy(
        new StrategyObservation(remembered, new StrategyId("FLANK"), 0.4, 1.0, 100));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static Group newGroup(long n) {
    return new Group(
        groupId(n),
        SelectionPolicyType.THOMPSON_SAMPLING,
        new GroupKnowledge(
            new GroupMemory(() -> TestSettings.defaults().memory()),
            new ThreatLedger(() -> TestSettings.defaults().target())));
  }
}
