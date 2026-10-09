# WP-33I — Base del server y entrenamiento por jugador

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33H (mergeado: `LinearPosterior.equals`, incidentes con rasgos) |
| Modelo | Sonnet |
| Rama | `wp-33i-base-y-entrenamiento` |

## Objetivo

CT-30, novena pieza: la **base del server** y el **entrenamiento por jugador**, en el dominio.

1. **Base:** un modelo de recetas para todo el server (`RecipeBase`). Aprende solo de los planes contra jugadores en entrenamiento.
2. **Punto de partida:** un jugador nuevo arranca desde la base y no desde cero. El olvido de cada jugador también lleva hacia la base.
3. **Tope de peso:** la base pesa como mucho `learning.base-weight-plans` planes (600 por defecto).
4. **Exploración:** contra un jugador en entrenamiento, el sorteo usa `training-exploration-scale`; contra los demás, `exploration-scale`.
5. **Incidentes:** copian la base y la lista de jugadores en entrenamiento, y la reproducción las restaura.

Guardar la base en disco y el comando `/mobai train` llegan en el WP-33J. El registro `training-data.jsonl` y `/mobai memory`, en el WP-33K.

## Decisiones tomadas en este WP

1. **Entrenamiento por jugador** (Nico, 9 oct): solo los planes contra jugadores marcados enseñan a la base y exploran de más. Un jugador que entra a pasear no ensucia la base ni ve mobs explorando.
2. **La base no olvida:** acumula las sesiones de entrenamiento.
3. **Tope por la misma operación del olvido:** si la base tiene más observaciones que el tope, el punto de partida es `base.shrunkToward(prior, tope / observaciones)`. Así pesa exactamente como `tope` planes. Con `base-weight-plans: 0` la base no se usa.
4. **El tope es configurable** (Nico, 9 oct): `learning.base-weight-plans`, 600 por defecto, el número de planes con el que la simulación (WP-33C) dio una base competente desde el plan 10.
5. **La base vive en el planificador:** `RecipePlanner` recibe la `RecipeBase` en lugar de `CombatGeometry`, que no tiene estado y pasa a crearse adentro. Así el constructor sigue en 3 parámetros.
6. **La base se enseña después del jugador,** en `ClosePlan`, con la misma observación (rasgos, éxito).
7. **Los incidentes copian la base solo antes de la decisión:** `Brain.decide` no la cambia (aprende `ClosePlan`, fuera de la decisión).
8. **Versión 4 del JSON de incidentes.** La 3 deja de leerse.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `RecipePlanner`, `RecipeRequest`, `RecipeOutcome`, `RecipePlay`, `TraitLedger` (como modelo de clase con estado, captura y restauración)
- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` (`prior`, `withObservation`, `shrunkToward`, `observations`, `equals`)
- `src/main/java/io/github/nicodoou/mobai/domain/settings/LearningSettings.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` (`standard`)
- `src/main/java/io/github/nicodoou/mobai/application/`: `ClosePlan`, `IncidentReport`, `TraitCaptureMapper`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/`: `DecisionWitness`, `WitnessParts`, `Observation`, `IncidentJson`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (`learning`)
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`messaging`, `Messaging`) y `AdapterServices.java` (`debugParts`)
- `src/main/resources/config.yml` y `docs/plan/config-de-prueba.yml` (sección `learning`)
- Pruebas: `domain/strategy/RecipePlannerTest`, `application/ClosePlanTest`, `application/RecordPlayerDeathTest`, `simulation/LearningSimulation`, `domain/settings/SettingsValidationTest`, `domain/strategy/TraitLedgerTest`, `adapter/config/ConfigLoaderTest`, `testsupport/TestSettings`, `testsupport/TraceReplay`, `testsupport/IncidentFixture`, `replay/IncidentReproductionTest`, `adapter/debug/DecisionWitnessTest`, `adapter/debug/IncidentJsonTest`, `application/IncidentReportTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeBase.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeBaseCapture.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/LearningSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/ClosePlan.java`, `IncidentReport.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DecisionWitness.java`, `WitnessParts.java`, `Observation.java`, `IncidentJson.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java`, `AdapterServices.java` |
| Modificar | `src/main/resources/config.yml`, `docs/plan/config-de-prueba.yml` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipeBaseTest.java` |
| Modificar | pruebas: `RecipePlannerTest`, `ClosePlanTest`, `SettingsValidationTest`, `DecisionWitnessTest`, `IncidentJsonTest`, `IncidentReproductionTest`, `TraceReplay`, `IncidentFixture` |
| Modificar (cambio mecánico) | `RecordPlayerDeathTest`, `LearningSimulation`, `TraitLedgerTest`, `ConfigLoaderTest`, `TestSettings`, `IncidentReportTest` |
| Modificar | `docs/arquitectura.md` |

## Especificación

### `RecipeBaseCapture.java` (`domain.strategy`, nuevo)

`public record RecipeBaseCapture(Optional<LinearPosterior> model, List<PlayerId> trainers)`, con `requireNonNull` de `model` y `List.copyOf(trainers)`.

### `RecipeBase.java` (`domain.strategy`, nuevo)

`public final class`, con estado, como `TraitLedger`. Empieza sin modelo y sin jugadores en entrenamiento.

| Método | Qué hace |
| --- | --- |
| `Optional<LinearPosterior> model()` | el modelo de la base, vacío si nunca aprendió |
| `void replace(LinearPosterior model)` | reemplaza el modelo (lo usan `RecipePlanner` y, en el WP-33J, la carga desde disco) |
| `boolean isTraining(PlayerId player)` | si el jugador está en entrenamiento |
| `void startTraining(PlayerId player)` | lo suma a la lista |
| `void stopTraining(PlayerId player)` | lo saca de la lista |
| `RecipeBaseCapture capture()` | el modelo y los jugadores en entrenamiento, ordenados por `PlayerId::value` |
| `void restore(RecipeBaseCapture capture)` | reemplaza el modelo y la lista por los de la copia |

`requireNonNull` en todos los argumentos.

### `LearningSettings.java`

Componente nuevo **al final**: `long baseWeightPlans`. Validación: `SettingsChecks.requireAtLeast("LearningSettings.baseWeightPlans", baseWeightPlans, 0)`.

### `RecipePlanner.java`

- **Constructor:** `RecipePlanner(Supplier<MobAiSettings> settings, RandomSource random, RecipeBase base)`. `CombatGeometry` pasa a ser un campo `new CombatGeometry()`.
- **`RecipeBase base()`:** devuelve la base.
- **`LinearPosterior anchor()`** (público), el punto de partida:
  - sin modelo en la base, o con `baseWeightPlans == 0`: `prior()`;
  - si las observaciones de la base no pasan el tope: el modelo tal cual;
  - si las pasan: `model.shrunkToward(prior(), tope / observaciones)`.
- **`current(stored, tick)`:** donde hoy usa `prior()` (sin modelo guardado y como ancla del olvido), usa `anchor()`.
- **`double explorationScaleFor(PlayerId target)`** (público): `trainingExplorationScale` si `base.isTraining(target)`, si no `explorationScale`. `foldedWeights` lo usa con `request.target()`.
- **`void teachBase(PlayerId target, RecipeOutcome outcome)`:**
  - si el jugador no está en entrenamiento, no hace nada;
  - si está: `base.replace(baseModel.withObservation(rasgos, éxito, modelNoiseVariance))`, con `baseModel = base.model().orElseGet(this::prior)` y los rasgos tomados de `outcome.play().features()`, como en `learned`.

### `BrainParts.java`

`standard(...)` arma el planificador con `new RecipePlanner(settings, random, new RecipeBase())`. La firma de `standard` no cambia.

### `ClosePlan.java`

`learnRecipe`, después de guardar el modelo del jugador, llama `recipePlanner.teachBase(plan.target(), outcome)` con el mismo `RecipeOutcome`.

### Incidentes

- `IncidentReport`: componente nuevo al final, `RecipeBaseCapture base`, con `requireNonNull`.
- `WitnessParts`: componente nuevo al final, `RecipeBase recipeBase`, con `requireNonNull`.
- `Observation`: componente nuevo al final, `RecipeBaseCapture base`, con `requireNonNull`.
- `DecisionWitness`: `before(...)` copia `parts.recipeBase().capture()` en la `Observation`; `incident(...)` pasa `observation.base()`.
- `IncidentJson`: `CURRENT_VERSION = 4`.

### `CoreServices.java` y `AdapterServices.java`

- `CoreServices`: componente nuevo al final, `RecipeBase recipeBase`, tomado de `parts.recipePlanner().base()` en `messaging` (el record privado `Messaging` lo suma, como `traitLedger`).
- `AdapterServices.debugParts`: pasa `core.recipeBase()` como último argumento de `WitnessParts`.

### Configuración

- `ConfigLoader.learning`: `wholeNumber(root, "learning.base-weight-plans")` al final.
- `config.yml` y `docs/plan/config-de-prueba.yml`, al final de `learning`:

```yaml
  # Cuánto pesa la base del server para un jugador nuevo, en planes (0: no se usa).
  base-weight-plans: 600
