package io.github.nicodoou.mobai.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.nicodoou.mobai.application.LoadReport;
import io.github.nicodoou.mobai.application.RecruitRequest;
import io.github.nicodoou.mobai.application.RecruitResult;
import io.github.nicodoou.mobai.application.RemovalCause;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.PlanEndReason;
import io.github.nicodoou.mobai.domain.group.PlanStart;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.port.StoredState;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.shared.StrategyId;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.GroupSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.InMemoryMemoryRepository;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CoreServicesTest {
  private static final StrategyId STRATEGY = new StrategyId("DIRECT_ASSAULT");
  private static final PlayerId PLAYER = new PlayerId(new UUID(2, 1));
  private static final long SEED = 7;

  private final InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
  private final CoreServices core = newCore();

  @Test
  void closePlanIsSubscribedToPlanClosed() {
    Group group = recruitedGroup();
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(new PlanStart(STRATEGY, PLAYER, Map.of(mob(1), Role.PRESS), 20.0, 100));

    core.recordPlayerDeath().execute(PLAYER, 160);

    assertThat(group.memory().strategyRecords().get(PLAYER).get(STRATEGY).successes())
        .isEqualTo(1.0, within(1e-9));
  }

  @Test
  void removingTheLastMemberWhileRegroupingShortensTheWindow() {
    Group group = recruitedGroup();
    group.lifecycle().beginPlanning();
    group
        .lifecycle()
        .startPlan(new PlanStart(STRATEGY, PLAYER, Map.of(mob(1), Role.PRESS), 20.0, 100));
    group.lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 200, 0.5);
    group.lifecycle().finishEvaluation();

    core.removeMember().execute(mob(1), RemovalCause.DIED, 300);

    assertThat(core.regroupWindow().currentTicks()).isEqualTo(550);
  }

  @Test
  void brainDrawsAreRecorded() {
    Group group = recruitedGroup();
    GroupSnapshot snapshot =
        new GroupSnapshotBuilder()
            .withGroupId(group.id())
            .withTick(BrainFixture.START_TICK)
            .withMob(new MobSnapshotBuilder().withId(mob(1)).withKind(MobKind.ZOMBIE).build())
            .withPlayer(BrainFixture.alice())
            .build();

    core.tickGroups().execute(snapshot);

    assertThat(core.randomDraws().draws()).isNotEmpty();
  }

  @Test
  void storedStateReflectsClockAndWindow() {
    core.clock().restore(1234);

    StoredState state = core.storedState();

    assertThat(state).isEqualTo(new StoredState(1234, 600));
  }

  @Test
  void restoreAppliesTheSavedState() {
    core.restore(new StoredState(5000, 700));

    assertThat(core.clock().currentTick()).isEqualTo(5000);
    assertThat(core.regroupWindow().currentTicks()).isEqualTo(700);
  }

  @Test
  void savedMemoriesLoadIntoAFreshCore() {
    recruitedGroup();
    core.saveMemories().write(core.saveMemories().capture(core.storedState()));
    CoreServices freshCore = newCore();

    LoadReport report = freshCore.loadMemories().execute();

    assertThat(report.loadedGroups()).hasSize(1);
    assertThat(freshCore.activeGroups().groupOf(mob(1))).isPresent();
  }

  private CoreServices newCore() {
    return CoreServices.create(TestSettings.defaults(), repository, new SeededRandomSource(SEED));
  }

  private Group recruitedGroup() {
    RecruitResult result = core.recruitMob().execute(RecruitRequest.loose(mob(1), MobKind.ZOMBIE));
    RecruitResult.Founded founded = (RecruitResult.Founded) result;
    return core.activeGroups().group(founded.groupId()).orElseThrow();
  }

  private static MobId mob(long n) {
    return new MobId(new UUID(1, n));
  }
}
