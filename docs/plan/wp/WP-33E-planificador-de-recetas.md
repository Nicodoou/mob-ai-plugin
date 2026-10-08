# WP-33E — Planificador de recetas (dominio)

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33D (mergeado: `LearningSettings`, `TraitLedger`) |
| Modelo | Opus |
| Rama | `wp-33e-planificador-de-recetas` |

## Objetivo

CT-30, quinta pieza: **`RecipePlanner`**, el que decide un plan con recetas, puro y probado. El cerebro lo usa en el WP-33F. El planificador:

1. **Da el modelo vigente de un jugador:** el punto de partida, o el guardado con olvido hacia el punto de partida.
2. **Planifica:** sortea pesos, los reduce con los rasgos del jugador, busca la mejor receta y reparte los roles entre los mobs concretos (quién flanquea, quién queda de reserva).
3. **Aprende:** suma al modelo el éxito del plan cerrado.

Además:
- `GroupMemory` guarda el modelo de recetas de cada jugador (solo en memoria; el disco es el WP-33G);
- `RetreatRule` sabe retirar con un umbral dado;
- se corrige un problema de la configuración del WP-33D.

## Decisiones tomadas en este WP

1. **Corrección del umbral máximo:** el WP-33D dejó `max-retreat-health-fraction: 0.6`, igual que la vuelta a pelear (`recovery-health-fraction: 0.6`). Con una receta de umbral 0,6, un mob se retiraría y volvería en la misma decisión. El máximo baja a **0,5**, y `MobAiSettings` valida que `learning.maxRetreatHealthFraction < retreat.recoveryHealthFraction`, igual que hoy valida el umbral fijo del plan.
2. **El modelo guardado no sabe olvidar solo:** `GroupMemory` guarda `(modelo, último tick)` (`RecipeModelRecord`). El planificador lo acerca al punto de partida con `keep = 0,5^(Δticks / memory.half-life-ticks)`, la misma vida media que el resto de la memoria. Así `GroupMemory` no cambia su constructor (lo construyen 30 pruebas).
3. **Punto de partida:** `LinearPosterior.prior(media, priorVariance)` de 60, con `priorSuccess` en el índice 0. La base (WP-33G) lo reemplazará.
4. **Quién flanquea:** los más de costado respecto de la mirada del objetivo, con el mismo orden que `FlankStrategy`. Ese orden se saca a una clase compartida, `SidewaysOrder`, y `FlankStrategy` la usa sin cambiar su comportamiento.
5. **Quién queda de reserva:** entre los zombies que no flanquean, los más lejos del objetivo (distancia horizontal), porque son los que menos tardan en quedar fuera de su alcance. Sin el objetivo en la foto, el orden de la foto.
6. **Los de reserva van con `PRESS` en el mapa de roles** y su lista aparte (`reserve`). El cerebro (WP-33F) les da `FALL_BACK` hasta la demora, por fases como la andanada; así un mob que se retira y vuelve recupera `PRESS` sin un rol nuevo.
7. **Lo que se aprende se guarda al planificar:** `RecipePlay` lleva los 60 rasgos ya calculados. Si un `/mobai reload` cambia los límites a mitad de plan, el aprendizaje igual usa lo que se jugó.
8. **Exploración:** `learning.exploration-scale`. El modo entrenamiento (WP-33H) usará la otra escala.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `FlankStrategy`, `RecipeSearch`, `RecipeFeatures`, `RecipeBounds`, `PlanRecipe`, `RoleSplit`, `ContextualFeatures`, `PlayerTraits`, `GroupComposition`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/RetreatRule.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `LearningSettings.java`, `MemorySettings.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` (`angleFromFacingDegrees`)
- `src/main/resources/config.yml` (sección `learning`), `docs/plan/config-de-prueba.yml`
- Pruebas: `testsupport/TestSettings`, `testsupport/MobSnapshotBuilder`, `testsupport/PlayerSnapshotBuilder`, `testsupport/ScriptedRandomSource`, `domain/memory/GroupMemoryTest`, `domain/brain/RetreatRuleTest`, `domain/settings/SettingsValidationTest`, `adapter/config/ConfigLoaderTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/memory/RecipeModelRecord.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/SidewaysOrder.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/FlankStrategy.java` (solo usar `SidewaysOrder`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlay.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeRequest.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeOutcome.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/RetreatRule.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java` |
| Modificar | `src/main/resources/config.yml` y `docs/plan/config-de-prueba.yml` (`max-retreat-health-fraction: 0.5`) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` (0,6 → 0,5 en `learning`) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` (0,6 → 0,5) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipePlannerTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/memory/GroupMemoryTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/RetreatRuleTest.java` |
| Modificar | `docs/arquitectura.md` |

Antes de empezar, buscá con grep `0.6` en `TestSettings`, `ConfigLoaderTest` y `SettingsValidationTest`: cambiá a 0,5 solo el `maxRetreatHealthFraction` de `LearningSettings`, nunca el `recoveryHealthFraction` de `RetreatSettings`. Si un caso de `SettingsValidationTest` usa 0,6 como umbral máximo válido, ajustalo y avisalo.

## Especificación

### `RecipeModelRecord.java` (`domain.memory`)

```java
/** One player's recipe model as last learned, and when (CT-30). */
public record RecipeModelRecord(LinearPosterior model, long lastTick) {
  public RecipeModelRecord { … requireNonNull(model, "RecipeModelRecord.model"); lastTick >= 0, si no `IllegalArgumentException("RecipeModelRecord.lastTick must be zero or positive, got " + lastTick)` … }
}
```

### `GroupMemory.java`

- Campo nuevo `Map<PlayerId, RecipeModelRecord> recipeModels = new HashMap<>()`.
- Métodos públicos nuevos:
  - `Optional<RecipeModelRecord> recipeModel(PlayerId player)`;
  - `void storeRecipeModel(PlayerId player, RecipeModelRecord record)` (con `requireNonNull` de los dos);
  - `Map<PlayerId, RecipeModelRecord> recipeModels()`: copia inmodificable.
- `clearPlayer` y `clear` también borran los modelos de recetas.
- Nada más cambia (ni el constructor ni `restore`).

Justificación de los tres métodos públicos (regla de 20): el modelo de recetas reemplaza a los registros por estrategia de la misma memoria del grupo, y `GroupMemory` queda en 17.

### `SidewaysOrder.java` (`domain.strategy`)

```java
/** Mobs ordered from the most to the least to the side of where the target looks. */
final class SidewaysOrder {
  private SidewaysOrder() {}

  static List<MobSnapshot> of(
      CombatGeometry geometry, List<MobSnapshot> mobs, Optional<PlayerPose> pose) { … }
}
```

Es el cuerpo actual de `FlankStrategy.sideFirst`, movido sin cambios: sin pose, la lista tal cual; con pose, ordenada por `angleFromFacingDegrees` de mayor a menor. `FlankStrategy.sideFirst` se borra y `mostSideways` llama a `SidewaysOrder.of(geometry, mobs, pose)`.

### `RecipePlay.java`, `RecipeRequest.java` y `RecipeOutcome.java`

```java
/** A recipe as handed out: what was chosen, against whom, what the model will learn from, and who does what. */
public record RecipePlay(
    PlanRecipe recipe,
    PlayerTraits traits,
    List<Double> features,
    Map<MobId, Role> roles,
    Set<MobId> reserve) { … }

/** What the planner needs for one plan: the model, the target's traits, the group and the target. */
public record RecipeRequest(
    LinearPosterior model, PlayerTraits traits, GroupSnapshot snapshot, PlayerId target) { … }

/** How a recipe plan went, for the model. */
public record RecipeOutcome(RecipePlay play, double success, long tick) { … }
```

- `RecipePlay`: `requireNonNull` de todo; `features.size() != ContextualFeatures.DIMENSION` → `IllegalArgumentException("RecipePlay.features must have 60 values, got " + n)` (con la constante); copias inmodificables, y `roles` en `LinkedHashMap` para conservar el orden de la foto.
- `RecipeRequest`: `requireNonNull` de todo.
- `RecipeOutcome`: `requireNonNull(play)`; `success` en [0, 1] → si no, `"RecipeOutcome.success must be in [0, 1], got " + v`; `tick >= 0` → `"RecipeOutcome.tick must be zero or positive, got " + t`.

### `RecipePlanner.java`

```java
/** Plans with learned recipes (CT-30): the current model, the recipe and its roles, and learning. */
public final class RecipePlanner {
  private final Supplier<MobAiSettings> settings;
  private final RandomSource random;
  private final CombatGeometry geometry;
  private final RecipeSearch search;

  public RecipePlanner(Supplier<MobAiSettings> settings, RandomSource random, CombatGeometry geometry) { … }

  public LinearPosterior prior() { … }
  public LinearPosterior current(Optional<RecipeModelRecord> stored, long tick) { … }
  public RecipePlay plan(RecipeRequest request) { … }
  public RecipeModelRecord learned(LinearPosterior current, RecipeOutcome outcome) { … }
}
```

- **Constructor:** `requireNonNull` de los tres. `search = new RecipeSearch(this::bounds)`, con `bounds()` privado = `new RecipeBounds(learning.minReserveDelayTicks(), learning.maxReserveDelayTicks(), learning.maxRetreatHealthFraction())`.
- **`prior()`:** media de largo `ContextualFeatures.DIMENSION` con `learning.priorSuccess()` en el índice 0; `LinearPosterior.prior(media, learning.priorVariance())`.
- **`current(stored, tick)`:** sin guardado, `prior()`. Con guardado: `keep = Math.pow(0.5, Math.max(0, tick − lastTick) / (double) memory.halfLifeTicks())` y `stored.model().shrunkToward(prior(), keep)`.
- **`plan(request)`**, coordinando métodos privados de una tarea:
  1. `w = request.model().sample(random, learning.explorationScale())`;
  2. `pesos = ContextualFeatures.weightsFor(w, request.traits())`;
  3. `composición = GroupComposition.of(request.snapshot())`; `receta = search.best(pesos, composición)`;
  4. `rasgos = ContextualFeatures.of(RecipeFeatures.of(receta, composición.skeletons(), bounds()), request.traits())`;
  5. roles y reserva (abajo);
  6. `new RecipePlay(receta, request.traits(), rasgos como lista, roles, reserva)`.
- **Roles**, en el orden de la foto:
  - esqueletos: `SHOOT`;
  - zombies: flanqueadores = los primeros `receta.zombies().flank()` de `SidewaysOrder.of(geometry, zombies, pose)`; reserva = de los demás zombies, los `receta.zombies().reserve()` más lejos del objetivo (distancia horizontal de mayor a menor; empate o sin objetivo: orden de la foto); el resto, `PRESS`. Los de reserva van con `PRESS` en el mapa y entran en `reserve`;
  - arañas: flanqueadoras = las primeras `receta.spiders().flank()` de `SidewaysOrder.of(geometry, arañas, pose)`; el resto, `PRESS`;
  - `pose` = la del objetivo en la foto (`snapshot.player(target).map(PlayerSnapshot::pose)`).
- **`learned(current, outcome)`:** `current.withObservation(rasgos del play como arreglo, outcome.success(), learning.modelNoiseVariance())` y `new RecipeModelRecord(eso, outcome.tick())`.
- La configuración se lee en cada llamada (`settings.get()`), para `/mobai reload`.

### `RetreatRule.java`

Método público nuevo `shouldRetreatAt(MobSnapshot mob, double healthFraction)`: `mob.health() <= healthFraction × mob.maxHealth()`. `shouldRetreat(mob)` pasa a llamarlo con `plan.get().retreatHealthFraction()`. Nada más cambia: la retirada del grupo (CT-13) sigue con el umbral fijo.

### `MobAiSettings.java`

Validación nueva, en un método privado, después de la que ya existe: si `learning.maxRetreatHealthFraction() >= retreat.recoveryHealthFraction()` → `IllegalArgumentException("LearningSettings.maxRetreatHealthFraction must be below RetreatSettings.recoveryHealthFraction, got " + max + " >= " + recovery)`.

### `config.yml` y `config-de-prueba.yml`

`max-retreat-health-fraction: 0.5`, y su comentario pasa a: `# Cuándo entra la reserva de zombies (ticks) y umbral máximo de retirada que se prueba (por debajo de recovery-health-fraction).`

### `docs/arquitectura.md`

Fila nueva al final de la tabla «Nombres en el código»:

| Planificador de recetas, pedido, plan entregado, resultado, modelo guardado por jugador, orden de costado (CT-30) | `RecipePlanner`, `RecipeRequest`, `RecipePlay`, `RecipeOutcome`, `RecipeModelRecord`, `SidewaysOrder` | Dominio |

## Pruebas obligatorias

### `RecipePlannerTest` (10)

**Armado.** `RecipePlanner` con `TestSettings.defaults()` (vida media de la memoria 12.000; ruido 0,01; varianza 1; éxito 0,5; exploración 1; demoras 20 a 400; umbral máximo 0,5) y `new CombatGeometry()`.
- **Sorteo neutro:** `ScriptedRandomSource` con 60 gaussianas en 0, así el sorteo es la media.
- **Modelo de cada prueba:** `LinearPosterior.prior(media, 1.0)`, con una media de 60 valores en 0 salvo los índices que diga la fila.
- **Objetivo:** en (0, 64, 0) mirando a (0, 0, 1) (`PlayerSnapshotBuilder` por defecto).
- **Rasgos:** `(0, 0, 0)` salvo que se diga otra cosa.
- **Grupos:** zombies, arañas y esqueletos con `MobSnapshotBuilder` en las posiciones de cada fila.

| Prueba | Verifica |
| --- | --- |
| `priorHasSixtyNumbersAndThePriorSuccess` | `prior().dimension()` = 60; media en el índice 0 = 0,5 y 0 en el resto |
| `withoutAStoredModelTheCurrentIsThePrior` | `current(Optional.empty(), 5000)` tiene la media de `prior()` |
| `aStoredModelForgetsTowardThePriorWithTheMemoryHalfLife` | guardado = `prior().withObservation(f, 1.0, 0.01)` en el tick 0, con f = 60 valores en 0,1; `current(guardado, 12000)` tiene la media y las observaciones (0,5) de `guardado.shrunkToward(prior(), 0.5)` |
| `zombiesFlankTheMostSideways` | media: 1 = 2, 2 = −2, 3 = −1. Zombies en (0,64,5), (5,64,0), (0,64,−5) y (−5,64,1) → receta `(2,2,0)`; `FLANK` para los de (0,64,−5) y (5,64,0) (180° y 90°), `PRESS` los otros dos; `reserve` vacía |
| `theReserveIsTheFarthestFromTheTarget` | media: 1 = −1, 3 = 1, 4 = −1. Zombies en (0,64,3), (6,64,0), (0,64,−9) y (−4,64,1) → receta `(2,0,2)`, demora 20; `reserve` = los de (0,64,−9) y (6,64,0); los cuatro con `PRESS` en el mapa |
| `spidersFlankTheMostSideways` | media: 5 = 0,8, 6 = −1. Arañas en (5,64,0) y (0,64,5) → arañas `(1,1,0)`; `FLANK` la de (5,64,0) |
| `skeletonsShoot` | media en 0 salvo el índice 0. Dos zombies y un esqueleto → el esqueleto con `SHOOT` |
| `theTargetTraitsFoldTheWeights` | media: 16 = 2, 17 = −2, 18 = −1 (bloque del escudo), los 4 zombies de `zombiesFlank…`. Con escudo 1 → receta `(2,2,0)`; con escudo 0 → `(4,0,0)` |
| `thePlayCarriesTheFeaturesItLearnsFrom` | en `zombiesFlank…`, `play.features()` es `ContextualFeatures.of(RecipeFeatures.of(receta, 0, new RecipeBounds(20, 400, 0.5)), rasgos)` |
| `learningAddsTheOutcome` | `learned(prior(), new RecipeOutcome(play de zombiesFlank…, 0.7, 500))`: `lastTick` = 500; `model().observations()` = 1; media igual a la de `prior().withObservation(rasgos, 0.7, 0.01)` |

### `GroupMemoryTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `recipeModelsAreStoredAndCleared` | `storeRecipeModel` y `recipeModel` lo devuelven; `recipeModels()` lo trae; `clearPlayer` lo borra para ese jugador y `clear` para todos |

### `RetreatRuleTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `retreatAtAGivenFraction` | mob con 8 de 20: `shouldRetreatAt(mob, 0.4)` verdadero; `shouldRetreatAt(mob, 0.35)` falso |

### `SettingsValidationTest` (+1 caso)

`LearningSettings` con umbral máximo 0,6 y recuperación 0,6 → `"LearningSettings.maxRetreatHealthFraction must be below RetreatSettings.recoveryHealthFraction, got 0.6 >= 0.6"` (armado con `new MobAiSettings(…)`, como el caso de `requireRecoveryAboveRetreat`).

Total: **13 pruebas** y **1 caso**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `current`, usar `traitsHalfLifeTicks` (6000) en vez de la vida media de la memoria | `keep` = 0,25 | `aStoredModelForgetsTowardThePriorWithTheMemoryHalfLife` |
| 2 | Flanqueadores de menos a más de costado | flanquean (0,64,5) y (−5,64,1) | `zombiesFlankTheMostSideways` |
| 3 | Reserva de más cerca a más lejos | reserva (0,64,3) y (−4,64,1) | `theReserveIsTheFarthestFromTheTarget` |
| 4 | Usar los primeros 15 pesos sin `weightsFor` | `(4,0,0)` también con escudo | `theTargetTraitsFoldTheWeights` |
| 5 | En `learned`, ruido fijo en 1 | otra media | `learningAddsTheOutcome` |

## Procedimiento

1. Rama `wp-33e-planificador-de-recetas` desde `origin/main` actualizado (con el WP-33D).
2. Umbral máximo 0,5: `MobAiSettings`, `config.yml`, `config-de-prueba.yml`, `TestSettings`, `ConfigLoaderTest` y el caso nuevo de `SettingsValidationTest`. Commit: `fix: the learned retreat threshold stays below recovery`.
3. `RecipeModelRecord`, `GroupMemory` y su prueba; `RetreatRule.shouldRetreatAt` y su prueba. Commit: `feat: recipe models in the group memory`.
4. `SidewaysOrder` y `FlankStrategy`. Commit: `refactor: share the sideways order of flankers`.
5. `RecipePlay`, `RecipeRequest`, `RecipeOutcome`, `RecipePlanner` y su prueba. Commit: `feat: recipe planner (CT-30)`.
6. `docs/arquitectura.md`. Commit: `docs: names for the recipe planner`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33E: recipe planner`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Todo menos `ConfigLoader`/`ConfigLoaderTest` es Java puro: compila y se prueba con el JDK 21 del contenedor. Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 (Maven Central; si da 429, `https://repo.maven.apache.org/maven2/`). Compilá `src/main/java/io/github/nicodoou/mobai/domain`, lo necesario de `testsupport` y las pruebas de `domain/`, y corré con los jars **explícitos** en `-cp`. Corré también las pruebas de `FlankStrategy` (no pueden cambiar).
- Lo que usa Paper lo verifica el CI del PR. Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.

## Fuera de alcance

- Usar el planificador en el cerebro, el conmutador, la reserva por fases, el umbral por plan, aprender al cerrar, los incidentes y el log de debug (WP-33F).
- Guardar los modelos en disco y la base (WP-33G).
- El modo entrenamiento (WP-33H).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 13 pruebas y el caso de validación, con sus nombres exactos, en verde; la suite completa en verde, incluidas las de `FlankStrategy` sin cambios.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
