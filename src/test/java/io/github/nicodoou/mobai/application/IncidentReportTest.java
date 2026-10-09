package io.github.nicodoou.mobai.application;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.testsupport.IncidentFixture;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IncidentReportTest {
  private static final String EITHER_MESSAGE =
      "IncidentReport must have either a failure or a result";

  @Test
  void reportNeedsAFailureOrAResult() {
    IncidentReport recorded = IncidentFixture.recordedDecision(START_TICK + 20);

    assertThatThrownBy(() -> withOutcome(recorded, Optional.empty(), Optional.empty()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(EITHER_MESSAGE);
  }

  @Test
  void reportCannotHaveBothAFailureAndAResult() {
    IncidentReport recorded = IncidentFixture.recordedDecision(START_TICK + 20);
    Optional<IncidentFailure> failure =
        Optional.of(IncidentFailure.of(new IllegalStateException("boom")));

    assertThatThrownBy(() -> withOutcome(recorded, failure, recorded.result()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(EITHER_MESSAGE);
  }

  @Test
  void failureKeepsClassMessageAndStackTrace() {
    IncidentFailure failure = IncidentFailure.of(new IllegalStateException("boom"));

    assertThat(failure.exceptionClass()).isEqualTo("java.lang.IllegalStateException");
    assertThat(failure.message()).isEqualTo("boom");
    assertThat(failure.stackTrace()).startsWith("java.lang.IllegalStateException: boom\n\tat ");
    assertThat(failure.summary()).isEqualTo("java.lang.IllegalStateException: boom");
  }

  @Test
  void missingMessageBecomesEmpty() {
    IncidentFailure failure = IncidentFailure.of(new IllegalStateException());

    assertThat(failure.message()).isEmpty();
  }

  private static IncidentReport withOutcome(
      IncidentReport base, Optional<IncidentFailure> failure, Optional<BrainResult> result) {
    return new IncidentReport(
        base.id(),
        base.tick(),
        base.location(),
        failure,
        base.before(),
        base.regroupWindowTicksBefore(),
        base.snapshot(),
        base.settings(),
        base.draws(),
        result,
        base.after(),
        base.regroupWindowTicksAfter(),
        base.traitsBefore(),
        base.traitsAfter());
  }
}
