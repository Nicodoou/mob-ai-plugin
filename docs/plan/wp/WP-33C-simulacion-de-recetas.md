# WP-33C — Simulación de recetas (puerta del CT-30)

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33A y WP-33B (mergeados; si no lo están, partí de la rama del 33B y avisá) |
| Modelo | Opus |
| Rama | `wp-33c-simulacion-de-recetas` |

## Objetivo

Comprobar, **antes de tocar el juego**, que el aprendizaje de recetas funciona, y dejar calibrados sus números. Es la **puerta** del CT-30: si el informe no cumple el criterio, la serie se frena.

Tiene dos partes:

1. **Producción, pura (dominio):** los rasgos del jugador (`PlayerTraits`) y su combinación con los de la receta (`ContextualFeatures`, 60 números), que el cerebro usará en el WP-33D.
2. **Simulación (pruebas):** tres estilos de jugador sintéticos con un óptimo conocido, un aprendiz que usa las clases reales (`LinearPosterior`, `RecipeSearch`, `RecipeFeatures`, `ContextualFeatures`) y un informe de calibración. El informe se copia a `docs/plan/calibracion-recetas.md`.

El prototipo en Python del orquestador (CT-30, decisión 8) dio estos números, que este WP tiene que reproducir aproximadamente en Java:
- **de cero:** competente recién después de 40 a 80 planes;
- **con base de 600 planes mezclados y rasgos ruidosos:** distancia al óptimo de 0,03 a 0,05 en los primeros 20 planes;
- **con base mezclada sin rasgos:** de 0,20 a 0,30;
- **las 4 estrategias de hoy:** de 0,07 a 0,13.

## Decisiones tomadas en este WP

1. **Rasgos del jugador:** `escudo`, `a distancia` y `armadura`, cada uno en [0, 1]. Su medición en el juego es del WP-33D; acá son datos.
2. **Contexto por bloques:** el vector de 60 es `[f, f·escudo, f·aDistancia, f·armadura]`, con f los 15 rasgos de la receta. Para buscar, los pesos sorteados se reducen a 15: `W₀ + escudo·W₁ + aDistancia·W₂ + armadura·W₃`, con Wₖ el bloque k de 15. Es una identidad: `pesos₆₀ · contexto(f) = pesos₁₅ · f`. Una prueba la verifica.
3. **Mundo sintético:** cada estilo tiene una función de puntaje verdadera, escrita en términos naturales (abajo). El éxito observado es `clamp(verdadero + 0,15·N(0,1), 0, 1)`, como el éxito real, que está entre 0 y 1.
4. **Números del modelo**, los mejores del prototipo: varianza del ruido del modelo 0,01; varianza del punto de partida 1; media del punto de partida 0,5 en la constante y 0 en el resto; exploración 2 en entrenamiento y 1 en el juego. Son constantes de la simulación. A la configuración llegan en el WP-33D, con los valores que deje este informe.
5. **Grupo y límites fijos:** el grupo de prueba (4 zombies, 3 esqueletos, 2 arañas) y `RecipeBounds(20, 400, 0.6)`.
6. **Sin olvido en la simulación:** los planes no tienen ticks. El olvido ya está probado en el WP-33A.
7. **El informe se versiona:** se copia a `docs/plan/calibracion-recetas.md`, que es la evidencia de la puerta.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `PlanRecipe`, `RoleSplit`, `RecipeBounds`, `RecipeFeatures`, `RecipeSearch`, `GroupComposition`
- `src/test/java/io/github/nicodoou/mobai/simulation/CalibrationReport.java` (estilo del informe y la etiqueta `calibration`), `LearningSimulationTest.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/SeededRandomSource.java`
- `build.gradle.kts` (tarea `simulationReport`: corre las pruebas con la etiqueta `calibration`)
- `docs/arquitectura.md` (tabla «Nombres en el código»), para actualizarla

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/PlayerTraits.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/ContextualFeatures.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/PlayerTraitsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/ContextualFeaturesTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/PlayStyle.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/RecipeLearner.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/EpisodeMetrics.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/RecipeCalibrationReport.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/PlayStyleTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/RecipeLearnerTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/simulation/EpisodeMetricsTest.java` |
| Crear | `docs/plan/calibracion-recetas.md` (copia del informe generado) |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

## Especificación: producción

### `PlayerTraits.java`

```java
/** How a player fights, each in [0, 1]: shield use, ranged weapon use and armor (CT-30). */
public record PlayerTraits(double shield, double ranged, double armor) {
  public PlayerTraits { … }
}
```

Cualquier valor fuera de [0, 1] o no finito → `IllegalArgumentException("PlayerTraits values must be in [0, 1], got " + shield + "/" + ranged + "/" + armor)`.

### `ContextualFeatures.java`

```java
/** Recipe features crossed with the player's traits, and the sampled weights folded back (CT-30). */
public final class ContextualFeatures {
  public static final int BLOCKS = 4;
  public static final int DIMENSION = RecipeFeatures.DIMENSION * BLOCKS;

