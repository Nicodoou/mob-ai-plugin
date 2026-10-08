# WP-33F — Las recetas en el cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33E (mergeado: `RecipePlanner`) |
| Modelo | Opus |
| Rama | `wp-33f-recetas-en-el-cerebro` |

## Objetivo

CT-30, sexta pieza y la primera que **cambia lo que se ve en el juego**, solo con `learning.planner: "RECIPES"`. Con `"STRATEGIES"` (el valor por defecto) todo sigue como hoy.

Con recetas, el cerebro:
1. **observa los rasgos** de los jugadores de la foto en cada decisión (`TraitLedger`);
2. **planifica con `RecipePlanner`:** modelo del objetivo, rasgos, receta y roles;
3. **maneja la reserva por fases:** los zombies de reserva van con `FALL_BACK` mientras el plan es más joven que la demora, y después con `PRESS`;
4. **usa la andanada de la receta** con las fases de siempre (CT-23);
5. **retira con el umbral de la receta** (la retirada de grupo, CT-13, sigue fija);
6. **aprende al cerrar:** `ClosePlan` suma el éxito al modelo del jugador, en vez de al registro por estrategia;
7. **lo muestra en el log de debug:** la línea `PLAN` suma la receta y los rasgos.

## Decisiones tomadas en este WP

