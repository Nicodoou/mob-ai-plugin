package io.github.nicodoou.mobai.domain.brain;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.ALICE;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.alice;
import static io.github.nicodoou.mobai.testsupport.BrainFixture.withPosition;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.decision.RoleAssignment;
import io.github.nicodoou.mobai.domain.group.Role;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.BrainFixture;
import java.util.List;
import org.junit.jupiter.api.Test;

class BrainPackTest {
  private static final int DIRECT_ASSAULT_INDEX = 0;
  private static final long NEXT_DECISION_TICKS = 10;
  private static final List<Integer> ZOMBIES = List.of(0, 1, 2, 3);
  private static final List<Integer> SKELETONS = List.of(4, 5, 6);
  private static final List<Integer> SPIDERS = List.of(7, 8);
  private static final Vec3 ZOMBIE_POSITION = new Vec3(0, 64, 20);
  private static final Vec3 SPIDER_AHEAD_POSITION = new Vec3(0, 64, 10);
  private static final Vec3 SPIDER_CAUGHT_UP_POSITION = new Vec3(0, 64, 14);

  private final BrainFixture fixture = BrainFixture.choosingStrategy(DIRECT_ASSAULT_INDEX);
  private final List<MobSnapshot> catalog = fixture.catalogGroup();

  @Test
  void spidersAheadOfTheZombiesWaitForThem() {
    List<MobSnapshot> mobs = placed(SPIDER_AHEAD_POSITION);

    BrainResult result = fixture.decide(START_TICK, mobs, alice());

    List<RoleAssignment> orders = result.decision().assignments();
    for (int index : SPIDERS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.GATHER);
      assertThat(orders.get(index).target()).contains(ALICE);
      assertThat(orders.get(index).suggestedAttack()).isEmpty();
    }
    for (int index : ZOMBIES) {
      assertThat(orders.get(index).role()).isEqualTo(Role.PRESS);
    }
    for (int index : SKELETONS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.SHOOT);
    }
  }

  @Test
  void gatheredSpidersRejoinOnceThePackCatchesUp() {
    fixture.decide(START_TICK, placed(SPIDER_AHEAD_POSITION), alice());

    BrainResult result =
        fixture.decide(
            START_TICK + NEXT_DECISION_TICKS, placed(SPIDER_CAUGHT_UP_POSITION), alice());

    List<RoleAssignment> orders = result.decision().assignments();
    for (int index : SPIDERS) {
      assertThat(orders.get(index).role()).isEqualTo(Role.PRESS);
      assertThat(orders.get(index).suggestedAttack()).isPresent();
    }
  }

  private List<MobSnapshot> placed(Vec3 spiderPosition) {
    List<MobSnapshot> mobs = catalog;
    for (int index : ZOMBIES) {
      mobs = withPosition(mobs, index, ZOMBIE_POSITION);
    }
    for (int index : SPIDERS) {
      mobs = withPosition(mobs, index, spiderPosition);
    }
    return mobs;
  }
}
