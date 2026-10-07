package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.GROUP_ID;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withHealth;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.GroupState;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.PlanId;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BrainRegroupingTest {
  private static final int WOUNDED_MOBS = 5;
  private static final double LOW_HEALTH = 5;
  private static final double RECOVERED_HEALTH = 12;
  private static final double PARTLY_RECOVERED_HEALTH = 8;
  private static final long REGROUP_START_TICK = START_TICK + 10;
  private static final long INITIAL_WINDOW_TICKS = 600;
  private static final long LENGTHENED_WINDOW_TICKS = 650;

  private final BrainFixture fixture = BrainFixture.choosingStrategy(0);
  private List<MobSnapshot> mobs;
  private List<MobSnapshot> wounded;

  @BeforeEach
  void enterRegrouping() {
    mobs = fixture.catalogGroup();
    fixture.decide(START_TICK, mobs, alice());
    wounded = healthFrom(mobs, 0, WOUNDED_MOBS, LOW_HEALTH);
    fixture.decide(REGROUP_START_TICK, wounded, alice());
  }

  private static List<MobSnapshot> healthFrom(
      List<MobSnapshot> mobs, int fromIndex, int toIndex, double health) {
    List<MobSnapshot> changed = mobs;
    for (int index = fromIndex; index < toIndex; index++) {
      changed = withHealth(changed, index, health);
    }
    return changed;
  }

  @Test
  void regroupingKeepsEveryoneRetreating() {
    BrainResult result = fixture.decide(START_TICK + 100, wounded, alice());

    assertThat(result.trace().stateBefore()).isEqualTo(GroupState.REGROUPING);
    assertThat(result.trace().regroupEnd()).isEmpty();
    assertThat(result.decision().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(result.decision().target()).contains(ALICE);
    assertThat(result.decision().plan()).isEmpty();
    assertThat(result.decision().assignments())
        .hasSize(mobs.size())
        .allSatisfy(
            order -> {
              assertThat(order.role()).isEqualTo(Role.RETREAT);
              assertThat(order.target()).contains(ALICE);
              assertThat(order.suggestedAttack()).isEmpty();
            });
    assertThat(fixture.regroupWindow().currentTicks()).isEqualTo(INITIAL_WINDOW_TICKS);
  }

  @Test
  void recoveredMajorityEndsRegroupingAndLengthensTheWindow() {
    List<MobSnapshot> recovered =
        healthFrom(
            healthFrom(mobs, 0, WOUNDED_MOBS, RECOVERED_HEALTH),
            WOUNDED_MOBS,
            mobs.size(),
            LOW_HEALTH);

    BrainResult result = fixture.decide(START_TICK + 100, recovered, alice());

    assertThat(result.trace().regroupEnd()).contains(RegroupEndReason.RECOVERED);
    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
    assertThat(result.decision().target()).isEmpty();
    assertThat(fixture.group().lifecycle().regroupStartTick()).isEmpty();
    assertThat(fixture.regroupWindow().currentTicks()).isEqualTo(LENGTHENED_WINDOW_TICKS);
  }

  @Test
  void expiredWindowWithTheGroupStillRetreatedKeepsRegrouping() {
    BrainResult result =
        fixture.decide(REGROUP_START_TICK + INITIAL_WINDOW_TICKS, wounded, alice());

    assertThat(result.trace().regroupEnd()).contains(RegroupEndReason.WINDOW_EXPIRED);
    assertThat(result.trace().stillRetreated()).isTrue();
    assertThat(result.decision().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(result.decision().assignments())
        .hasSize(mobs.size())
        .allSatisfy(order -> assertThat(order.role()).isEqualTo(Role.RETREAT));
    assertThat(fixture.group().lifecycle().regroupStartTick())
        .hasValue(REGROUP_START_TICK + INITIAL_WINDOW_TICKS);
    assertThat(fixture.regroupWindow().currentTicks()).isEqualTo(INITIAL_WINDOW_TICKS);

    BrainResult next =
        fixture.decide(REGROUP_START_TICK + INITIAL_WINDOW_TICKS + 590, wounded, alice());

    assertThat(next.decision().state()).isEqualTo(GroupState.REGROUPING);
    assertThat(next.trace().regroupEnd()).isEmpty();
  }

  @Test
  void expiredWindowEndsRegroupingOnceTheGroupCanFight() {
    List<MobSnapshot> canFight = withHealth(wounded, WOUNDED_MOBS - 1, PARTLY_RECOVERED_HEALTH);

    BrainResult result =
        fixture.decide(REGROUP_START_TICK + INITIAL_WINDOW_TICKS, canFight, alice());

    assertThat(result.trace().regroupEnd()).contains(RegroupEndReason.WINDOW_EXPIRED);
    assertThat(result.trace().stillRetreated()).isFalse();
    assertThat(result.decision().state()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().assignments()).isEmpty();
    assertThat(fixture.regroupWindow().currentTicks()).isEqualTo(LENGTHENED_WINDOW_TICKS);
  }

  @Test
  void nextDecisionAfterRegroupingPlansAgain() {
    fixture.decide(START_TICK + 100, mobs, alice());

    BrainResult result = fixture.decide(START_TICK + 110, mobs, alice());

    assertThat(result.trace().stateBefore()).isEqualTo(GroupState.OBSERVING);
    assertThat(result.decision().state()).isEqualTo(GroupState.EXECUTING);
    assertThat(result.decision().plan()).contains(new PlanId(GROUP_ID, 2));
    assertThat(result.decision().target()).contains(ALICE);
    assertThat(result.decision().assignments()).hasSize(mobs.size());
  }
}