1. **El plan lleva su receta:** `PlanStart`, `Plan` y `ClosedPlan` suman `Optional<RecipePlay> recipe` (vacío en los planes de estrategia). El cambio en las pruebas existentes es mecánico.
2. **Identidad del plan de recetas:** `StrategyId` `"RECIPE"` (constante `RecipePlanner.STRATEGY_ID`). `/mobai status` lo muestra así.
3. **Los planes de recetas no tocan la memoria por estrategia,** y los de estrategia no tocan los modelos de recetas: cada sistema aprende lo suyo y se pueden comparar alternando el conmutador.
4. **Rasgos en cada decisión, en cualquier estado:** cuantas más observaciones, mejor promedio. `TraitLedger` ya ignora el mismo tick repetido.
5. **`RecipePlanner` y `TraitLedger` van en `BrainParts`,** armados en `standard`, como `RegroupWindow`. `ClosePlan` recibe el mismo `RecipePlanner`: `CoreServices` arma las partes antes que `ClosePlan`.
6. **Ciclo de paquetes nuevo:** `domain.group` (`Plan`) pasa a usar `domain.strategy` (`RecipePlay`), que ya usa `domain.group` (`Role`). Se suma a los ciclos ya anotados en «Decisiones abiertas» y se resuelve en el WP de limpieza.
7. **Hueco conocido, hasta el WP-33G:** reproducir un incidente de una decisión con recetas puede no dar igual, porque la copia del grupo todavía no guarda los modelos de recetas ni los rasgos. Los incidentes de planes de estrategia no cambian. El WP-33G los suma a lo que se guarda.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `BrainParts.java`, `RetreatRule.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java`, `PlanStart.java`, `PlanLifecycle.java` (`closedPlan`)
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java`, `RecipePlay.java`, `RecipeRequest.java`, `RecipeOutcome.java`, `TraitLedger.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (`recipeModel`, `storeRecipeModel`)
- `src/main/java/io/github/nicodoou/mobai/application/ClosePlan.java`, `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`messaging`)
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java` (`planLine`)
- Pruebas: `testsupport/BrainFixture`, `testsupport/TestSettings`, `domain/brain/BrainVolleyTest`, `domain/brain/BrainObservingTest`, `application/ClosePlanTest`, `adapter/debug/DebugLogTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanStart.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` (solo `closedPlan`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java` (solo `STRATEGY_ID`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/ClosePlan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (solo `messaging`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java` (solo `planLine`) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` (`withRecipes()`) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java` (`seededWithRecipes`, `parts()`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainRecipesTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/ClosePlanTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/debug/DebugLogTest.java` |
| Modificar | las pruebas con `new PlanStart(`, `new ClosedPlan(`, `new ClosePlan(` o `new BrainParts(` (cambio mecánico, lista abajo) |
| Modificar | `docs/arquitectura.md` y `docs/plan/estado.md` (sección «Decisiones abiertas»: el ciclo nuevo) |

**Cambio mecánico.** Antes de empezar corré `grep -rln "new PlanStart(\|new ClosedPlan(\|new ClosePlan(\|new BrainParts(" src/`. Hoy, además de los de producción de la tabla:
- **`PlanStart`:** `AttackTrackerTest`, `DescribeGroupTest`, `RecordOutcomeTest`, `RecordPlayerDeathTest`, `RecruitMobTest`, `RemoveMemberTest`, `StoredMemoriesMapperTest`, `CoreServicesTest`, `PlanEndDetectorTest`, `GroupLifecycleTest`, `LifecycleCaptureTest`, `PlanScoringTest`, `PlanSequenceRestoreTest` y `PlanTest`;
- **`ClosedPlan`:** `DebugLogTest`, `FlightRecorderTest`, `TraceHubTest`, `TraceLevelsTest`, `ClosePlanTest`, `ClosedPlanTest`, `DomainEventPublisherTest` y `GroupLifecycleTest`;
- **`ClosePlan`:** `ClosePlanTest` y `RecordPlayerDeathTest`;
- **`BrainParts`:** `LearningSimulation`.

Si aparece otro archivo, frená y reportá. El cambio en esas pruebas:
- `new PlanStart(…, tick)` → `new PlanStart(…, tick, Optional.empty())`;
- `new ClosedPlan(…, endTick)` → `new ClosedPlan(…, endTick, Optional.empty())`;
- `new ClosePlan(activeGroups)` → `new ClosePlan(activeGroups, new RecipePlanner(TestSettings::defaults, new SeededRandomSource(1), new CombatGeometry()))`;
- `new BrainParts(…)` suma al final un `RecipePlanner` y un `TraitLedger` armados como en `BrainParts.standard`.

## Especificación

### `PlanStart`, `Plan`, `ClosedPlan` y `PlanLifecycle`

- Los tres records suman **al final** `Optional<RecipePlay> recipe`, con `requireNonNull(recipe, "<Record>.recipe")`.
- `Plan.start` copia `start.recipe()`, y todos los métodos de copia de `Plan` (`withDamageDealt`, `withTargetSeenAt`, `withRole`, `withoutMember`, `withHealthSeen` y el que corresponda) lo conservan.
- `PlanLifecycle.closedPlan` pasa `plan.recipe()`.
- Nada más cambia en esas clases.

### `RecipePlanner.java`

Constante pública nueva: `public static final StrategyId STRATEGY_ID = new StrategyId("RECIPE");`, con el comentario `// The id recipe plans carry where a strategy id is expected (status, debug log).`

### `BrainParts.java`

Componentes nuevos **al final**: `RecipePlanner recipePlanner` y `TraitLedger traitLedger`, con `requireNonNull`. En `standard(settings, random, regroupWindow)`, al final: `new RecipePlanner(settings, random, new CombatGeometry())` y `new TraitLedger(() -> settings.get().learning())`.

### `Brain.java`

Todo lo que sigue usa `settings.get().learning().planner()` (se lee en cada decisión, para `/mobai reload`).

1. **Rasgos:** al empezar cada decisión, antes de `step(turn)`, `parts.traitLedger().observe(snapshot)`.
2. **Planificar:** en `observe`, la línea `GroupStrategy strategy = chooseStrategy(turn, target.get()); startPlan(turn, strategy, target.get());` pasa a un método privado `openPlan(Turn turn, PlayerId target)`:
   - con `STRATEGIES`, hace lo de hoy (y `startPlan` arma el `PlanStart` con `Optional.empty()`);
   - con `RECIPES`, `RecipePlay play = planRecipe(turn, target)` y `startRecipePlan(turn, play, target)`.
3. **`planRecipe(turn, target)`:**

```java
private RecipePlay planRecipe(Turn turn, PlayerId target) {
  RecipePlanner planner = parts.recipePlanner();
  long tick = turn.snapshot().tick();
  LinearPosterior model = planner.current(turn.group().memory().recipeModel(target), tick);
  PlayerTraits traits = parts.traitLedger().traitsOf(target);
  return planner.plan(new RecipeRequest(model, traits, turn.snapshot(), target));
}
```

4. **`startRecipePlan(turn, play, target)`:** como `startPlan`, pero con `PlanStart(RecipePlanner.STRATEGY_ID, target, play.roles(), vidaMáxima, tick, Optional.of(play))`. Si `startPlan` y `startRecipePlan` repiten código, sacá lo común a un método privado que reciba el `PlanStart`.
5. **Umbral de retirada individual:** método privado `boolean shouldRetreat(Turn turn, MobSnapshot mob)`:
   - si el plan vigente tiene receta, `parts.retreatRule().shouldRetreatAt(mob, receta.retreatHealthFraction())`;
   - si no, `parts.retreatRule().shouldRetreat(mob)`.

   Lo usan `retreatLowHealth`, `updateRole` y `assignJoiningRole`, en lugar de `shouldRetreat(mob)`. `isGroupRetreated` **no cambia**.
6. **Fases:** `phasedRole(turn, planned)` pasa a `phasedRole(Turn turn, MobSnapshot mob, Role planned)`, con este orden:
   1. **reserva:** si el plan tiene receta, el mob está en `play.reserve()`, `planned == PRESS` y `plan.ageTicks(tick) < receta.reserveDelayTicks()` → `FALL_BACK`;
   2. **andanada:** si `plan.strategy().equals(VolleyStrategy.ID)` **o** la receta tiene andanada → las fases de siempre;
   3. si no, `planned`.

   El comentario del método se actualiza para nombrar las dos fases (CT-23 y CT-30).

### `ClosePlan.java`

- **Constructor:** `ClosePlan(ActiveGroups activeGroups, RecipePlanner recipePlanner)`.
- **`record(group, plan)`:**
  - si `plan.recipe()` está, llama a `learnRecipe(group, plan)` y **no** registra estrategia;
  - si no, el registro por estrategia de hoy;
  - en los dos casos, el registro de peligro.
- **Qué devuelve:** `execute` devuelve `Optional<RecordChange>`, y para un plan de recetas no hay `RecordChange`. Cambiá `record` para que devuelva `Optional<RecordChange>` (vacío con recetas) y `execute` use `flatMap`. Si algún llamador de `execute` usa el valor, frená y reportá.
- **`learnRecipe`:**

```java
private void learnRecipe(Group group, ClosedPlan plan) {
  RecipePlay play = plan.recipe().orElseThrow();
  LinearPosterior current =
      recipePlanner.current(group.memory().recipeModel(plan.target()), plan.endTick());
  group.memory().storeRecipeModel(
      plan.target(),
      recipePlanner.learned(current, new RecipeOutcome(play, plan.success(), plan.endTick())));
}
```

### `CoreServices.java` (`messaging`)

Armá `BrainParts.standard(...)` en una variable antes de `ClosePlan`, y pasá `parts.recipePlanner()` a `new ClosePlan(...)`. El `Brain` usa esas mismas partes. Nada más cambia.

### `DebugLog.java` (`planLine`)

Si `plan.recipe()` está, a la línea de hoy se le suma al final:

```
 recipe=z%d/%d/%d s%d/%d volley=%s delay=%d retreat=%.2f traits=%.2f/%.2f/%.2f
```

con zombies (presionan/flanquean/reserva), arañas (presionan/flanquean), andanada (`true`/`false`), demora en ticks, umbral, y escudo, a distancia y armadura. Con `Locale.ROOT`. Armalo en un método privado `recipePart(RecipePlay play)`.

### `TestSettings.java` y `BrainFixture.java`

- `TestSettings.withRecipes()`: los mismos valores que `defaults()`, con `learning.planner = RECIPES`.
- `BrainFixture`: el constructor recibe además el `MobAiSettings`; `seeded` y los demás usan `defaults()`. Agregá `seededWithRecipes(long seed)` (Thompson, `SeededRandomSource(seed)`, `withRecipes()`) y el accessor `parts()` (las `BrainParts` que usa el cerebro).

## Pruebas obligatorias

**Modelo «seguro»** para que la receta no dependa del azar: `LinearPosterior.of(precisión, información, 0)`, con precisión = 10⁶·I (60×60) e información = 10⁶·media. El sorteo se aparta de la media unas milésimas. Se guarda antes de decidir con `fixture.group().memory().storeRecipeModel(ALICE, new RecipeModelRecord(modelo, START_TICK))`. Media de 60 en 0 salvo lo que diga cada prueba, siempre con el índice 0 en 0,5 y, para cortar empates, **1 = −1, 3 = −1, 5 = −1, 9 = −1, 11 = −1**, salvo donde la fila los cambie.

### `BrainRecipesTest` (7)

`BrainFixture.seededWithRecipes(7)`, grupo de catálogo (4 zombies en x = 1…4, 3 esqueletos, 2 arañas; z = 5) y Alice en (0, 64, 0).

| Prueba | Verifica |
| --- | --- |
| `withRecipesThePlanIsARecipe` | sin modelo guardado: `decide(START_TICK, …)` abre un plan con estrategia `RECIPE` y receta presente; en los **roles del plan** (`plan.roleOf`), los esqueletos tienen `SHOOT` (las órdenes pueden decir `HOLD_FIRE` si la receta sorteó andanada) |
| `theReserveFallsBackUntilTheDelay` | media: 3 = 1, 4 = −1, 7 = 1, 8 = −1 (con 1 = −1). Receta: zombies `(2,0,2)`, demora 89. En `START_TICK`, los zombies 3 y 2 (los más lejos de Alice) tienen `FALL_BACK` y los zombies 0 y 1, `PRESS`; en `START_TICK + 80`, igual; en `START_TICK + 90`, los cuatro con `PRESS` |
| `theRecipeThresholdDecidesIndividualRetreats` | media: 9 = 0,6, 10 = −1 → umbral 0,15. El zombie 0 con vida 4 (0,2): su orden **no** es `RETREAT` (con el umbral fijo de 0,3 lo sería) |
| `aVolleyRecipeUsesTheVolleyPhases` | media: 11 = 1. Receta con andanada; en `START_TICK` los esqueletos tienen `HOLD_FIRE` (fase de presión) |
| `traitsAreObservedEveryDecision` | Alice bloqueando: después de una decisión, `fixture.parts().traitLedger().traitsOf(ALICE).shield()` = 1 |
| `theRecipeTargetsTheChosenPlayer` | en `withRecipesThePlanIs…`, `plan.target()` = Alice y la receta tiene 4 zombies, 2 arañas y rasgos `(0, 0, 0)` |
| `strategiesRemainTheDefault` | `BrainFixture.seeded(7)` (sin recetas): el plan abierto no tiene receta y su estrategia no es `RECIPE` |

### `ClosePlanTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `aRecipePlanTeachesTheRecipeModel` | `ClosedPlan` con receta (un `RecipePlay` armado a mano: zombies `(2,1,1)`, arañas `(1,1,0)`, sin andanada, demora 89, umbral 0,15, rasgos `(0,0,0)`, rasgos del modelo = 60 valores con el índice 0 en 1, roles y reserva vacíos), éxito 0,6 y fin en el tick 900 → `recipeModel(target)` con `lastTick` 900 y `observations()` 1; `strategyRecords()` vacío |
| `aStrategyPlanLeavesTheRecipeModelsAlone` | el caso de hoy → `recipeModels()` vacío |

### `DebugLogTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `aRecipePlanLineShowsTheRecipeAndTraits` | con el `RecipePlay` de arriba, pero con rasgos `(0.5, 0, 1)` y andanada: la línea termina en ` recipe=z2/1/1 s1/1 volley=true delay=89 retreat=0.15 traits=0.50/0.00/1.00` |

Total: **10 pruebas**. Todas las demás tienen que seguir en verde: con `STRATEGIES`, el cerebro hace lo mismo que antes.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Reserva siempre con `FALL_BACK`, sin mirar la demora | en `START_TICK + 90` siguen con `FALL_BACK` | `theReserveFallsBackUntilTheDelay` |
| 2 | Retirada con el umbral fijo aunque haya receta | el zombie 0 con `RETREAT` | `theRecipeThresholdDecidesIndividualRetreats` |
| 3 | `ClosePlan` registra también la estrategia en los planes de recetas | `strategyRecords()` con `RECIPE` | `aRecipePlanTeachesTheRecipeModel` |
| 4 | No observar los rasgos | escudo 0 | `traitsAreObservedEveryDecision` |
| 5 | Ignorar la andanada de la receta | esqueletos con `SHOOT` | `aVolleyRecipeUsesTheVolleyPhases` |

## Verificación en el juego (Nico, después del merge)

**Antes:** el `config.yml` del server de prueba tiene `max-retreat-health-fraction: 0.5` (WP-33E). Para probar las recetas, `planner: "RECIPES"` y `/mobai reload`.

1. Con `"STRATEGIES"`: todo como antes.
2. Con `"RECIPES"`, `/mobai debug all full` y un `spawngroup`:
   - cada línea `PLAN` del `mobai-debug.log` trae `recipe=… traits=…`;
   - con escudo en la mano, `traits` muestra el primer número subiendo; con un arco o una ballesta en cualquiera de las dos manos, el segundo; con armadura, el tercero.
3. **Reserva:** si una receta trae zombies de reserva (`z…/…/2`, por ejemplo), esos zombies se quedan a distancia y entran a pelear después de la demora.
4. **Retirada:** con umbral bajo (`retreat=0.05`), los mobs pelean casi hasta morir; con umbral alto (`retreat=0.45`), se retiran temprano.
5. **Variación:** las recetas cambian de un plan a otro al principio. Contra el mismo jugador, con los planes se van pareciendo (aprende). Pasá el log para leerlo.

## Procedimiento

1. Rama `wp-33f-recetas-en-el-cerebro` desde `origin/main` actualizado (con el WP-33E).
2. `PlanStart`, `Plan`, `ClosedPlan`, `PlanLifecycle` y el cambio mecánico de `PlanStart`/`ClosedPlan`. Commit: `feat: plans carry their recipe`.
3. `RecipePlanner.STRATEGY_ID`, `BrainParts`, `Brain`, `TestSettings`, `BrainFixture` y `BrainRecipesTest` (con el cambio mecánico de `BrainParts`). Commit: `feat: the brain plans with recipes when the switch says so (CT-30)`.
4. `ClosePlan`, `CoreServices` y `ClosePlanTest` (con el cambio mecánico de `ClosePlan`). Commit: `feat: recipe plans teach the recipe model`.
5. `DebugLog` y su prueba. Commit: `feat: the debug log shows recipes and traits`.
6. Documentos (`arquitectura.md`: fila nueva con `RecipePlanner.STRATEGY_ID` y `BrainParts.recipePlanner`/`traitLedger`; `estado.md`, «Decisiones abiertas»: el ciclo `group` ↔ `strategy`). Commit: `docs: recipes in the brain`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33F: recipes in the brain`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación y sus pruebas compilan con el JDK 21 del contenedor (`javac --release 21`, sumando `src/main/java/io/github/nicodoou/mobai/application` y lo de `testsupport` que no use Paper). Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 (Maven Central; si da 429, `https://repo.maven.apache.org/maven2/`) y corré con los jars **explícitos** en `-cp`. Corré **todas** las pruebas de `domain` y `application`: con `STRATEGIES` no puede cambiar ningún resultado.
- `DebugLog`, `CoreServices` y lo que use Paper o Gson lo verifica el CI del PR. Si fallan las pruebas de incidentes (JSON de un `Plan` con receta), frená y reportá con el error.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si `Brain` pasa los 20 métodos públicos, frená y reportá (los métodos nuevos son privados).

## Fuera de alcance

- Guardar los modelos de recetas y los rasgos en disco y en los incidentes, la migración y la base (WP-33G).
- El modo entrenamiento y `/mobai memory` con recetas (WP-33H).
- Quitar las estrategias viejas (cuando Nico lo decida, después de comparar).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 10 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Con `STRATEGIES`, ninguna prueba existente cambió su resultado.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
