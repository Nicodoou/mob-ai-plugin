package io.github.nicodoou.mobai.simulation;

import io.github.nicodoou.mobai.domain.learning.LinearPosterior;
import io.github.nicodoou.mobai.domain.port.RandomSource;
import io.github.nicodoou.mobai.simulation.RecipeLearner.ModelKind;
import io.github.nicodoou.mobai.simulation.RecipeLearner.Opponent;
import io.github.nicodoou.mobai.simulation.RecipeLearner.Session;
import io.github.nicodoou.mobai.testsupport.SeededRandomSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("calibration")
class RecipeCalibrationReport {
  private static final long FIRST_SEED = 1;
  private static final long LAST_SCRATCH_SEED = 20;
  private static final long LAST_BASE_SEED = 10;
  private static final int SCRATCH_PLANS = 150;
  private static final int TRAINING_PLANS = 600;
  private static final int TRAINING_SESSION_PLANS = 1;
  private static final int EVALUATION_PLANS = 40;
  private static final double TRAINING_EXPLORATION = 2;
  private static final double PLAY_EXPLORATION = 1;
  private static final double NO_JITTER = 0;
  private static final int FIRST_PLANS = 20;
  private static final int LAST_PLANS = 10;
  private static final double GATE_MAX_REGRET = 0.07;
  private static final int HALF = 2;
  private static final String NEVER = "nunca";
  private static final Path REPORT_PATH = Path.of("build", "reports", "simulation", "recipes.md");

  private enum Experiment {
    SCRATCH(ModelKind.CONTEXTUAL, 0.15, 0.05),
    BASE_FLAT(ModelKind.FLAT, 0.15, NO_JITTER),
    BASE_CONTEXTUAL_EXACT(ModelKind.CONTEXTUAL, 0, NO_JITTER),
    BASE_CONTEXTUAL_NOISY(ModelKind.CONTEXTUAL, 0.15, 0.05);

    private final ModelKind kind;
    private final double deviation;
    private final double jitter;

    Experiment(ModelKind kind, double deviation, double jitter) {
      this.kind = kind;
      this.deviation = deviation;
      this.jitter = jitter;
    }

    Opponent opponentFor(PlayStyle style, RandomSource random) {
      return new Opponent(style, style.measuredTraits(random, deviation));
    }

    Session playSession(int plans) {
      return new Session(kind, plans, PLAY_EXPLORATION, jitter);
    }

    Session trainingSession() {
      return new Session(kind, TRAINING_SESSION_PLANS, TRAINING_EXPLORATION, NO_JITTER);
    }
  }

  private record SeedRun(Experiment experiment, RandomSource random, RecipeLearner learner) {
    static SeedRun of(Experiment experiment, long seed) {
      RandomSource random = new SeededRandomSource(seed);
      return new SeedRun(experiment, random, new RecipeLearner(random));
    }

    List<Double> play(LinearPosterior start, PlayStyle style, int plans) {
      Opponent opponent = experiment.opponentFor(style, random);
      return learner.play(start, opponent, experiment.playSession(plans)).trueScores();
    }
  }

  private record Row(Experiment experiment, PlayStyle style, List<List<Double>> runs) {
    double regretOfFirst() {
      return averageOver(scores -> EpisodeMetrics.regretOfFirst(scores, optimum(), FIRST_PLANS));
    }

    double regretOfLast() {
      return averageOver(scores -> EpisodeMetrics.regretOfLast(scores, optimum(), LAST_PLANS));
    }

    List<OptionalInt> plansToCompetent() {
      return runs.stream()
          .map(scores -> EpisodeMetrics.plansToCompetent(scores, optimum()))
          .toList();
    }

    boolean passesGate() {
      return regretOfFirst() <= GATE_MAX_REGRET && regretOfFirst() < style.legacyRegret();
    }

    private double optimum() {
      return style.optimum();
    }

    private double averageOver(ToDoubleFunction<List<Double>> metric) {
      return runs.stream().mapToDouble(metric).average().orElseThrow();
    }
  }

  @Test
  void writesTheRecipeCalibration() throws IOException {
    List<Row> rows = new ArrayList<>(scratchRows());
    for (Experiment experiment :
        List.of(
            Experiment.BASE_FLAT,
            Experiment.BASE_CONTEXTUAL_EXACT,
            Experiment.BASE_CONTEXTUAL_NOISY)) {
      rows.addAll(baseRows(experiment));
    }

    Files.createDirectories(REPORT_PATH.getParent());
    Files.writeString(REPORT_PATH, render(rows));
  }

  private static List<Row> scratchRows() {
    return Arrays.stream(PlayStyle.values())
        .map(style -> new Row(Experiment.SCRATCH, style, scratchRuns(style)))
        .toList();
  }

  private static List<List<Double>> scratchRuns(PlayStyle style) {
    return LongStream.rangeClosed(FIRST_SEED, LAST_SCRATCH_SEED)
        .mapToObj(
            seed ->
                SeedRun.of(Experiment.SCRATCH, seed)
                    .play(RecipeLearner.prior(ModelKind.CONTEXTUAL), style, SCRATCH_PLANS))
        .toList();
  }

