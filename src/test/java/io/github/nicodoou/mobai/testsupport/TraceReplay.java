package io.github.nicodoou.mobai.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.nicodoou.mobai.application.GroupCapture;
import io.github.nicodoou.mobai.application.GroupCaptureMapper;
import io.github.nicodoou.mobai.application.IncidentReport;
import io.github.nicodoou.mobai.application.ReplayRandomSource;
import io.github.nicodoou.mobai.application.SettingsHolder;
import io.github.nicodoou.mobai.application.TraitCaptureMapper;
import io.github.nicodoou.mobai.domain.brain.Brain;
import io.github.nicodoou.mobai.domain.brain.BrainParts;
import io.github.nicodoou.mobai.domain.brain.RegroupWindow;
import io.github.nicodoou.mobai.domain.decision.BrainResult;
import io.github.nicodoou.mobai.domain.group.Group;
import io.github.nicodoou.mobai.domain.port.StoredTraits;
import io.github.nicodoou.mobai.domain.snapshot.GroupSnapshot;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Repeats the decision of an incident and checks that it comes out exactly the same. */
public final class TraceReplay {
  private static final TraitCaptureMapper TRAITS = new TraitCaptureMapper();

  private TraceReplay() {}

  public record Outcome(
      Optional<BrainResult> result,
      Optional<String> failure,
      GroupCapture after,
      long regroupWindowTicksAfter,
      int remainingDraws,
      List<StoredTraits> traitsAfter) {}

  public static Outcome replay(IncidentReport report) {
    SettingsHolder holder = new SettingsHolder(report.settings());
    RegroupWindow window = new RegroupWindow(() -> holder.current().retreat());
    window.restore(report.regroupWindowTicksBefore());
    GroupCaptureMapper mapper = new GroupCaptureMapper();
    Group group = mapper.restore(report.before(), holder);
    ReplayRandomSource random = new ReplayRandomSource(report.draws());
    BrainParts parts = BrainParts.standard(holder::current, random, window);
    parts.traitLedger().restore(TRAITS.toSums(report.traitsBefore()));
    Decision decision = decide(new Brain(holder::current, parts), group, report.snapshot());
    return new Outcome(
        decision.result(),
        decision.failure(),
        mapper.capture(group),
        window.currentTicks(),
        random.remaining(),
        TRAITS.forSnapshot(parts.traitLedger(), report.snapshot()));
  }

  private static Decision decide(Brain brain, Group group, GroupSnapshot snapshot) {
    try {
      return new Decision(Optional.of(brain.decide(group, snapshot)), Optional.empty());
    } catch (RuntimeException exception) {
      return new Decision(Optional.empty(), Optional.of(summaryOf(exception)));
    }
  }

  public static void assertReproduces(IncidentReport report) {
    Outcome outcome = replay(report);

    report
        .result()
        .ifPresent(result -> assertThat(outcome.result()).as("brain result").contains(result));
    report
        .failure()
        .ifPresent(
            failure -> assertThat(outcome.failure()).as("failure").contains(failure.summary()));
    assertThat(outcome.after()).as("group after").isEqualTo(report.after());
    assertThat(outcome.regroupWindowTicksAfter())
        .as("regroup window after")
        .isEqualTo(report.regroupWindowTicksAfter());
    assertThat(outcome.traitsAfter()).as("traits after").isEqualTo(report.traitsAfter());
    assertThat(outcome.remainingDraws()).as("unused draws").isZero();
  }

  private static String summaryOf(RuntimeException exception) {
    String message = Objects.requireNonNullElse(exception.getMessage(), "");
    return exception.getClass().getName() + ": " + message;
  }

  private record Decision(Optional<BrainResult> result, Optional<String> failure) {}
}
