# WP-33H — Incidentes con rasgos

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33G (mergeado: `StoredTraits`, `StoredGroup.recipeModels`, `CoreServices.traitLedger`) |
| Modelo | Sonnet |
| Rama | `wp-33h-incidentes-con-rasgos` |

## Objetivo

CT-30, octava pieza: que **un incidente de una decisión con recetas se reproduzca igual** en una prueba JUnit (paso 3 de `docs/resolucion-de-bugs.md`). Hoy le faltan dos cosas:

1. **Los rasgos del jugador.** El cerebro los lee y los actualiza en cada decisión (`TraitLedger`), pero el incidente no los copia y la reproducción arranca con el registro vacío.
2. **La igualdad del modelo de recetas.** Desde el WP-33G, la copia del grupo (`GroupCapture`) lleva los modelos de recetas, pero `LinearPosterior` no define `equals`: dos modelos iguales se comparan por identidad, y la reproducción de cualquier grupo con un modelo aprendido falla en «group after» aunque todo haya salido igual.

## Decisiones tomadas en este WP

1. **Solo los rasgos de los jugadores del snapshot.** La decisión lee y escribe solo esos (`observe` recorre `snapshot.players()`, y el objetivo sale de ahí). Copiar el registro entero haría crecer cada incidente con jugadores que no participan.
2. **Antes y después.** El incidente guarda los rasgos de antes de la decisión (para restaurarlos) y los de después (para comprobar que la reproducción llegó al mismo lugar), igual que el grupo.
3. **Como lista ordenada de `StoredTraits`,** el mismo formato que `state.json`. Así el JSON es legible y la comparación no depende del orden de un mapa.
4. **Un solo traductor:** `TraitCaptureMapper` (aplicación) convierte entre el registro y la lista. `CoreServices` lo usa también y pierde sus dos funciones privadas del WP-33G, que hacían lo mismo.
5. **`LinearPosterior` pasa a ser un valor:** `equals` y `hashCode` por contenido (`Arrays.deepEquals` de la precisión, `Arrays.equals` de la información, `Double.compare` de las observaciones). Es inmutable, así que la igualdad por valor es la correcta.
6. **Versión 3 del JSON de incidentes.** La versión 2 deja de leerse, como ya pasa con cualquier versión distinta. Los incidentes versión 2 escritos después del WP-33G tampoco se podían leer (no tienen `recipeModels`), y los de antes reproducen código viejo.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` y su prueba `LinearPosteriorTest`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/TraitLedger.java`, `TraitSums.java`
- `src/main/java/io/github/nicodoou/mobai/domain/port/StoredTraits.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` (`decide`) y `BrainParts.java` (`standard`, `traitLedger`, `recipePlanner`)
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java` (`prior`)
- `src/main/java/io/github/nicodoou/mobai/application/`: `IncidentReport`, `GroupCaptureMapper`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/`: `DecisionWitness`, `WitnessParts`, `Observation`, `IncidentJson`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`storedState`, `restore`) y `AdapterServices.java` (`debugParts`)
- Pruebas: `testsupport/TraceReplay`, `testsupport/IncidentFixture`, `testsupport/TestSettings` (`withRecipes`), `replay/IncidentReproductionTest`, `adapter/debug/DecisionWitnessTest`, `adapter/debug/IncidentJsonTest`, `application/IncidentReportTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/TraitCaptureMapper.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/IncidentReport.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/WitnessParts.java`, `Observation.java`, `DecisionWitness.java`, `IncidentJson.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java`, `AdapterServices.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TraceReplay.java`, `IncidentFixture.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/TraitCaptureMapperTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/learning/LinearPosteriorTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/replay/IncidentReproductionTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/debug/DecisionWitnessTest.java`, `IncidentJsonTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/IncidentReportTest.java` (cambio mecánico) |
| Modificar | `docs/arquitectura.md` |

## Especificación

### `LinearPosterior.java`

- `equals(Object)`: otro `LinearPosterior` con `Arrays.deepEquals(precision, …)`, `Arrays.equals(information, …)` y `Double.compare(observations, …) == 0`.
- `hashCode()`: combinación de `Arrays.deepHashCode(precision)`, `Arrays.hashCode(information)` y `Double.hashCode(observations)`.
- Nada más cambia.

### `TraitCaptureMapper.java` (`application`, nuevo)

`public final class`, sin estado, como `GroupCaptureMapper`.

| Método | Qué hace |
| --- | --- |
| `List<StoredTraits> toStored(Map<PlayerId, TraitSums> sums)` | una entrada por jugador, ordenada por `PlayerId::value` |
| `Map<PlayerId, TraitSums> toSums(List<StoredTraits> stored)` | el mapa inverso |
| `List<StoredTraits> forSnapshot(TraitLedger ledger, GroupSnapshot snapshot)` | `toStored` de `ledger.capture()` quedándose solo con los jugadores de `snapshot.players()` |