  private static List<Row> baseRows(Experiment experiment) {
    Map<PlayStyle, List<List<Double>>> runsByStyle = new EnumMap<>(PlayStyle.class);
    for (PlayStyle style : PlayStyle.values()) {
      runsByStyle.put(style, new ArrayList<>());
    }
    for (long seed = FIRST_SEED; seed <= LAST_BASE_SEED; seed++) {
      SeedRun seedRun = SeedRun.of(experiment, seed);
      LinearPosterior base = trainedBase(seedRun);
      for (PlayStyle style : PlayStyle.values()) {
        runsByStyle.get(style).add(seedRun.play(base, style, EVALUATION_PLANS));
      }
    }
    return Arrays.stream(PlayStyle.values())
        .map(style -> new Row(experiment, style, runsByStyle.get(style)))
        .toList();
  }

  private static LinearPosterior trainedBase(SeedRun seedRun) {
    Experiment experiment = seedRun.experiment();
    PlayStyle[] styles = PlayStyle.values();
    LinearPosterior base = RecipeLearner.prior(experiment.kind);
    for (int plan = 0; plan < TRAINING_PLANS; plan++) {
      PlayStyle style = styles[plan % styles.length];
      Opponent opponent = experiment.opponentFor(style, seedRun.random());
      base = seedRun.learner().play(base, opponent, experiment.trainingSession()).model();
    }
    return base;
  }

  private static String render(List<Row> rows) {
    return header() + tableOf(rows) + gateOf(rows) + controlOf(rows);
  }

  private static String header() {
    return String.format(
        Locale.ROOT,
        "# Calibración de las recetas (CT-30)%n%n"
            + "Distancia = óptimo verdadero − puntaje verdadero de la receta jugada, promediada"
            + " sobre las semillas. SCRATCH: semillas %d a %d, %d planes desde cero. Con base:"
            + " semillas %d a %d, %d planes de entrenamiento y %d planes por estilo. Competente:"
            + " primer bloque de %d planes con promedio ≥ óptimo − %.2f; la mediana es sobre las"
            + " corridas que lo alcanzan.%n%n",
        FIRST_SEED,
        LAST_SCRATCH_SEED,
        SCRATCH_PLANS,
        FIRST_SEED,
        LAST_BASE_SEED,
        TRAINING_PLANS,
        EVALUATION_PLANS,
        EpisodeMetrics.WINDOW,
        EpisodeMetrics.COMPETENCE_TOLERANCE);
  }

  private static String tableOf(List<Row> rows) {
    StringBuilder table = new StringBuilder();
    table.append(
        "| Experimento | Estilo | Óptimo | Distancia, 4 estrategias de hoy "
            + "| Distancia, primeros 20 (promedio) | Distancia, últimos 10 (promedio) "
            + "| Planes hasta competente (mediana; «nunca» si falta en más de la mitad) |\n");
    table.append("| --- | --- | --- | --- | --- | --- | --- |\n");
    rows.forEach(row -> table.append(rowOf(row)));
    return table.toString();
  }

  private static String rowOf(Row row) {
    return String.format(
        Locale.ROOT,
        "| %s | %s | %.4f | %.4f | %.4f | %.4f | %s |\n",
        row.experiment(),
        row.style(),
        row.style().optimum(),
        row.style().legacyRegret(),
        row.regretOfFirst(),
        row.regretOfLast(),
        medianOf(row.plansToCompetent()));
  }

  private static String medianOf(List<OptionalInt> plans) {
    List<Integer> reached =
        plans.stream().filter(OptionalInt::isPresent).map(OptionalInt::getAsInt).sorted().toList();
    if ((plans.size() - reached.size()) * HALF > plans.size()) {
      return NEVER;
    }
    return String.format(Locale.ROOT, "%.1f", medianOfSorted(reached));
  }

  private static double medianOfSorted(List<Integer> sorted) {
    int middle = sorted.size() / HALF;
    if (sorted.size() % HALF == 1) {
      return sorted.get(middle);
    }
    return (sorted.get(middle - 1) + sorted.get(middle)) / (double) HALF;
  }

  private static String gateOf(List<Row> rows) {
    List<Row> failing = noisyRows(rows).stream().filter(row -> !row.passesGate()).toList();
    String criterion =
        String.format(
            Locale.ROOT,
            "Criterio: en BASE_CONTEXTUAL_NOISY, para cada estilo, distancia en los primeros %d"
                + " planes ≤ %.2f y menor que la de las 4 estrategias de hoy.%n%n",
            FIRST_PLANS,
            GATE_MAX_REGRET);
    if (failing.isEmpty()) {
      return "\n## Puerta\n\n" + criterion + "**PASS**\n";
    }
    String styles =
        failing.stream().map(row -> row.style().name()).collect(Collectors.joining(", "));
    return "\n## Puerta\n\n" + criterion + "**FAIL**: " + styles + "\n";
  }

  private static String controlOf(List<Row> rows) {
    String values =
        rows.stream()
            .filter(row -> row.experiment() == Experiment.BASE_FLAT)
            .map(row -> String.format(Locale.ROOT, "%s %.4f", row.style(), row.regretOfFirst()))
            .collect(Collectors.joining(", "));
    return "\nControl: `BASE_FLAT` primeros 20: " + values + "\n";
  }

  private static List<Row> noisyRows(List<Row> rows) {
    return rows.stream()
        .filter(row -> row.experiment() == Experiment.BASE_CONTEXTUAL_NOISY)
        .toList();
  }
}