  private ContextualFeatures() {}

  public static double[] of(double[] recipeFeatures, PlayerTraits traits) { … }

  public static double[] weightsFor(double[] weights, PlayerTraits traits) { … }
}
```

- **`of`:** `recipeFeatures` de largo `RecipeFeatures.DIMENSION` (si no, `IllegalArgumentException("ContextualFeatures.recipeFeatures must have 15 values, got " + n)`, con la constante). Devuelve 60 valores: el bloque 0 es f; el 1, f·escudo; el 2, f·aDistancia; el 3, f·armadura.
- **`weightsFor`:** `weights` de largo `DIMENSION` (si no, `"ContextualFeatures.weights must have 60 values, got " + n`, con la constante). Devuelve 15 valores: `w[i] + escudo·w[15+i] + aDistancia·w[30+i] + armadura·w[45+i]`.
- Los multiplicadores de cada bloque salen de un método privado `blockScales(PlayerTraits)`, que devuelve `[1, escudo, aDistancia, armadura]`, para que los dos métodos usen el mismo orden.

## Especificación: simulación (en `src/test`, paquete `simulation`)

### `PlayStyle.java`

```java
/** A synthetic opponent: its traits and the true worth of each recipe against it. */
enum PlayStyle {
  SHIELD_BLOCKER(new PlayerTraits(1, 0, 0.5)),
  BERSERKER(new PlayerTraits(0, 0, 0.5)),
  ARCHER(new PlayerTraits(0, 1, 0.5));

  PlayerTraits traits() { … }
  double trueScore(PlanRecipe recipe) { … }
  double optimum() { … }
  double legacyRegret() { … }
  PlayerTraits measuredTraits(RandomSource random, double deviation) { … }
}
```

**Valores de la receta** (con `RecipeBounds(20, 400, 0.6)` y el grupo de prueba): zf, zr y sf como en `RecipeFeatures`; `t = umbral / 0,6`; `v` = 1 con andanada; `u = ln(demora/20)/ln(20)`, que existe solo si hay zombies en reserva.

**Puntaje verdadero:**

| Estilo | Fórmula |
| --- | --- |
| `SHIELD_BLOCKER` | 0,75 − 1,2(zf − 0,5)² − 0,8(zr − 0,25)² − 0,4(sf − 0,5)² − 0,5(t − 0,5)² + 0,06·v, y si hay reserva, − 0,3(u − 0,5)² |
| `BERSERKER` | 0,70 − 0,9·zf² − 1,0·zr² − 0,3(sf − 0,25)² − 0,6(t − 0,15)² − 0,05·v |
| `ARCHER` | 0,70 − 0,6(zf − 0,25)² − 0,6·zr² − 1,0(sf − 1)² − 0,4(t − 0,7)² + 0,08·v |

Los coeficientes son constantes con nombre de cada estilo: un record privado con los números de cada fila, como argumento del constructor del enum.

**`optimum()`:** el mayor puntaje verdadero sobre todos los repartos de 4 zombies y 2 arañas, con y sin andanada, con t ∈ {0; 0,05; …; 1} (21 valores, umbral = t·0,6) y u ∈ {0; 0,1; …; 1} (11 valores, demora = `Math.round(20·20^u)` si hay reserva y 20 si no). Se calcula una vez (campo perezoso o en el constructor).

**`legacyRegret()`:** `optimum()` menos el mejor puntaje verdadero de las 4 estrategias de hoy, expresadas como recetas (demora 20 y umbral 0,3):
- asalto directo: zombies `(4,0,0)`, arañas `(2,0,0)`, sin andanada;
- flanqueo: `(2,2,0)`, `(1,1,0)`, sin andanada;
- contener y disparar: `(4,0,0)`, `(0,2,0)`, sin andanada;
- andanada: `(4,0,0)`, `(2,0,0)`, con andanada.

**`measuredTraits(random, deviation)`:** cada rasgo + `deviation·random.nextGaussian()`, recortado a [0, 1], en el orden escudo, a distancia, armadura.

### `RecipeLearner.java`

```java
/** Plays recipes against a synthetic opponent with the real model and search. */
final class RecipeLearner {
  static final GroupComposition GROUP = new GroupComposition(4, 3, 2);
  static final RecipeBounds BOUNDS = new RecipeBounds(20, 400, 0.6);
  static final double MODEL_NOISE_VARIANCE = 0.01;
  static final double PRIOR_VARIANCE = 1.0;
  static final double PRIOR_SUCCESS = 0.5;
  static final double REWARD_NOISE = 0.15;

