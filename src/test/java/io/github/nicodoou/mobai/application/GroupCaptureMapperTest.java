package io.github.nicodoou.mobai.application;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.BOB;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GroupCaptureMapperTest {
  private static final long SEED = 7;

  private final GroupCaptureMapper mapper = new GroupCaptureMapper();

  @Test
  void groupMidPlanRoundTrips() {
    BrainFixture fixture = BrainFixture.seeded(SEED);
    List<MobSnapshot> mobs = fixture.catalogGroup();
    fixture.decide(START_TICK, mobs, alice());
    fixture.decide(START_TICK + 10, mobs, alice());
    fixture.group().threat().recordDamage(ALICE, 3.0, START_TICK + 12);
    SettingsHolder holder = new SettingsHolder(TestSettings.defaults());

    GroupCapture capture = mapper.capture(fixture.group());
    GroupCapture roundTrip = mapper.capture(mapper.restore(capture, holder));

    assertThat(capture.lifecycle().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(capture.spiderTargets()).isNotEmpty();
    assertThat(roundTrip).isEqualTo(capture);
  }

  @Test
  void spiderTargetsAreACopy() {
    BrainFixture fixture = BrainFixture.seeded(SEED);
    List<MobId> spiders = spiders(fixture.catalogGroup());
    Group group = fixture.group();
    group.roster().assignSpiderTarget(spiders.get(0), ALICE);

    Map<MobId, PlayerId> targets = group.roster().spiderTargets();
    group.roster().assignSpiderTarget(spiders.get(1), BOB);

    assertThat(targets).containsExactly(Map.entry(spiders.get(0), ALICE));
  }

  private static List<MobId> spiders(List<MobSnapshot> mobs) {
    return mobs.stream().filter(mob -> mob.kind() == MobKind.SPIDER).map(MobSnapshot::id).toList();
  }
}
