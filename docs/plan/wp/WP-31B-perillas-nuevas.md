# WP-31B — Perillas nuevas, arranque en el medio y recalibración

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-34B (mergeado, #74) |
| Modelo | Opus |
| Rama | `wp-31b-perillas-nuevas` |

## Objetivo

Primera parte del CT-33, aprobado por Nico el 14 oct. La receta suma **dos perillas continuas** que el modelo aprende por jugador:

- `groupRetreatStrength`, entre 0 y `learning.max-group-retreat-strength` (0,6): con qué fuerza se retira todo el grupo;
- `chaseCommitment`, entre 0 y 1: cuánto persiguen.

Además:

- **Arranque en el medio (D-02):** el punto de partida del modelo prefiere el medio de las 4 perillas continuas, con la intensidad `k` calibrada en la simulación.
- **Regla B:** no hay recetas con todos los zombies de reserva.
- **Simulación:** los estilos sintéticos suman cuánto les conviene cada perilla nueva, y se recalibra con la misma puerta.

**En este WP las perillas nuevas no cambian el juego**: el cerebro las usa recién en el WP-34C. Agrandar los modelos guardados es el WP-31C. Mientras tanto, un server con memorias viejas no puede planificar con recetas: es esperado, y Nico no reinicia hasta que estén los tres.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `PlanRecipe`, `RecipeBounds`, `RecipeFeatures`, `RecipeSearch`, `UnitIntervalPeak`, `ContextualFeatures`, `RecipePlanner` (solo `prior` y `bounds`)
- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java`: solo `prior`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/LearningSettings.java`, `SettingsChecks.java`, `NamedSetting.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (`learning`), `src/main/resources/config.yml` (sección `learning`)
- `src/test/java/io/github/nicodoou/mobai/simulation/`: `PlayStyle`, `PlayStyleTest`, `RecipeLearner`, `RecipeLearnerTest`, `RecipeCalibrationReport`
- `docs/plan/calibracion-recetas.md`
- Pruebas: `PlanRecipeTest`, `RecipeFeaturesTest`, `RecipeSearchTest`, `ContextualFeaturesTest`, `RecipePlannerTest`, `SettingsValidationTest`, `ConfigLoaderTest`, `testsupport/TestSettings.java`
- Las que encuentren los greps de «Archivos»

## Reglas de negocio

1. **`PlanRecipe`** suma dos componentes al final:
   - `groupRetreatStrength` en [0, 1);
   - `chaseCommitment` en [0, 1].
2. **`RecipeBounds`** suma `maxGroupRetreatStrength`, en (0, 1).
3. **Rasgos** (`RecipeFeatures`): de 15 a **19**, agregados **al final** (el orden de los 15 de hoy no cambia):

   | Índice | Rasgo |
   | --- | --- |
   | 15 | `GROUP_RETREAT_LINEAR` = `groupRetreatStrength ÷ maxGroupRetreatStrength` |
   | 16 | `GROUP_RETREAT_SQUARE` = su cuadrado |
   | 17 | `CHASE_LINEAR` = `chaseCommitment` |
   | 18 | `CHASE_SQUARE` = su cuadrado |

   Si `groupRetreatStrength > maxGroupRetreatStrength`: `IllegalArgumentException` con el mensaje `"RecipeFeatures.groupRetreatStrength must not exceed <max>, got <valor>"`, igual que la retirada individual.

   `ContextualFeatures.DIMENSION` pasa sola a 76.
4. **Búsqueda:** las perillas nuevas salen de su pico, como la retirada individual:
   - `groupRetreatStrength = UnitIntervalPeak.of(w[15], w[16]) × maxGroupRetreatStrength`;
   - `chaseCommitment = UnitIntervalPeak.of(w[17], w[18])`.

   Son las mismas para todas las candidatas.
5. **Regla B:** en `zombieSplits`, si hay zombies (`zombies > 0`), se descarta la división con `reserve == zombies`. La de 0 zombies, `(0, 0, 0)`, sigue.
6. **Arranque en el medio** (clase nueva `RecipePrior`):
   - la media del punto de partida es `priorSuccess` en el sesgo (índice 0);
   - en el **bloque base** (índices 0 a 18), `+k` en el lineal y `−k` en el cuadrado de las 4 perillas continuas: demora (7, 8), retirada (9, 10), retirada del grupo (15, 16) y persecución (17, 18);
   - 0 en el resto, incluidos los bloques de los rasgos del jugador.

   El pico de `k·x − k·x²` está en 0,5. `RecipePlanner.prior()` y la simulación lo usan.
7. **Configuración (`learning`):**
   - `max-group-retreat-strength: 0.6`, estrictamente entre 0 y 1;
   - `mid-knob-prior: <k calibrado>`, cero o positivo.

   En `TestSettings`, `0.6` y **`0.0`**: el arranque plano de hoy, para que las pruebas sembradas del planificador y del cerebro no dependan de `k`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePrior.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/PlanRecipe.java`, `RecipeBounds.java`, `RecipeFeatures.java`, `RecipeSearch.java`, `RecipePlanner.java` (`prior` y `bounds`, nada más) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/LearningSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/simulation/PlayStyle.java`, `PlayStyleTest.java`, `RecipeLearner.java`, `RecipeLearnerTest.java`, `RecipeCalibrationReport.java` |
| Modificar | `docs/plan/calibracion-recetas.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipePriorTest.java` |
| Modificar | `PlanRecipeTest`, `RecipeFeaturesTest`, `RecipeSearchTest`, `ContextualFeaturesTest`, `RecipePlannerTest`, `SettingsValidationTest` |
| Modificar | los archivos con `new PlanRecipe(`, `new RecipeBounds(` o `new LearningSettings(` (`grep -rln` en `src/test`). Hoy, además de los de arriba: `MemoryReportTest`, `DebugLogTest`, `TrainingDataLogTest`, `ClosePlanTest`, `TraitLedgerTest`, `ConfigLoaderTest`, `TestSettings` |
| Modificar | los archivos de prueba que usan `60` como dimensión del modelo (`SaveAndLoadMemoriesTest`, `TrainPlayersTest`, `CoreServicesTest`): solo si fallan (ver «Correcciones permitidas») |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código»: `RecipePrior`) |

`MemoryReport`, `DebugLog` y `TrainingDataLog` **no** se tocan: mostrar las perillas es el WP-31C.

## Especificación

### `PlanRecipe`

```java
public record PlanRecipe(
    RoleSplit zombies,
    RoleSplit spiders,
    boolean volley,
    long reserveDelayTicks,
    double retreatHealthFraction,
    double groupRetreatStrength,
    double chaseCommitment)
```

- Javadoc: `/** A plan as played (CT-30, CT-33): roles by kind, volley, when the reserve joins, when mobs retreat alone and together, and how far they chase. */`.
- Validaciones nuevas, con el estilo de `retreatHealthFraction`:
  - `"PlanRecipe.groupRetreatStrength must be in [0, 1), got <valor>"`;
  - `"PlanRecipe.chaseCommitment must be in [0, 1], got <valor>"`.

**En las construcciones existentes de las pruebas**, agregá `, 0.3, 0.5` (el medio de cada perilla), salvo donde este WP diga otra cosa.

### `RecipeBounds`

Cuarto componente `double maxGroupRetreatStrength`. Validación con el mismo chequeo que `maxRetreatHealthFraction`: `"RecipeBounds.maxGroupRetreatStrength must be in (0, 1), got <valor>"`.

**En las construcciones existentes**, agregá `, 0.6` al final.

### `RecipeFeatures`

- `DIMENSION = 19` y constantes públicas nuevas: `GROUP_RETREAT_LINEAR = 15`, `GROUP_RETREAT_SQUARE = 16`, `CHASE_LINEAR = 17`, `CHASE_SQUARE = 18`.
- El record privado `Knobs` suma `groupRetreat` y `chase`.
- El vector suma los 4 valores al final, en el orden de la tabla de las reglas.
- Validación nueva `validateGroupRetreat`, como `validateRetreat`.

### `RecipeSearch`

- `Tuning` suma `groupRetreat` y `chase`. En `tuningFor`:
  - `groupRetreat = UnitIntervalPeak.of(weights[GROUP_RETREAT_LINEAR], weights[GROUP_RETREAT_SQUARE]) * bounds.maxGroupRetreatStrength()`;
  - `chase = UnitIntervalPeak.of(weights[CHASE_LINEAR], weights[CHASE_SQUARE])`.
- `candidatesFor` pasa los dos valores al construir cada `PlanRecipe`.
- En `zombieSplits`:

  ```java
  // CT-33 (B): a recipe that holds every zombie back sends nobody in, so at least one fights.
  ```

  y la condición que salta `reserve == zombies` cuando `zombies > 0`.

Si al pasar `Tuning` o `candidatesFor` una función supera los 3 parámetros o las 20 líneas, usá un record privado (corrección 2).

### `RecipePrior` (nueva)

```java
package io.github.nicodoou.mobai.domain.strategy;

/**
 * Where the recipe model starts (CT-30, CT-33): the prior success in the bias and, with strength k,
 * a preference for the middle of every continuous knob, so sampled peaks seldom fall on an edge
 * before the model knows better (D-02).
 */
public final class RecipePrior {
  private RecipePrior() {}

  /** The mean for the contextual model: the preference lives in the base block only. */
  public static double[] contextualMean(double priorSuccess, double midKnobPrior)

  /** The mean for a model over the recipe features alone (the simulation's flat model). */
  public static double[] recipeMean(double priorSuccess, double midKnobPrior)
}
```

- `contextualMean` tiene largo `ContextualFeatures.DIMENSION`, y `recipeMean` tiene largo `RecipeFeatures.DIMENSION`. Los primeros 19 valores son los mismos.
- Valores:
  - `mean[0] = priorSuccess`;
  - para cada par `(DELAY_LINEAR, DELAY_SQUARE)`, `(RETREAT_LINEAR, RETREAT_SQUARE)`, `(GROUP_RETREAT_LINEAR, GROUP_RETREAT_SQUARE)` y `(CHASE_LINEAR, CHASE_SQUARE)`, el lineal vale `+midKnobPrior` y el cuadrado `−midKnobPrior`;
  - el resto, 0.
- Validación:
  - `"RecipePrior.midKnobPrior must be zero or positive, got <valor>"`;
  - `"RecipePrior.priorSuccess must be between 0 and 1, got <valor>"`.
- Las dos funciones comparten una privada que llena el bloque base.

### `RecipePlanner`

- `prior()`:

  ```java
  LinearPosterior.prior(
      RecipePrior.contextualMean(learning.priorSuccess(), learning.midKnobPrior()),
      learning.priorVariance())
  ```

  Se borra `BIAS_INDEX` si queda sin uso.
- `bounds()` pasa `learning.maxGroupRetreatStrength()` como cuarto argumento.

### `LearningSettings`

- Dos componentes nuevos **al final**: `double maxGroupRetreatStrength` y `double midKnobPrior`.
- La función `requireRetreatFractionInsideUnit` se generaliza a `requireStrictlyInsideUnit(NamedSetting setting)` y sirve a `maxRetreatHealthFraction` y a `maxGroupRetreatStrength`. Los mensajes de hoy no cambian; los nuevos usan el nombre `LearningSettings.maxGroupRetreatStrength`.
- `SettingsChecks.requireNonNegative("LearningSettings.midKnobPrior", midKnobPrior)`.
- **En las construcciones existentes de las pruebas**, `0.6, 0.0` al final. El helper `learning(...)` de `SettingsValidationTest` también lo lleva.

### `config.yml` y `ConfigLoader`

Al final de la sección `learning`:

```yaml
  # Umbral máximo de retirada de todo el grupo que se prueba (CT-33).
  max-group-retreat-strength: 0.6
  # Arranque en el medio (D-02): cuánto prefiere el modelo, antes de aprender, el medio de las
  # perillas continuas. Calibrado con la simulación (docs/plan/calibracion-recetas.md).
  mid-knob-prior: <k elegido>
```

`ConfigLoader.learning` lee `learning.max-group-retreat-strength` y `learning.mid-knob-prior` con `number`, en ese orden y al final.

### Simulación

**`RecipeLearner`:**
- `BOUNDS = new RecipeBounds(20, 400, 0.6, 0.6)`.
- Constante nueva `static final double MID_KNOB_PRIOR = <k elegido>;`.
- `prior(kind)` usa `RecipePrior.recipeMean(PRIOR_SUCCESS, MID_KNOB_PRIOR)` para `FLAT` y `RecipePrior.contextualMean(...)` para `CONTEXTUAL`. Se borra `BIAS_INDEX` si queda sin uso.
- `Episode` suma `List<PlanRecipe> recipes` (una por plan, en orden, copiada con `List.copyOf`), y `play` la llena.

**`PlayStyle`:**
- `Coefficients` suma `Penalty groupRetreat` y `Penalty chase`, al final.
- `scoreOf` resta `groupRetreat.of(knobs.groupRetreat())` y `chase.of(knobs.chase())`.
- `Knobs` suma:
  - `groupRetreat = recipe.groupRetreatStrength() / RecipeLearner.BOUNDS.maxGroupRetreatStrength()`;
  - `chase = recipe.chaseCommitment()`.
- **Valores** (peso, objetivo), al final de cada `Coefficients`:

  | Estilo | `groupRetreat` | `chase` | Por qué |
  | --- | --- | --- | --- |
  | `SHIELD_BLOCKER` | `(0.3, 0.5)` | `(0.3, 0.7)` | Bloquea y aguanta: conviene presionarlo sin dispersarse |
  | `BERSERKER` | `(0.4, 0.2)` | `(0.5, 1.0)` | Va al frente: conviene pelearle hasta el final y perseguirlo |
  | `ARCHER` | `(0.3, 0.7)` | `(0.4, 0.3)` | Se aleja y tira: perseguirlo disperso es caro, y conviene retirarse antes |

- **Grilla del óptimo:**
  - suma `GROUP_RETREAT_STEPS = 10` (posiciones `step ÷ 10 × 0,6`) y `CHASE_STEPS = 10` (posiciones `step ÷ 10`);
  - descarta la división de zombies con todos de reserva, como la búsqueda (regla B).

  Los objetivos de la tabla caen en la grilla, así que **los óptimos no cambian**.
- **Recetas de hoy** (`legacyRecipe`): `groupRetreatStrength = 0.4` (la constante `LEGACY_GROUP_RETREAT_STRENGTH`) y `chaseCommitment = 1` (`LEGACY_CHASE_COMMITMENT`: hoy siempre persiguen).

**`RecipeCalibrationReport`:**
- **Tabla nueva**, debajo de la de distancias, con el título `## Perillas en el borde (D-02)`:
  - para SCRATCH, por estilo, el porcentaje de planes, entre los primeros 20 de cada semilla, en que alguna perilla continua cayó en un borde (posición 0 o 1, con tolerancia 1e-9);
  - las posiciones son: retirada ÷ máximo, retirada del grupo ÷ máximo, persecución, y la demora (en escala logarítmica, como `PlayStyle.Knobs`) solo si hay reserva;
  - con un decimal.
- **El encabezado** dice el `k` usado: `Arranque en el medio: k = <valor>`.

### Elección de `k` (calibración)

1. Con todo lo anterior hecho, corré `./gradlew simulationReport --no-daemon` con `MID_KNOB_PRIOR` = 0, 0,5, 1 y 2, de a uno.
2. Por cada corrida, anotá:
   - las tres distancias de BASE_CONTEXTUAL_NOISY en los primeros 20;
   - si la puerta da PASS;
   - el porcentaje de bordes de SCRATCH por estilo.
3. **Elegí el `k`** con PASS y la menor distancia máxima entre los tres estilos de BASE_CONTEXTUAL_NOISY en los primeros 20. Si empatan con 4 decimales, el menor `k`.
4. **Si ninguno da PASS, frená y reportá** la tabla: no se afloja la puerta.
5. Dejá `MID_KNOB_PRIOR` y `mid-knob-prior` del `config.yml` en el `k` elegido. Reemplazá `docs/plan/calibracion-recetas.md` por el reporte de esa corrida y agregale al final una sección `## Elección de k (CT-33)` con la tabla de las 4 corridas y una línea con el `k` elegido y por qué.

## Pruebas obligatorias

### `RecipePriorTest` (5)

| Prueba | Verifica |
| --- | --- |
| `priorPrefersTheMiddleOfEveryContinuousKnob` | `contextualMean(0.5, 1.0)`: largo 76; `[0] = 0,5`; `[7] = [9] = [15] = [17] = 1`; `[8] = [10] = [16] = [18] = −1`; todos los demás 0 |
| `itsPeaksAreInTheMiddle` | con esa media, `UnitIntervalPeak.of(m[15], m[16])` y `of(m[17], m[18])` dan 0,5 |
| `withoutStrengthOnlyTheBiasIsSet` | `contextualMean(0.5, 0)`: `[0] = 0,5` y el resto 0 |
| `recipeMeanIsTheBaseBlock` | `recipeMean(0.5, 1.0)`: largo 19 e igual a los primeros 19 de `contextualMean(0.5, 1.0)` |
| `negativeStrengthIsRejected` | `contextualMean(0.5, -1.0)`: `"RecipePrior.midKnobPrior must be zero or positive, got -1.0"` |

### `RecipeFeaturesTest`

- `BOUNDS = new RecipeBounds(25, 400, 0.6, 0.6)`.
- `knownRecipeGivesKnownFeatures`: la receta suma `0.3, 0.5` y el vector suma al final `0.5, 0.25, 0.5, 0.25`.
- **Nueva** `groupRetreatAboveItsMaximumIsRejected`: `new PlanRecipe(new RoleSplit(1, 0, 0), NO_MOBS, false, 25, 0, 0.7, 0.5)` → `"RecipeFeatures.groupRetreatStrength must not exceed 0.6, got 0.7"`.

### `RecipeSearchTest`

- **`allZeroWeightsKeepTheFirstCandidate`:** suma `groupRetreatStrength` 0 y `chaseCommitment` 0. Con pesos 0, el pico es el menor `x`.
- **Nuevas:**

  | Prueba | Pesos | Composición | Espera |
  | --- | --- | --- | --- |
  | `groupRetreatFollowsItsPeak` | `w[15] = 0.6`, `w[16] = −1` | (4, 0, 0) | `groupRetreatStrength` 0,18 (pico 0,3 × 0,6) |
  | `chaseFollowsItsPeak` | `w[17] = 0.6`, `w[18] = −1` | (4, 0, 0) | `chaseCommitment` 0,3 |
  | `aReserveNeverTakesEveryZombie` | `w[3] = 5` | (4, 0, 0) | zombies `RoleSplit(1, 0, 3)` |
  | `withoutZombiesTheEmptySplitStays` | todos 0 | (0, 3, 0) | zombies `RoleSplit(0, 0, 0)` |

### `PlanRecipeTest`

`reserveDelayAndRetreatAreValidated` suma dos casos:
- `groupRetreatStrength` 1,0 → `"PlanRecipe.groupRetreatStrength must be in [0, 1), got 1.0"`;
- `chaseCommitment` 1,5 → `"PlanRecipe.chaseCommitment must be in [0, 1], got 1.5"`.

`boundsAreValidated` suma `new RecipeBounds(25, 400, 0.6, 1.0)` → `"RecipeBounds.maxGroupRetreatStrength must be in (0, 1), got 1.0"`.

### `ContextualFeaturesTest`

- `blocksAreTheFeaturesScaledByEachTrait`: índices 19, 38, 57 y 75 (en lugar de 15, 30, 45 y 59), con los mismos valores esperados, salvo el último, que pasa de 15 a 19.
- `wrongLengthsAreRejected`: `new double[18]` → `"… must have 19 values, got 18"`, y `new double[75]` → `"… must have 76 values, got 75"`.

### `RecipePlannerTest`

- La prueba de la dimensión del prior pasa de 60 a 76.
- **Nueva** `priorStartsAtTheConfiguredMiddle`: con `TestSettings` cambiado a `midKnobPrior` 1,0, la media de `prior()` es igual a `RecipePrior.contextualMean(0.5, 1.0)` (tolerancia 1e-9). Armá esa configuración con un helper de la prueba.

### `SettingsValidationTest`

Casos nuevos:
- `maxGroupRetreatStrength` 1,0 → `"LearningSettings.maxGroupRetreatStrength must be strictly between 0 and 1, got 1.0"`;
- `midKnobPrior` −1,0 → `"LearningSettings.midKnobPrior must be zero or positive, got -1.0"`.

### Simulación

**`PlayStyleTest`:** en las recetas de las pruebas, `groupRetreatStrength` 0,3 y `chaseCommitment` 0,5.

| Prueba | Valores nuevos |
| --- | --- |
| `trueScoresOfKnownRecipes` | `flank`: SHIELD 0,748, BERSERKER 0,17175, ARCHER 0,4485. `assault`: SHIELD 0,163, BERSERKER 0,50675, ARCHER −0,5615 |
| `aReserveAddsTheDelayTerm` | 0,722999176931682 |
| `optimaAreTheKnownValues` | sin cambios: 0,809999176931682, 0,68125 y 0,78 |
| `legacyRegretsAreTheKnownValues` | SHIELD 0,1453325102650154, BERSERKER 0,16061111111111115, ARCHER 0,3298333333333333 |

**Cuentas** (posición del grupo 0,3 ÷ 0,6 = 0,5; persecución 0,5):
- SHIELD resta 0,3 × 0² + 0,3 × 0,2² = 0,012;
- BERSERKER resta 0,4 × 0,3² + 0,5 × 0,5² = 0,161;
- ARCHER resta 0,3 × 0,2² + 0,4 × 0,2² = 0,028.

Las recetas de hoy, en 0,4 ÷ 0,6 y 1, suman:
- SHIELD: 0,3 × (2/3 − 0,5)² + 0,3 × 0,3² = 0,0353333;
- BERSERKER: 0,4 × (2/3 − 0,2)² = 0,0871111;
- ARCHER: 0,3 × (2/3 − 0,7)² + 0,4 × 0,7² = 0,1963333.

**`RecipeLearnerTest`:**
- `priorsHaveTheirDimensions` pasa a 19 y 76.
- **Nueva** `anEpisodeKeepsEachRecipe`: un episodio de 3 planes tiene 3 recetas.

### Pruebas sembradas

Al pasar de 60 a 76 parámetros, el sorteo con la misma semilla reparte distinto los números, aunque `midKnobPrior` sea 0 en `TestSettings`. Algunas pruebas sembradas pueden esperar otra receta, por ejemplo `BrainRecipesTest`, `RecipePlannerTest`, `TrainPlayersTest`, `CoreServicesTest` o las de los incidentes.

**Una prueba que cambia por eso se puede actualizar** (corrección 4), con una condición: **anotá en el informe cada valor viejo y nuevo, y por qué cambió**. Si una prueba cambia por otra cosa, frená y reportá.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `RecipePrior`, el cuadrado con `+k` | `priorPrefersTheMiddleOfEveryContinuousKnob` y `itsPeaksAreInTheMiddle` (el pico se va a 1) |
| 2 | Sin la regla B | `aReserveNeverTakesEveryZombie` (da `RoleSplit(0, 0, 4)`) |
| 3 | En `tuningFor`, sin multiplicar por `maxGroupRetreatStrength` | `groupRetreatFollowsItsPeak` (da 0,3) |
| 4 | En `PlayStyle.scoreOf`, sin restar `chase` | `trueScoresOfKnownRecipes` |

## Verificación

No hay verificación en el server: las perillas no cambian el juego hasta el WP-34C. Opus suma `max-group-retreat-strength` y `mid-knob-prior` al `config.yml` de prueba al mergear, y le pide a Nico que no reinicie el server hasta el WP-31C.

## Procedimiento

1. Rama `wp-31b-perillas-nuevas` desde `origin/main` actualizado.
2. `PlanRecipe`, `RecipeBounds`, `RecipeFeatures`, `RecipeSearch`, `LearningSettings`, `ConfigLoader`, `config.yml` (con `mid-knob-prior: 0.0` provisorio) y todas las pruebas que construyen esos tipos. Build en verde. Commit: `feat: group retreat and chase knobs in the recipe (CT-33)`.
3. `RecipePrior`, `RecipePriorTest` y `RecipePlanner.prior`. Commit: `feat: the recipe model starts in the middle of its continuous knobs (D-02, CT-33)`.
4. Simulación (`PlayStyle`, `RecipeLearner`, el reporte y sus pruebas). Commit: `test: simulation styles weigh the new knobs (CT-33)`.
5. Elección de `k` (ver arriba), `MID_KNOB_PRIOR`, `config.yml` y `calibracion-recetas.md`. Commit: `docs: recalibrate the recipe model with the middle start (CT-33)`.
6. `arquitectura.md`. Commit: `docs: recipe prior in the names table`.
7. Pruebas que muerden (de a una; sin commit).
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`: Nico puede tener su server corriendo con un daemon de Gradle.
9. Push, PR `WP-31B: new knobs, middle start and recalibration (CT-33)`, CI en verde e informe con:
   - la tabla de la elección de `k`;
   - la prueba exacta que falló en cada rotura;
   - cada prueba sembrada que cambió, con valor viejo, nuevo y causa.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o usá un record privado y avisalo.
3. En `SaveAndLoadMemoriesTest`, `TrainPlayersTest` y `CoreServicesTest`, si fallan porque usan `60` como dimensión del modelo de recetas, reemplazalo por `ContextualFeatures.DIMENSION`.
4. Actualizar pruebas sembradas que cambian solo porque el sorteo reparte distinto, informando cada una.

## Fuera de alcance

- Agrandar los modelos guardados, el esquema 4 y `base.json` versión 2 (WP-31C).
- Mostrar las perillas en el log o en `/mobai memory` (WP-31C).
- Que el cerebro use las perillas (WP-34C).

## Aceptación

- [ ] Los archivos indicados y ninguno más.
- [ ] Las pruebas nuevas y las modificadas, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Elección de `k` documentada, con PASS en la puerta.
- [ ] Build, cobertura y CI en verde.

## Enmienda (14 oct, decisión de Nico, opción 1)

En la primera elección de `k`, ninguno pasó la puerta: el berserker quedó en 0,07 a 0,10. Opus lo midió con 40 semillas:
- `k` casi no cambia el resultado;
- tampoco cambia si el óptimo de persecución del berserker se aleja del borde;
- el costo viene de aprender 16 parámetros más con la misma base.

Con la base entrenada con **1000 planes** pasan los tres estilos. Cambios en la calibración:

1. **`RecipeCalibrationReport`:**
   - `TRAINING_PLANS = 1000`, en lugar de 600;
   - `LAST_BASE_SEED = 40`, en lugar de 10: con 10 semillas la medición es demasiado ruidosa.

   El criterio de la puerta no cambia (≤ 0,07 y menor que las 4 estrategias de hoy). El encabezado del reporte lo dice solo: lee las constantes.
2. **`k` = 2.**
   - Medición de Opus (primeros 20 planes de BASE_CONTEXTUAL_NOISY):
     - escudo 0,034;
     - berserker 0,062;
     - arquero 0,037.
   - Planes de SCRATCH con alguna perilla en el borde: 63 / 48 / 66 %, contra 95 / 97 / 97 % con `k` = 0.
   - En la sección «Elección de k (CT-33)», la tabla de las corridas con 1000 planes y 40 semillas para `k` = 0, 1 y 2, y la de la primera corrida (600 planes, 10 semillas), para que quede el porqué.
3. **`RecipeLearnerTest.priorsHaveTheirDimensions`:** con `k` > 0, la media ya no es 0 fuera del sesgo. Compará contra `RecipePrior.recipeMean(PRIOR_SUCCESS, MID_KNOB_PRIOR)` y `contextualMean(...)`.
4. **Consecuencia para el juego** (va en `calibracion-recetas.md`): con las perillas nuevas, la base del server rinde como hoy recién después de unos 1000 planes de entrenamiento.