### `IncidentReport.java`

Dos componentes nuevos **al final**: `List<StoredTraits> traitsBefore`, `List<StoredTraits> traitsAfter`, con `List.copyOf` en el constructor compacto.

### `WitnessParts.java`, `Observation.java`, `DecisionWitness.java`

- `WitnessParts`: componente nuevo al final, `TraitLedger traitLedger`, con su `requireNonNull`.
- `Observation`: componente nuevo al final, `List<StoredTraits> traitsBefore`, con `List.copyOf`.
- `DecisionWitness`:
  - un campo `TraitCaptureMapper traits = new TraitCaptureMapper()`, como el `mapper` que ya tiene;
  - `before(...)` copia los rasgos con `forSnapshot(parts.traitLedger(), snapshot)` y los pone en la `Observation`;
  - `incident(...)` pasa `observation.traitsBefore()` y, como `traitsAfter`, `forSnapshot(parts.traitLedger(), snapshot)` en el momento del fallo.

### `IncidentJson.java`

`CURRENT_VERSION = 3`.

### `CoreServices.java` y `AdapterServices.java`

- `CoreServices`: `storedState()` usa `new TraitCaptureMapper().toStored(traitLedger.capture())` y `restore(...)` usa `toSums(state.traits())`. Se borran `storedTraits()` y `capturedTraits(...)`. Si preferís un campo o una constante para el mapper, lo que menos cambie, y avisalo.
- `AdapterServices.debugParts`: pasa `core.traitLedger()` como último argumento de `WitnessParts`.

### `TraceReplay.java`

- Guardar las partes del cerebro en una variable: `BrainParts parts = BrainParts.standard(holder::current, random, window)`.
- Antes de decidir: `parts.traitLedger().restore(traits.toSums(report.traitsBefore()))`.
- `Outcome` suma, al final, `List<StoredTraits> traitsAfter`: `traits.forSnapshot(parts.traitLedger(), report.snapshot())` después de decidir (también si falló).
- `assertReproduces` suma `assertThat(outcome.traitsAfter()).as("traits after").isEqualTo(report.traitsAfter())`.
- Si `replay` pasa las 20 líneas, separala y avisalo.

### `IncidentFixture.java`

- El constructor privado recibe `MobAiSettings`; las fábricas que ya existen pasan `TestSettings.defaults()`.
- Guardá las `BrainParts` en un campo para usar su `traitLedger()`.
- `record(...)` copia `forSnapshot` antes de decidir (en `Start`) y después, y los pasa al `IncidentReport`.
- Fábrica nueva: `public static IncidentReport recordedRecipeDecision(long tick)`.
  1. Fixture con `TestSettings.withRecipes()`.
  2. Antes de las decisiones previas, guarda un modelo aprendido para Alice: `group.memory().storeRecipeModel(ALICE, new RecipeModelRecord(learnedModel(), START_TICK))`.
  3. Después sigue como `recordedDecision`: decisiones previas, daño y la decisión en `tick`.
- `learnedModel()`: `parts.recipePlanner().prior()` con una observación: rasgos de `model.dimension()` valores en 0,5, recompensa 1,0 y ruido `settings.learning().modelNoiseVariance()`. Constantes con nombre.

### `docs/arquitectura.md`

Fila nueva al final de la tabla «Nombres en el código»:

| Rasgos del jugador en los incidentes, antes y después de la decisión (CT-30) | `TraitCaptureMapper`, `IncidentReport.traitsBefore`, `IncidentReport.traitsAfter` | Aplicación |

## Pruebas obligatorias

### `LinearPosteriorTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `modelsWithTheSameContentAreEqual` | dos modelos armados igual (`prior` + la misma observación) son `equals` y tienen el mismo `hashCode` |
| `aDifferentObservationMakesADifferentModel` | la misma `prior` con recompensas distintas (0,7 y 0,2) no son `equals` |

### `TraitCaptureMapperTest` (+3, nuevo)

Registro con rasgos de Alice y de Bob (observados con un snapshot de los dos); snapshot de prueba solo con Alice.

| Prueba | Verifica |
| --- | --- |
| `forSnapshotKeepsOnlyItsPlayers` | `forSnapshot(registro, snapshotDeAlice)` trae solo a Alice |
| `storedTraitsAreSortedByPlayer` | `toStored` de un mapa con los dos devuelve primero el de `PlayerId` menor |
| `sumsSurviveTheRoundTrip` | `toSums(toStored(mapa))` es igual al mapa |

### `DecisionWitnessTest` (+1)

El `@BeforeEach` guarda las `BrainParts` en un campo y le pasa su `traitLedger()` a `WitnessParts` (cambio mecánico).