```

### Pruebas de soporte

- `TestSettings`: `baseWeightPlans` 600.
- `TraceReplay`:
  - antes de decidir, `parts.recipePlanner().base().restore(report.base())`;
  - `Outcome` suma, al final, `RecipeBaseCapture baseAfter`: `parts.recipePlanner().base().capture()` después de decidir;
  - `assertReproduces` suma `assertThat(outcome.baseAfter()).as("base after").isEqualTo(report.base())`. La decisión no cambia la base (aprende `ClosePlan`), así que la de después tiene que ser la del incidente.
- `IncidentFixture`:
  - `record(...)` copia `parts.recipePlanner().base().capture()` antes de decidir y la pasa al `IncidentReport`;
  - fábrica nueva `public static IncidentReport trainingPlanOpening()`, con `TestSettings.withRecipes()`:
    1. la base recibe `replace(baseModel())` y Alice entra en entrenamiento;
    2. se graba la **primera** decisión, en `START_TICK` (la que abre el plan), sin decisiones previas;
  - `baseModel()`: `learnedModel()` (la del WP-33H) con 20 observaciones más, todas con rasgos en 1,0 y recompensa 0,0. Constantes con nombre.

### `docs/arquitectura.md`

Fila nueva después de la de los rasgos en los incidentes:

| Base del server y entrenamiento por jugador (CT-30) | `RecipeBase`, `RecipeBaseCapture`, `RecipePlanner.anchor`, `RecipePlanner.teachBase`, `learning.base-weight-plans` | Dominio |

## Pruebas obligatorias

Modelos de las pruebas: `planner.prior()` con `n` observaciones `withObservation(rasgos en 0,5, 1.0, 0.01)`. Alice está en entrenamiento y Bob no.

### `RecipeBaseTest` (+3, nuevo)

| Prueba | Verifica |
| --- | --- |
| `trainingStartsAndStopsPerPlayer` | `startTraining(Alice)`: Alice sí y Bob no; `stopTraining(Alice)`: ninguno |
| `captureListsTrainersInOrder` | con Bob y Alice en entrenamiento, `capture().trainers()` empieza por el `PlayerId` menor |
| `restoreReplacesModelAndTrainers` | otra base con modelo y Bob; `restore(capturaDeLaPrimera)`: mismo modelo (`equals`) y solo los de la primera |

### `RecipePlannerTest` (+8)

| Prueba | Verifica |
| --- | --- |
| `withoutABaseTheAnchorIsThePrior` | `anchor()` igual a `prior()` |
| `aSmallBaseIsTheAnchorAsIs` | base con 3 observaciones y tope 600: `anchor()` igual al modelo de la base |
| `aLargeBaseWeighsAsTheCap` | tope 2 y base con 4 observaciones: `anchor().observations()` es 2 (`1e-9`) |
| `aZeroCapIgnoresTheBase` | tope 0 y base con 4 observaciones: `anchor()` igual a `prior()` |
| `aNewPlayerStartsFromTheBase` | base con 3 observaciones: `current(Optional.empty(), tick)` igual a `anchor()` |
| `forgettingPullsTowardTheBase` | jugador con un modelo de 5 observaciones guardado 100 vidas medias atrás: la media de `current` coincide con la de `anchor()` (`1e-6`) |
| `trainingExploresMore` | `explorationScaleFor(Alice)` es 2,0 y `explorationScaleFor(Bob)` es 1,0 |
| `onlyTrainersTeachTheBase` | `teachBase(Bob, …)`: la base sigue vacía; `teachBase(Alice, …)` dos veces: la base tiene 2 observaciones |

### `ClosePlanTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `closingATrainersRecipePlanTeachesTheBase` | plan de receta contra Alice cerrado: la base tiene 1 observación |
| `closingSomeoneElsesRecipePlanLeavesTheBase` | el mismo plan contra Bob: la base sigue vacía |