  enum ModelKind { FLAT, CONTEXTUAL }

  record Session(ModelKind kind, int plans, double explorationScale, double traitJitter) { … }
  record Opponent(PlayStyle style, PlayerTraits traits) { … }
  record Episode(LinearPosterior model, List<Double> trueScores) { … }

  RecipeLearner(RandomSource random) { … }

  static LinearPosterior prior(ModelKind kind) { … }

  Episode play(LinearPosterior start, Opponent opponent, Session session) { … }
}
```

- **`prior(kind)`:** `LinearPosterior.prior(media, PRIOR_VARIANCE)`, con media de largo 15 (`FLAT`) o 60 (`CONTEXTUAL`): `PRIOR_SUCCESS` en el índice 0 y 0 en el resto.
- **`play`:** por cada plan, **en este orden** (el orden de los sorteos hace reproducible la corrida):
  1. si `traitJitter > 0`, rasgos del plan = rasgos del oponente + `traitJitter·nextGaussian()` cada uno (escudo, a distancia, armadura), recortados a [0, 1]; si no, los del oponente;
  2. `w = model.sample(random, explorationScale)`;
  3. pesos de 15 = `w` (`FLAT`) o `ContextualFeatures.weightsFor(w, rasgos)` (`CONTEXTUAL`);
  4. `receta = new RecipeSearch(() -> BOUNDS).best(pesos, GROUP)` (la búsqueda se crea una vez, en el constructor);
  5. `verdadero = opponent.style().trueScore(receta)`; se agrega a la lista;
  6. `éxito = clamp(verdadero + REWARD_NOISE·nextGaussian(), 0, 1)`;
  7. `x = RecipeFeatures.of(receta, GROUP.skeletons(), BOUNDS)`, y con `CONTEXTUAL`, `ContextualFeatures.of(x, rasgos)`;
  8. `model = model.withObservation(x, éxito, MODEL_NOISE_VARIANCE)`.
- Separá cada paso en métodos privados de una tarea.

### `EpisodeMetrics.java`

```java
/** How close an episode got to the best recipe (CT-30 gate). */
final class EpisodeMetrics {
  static final int WINDOW = 10;
  static final double COMPETENCE_TOLERANCE = 0.05;