| Prueba | Verifica |
| --- | --- |
| `incidentKeepsTheTraitsFromBeforeTheDecision` | `observation = witness.before(grupo, snapshotConExtraño)`; después, el registro observa `BrainFixture.snapshot(MID_PLAN_TICK + 1, mobs, aliceBloqueando)`, con `aliceBloqueando = new PlayerSnapshotBuilder().withId(ALICE).withBlocking(true).build()`; `failed(...)`: `traitsBefore` es igual a `observation.traitsBefore()` y `traitsAfter` es distinto |

### `IncidentJsonTest` (+1)

Las dos pruebas que usan el número de versión pasan de 2 a 3 (`jsonCarriesTheSchemaVersion` y el reemplazo de texto en `unknownSchemaVersionIsRejected`).

| Prueba | Verifica |
| --- | --- |
| `recipeModelsAndTraitsSurviveTheJson` | `IncidentFixture.recordedRecipeDecision(START_TICK + 20)` escrito y leído: el reporte leído es igual al original (`isEqualTo`) |

### `IncidentReproductionTest` (+3)

`withDraws` y `withBefore` copian los dos campos nuevos (cambio mecánico).

| Prueba | Verifica |
| --- | --- |
| `recipeDecisionReproducesFromItsJson` | `recordedRecipeDecision(START_TICK + 20)` pasado por el JSON: `before().stored().recipeModels()` no está vacío, `traitsBefore()` trae a Alice y `assertReproduces` no lanza |
| `recipePlanClosingReproducesFromItsJson` | `recordedRecipeDecision(START_TICK + 700)`: el resultado trae `closedPlan` con la receta presente, y `assertReproduces` no lanza |
| `tamperedTraitsAreDetected` | el mismo incidente de `START_TICK + 20` con el escudo de Alice en `traitsBefore` cambiado por su peso: `assertReproduces` lanza `AssertionError` con «traits after» en el mensaje |

Total: **10 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `TraceReplay` sin restaurar los rasgos | los rasgos de después son solo la última observación | `recipeDecisionReproducesFromItsJson` |
| 2 | `LinearPosterior` sin `equals` ni `hashCode` | «group after» distinto por identidad | `recipeDecisionReproducesFromItsJson` y `modelsWithTheSameContentAreEqual` |
| 3 | `forSnapshot` sin filtrar | trae también a Bob | `forSnapshotKeepsOnlyItsPlayers` |
| 4 | `DecisionWitness` copia los rasgos de antes en `failed` y no en `before` | `traitsBefore` igual a `traitsAfter` | `incidentKeepsTheTraitsFromBeforeTheDecision` |

## Verificación en el juego (Nico, después del merge)

Nada visible en el juego. Si aparece un incidente en `plugins/MobAI/debug/`, tiene que traer `"schemaVersion": 3` y las listas `traitsBefore` y `traitsAfter`.

## Procedimiento

1. Rama `wp-33h-incidentes-con-rasgos` desde `origin/main` actualizado (con el WP-33G).
2. `LinearPosterior` y sus pruebas. Commit: `fix: recipe models compare by value`.
3. `TraitCaptureMapper`, su prueba y `CoreServices`. Commit: `feat: one mapper for stored traits`.
4. `IncidentReport`, `WitnessParts`, `Observation`, `DecisionWitness`, `IncidentJson`, `AdapterServices` y los cambios mecánicos. Commit: `feat: incidents carry the player traits (CT-30)`.
5. `TraceReplay`, `IncidentFixture` y las pruebas de reproducción y de JSON. Commit: `test: recipe decisions reproduce from their incident`.
6. `docs/arquitectura.md`. Commit: `docs: names for traits in incidents`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33H: incidents with traits`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación, `adapter.debug`, `testsupport`, `replay` y sus pruebas compilan con el JDK 21 del contenedor, siempre que no toquen clases de Paper. Bajá de Maven Central (si da 429, `https://repo.maven.apache.org/maven2/`) `gson` 2.11.0, `slf4j-api` (para `NOPLogger`), `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10, con los jars **explícitos** en `-cp`.
- Lo que use Paper (`AdapterServices`) lo verifica el CI.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si en `START_TICK + 700` el plan de receta todavía no cerró, usá el primer tick múltiplo de 100 que lo cierre y avisalo.
4. Si `DecisionWitness` o `IncidentFixture` pasan las 20 líneas en algún método por los campos nuevos, separá como en el punto 2.

## Fuera de alcance

- La base del server y el modo entrenamiento (WP-33I).
- Leer incidentes de la versión 2.
- Reproducir lo que pasa fuera de `Brain.decide` (por ejemplo, el aprendizaje de `ClosePlan` al cerrar un plan).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 10 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