### `SettingsValidationTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `negativeBaseWeightPlansIsRejected` | `-1` → `LearningSettings.baseWeightPlans must be at least 0, got -1` |

### `DecisionWitnessTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `incidentKeepsTheBaseFromBeforeTheDecision` | Alice en entrenamiento; `before(...)`; Alice sale del entrenamiento; `failed(...)`: `report.base().trainers()` es `[ALICE]` |

### `IncidentJsonTest` (+1)

Las pruebas de la versión pasan de 3 a 4.

| Prueba | Verifica |
| --- | --- |
| `baseSurvivesTheJson` | `IncidentFixture.trainingPlanOpening()` escrito y leído: igual al original |

### `IncidentReproductionTest` (+1)

`withDraws` y `withBefore` copian el campo nuevo (cambio mecánico).

| Prueba | Verifica |
| --- | --- |
| `trainingPlanOpeningReproducesFromItsJson` | `trainingPlanOpening()` pasado por el JSON: `base().model()` presente, `base().trainers()` es `[ALICE]` y `assertReproduces` no lanza |

Total: **17 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `current` sin modelo guardado devuelve `prior()` | el jugador nuevo arranca de cero | `aNewPlayerStartsFromTheBase` |
| 2 | `anchor` sin tope | 4 observaciones | `aLargeBaseWeighsAsTheCap` |
| 3 | `teachBase` sin mirar el entrenamiento | la base aprende de Bob | `onlyTrainersTeachTheBase` |
| 4 | `explorationScaleFor` devuelve siempre `explorationScale` | 1,0 para Alice | `trainingExploresMore` |
| 5 | `TraceReplay` sin restaurar la base | «base after» vacía | `trainingPlanOpeningReproducesFromItsJson` |

