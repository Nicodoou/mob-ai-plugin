package io.github.nicodoou.mobai.domain.brain;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.domain.decision.AttackChoice;
import io.github.nicodoou.mobai.domain.memory.AttackObservation;
import io.github.nicodoou.mobai.domain.memory.GroupMemory;
import io.github.nicodoou.mobai.domain.selection.CandidateScore;
import io.github.nicodoou.mobai.domain.selection.SelectionCandidate;
import io.github.nicodoou.mobai.domain.selection.SelectionPolicy;
import io.github.nicodoou.mobai.domain.selection.SelectionResult;
import io.github.nicodoou.mobai.domain.shared.Attack;
import io.github.nicodoou.mobai.domain.shared.MobKind;
import io.github.nicodoou.mobai.domain.shared.PlayerId;
import io.github.nicodoou.mobai.domain.snapshot.MobSnapshot;
import io.github.nicodoou.mobai.testsupport.MobSnapshotBuilder;
import io.github.nicodoou.mobai.testsupport.TestSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AttackSuggesterTest {
  private static final PlayerId ALICE = new PlayerId(new UUID(0, 10));
  private static final PlayerId BOB = new PlayerId(new UUID(0, 11));
  private static final long TICK = 1000;
  private static final int PRIOR_OBSERVATIONS = 5;
  private static final long OBSERVATION_TICK = 900;

  private final GroupMemory memory = new GroupMemory(() -> TestSettings.defaults().memory());
  private final RecordingPolicy policy = new RecordingPolicy();
  private final AttackContext context = new AttackContext(memory, policy, TICK);
  private final AttackSuggester suggester = new AttackSuggester();

  @Test
  void spiderAlwaysBitesWithoutAskingThePolicy() {
    AttackChoice choice = suggester.suggest(mobOf(MobKind.SPIDER), ALICE, context);

    assertThat(choice.attack()).isEqualTo(Attack.SPIDER_BITE);
    assertThat(choice.selection()).isEmpty();
    assertThat(policy.calls).isZero();
  }

  @Test
  void zombieChoosesAmongItsFourAttacks() {
    suggester.suggest(mobOf(MobKind.ZOMBIE), ALICE, context);

    assertThat(policy.lastCandidates)
        .extracting(SelectionCandidate::option)
        .containsExactly(
            Attack.ZOMBIE_FRONT_STRIKE,
            Attack.ZOMBIE_FLANK_STRIKE,
            Attack.ZOMBIE_PATIENT_STRIKE,
            Attack.ZOMBIE_EVASIVE_STRIKE);
    assertThat(policy.lastCandidates).extracting(SelectionCandidate::baseScore).containsOnly(1.0);
  }

  @Test
  void skeletonChoosesAmongItsThreeShots() {
    suggester.suggest(mobOf(MobKind.SKELETON), ALICE, context);

    assertThat(policy.lastCandidates)
        .extracting(SelectionCandidate::option)
        .containsExactly(
            Attack.SKELETON_DIRECT_SHOT,
            Attack.SKELETON_LEAD_SHOT,
            Attack.SKELETON_OPPORTUNISTIC_SHOT);
  }

  @Test
  void estimatesComeFromTheMemoryForThatTarget() {
    for (int observation = 0; observation < PRIOR_OBSERVATIONS; observation++) {
      memory.recordAttack(
          new AttackObservation(ALICE, Attack.ZOMBIE_FLANK_STRIKE, 1.0, OBSERVATION_TICK));
    }

    AttackChoice againstAlice = suggester.suggest(mobOf(MobKind.ZOMBIE), ALICE, context);
    SelectionCandidate<Object> flankCandidate = policy.lastCandidates.get(1);

    assertThat(flankCandidate.option()).isEqualTo(Attack.ZOMBIE_FLANK_STRIKE);
    assertThat(flankCandidate.estimate())
        .isEqualTo(memory.attackEstimate(ALICE, Attack.ZOMBIE_FLANK_STRIKE, TICK));
    assertThat(againstAlice.attack()).isEqualTo(Attack.ZOMBIE_FLANK_STRIKE);
    assertThat(againstAlice.selection().orElseThrow()).isSameAs(policy.lastResult);

    AttackChoice againstBob = suggester.suggest(mobOf(MobKind.ZOMBIE), BOB, context);

    assertThat(againstBob.attack()).isEqualTo(Attack.ZOMBIE_FRONT_STRIKE);
  }

  private static MobSnapshot mobOf(MobKind kind) {
    return new MobSnapshotBuilder().withKind(kind).build();
  }

  private static final class RecordingPolicy implements SelectionPolicy {
    private int calls;
    private List<SelectionCandidate<Object>> lastCandidates = List.of();
    private SelectionResult<?> lastResult;

    @Override
    public <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates) {
      calls++;
      lastCandidates = new ArrayList<>();
      for (SelectionCandidate<T> candidate : candidates) {
        lastCandidates.add(
            new SelectionCandidate<>(
                candidate.option(), candidate.baseScore(), candidate.estimate()));
      }
      SelectionCandidate<T> best = candidates.get(0);
      List<CandidateScore<T>> scores = new ArrayList<>();
      for (SelectionCandidate<T> candidate : candidates) {
        scores.add(
            new CandidateScore<>(
                candidate.option(), candidate.estimate().mean(), candidate.baseScore()));
        if (candidate.estimate().mean() > best.estimate().mean()) {
          best = candidate;
        }
      }
      SelectionResult<T> result = new SelectionResult<>(best.option(), scores, false);
      lastResult = result;
      return result;
    }
  }
}