  static double regretOfFirst(List<Double> trueScores, double optimum, int plans) { … }
  static double regretOfLast(List<Double> trueScores, double optimum, int plans) { … }
  static OptionalInt plansToCompetent(List<Double> trueScores, double optimum) { … }
}
```

- `regretOfFirst`: `optimum` − promedio de los primeros `plans`. `regretOfLast`: lo mismo con los últimos.
- `plansToCompetent`: el primer índice s tal que el promedio de `trueScores[s .. s+WINDOW−1]` es ≥ `optimum − COMPETENCE_TOLERANCE`; devuelve `s + WINDOW`. Si no hay ninguno, vacío. El bucle está acotado por el largo de la lista.

### `RecipeCalibrationReport.java` (`@Tag("calibration")`)

Escribe `build/reports/simulation/recipes.md` con cuatro experimentos. Cada uno usa un `SeededRandomSource(seed)` nuevo por semilla.

| Experimento | Qué | Semillas |
| --- | --- | --- |
| `SCRATCH` | para cada estilo: modelo `CONTEXTUAL` desde `prior`; oponente con `measuredTraits(random, 0.15)`; sesión de 150 planes, exploración 1, jitter 0,05 | 1 a 20 |
| `BASE_FLAT` | entrenar una base `FLAT` con 600 planes (estilo = `values()[i % 3]`, un oponente medido nuevo por plan con desvío 0,15, sesión de 1 plan, exploración 2, jitter 0); después, para cada estilo, 40 planes desde esa base (exploración 1, jitter 0) | 1 a 10 |
| `BASE_CONTEXTUAL_EXACT` | igual, `CONTEXTUAL`, con desvío 0 y jitter 0 | 1 a 10 |
| `BASE_CONTEXTUAL_NOISY` | igual, `CONTEXTUAL`, con desvío 0,15 y jitter 0,05 | 1 a 10 |

En los tres experimentos con base, la misma semilla sirve para el entrenamiento y para los 40 planes de cada estilo: se entrena una vez por semilla y la misma base se usa contra los tres estilos.

**Tabla del informe**, una fila por experimento y estilo:

| Experimento | Estilo | Óptimo | Distancia, 4 estrategias de hoy | Distancia, primeros 20 (promedio) | Distancia, últimos 10 (promedio) | Planes hasta competente (mediana; «nunca» si falta en más de la mitad) |

**La puerta, al final del informe:** `PASS` si en `BASE_CONTEXTUAL_NOISY`, para **cada** estilo, la distancia promedio en los primeros 20 planes es ≤ 0,07 **y** menor que la de las 4 estrategias de hoy. Si no, `FAIL` con los estilos que no cumplen. Se escribe también la línea «Control: `BASE_FLAT` primeros 20» con sus tres valores, para que se vea por qué hacen falta los rasgos.

## Pruebas obligatorias

### `PlayerTraitsTest` (1)

| Prueba | Verifica |
| --- | --- |
| `valuesOutsideZeroToOneAreRejected` | `(1.1, 0, 0)` y `(0, NaN, 0)` con su mensaje |

### `ContextualFeaturesTest` (3)

| Prueba | Verifica |
| --- | --- |
| `blocksAreTheFeaturesScaledByEachTrait` | f = `[1, 2, …, 15]`, rasgos `(0.5, 0.25, 1)`: índice 0 = 1; 15 = 0,5; 30 = 0,25; 45 = 1; 59 = 15 |
| `foldedWeightsGiveTheSameScore` | pesos de 60 con `w[i] = 0.01·(i+1)`, f = `[1, 0.5, 0.25, …]` (índice i: `1/(i+1)`), rasgos `(0.3, 0.7, 0.2)`: `pesos · of(f) = weightsFor(pesos) · f` dentro de `1e-12` |
| `wrongLengthsAreRejected` | f de 14 y pesos de 59, con sus mensajes |

### `PlayStyleTest` (4)

Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `trueScoresOfKnownRecipes` | receta `(2,2,0)`, `(1,1,0)`, andanada, demora 20, umbral 0,3: 0,76 / 0,33275 / 0,4765 (`SHIELD_BLOCKER` / `BERSERKER` / `ARCHER`); receta `(4,0,0)`, `(2,0,0)`, sin andanada, demora 20, umbral 0: 0,175 / 0,66775 / −0,5335 |
| `aReserveAddsTheDelayTerm` | `(2,1,1)`, `(1,1,0)`, andanada, demora 89, umbral 0,3: `SHIELD_BLOCKER` 0,734999176931682 |
| `optimaAreTheKnownValues` | 0,809999176931682 / 0,68125 / 0,78 |
| `legacyRegretsAreTheKnownValues` | 0,10999917693168204 / 0,0735 / 0,1335 |

### `EpisodeMetricsTest` (2)

| Prueba | Verifica |
| --- | --- |
| `regretsAverageTheFirstAndLastPlans` | 10 planes de 0,1 y 10 de 0,8, óptimo 0,81: primeros 10 → 0,71; últimos 10 → 0,01 |
| `competenceNeedsAWholeWindowCloseToTheOptimum` | los mismos datos: `plansToCompetent` = 20; con óptimo 0,9, vacío |

### `RecipeLearnerTest` (3)

| Prueba | Verifica |
| --- | --- |
| `priorsHaveTheirDimensions` | `FLAT` → 15 y `CONTEXTUAL` → 60; media en el índice 0 = 0,5 y 0 en el resto |
| `anEpisodeHasOneScorePerPlan` | `CONTEXTUAL`, 12 planes contra `ARCHER`: 12 puntajes; `observations()` = 12 |
| `sameSeedGivesTheSameEpisode` | dos aprendices con `SeededRandomSource(5)`, 20 planes `CONTEXTUAL` con jitter 0,05: mismas listas de puntajes |

Total: **13 pruebas** (fuera del informe de calibración).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `weightsFor`, cambiar el orden de los bloques de escudo y a distancia | otro puntaje | `foldedWeightsGiveTheSameScore` |
| 2 | En `SHIELD_BLOCKER`, sacar la condición «solo si hay reserva» del término de la demora | `(2,2,0)` da 0,76 − 0,3·0,25 = 0,685 (u = 0) | `trueScoresOfKnownRecipes` |
| 3 | En `plansToCompetent`, devolver `s` en vez de `s + WINDOW` | 10 | `competenceNeedsAWholeWindowCloseToTheOptimum` |
| 4 | En `RecipeLearner`, observar los 15 rasgos también con `CONTEXTUAL` | excepción por largo | `anEpisodeHasOneScorePerPlan` |

## Procedimiento

1. Rama `wp-33c-simulacion-de-recetas` desde `origin/main` actualizado (con los WP-33A y WP-33B).
2. `PlayerTraits`, `ContextualFeatures` y sus pruebas. Commit: `feat: player traits and contextual recipe features (CT-30)`.
3. `PlayStyle`, `EpisodeMetrics`, `RecipeLearner` y sus pruebas. Commit: `test: synthetic play styles and recipe learner`.
4. `RecipeCalibrationReport`. Commit: `test: recipe calibration report (CT-30 gate)`.
5. Pruebas que muerden, de a una y sin commit.
6. Correr el informe (`./gradlew simulationReport`, o el launcher con `--include-tag calibration`). Copiá `build/reports/simulation/recipes.md` a `docs/plan/calibracion-recetas.md` y sumá `docs/arquitectura.md`. Commit: `docs: recipe calibration report and names`.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-33C: recipe simulation and calibration`, CI en verde e informe. **El informe final incluye la tabla completa y el resultado de la puerta (`PASS` o `FAIL`).**

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Todo el WP es Java puro: compila y se prueba con el JDK 21 del contenedor. Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central (si da 429, de `https://repo.maven.apache.org/maven2/`) a una carpeta fuera del repo. Compilá con `javac --release 21` `src/main/java/io/github/nicodoou/mobai/domain`, `testsupport/SeededRandomSource.java` y las clases nuevas de `simulation` y `domain/strategy`. Corré las pruebas con los jars **explícitos** en `-cp`, y el informe con `--include-tag calibration --select-class io.github.nicodoou.mobai.simulation.RecipeCalibrationReport`. El informe se escribe relativo al directorio de trabajo: corrélo desde la raíz del worktree.
- Formateá con google-java-format 1.36.1 (bajalo a una carpeta fuera del repo). El CI del PR verifica Spotless, ArchUnit y la suite completa: esperá a que termine.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si el informe tarda más de 10 minutos, bajá las semillas de `SCRATCH` a 10 y avisalo.

## Fuera de alcance

- Medir los rasgos en el juego, la configuración y el cerebro (WP-33D).
- La base en disco y el modo entrenamiento (WP-33E y WP-33G).
- Recalibrar si la puerta da `FAIL`: en ese caso se reporta y el orquestador decide.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 13 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] `docs/plan/calibracion-recetas.md` con la tabla y el resultado de la puerta.
- [ ] Build, cobertura y CI en verde.
