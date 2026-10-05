package io.github.nicodoou.mobai.domain.shared;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MobKindTest {

  @Test
  void zombiesAndSpidersAreMeleeAndSkeletonsAreNot() {
    assertThat(MobKind.ZOMBIE.isMelee()).isTrue();
    assertThat(MobKind.SPIDER.isMelee()).isTrue();
    assertThat(MobKind.SKELETON.isMelee()).isFalse();
  }
}
