# WP-33K — Registro de entrenamiento y recetas en `/mobai memory`

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33J (mergeado: `RecipeBase` en `CoreServices`, `/mobai train`) |
| Modelo | Sonnet |
| Rama | `wp-33k-registro-y-memoria-de-recetas` |

## Objetivo

CT-30, última pieza antes de la puerta E6: **ver lo que aprendieron** los mobs y **guardar los datos** para analizarlos fuera del juego.

1. **`training-data.jsonl`:** cada plan de receta cerrado escribe una línea con la receta, los rasgos, los rasgos del modelo, el resultado y si el jugador estaba en entrenamiento. Con cientos de peleas, ese archivo permite recalibrar el modelo, o probar otro más grande, sin volver a jugar (CT-30, diseño punto 5).
2. **`/mobai memory <jugador>`:** suma, por grupo, las **5 mejores recetas estimadas** contra ese jugador, con su éxito estimado, y los rasgos con los que se estimaron.

## Decisiones tomadas en este WP

1. **«Mejores estimadas» es con la media del modelo, sin sorteo:** es lo que el grupo cree hoy, no lo que va a jugar (eso lleva exploración).
2. **El éxito estimado de una receta** es la predicción del modelo con los rasgos del jugador y la composición actual del grupo. Se muestra en porcentaje, recortado a 0–100 (la predicción lineal puede salirse un poco).
3. **Solo para jugadores con modelo propio en el grupo:** sin modelo, el grupo todavía no aprendió nada de esa persona y la línea no aparece.
4. **Las 5 recetas comparten la demora y el umbral de retirada:** la búsqueda los afina una vez por plan (WP-33B). Lo que cambia entre ellas es el reparto y la andanada.
5. **El registro escribe solo los planes de receta, siempre** (en entrenamiento o no, con el campo `training`). Sin configuración: es un archivo que crece unos cientos de bytes por plan.
6. **Cada línea lleva `version: 1`,** para leer archivos viejos si el formato cambia.
7. **El registro vive en `adapter.debug`,** junto a los otros escritores de líneas (`LineFileWriter`, `DebugGson`), aunque no es de depuración: así reusa el escritor en otro hilo y el formato de Gson.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `RecipeSearch`, `RecipePlanner`, `RecipeFeatures`, `ContextualFeatures`, `GroupComposition`, `PlanRecipe`, `RoleSplit`, `RecipePlay`, `PlayerTraits`, `TraitLedger`, `RecipeBase`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`, `domain/event/PlanClosed.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/`: `Group`, `GroupRoster` (`members`), `Member`
- `src/main/java/io/github/nicodoou/mobai/application/`: `DescribePlayerMemory`, `PlayerMemoryView`
- `src/main/java/io/github/nicodoou/mobai/adapter/command/`: `MemoryReport`, `MemoryCommand`, `MessageLine`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/`: `LineFileWriter`, `TraceWriter` (como modelo), `DebugGson`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/`: `CoreServices` (`tools`, `Messaging`), `AdapterServices` (`debugParts`, `traceOutputs`), `PluginRuntime` (apagado)
- Pruebas: `domain/strategy/RecipeSearchTest`, `domain/strategy/RecipePlannerTest`, `application/DescribePlayerMemoryTest`, `application/ClosePlanTest` (cómo arma un `ClosedPlan` de receta), `adapter/command/MemoryReportTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeSearch.java`, `RecipePlanner.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeQuery.java`, `RecipeEstimate.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecipeAdvisor.java`, `RecipeAdvice.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/DescribePlayerMemory.java`, `PlayerMemoryView.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/command/MemoryReport.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TrainingDataLog.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java`, `AdapterServices.java`, `PluginRuntime.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecipeAdvisorTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/debug/TrainingDataLogTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipeSearchTest.java`, `RecipePlannerTest.java`, `src/test/java/io/github/nicodoou/mobai/application/DescribePlayerMemoryTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/command/MemoryReportTest.java` |
| Modificar | `docs/arquitectura.md` |

## Especificación

### Dominio (`domain.strategy`)

- **`RecipeSearch.ranked(double[] weights, GroupComposition composition, int count)`** (público): los mismos candidatos y el mismo puntaje que `best`, ordenados de mayor a menor puntaje con orden estable (a igual puntaje, el orden de los candidatos), y los primeros `count`. `count` < 1 → `IllegalArgumentException`. `best` no cambia.
- **`RecipeQuery`:** `public record RecipeQuery(LinearPosterior model, PlayerTraits traits, GroupComposition composition)`, con `requireNonNull`.
- **`RecipeEstimate`:** `public record RecipeEstimate(PlanRecipe recipe, double predictedSuccess)`, con `requireNonNull`.
- **`RecipePlanner.estimates(RecipeQuery query, int count)`** (público):
  1. `folded = ContextualFeatures.weightsFor(query.model().mean(), query.traits())`;
  2. `search.ranked(folded, query.composition(), count)`;
  3. para cada receta, `predictedSuccess` = producto de `folded` con `RecipeFeatures.of(receta, composition.skeletons(), bounds())`.

### Aplicación

- **`RecipeAdvice`:** `public record RecipeAdvice(PlayerTraits traits, double observations, List<RecipeEstimate> best)`, con `List.copyOf`.
- **`RecipeAdvisor`:** `public final class`, constructor `RecipeAdvisor(RecipePlanner planner, TraitLedger traits)`.
  - `static final int SHOWN_RECIPES = 5`.
  - **`Optional<RecipeAdvice> adviceFor(Group group, PlayerId player, long tick)`:**
    - sin `group.memory().recipeModel(player)`: vacío;
    - composición: los `group.roster().members()` contados por `MobKind` en un `GroupComposition`; sin miembros, vacío;
    - modelo: `planner.current(guardado, tick)`; rasgos: `traits.traitsOf(player)`;
    - `RecipeAdvice(rasgos, modelo.observations(), planner.estimates(new RecipeQuery(modelo, rasgos, composición), SHOWN_RECIPES))`.
- **`PlayerMemoryView`:** componente nuevo al final, `Optional<RecipeAdvice> recipes`, con `requireNonNull`.
- **`DescribePlayerMemory`:**
  - constructor `DescribePlayerMemory(ActiveGroups activeGroups, Supplier<SuccessSettings> success, RecipeAdvisor advisor)`;
  - `remembers` suma `group.memory().recipeModels().containsKey(player)`;
  - `view` suma `advisor.adviceFor(group, player, tick)`.

### Comando (`MemoryReport`, `messages.yml`)

Después de la línea del grupo y antes de las estrategias, si `recipes` está presente:

1. `MEMORY_RECIPES` con `shield`, `ranged`, `armor` (`%.2f`) y `plans` (`%.0f` de `observations`).
2. Una línea por receta, en orden, con `MEMORY_RECIPE` o, si la receta usa andanada, `MEMORY_RECIPE_VOLLEY`. Valores:
   - `rank` (1 a 5);
   - `zombies`: `press/flank/reserve`;
   - `spiders`: `press/flank`;
   - `delay`: `reserveDelayTicks`;
   - `retreat`: el umbral en porcentaje entero;
   - `rate`: `predictedSuccess` en porcentaje entero, recortado a 0–100.

Claves nuevas en `MessageKey` y al final de `messages.yml`:

```yaml
memory-recipes: "<gray>  recetas (rasgos escudo <white><shield></white>, distancia <white><ranged></white>, armadura <white><armor></white> · <plans> planes):"
memory-recipe: "<gray>    <rank>. zombies <white><zombies></white> · arañas <white><spiders></white> · reserva <delay> ticks · retirada <retreat>%: <white><rate>%</white>"
memory-recipe-volley: "<gray>    <rank>. zombies <white><zombies></white> · arañas <white><spiders></white> · andanada · reserva <delay> ticks · retirada <retreat>%: <white><rate>%</white>"
```

(Las cantidades de zombies son atacan/flanquean/reserva; las de arañas, atacan/flanquean.)

### `TrainingDataLog.java` (`adapter.debug`, nuevo)

`public final class TrainingDataLog`, constructor `TrainingDataLog(LineFileWriter lines, RecipeBase base)`, con `DebugGson.compact()`.

- **`void planClosed(PlanClosed event)`:** si `event.plan().recipe()` está vacío, no hace nada. Si no, escribe una línea JSON con un record privado `TrainingLine` con estos campos:

| Campo | Valor |
| --- | --- |
| `version` | `1` |
| `tick` | `endTick` |
| `durationTicks` | `endTick - startTick` |
| `group` | `id().group().shortId()` |
| `player` | el UUID del objetivo, como texto |
| `training` | `base.isTraining(objetivo)` |
| `reason` | `reason().name()` |
| `success`, `danger`, `groupHealthLost`, `damageDealt` | los del plan |
| `recipe` | el `PlanRecipe` |
| `traits` | el `PlayerTraits` de la receta |
| `features` | los rasgos del modelo (`RecipePlay.features`) |

- **`void shutdown()`:** `lines.shutdown()`.

### Arranque

- **`CoreServices`:**
  - `DescribePlayerMemory` recibe `new RecipeAdvisor(parts.recipePlanner(), parts.traitLedger())`;
  - como `tools(...)` hoy no ve las partes del cerebro, sumá el planificador al record privado `Messaging` y pasáselo a `tools`, o lo que menos cambie, y avisalo.
- **`AdapterServices`:**
  - un `TrainingDataLog` con `new LineFileWriter(dataFolder.resolve("training-data.jsonl"), logger)` (en la carpeta del plugin, no en `debug/`; el nombre en una constante) y `core.recipeBase()`;
  - `core.events().subscribe(PlanClosed.class, log::planClosed)`;
  - el record `AdapterServices` suma el componente `TrainingDataLog trainingDataLog`.
- **`PluginRuntime`:** al apagar, `adapters.trainingDataLog().shutdown()`, junto a los otros escritores.

### `docs/arquitectura.md`

Fila nueva después de la de la base en disco:

| Recetas estimadas en la memoria y registro de entrenamiento (CT-30) | `RecipeQuery`, `RecipeEstimate`, `RecipeSearch.ranked`, `RecipeAdvisor`, `RecipeAdvice`, `TrainingDataLog` (`training-data.jsonl`) | Dominio, aplicación y adaptadores |

## Pruebas obligatorias

Modelo de las pruebas: `planner.prior()` con 3 observaciones con los rasgos de la receta `z 2/1/1 · s 0/2` y recompensa 1,0, más 3 con los de `z 4/0/0 · s 2/0` y recompensa 0,0. Las dos recetas con demora 20, retirada 0 y sin andanada; sus rasgos del modelo, `ContextualFeatures.of(RecipeFeatures.of(receta, 0, bounds), rasgos)` con los rasgos de la prueba. Grupo de 4 zombies y 2 arañas.

### `RecipeSearchTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `rankedStartsWithTheBest` | `ranked(pesos, composición, 5).getFirst()` es igual a `best(pesos, composición)` |
| `rankedIsOrderedByScore` | 5 recetas distintas, con puntaje no creciente |

