package io.github.nicodoou.mobai.domain.shared;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class AttackTest {

  @Test
  void idsMatchTheCatalog() {
    Attack[] attacks = Attack.values();
    assertThat(attacks).hasSize(7);
    assertThat(attacks[0].id()).isEqualTo("zombie.front_strike");
    assertThat(attacks[1].id()).isEqualTo("zombie.flank_strike");
    assertThat(attacks[2].id()).isEqualTo("zombie.patient_strike");
    assertThat(attacks[3].id()).isEqualTo("skeleton.direct_shot");
    assertThat(attacks[4].id()).isEqualTo("skeleton.lead_shot");
    assertThat(attacks[5].id()).isEqualTo("skeleton.opportunistic_shot");
    assertThat(attacks[6].id()).isEqualTo("spider.bite");
  }

  @Test
  void fromIdFindsEveryAttack() {
    for (Attack attack : Attack.values()) {
      assertThat(Attack.fromId(attack.id())).contains(attack);
    }
  }

  @Test
  void fromIdIsEmptyForUnknownId() {
    assertThat(Attack.fromId("zombie.unknown")).isEmpty();
  }

  @Test
  void forKindListsAttacksInCatalogOrder() {
    List<Attack> zombieAttacks = Attack.forKind(MobKind.ZOMBIE);
    assertThat(zombieAttacks)
        .containsExactly(
            Attack.ZOMBIE_FRONT_STRIKE, Attack.ZOMBIE_FLANK_STRIKE, Attack.ZOMBIE_PATIENT_STRIKE);

    List<Attack> skeletonAttacks = Attack.forKind(MobKind.SKELETON);
    assertThat(skeletonAttacks)
        .containsExactly(
            Attack.SKELETON_DIRECT_SHOT,
            Attack.SKELETON_LEAD_SHOT,
            Attack.SKELETON_OPPORTUNISTIC_SHOT);

    List<Attack> spiderAttacks = Attack.forKind(MobKind.SPIDER);
    assertThat(spiderAttacks).containsExactly(Attack.SPIDER_BITE);
  }

  @Test
  void forKindReturnsAnUnmodifiableList() {
    List<Attack> attacks = Attack.forKind(MobKind.ZOMBIE);
    assertThatThrownBy(() -> attacks.add(Attack.SPIDER_BITE))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
