package io.github.nicodoou.mobai.simulation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToDoubleFunction;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("calibration")
class CalibrationReport {
  private static final List<Double> LEARNING_SPEEDS = List.of(0.5, 0.7, 0.9, 1.0);
  private static final List<Long> HALF_LIVES_TICKS = List.of(6_000L, 12_000L, 24_000L);
  private static final long FIRST_SEED = 1;
  private static final long LAST_SEED = 30;
  private static final double REQUIRED_PASS_RATE = 0.8;
  private static final double PERCENT = 100;
  private static final Path REPORT_PATH =
      Path.of("build", "reports", "simulation", "calibration.md");

  private record GridRow(
      double learningSpeed,
      long halfLifeTicks,
      List<RunMetrics> blockerRuns,
      List<RunMetrics> openRuns) {
    double blockerPassRate() {
      return passRate(blockerRuns, PlayerArchetype.BLOCKER);
    }

    double openPassRate() {
      return passRate(openRuns, PlayerArchetype.OPEN);
    }

    boolean meetsCriterion() {
      return blockerPassRate() >= REQUIRED_PASS_RATE && openPassRate() >= REQUIRED_PASS_RATE;
    }
  }

  private final LearningSimulation simulation = new LearningSimulation();

  @Test
  void writesTheCalibrationGrid() throws IOException {
    List<GridRow> rows = new ArrayList<>();
    for (double learningSpeed : LEARNING_SPEEDS) {
      for (long halfLifeTicks : HALF_LIVES_TICKS) {
        rows.add(runConfiguration(learningSpeed, halfLifeTicks));
      }
    }

    Files.createDirectories(REPORT_PATH.getParent());
    Files.writeString(REPORT_PATH, render(rows));
  }

  private GridRow runConfiguration(double learningSpeed, long halfLifeTicks) {
    return new GridRow(
        learningSpeed,
        halfLifeTicks,
        runSeeds(learningSpeed, halfLifeTicks, PlayerArchetype.BLOCKER),
        runSeeds(learningSpeed, halfLifeTicks, PlayerArchetype.OPEN));
  }

  private List<RunMetrics> runSeeds(
      double learningSpeed, long halfLifeTicks, PlayerArchetype archetype) {
    return LongStream.rangeClosed(FIRST_SEED, LAST_SEED)
        .mapToObj(
            seed ->
                simulation.run(new SimulationConfig(learningSpeed, halfLifeTicks, archetype, seed)))
        .toList();
  }

  private static double passRate(List<RunMetrics> runs, PlayerArchetype archetype) {
    long passing = runs.stream().filter(run -> run.passes(archetype)).count();
    return (double) passing / runs.size();
  }

  private static double average(List<RunMetrics> runs, ToDoubleFunction<RunMetrics> metric) {
    return runs.stream().mapToDouble(metric).average().orElseThrow();
  }

  private static String render(List<GridRow> rows) {
    StringBuilder report = new StringBuilder();
    report.append("# Calibration of the learning simulation\n\n");
    report.append(
        String.format(
            Locale.ROOT,
            "Seeds %d to %d per cell. Averages are over the runs of each archetype.\n\n",
            FIRST_SEED,
            LAST_SEED));
    report.append(tableOf(rows));
    report.append(passingConfigurations(rows));
    return report.toString();
  }

  private static String tableOf(List<GridRow> rows) {
    StringBuilder table = new StringBuilder();
    table.append(
        "| Learning speed | Half-life (ticks) | BLOCKER pass | OPEN pass "
            + "| BLOCKER flankShareLast10 | BLOCKER frontShareLast10 | BLOCKER frontShareAll "
            + "| OPEN flankShareLast10 | OPEN frontShareLast10 | OPEN frontShareAll |\n");
    table.append("| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |\n");
    rows.forEach(row -> table.append(rowOf(row)));
    return table.toString();
  }

  private static String rowOf(GridRow row) {
    return String.format(
        Locale.ROOT,
        "| %.1f | %d | %.0f%% | %.0f%% | %.3f | %.3f | %.3f | %.3f | %.3f | %.3f |\n",
        row.learningSpeed(),
        row.halfLifeTicks(),
        row.blockerPassRate() * PERCENT,
        row.openPassRate() * PERCENT,
        average(row.blockerRuns(), RunMetrics::flankShareLast10),
        average(row.blockerRuns(), RunMetrics::frontShareLast10),
        average(row.blockerRuns(), RunMetrics::frontShareAll),
        average(row.openRuns(), RunMetrics::flankShareLast10),
        average(row.openRuns(), RunMetrics::frontShareLast10),
        average(row.openRuns(), RunMetrics::frontShareAll));
  }

  private static String passingConfigurations(List<GridRow> rows) {
    List<GridRow> passing = rows.stream().filter(GridRow::meetsCriterion).toList();
    StringBuilder section =
        new StringBuilder("\n## Configurations that pass the 80% criterion\n\n");
    if (passing.isEmpty()) {
      section.append("None.\n");
      return section.toString();
    }
    passing.forEach(
        row ->
            section.append(
                String.format(
                    Locale.ROOT,
                    "- learning speed %.1f, half-life %d ticks\n",
                    row.learningSpeed(),
                    row.halfLifeTicks())));
    return section.toString();
  }
}