### `RecipePlannerTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `estimatesPredictWithTheMean` | cada `predictedSuccess` coincide con `model.predict(ContextualFeatures.of(RecipeFeatures.of(...), rasgos))` (`1e-9`) |
| `estimatesFavorWhatWorked` | con el modelo de las pruebas, la primera estimada tiene más éxito que `z 4/0/0 · s 2/0` |

### `RecipeAdvisorTest` (+3, nuevo)

| Prueba | Verifica |
| --- | --- |
| `noRecipeModelGivesNoAdvice` | jugador sin modelo en el grupo: vacío |
| `adviceUsesTheGroupComposition` | grupo de 2 zombies y 1 araña: cada receta reparte 2 zombies y 1 araña |
| `adviceCarriesTraitsAndPlans` | rasgos de Alice observados (bloqueando) y modelo con 6 observaciones: `traits` son los del registro, `observations` es 6 y `best` es igual a `planner.estimates(...)` con esos rasgos |

### `DescribePlayerMemoryTest` (+1)

El constructor suma el `RecipeAdvisor` (cambio mecánico).

| Prueba | Verifica |
| --- | --- |
| `aPlayerWithOnlyARecipeModelIsRemembered` | grupo con solo un modelo de recetas para Alice: la vista aparece y trae `recipes` |

