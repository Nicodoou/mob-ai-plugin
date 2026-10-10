package io.github.nicodoou.mobai.adapter.goal;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.shared.MobId;
import io.github.nicodoou.mobai.domain.shared.Vec3;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PerchClaimsTest {
  private static final MobId A = new MobId(new UUID(0, 1));
  private static final MobId B = new MobId(new UUID(0, 2));
  private static final MobId C = new MobId(new UUID(0, 3));
  private static final Vec3 HILLTOP = new Vec3(10, 67, 0);

  private final PerchClaims claims = new PerchClaims();

  @Test
  void claimedPerchesAreTakenUntilReleased() {
    claims.claim(A, HILLTOP);

    assertThat(claims.takenBy(Set.of(A, B), B)).containsExactly(HILLTOP);

    claims.release(A);

    assertThat(claims.takenBy(Set.of(A, B), B)).isEmpty();
  }

  @Test
  void ownClaimIsNotTaken() {
    claims.claim(A, HILLTOP);

    assertThat(claims.takenBy(Set.of(A), A)).isEmpty();
  }

  @Test
  void onlyCurrentShootersCount() {
    claims.claim(A, HILLTOP);

    assertThat(claims.takenBy(Set.of(B, C), B)).isEmpty();
  }
}