## Verificación en el juego (Nico, después del merge)

Nada visible todavía: sin `/mobai train` (WP-33J) nadie entra en entrenamiento y la base queda vacía, así que todo sigue igual que hoy. Sumá `base-weight-plans: 600` a la sección `learning` del `config.yml` del server de prueba.

## Procedimiento

1. Rama `wp-33i-base-y-entrenamiento` desde `origin/main` actualizado (con el WP-33H).
2. `LearningSettings`, `ConfigLoader`, los dos `config.yml`, `SettingsValidationTest` y los cambios mecánicos de la configuración. Commit: `feat: base weight setting (CT-30)`.
3. `RecipeBase`, `RecipeBaseCapture` y `RecipeBaseTest`. Commit: `feat: server recipe base with per-player training`.
4. `RecipePlanner`, `BrainParts`, `ClosePlan`, sus pruebas y los cambios mecánicos de los constructores. Commit: `feat: players start from the base; trainers teach it`.
5. Incidentes: `IncidentReport`, `WitnessParts`, `Observation`, `DecisionWitness`, `IncidentJson`, `CoreServices`, `AdapterServices`, `TraceReplay`, `IncidentFixture` y sus pruebas. Commit: `feat: incidents carry the recipe base`.
6. `docs/arquitectura.md`. Commit: `docs: names for the recipe base`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33I: recipe base and per-player training`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación, `adapter.debug`, `testsupport`, `replay`, `simulation` y sus pruebas compilan con el JDK 21 del contenedor, siempre que no toquen clases de Paper. Bajá de Maven Central (si da 429, `https://repo.maven.apache.org/maven2/`) `gson` 2.11.0, `slf4j-api`, `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10, con los jars **explícitos** en `-cp`.
- Lo que use Paper (`ConfigLoader`, `ConfigLoaderTest`, `AdapterServices`) lo verifica el CI.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. (Corregido el 9 oct.) La primera versión esperaba que, sin la base, la reproducción abriera otra receta. No pasa: una base que aprende en una dirección compartida por todas las recetas no cambia su orden. Por eso `TraceReplay` compara la base de después.

## Fuera de alcance

- Guardar la base en disco y `/mobai train` (WP-33J).
- `training-data.jsonl` y `/mobai memory` (WP-33K).
- Leer incidentes de la versión 3.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 17 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
