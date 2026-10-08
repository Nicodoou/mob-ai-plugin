package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupKnowledge;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.DangerObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.memory.StrategyObservation;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.threat.ThreatLedger;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DescribePlayerMemoryTest {
  private static final long TICK = 100;

  private final PlayerId player = new PlayerId(new UUID(2, 1));
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final DescribePlayerMemory describePlayerMemory =
      new DescribePlayerMemory(activeGroups, () -> TestSettings.defaults().success());

  @Test
  void viewShowsOnlyTheRecordedAttacksAndStrategies() {
    addGroupWithMemory(1);

    List<PlayerMemoryView> views = describePlayerMemory.execute(player, TICK);

    assertThat(views).hasSize(1);
    assertThat(views.getFirst().attacks().keySet())
        .containsExactly(Attack.ZOMBIE_FRONT_STRIKE, Attack.SKELETON_DIRECT_SHOT);
    assertThat(views.getFirst().strategies().keySet()).containsExactly(new StrategyId("FLANK"));
  }

  @Test
  void viewEstimatesMatchTheGroupMemory() {
    Group group = addGroupWithMemory(1);

    PlayerMemoryView view = describePlayerMemory.execute(player, TICK).getFirst();

    assertThat(view.attacks().get(Attack.ZOMBIE_FRONT_STRIKE))
        .isEqualTo(group.memory().attackEstimate(player, Attack.ZOMBIE_FRONT_STRIKE, TICK));
  }

  @Test
  void groupsWithoutRecordsOfThePlayerAreLeftOut() {
    addGroupWithMemory(1);
    activeGroups.add(newGroup(2));

    List<PlayerMemoryView> views = describePlayerMemory.execute(player, TICK);

    assertThat(views).extracting(PlayerMemoryView::group).containsExactly(groupId(1));
  }

  @Test
  void viewCarriesTheDanger() {
    Group group = newGroup(1);
    activeGroups.add(group);
    group.memory().recordDanger(new DangerObservation(player, 80, 10, TICK));

    PlayerMemoryView view = describePlayerMemory.execute(player, TICK).getFirst();

    assertThat(view.danger()).isCloseTo(0.5, within(1e-9));
    assertThat(view.dangerRecord().healthLost()).isCloseTo(80, within(1e-9));
    assertThat(view.dangerRecord().damageDealt()).isCloseTo(10, within(1e-9));
  }

  @Test
  void groupRememberedOnlyByItsDangerIsListed() {
    Group group = newGroup(1);
    activeGroups.add(group);
    group.memory().recordDanger(new DangerObservation(player, 80, 10, TICK));

    List<PlayerMemoryView> views = describePlayerMemory.execute(player, TICK);

    assertThat(views).extracting(PlayerMemoryView::group).containsExactly(groupId(1));
  }

  private Group addGroupWithMemory(long n) {
    Group group = newGroup(n);
    activeGroups.add(group);
    activeGroups.join(groupId(n), mob(2 * n - 1), MobKind.ZOMBIE);
    activeGroups.join(groupId(n), mob(2 * n), MobKind.SKELETON);
    GroupMemory memory = group.memory();
    memory.recordAttack(new AttackObservation(player, Attack.ZOMBIE_FRONT_STRIKE, 1.0, TICK));
    memory.recordAttack(new AttackObservation(player, Attack.SKELETON_DIRECT_SHOT, 0.0, TICK));
    memory.recordStrategy(new StrategyObservation(player, new StrategyId("FLANK"), 0.4, 1.0, TICK));
    return group;
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
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