### `MemoryReportTest` (+2)

`PlayerMemoryView` suma `Optional.empty()` en las vistas que ya existen (cambio mecánico).

| Prueba | Verifica |
| --- | --- |
| `recipesFollowTheGroupHeader` | con 2 recetas: la línea 2 es `MEMORY_RECIPES` y la 3 y la 4 son las recetas con `rank` 1 y 2, `zombies` `2/1/1` y `rate` recortado (una receta con 1,2 da `100`) |
| `volleyRecipesUseTheirOwnLine` | una receta con andanada usa `MEMORY_RECIPE_VOLLEY` |

### `TrainingDataLogTest` (+3, nuevo)

`LineFileWriter` sobre un archivo de `@TempDir` con `NOPLogger`; leer el archivo después de `shutdown()`.

| Prueba | Verifica |
| --- | --- |
| `recipePlanWritesOneLine` | un plan de receta contra Alice en entrenamiento: una línea con `version` 1, el UUID de Alice, `training` `true`, el `success` del plan y 15·4 = 60 `features` |
| `strategyPlanWritesNothing` | un plan sin receta: el archivo está vacío o no existe |
| `eachPlanAddsALine` | dos planes de receta: dos líneas, en orden |

Total: **13 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `ranked` ordena de menor a mayor | la primera es la peor | `rankedStartsWithTheBest` |
| 2 | `RecipeAdvisor` pasa rasgos `(0, 0, 0)` | rasgos y estimaciones distintos | `adviceCarriesTraitsAndPlans` |
| 3 | `remembers` sin los modelos de recetas | el jugador no aparece | `aPlayerWithOnlyARecipeModelIsRemembered` |
| 4 | `TrainingDataLog` escribe también los planes sin receta | una línea de más | `strategyPlanWritesNothing` |

