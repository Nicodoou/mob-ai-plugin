package io.github.nicodoou.mobai.application;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Member;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicyType;
import io.github.nicodoou.mobai.domain.settings.GroupSettings;
import io.github.nicodoou.mobai.domain.settings.MobAiSettings;
import io.github.nicodoou.mobai.domain.settings.SelectionSettings;
import io.github.nicodoou.mobai.domain.shared.GroupId;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.testsupport.SequentialGroupIdSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecruitMobTest {
  private final ActiveGroups activeGroups = new ActiveGroups();
  private final SettingsHolder holder = new SettingsHolder(TestSettings.defaults());
  private final SequentialGroupIdSource groupIds = new SequentialGroupIdSource();
  private final RecruitMob recruitMob = new RecruitMob(activeGroups, holder, groupIds);

  @Test
  void recruitWithoutANearbyGroupFoundsAGroupOfOne() {
    RecruitResult result = recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    assertThat(result)
        .isEqualTo(new RecruitResult.Founded(groupId(1), new Member(mob(1), MobKind.ZOMBIE, 1)));
    Group group = activeGroups.group(groupId(1)).orElseThrow();
    assertThat(group.policy()).isEqualTo(SelectionPolicyType.THOMPSON_SAMPLING);
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(group.roster().members()).hasSize(1);
  }

  @Test
  void recruitNearAGroupJoinsIt() {
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(mob(2), MobKind.SKELETON, groupId(1)));

    assertThat(result)
        .isEqualTo(new RecruitResult.Joined(groupId(1), new Member(mob(2), MobKind.SKELETON, 2)));
    assertThat(groupIds.issued()).isEqualTo(1);
  }

  @Test
  void recruitNearAFullGroupFoundsANewGroup() {
    holder.replace(withMaxGroupSize(2));
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));
    recruitMob.execute(RecruitRequest.near(mob(2), MobKind.ZOMBIE, groupId(1)));

    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(mob(3), MobKind.ZOMBIE, groupId(1)));

    assertThat(result)
        .isEqualTo(new RecruitResult.Founded(groupId(2), new Member(mob(3), MobKind.ZOMBIE, 1)));
    assertThat(activeGroups.group(groupId(1)).orElseThrow().roster().members()).hasSize(2);
  }

  @Test
  void recruitOfAMobAlreadyInAGroupIsRejected() {
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    RecruitResult result = recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    assertThat(result)
        .isEqualTo(new RecruitResult.Rejected(RecruitResult.Rejection.ALREADY_IN_GROUP));
    assertThat(groupIds.issued()).isEqualTo(1);
    assertThat(activeGroups.size()).isEqualTo(1);
  }

  @Test
  void recruitNearAnUnknownGroupIsRejected() {
    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(mob(1), MobKind.ZOMBIE, groupId(9)));

    assertThat(result).isEqualTo(new RecruitResult.Rejected(RecruitResult.Rejection.UNKNOWN_GROUP));
    assertThat(groupIds.issued()).isZero();
    assertThat(activeGroups.size()).isZero();
  }

  @Test
  void recruitReadsTheMaximumSizeAfterAReload() {
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));
    recruitMob.execute(RecruitRequest.near(mob(2), MobKind.ZOMBIE, groupId(1)));
    holder.replace(withMaxGroupSize(2));

    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(mob(3), MobKind.ZOMBIE, groupId(1)));

    assertThat(result)
        .isEqualTo(new RecruitResult.Founded(groupId(2), new Member(mob(3), MobKind.ZOMBIE, 1)));
  }

  @Test
  void foundedGroupUsesTheDefaultPolicyInForce() {
    holder.replace(
        withSelection(
            new SelectionSettings(SelectionPolicyType.EPSILON_GREEDY, 0.5, 1.5, 0.1, 10)));

    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    assertThat(activeGroups.group(groupId(1)).orElseThrow().policy())
        .isEqualTo(SelectionPolicyType.EPSILON_GREEDY);
  }

  @Test
  void foundWithPolicyUsesTheGivenPolicy() {
    RecruitResult result =
        recruitMob.foundWithPolicy(
            RecruitRequest.loose(mob(1), MobKind.ZOMBIE), SelectionPolicyType.RANDOM);

    assertThat(result).isInstanceOf(RecruitResult.Founded.class);
    assertThat(activeGroups.group(groupId(1)).orElseThrow().policy())
        .isEqualTo(SelectionPolicyType.RANDOM);
  }

  @Test
  void foundWithPolicyRejectsAMobAlreadyInAGroup() {
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));

    RecruitResult result =
        recruitMob.foundWithPolicy(
            RecruitRequest.loose(mob(1), MobKind.ZOMBIE), SelectionPolicyType.RANDOM);

    assertThat(result)
        .isEqualTo(new RecruitResult.Rejected(RecruitResult.Rejection.ALREADY_IN_GROUP));
    assertThat(groupIds.issued()).isEqualTo(1);
  }

  @Test
  void joinWhileExecutingKeepsThePlanAndGivesNoRole() {
    recruitMob.execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));
    Group group = activeGroups.group(groupId(1)).orElseThrow();
    group.lifecycle().beginPlanning();
    PlayerId player = new PlayerId(new UUID(2, 1));
    group
        .lifecycle()
        .startPlan(
            new PlanStart(
                new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20.0, 100));

    RecruitResult result =
        recruitMob.execute(RecruitRequest.near(mob(2), MobKind.ZOMBIE, groupId(1)));

    assertThat(result).isInstanceOf(RecruitResult.Joined.class);
    assertThat(group.lifecycle().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(group.lifecycle().plan().orElseThrow().roleOf(mob(2))).isEmpty();
    assertThat(group.lifecycle().plan().orElseThrow().startingMembers()).isEqualTo(1);
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }

  private static GroupId groupId(long n) {
    return new GroupId(new UUID(0, n));
  }

  private static MobAiSettings withMaxGroupSize(int size) {
    MobAiSettings base = TestSettings.defaults();
    return new MobAiSettings(
        new GroupSettings(size, 10, 24.0),
        base.memory(),
        base.selection(),
        base.target(),
        base.plan(),
        base.attack(),
        base.spider(),
        base.persistence(),
        base.debug(),
        base.retreat());
  }

  private static MobAiSettings withSelection(SelectionSettings selection) {
    MobAiSettings base = TestSettings.defaults();
    return new MobAiSettings(
        base.group(),
        base.memory(),
        selection,
        base.target(),
        base.plan(),
        base.attack(),
        base.spider(),
        base.persistence(),
        base.debug(),
        base.retreat());
  }
}
