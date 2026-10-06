package io.github.nicodoou.mobai.replay;

import static io.github.nicodoou.mobai.testsupport.BrainFixture.START_TICK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.nicodoou.mobai.adapter.debug.IncidentJson;
import io.github.nicodoou.mobai.application.DrawKind;
import io.github.nicodoou.mobai.application.GroupCapture;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.application.RecordedDraw;
import io.github.nicodoou.mobai.domain.threat.ThreatCapture;
import io.github.nicodoou.mobai.testsupport.IncidentFixture;
import io.github.nicodoou.mobai.testsupport.TraceReplay;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class IncidentReproductionTest {
  private static final double UNIT_TAMPER = 0.25;
  private final IncidentJson json = new IncidentJson();

  @Test
  void recordedDecisionReproducesFromItsJson() {
    IncidentReport incident = throughJson(IncidentFixture.recordedDecision(START_TICK + 20));

    assertThatCode(() -> TraceReplay.assertReproduces(incident)).doesNotThrowAnyException();
  }

  @Test
  void decisionThatClosesThePlanReproducesFromItsJson() {
    IncidentReport incident = throughJson(IncidentFixture.recordedDecision(START_TICK + 700));

    assertThat(incident.result().get().closedPlan()).isPresent();
    assertThatCode(() -> TraceReplay.assertReproduces(incident)).doesNotThrowAnyException();
  }

  @Test
  void provokedFailureReproducesFromItsJson() {
    IncidentReport incident = throughJson(IncidentFixture.provokedFailure());

    assertThat(incident.failure()).isPresent();
    assertThatCode(() -> TraceReplay.assertReproduces(incident)).doesNotThrowAnyException();
  }

  @Test
  void tamperedDrawIsDetected() {
    IncidentReport original = throughJson(IncidentFixture.recordedDecision(START_TICK + 20));
    IncidentReport tampered = withDraws(original, tamperedFirstDraw(original.draws()));

    assertThatThrownBy(() -> TraceReplay.assertReproduces(tampered))
        .isInstanceOf(AssertionError.class);
  }

  @Test
  void tamperedStateIsDetected() {
    IncidentReport original = throughJson(IncidentFixture.recordedDecision(START_TICK + 20));
    IncidentReport tampered = withBefore(original, withoutThreat(original.before()));

    assertThatThrownBy(() -> TraceReplay.assertReproduces(tampered))
        .isInstanceOf(AssertionError.class);
  }

  private IncidentReport throughJson(IncidentReport report) {
    return json.read(json.write(report));
  }

  private static List<RecordedDraw> tamperedFirstDraw(List<RecordedDraw> draws) {
    List<RecordedDraw> tampered = new ArrayList<>(draws);
    int position = positionToTamper(draws);
    tampered.set(position, changed(draws.get(position)));
    return tampered;
  }

  private static int positionToTamper(List<RecordedDraw> draws) {
    RecordedDraw first = draws.getFirst();
    if (first.kind() != DrawKind.INDEX || first.bound() > 1) {
      return 0;
    }
    return draws.indexOf(
        draws.stream().filter(draw -> draw.kind() != DrawKind.INDEX).findFirst().orElseThrow());
  }

  private static RecordedDraw changed(RecordedDraw draw) {
    if (draw.kind() == DrawKind.INDEX) {
      double otherIndex = (draw.value() + 1) % draw.bound();
      return new RecordedDraw(draw.kind(), otherIndex, draw.bound());
    }
    return new RecordedDraw(draw.kind(), draw.value() + UNIT_TAMPER, draw.bound());
  }

  private static GroupCapture withoutThreat(GroupCapture capture) {
    ThreatCapture empty = new ThreatCapture(List.of(), capture.threat().lastTick());
    return new GroupCapture(capture.stored(), capture.lifecycle(), empty, capture.spiderTargets());
  }

  private static IncidentReport withDraws(IncidentReport base, List<RecordedDraw> draws) {
    return new IncidentReport(
        base.id(),
        base.tick(),
        base.location(),
        base.failure(),
        base.before(),
        base.regroupWindowTicksBefore(),
        base.snapshot(),
        base.settings(),
        draws,
        base.result(),
        base.after(),
        base.regroupWindowTicksAfter());
  }

  private static IncidentReport withBefore(IncidentReport base, GroupCapture before) {
    return new IncidentReport(
        base.id(),
        base.tick(),
        base.location(),
        base.failure(),
        before,
        base.regroupWindowTicksBefore(),
        base.snapshot(),
        base.settings(),
        base.draws(),
        base.result(),
        base.after(),
        base.regroupWindowTicksAfter());
  }
}