## Verificación en el juego (Nico, después del merge)

1. Antes de levantar el server, borrá `run/plugins/MobAI/messages.yml` para que se regenere (claves `memory-recipe*`).
2. Con `planner: "RECIPES"`, peleá unos planes y corré `/mobai memory <tu nombre>`: cada grupo que te recuerda muestra sus 5 recetas con su porcentaje.
3. Existe `plugins/MobAI/training-data.jsonl`, con una línea por plan cerrado.

## Procedimiento

1. Rama `wp-33k-registro-y-memoria-de-recetas` desde `origin/main` actualizado (con el WP-33J).
2. `RecipeSearch.ranked`, `RecipeQuery`, `RecipeEstimate`, `RecipePlanner.estimates` y sus pruebas. Commit: `feat: ranked recipe estimates (CT-30)`.
3. `RecipeAdvice`, `RecipeAdvisor`, `PlayerMemoryView`, `DescribePlayerMemory`, `CoreServices` y sus pruebas. Commit: `feat: the memory describes the best recipes per player`.
4. `MemoryReport`, `MessageKey`, `messages.yml` y sus pruebas. Commit: `feat: /mobai memory lists the best recipes`.
5. `TrainingDataLog`, su prueba, `AdapterServices` y `PluginRuntime`. Commit: `feat: training-data.jsonl, one line per recipe plan`.
6. `docs/arquitectura.md`. Commit: `docs: names for recipe estimates and the training log`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33K: training log and best recipes in /mobai memory`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación, `adapter.debug`, `adapter.command.MemoryReport` (si no importa Paper) y sus pruebas compilan con el JDK 21 del contenedor. Bajá de Maven Central (si da 429, `https://repo.maven.apache.org/maven2/`) `gson` 2.11.0, `slf4j-api`, `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10, con los jars **explícitos** en `-cp`.
- Lo que use Paper (`MessageKey`/`MessagesTest`, `CoreServices`, `AdapterServices`, `PluginRuntime`) lo verifica el CI. Revisá ese código con cuidado antes del push.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si la composición de las pruebas no deja 5 recetas distintas, usá la cantidad que haya y avisalo.
4. Si `MemoryReport` importa Paper y no compila con JDK 21, dejá sus pruebas para el CI y avisalo.

## Fuera de alcance

- Analizar `training-data.jsonl` o reentrenar fuera del juego (otra fase).
- Mostrar la base del server en `/mobai memory` (lo muestra `/mobai train status`).
- Rotar o limitar el tamaño de `training-data.jsonl`.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 13 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
