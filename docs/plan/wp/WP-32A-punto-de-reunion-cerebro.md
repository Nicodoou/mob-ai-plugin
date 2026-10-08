# WP-32A — Punto de reunión: el cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-25 y WP-30D (mergeados) |
| Modelo | Opus |
| Rama | `wp-32a-punto-de-reunion` |

## Objetivo

CT-29, primera mitad: al entrar en `REGROUPING`, el grupo elige un **punto de reunión** lejos del jugador, lo guarda mientras dure el reagrupamiento y lo manda en la orden de cada mob. Los goals todavía no lo usan; eso es el WP-32B.

El punto es el **centro de los mobs**, corrido en horizontal `retreat.rally-distance-blocks` (12) **lejos del jugador** del que se retira el grupo. Con 12 bloques, el punto queda a 12 o más del jugador, la distancia a la que un mob se cura.

## Decisiones tomadas en este WP

1. **De quién se aleja:** del objetivo comprometido si está en la foto y vivo; si no (por ejemplo, el grupo que no llegó a abrir un plan, CT-13), del jugador vivo más cercano al centro. Sin jugadores vivos en la foto, el punto es el centro.
2. **El centro** es el promedio de las posiciones de los mobs de la foto, también la altura. El corrimiento es solo horizontal.
3. **Cuándo se elige:** al entrar en `REGROUPING` (después de un plan cerrado con `GROUP_RETREATED`, y al quedarse sin planificar por CT-13) y al reiniciar la ventana con el grupo todavía en retirada (`keepRegrouping`). Fuera de esos momentos, **no se recalcula**: el centro se mueve con los mobs que caminan hacia el punto y lo correría sin fin.
4. **Sin mobs en la foto, no hay punto** (`Optional.empty()`): el reagrupamiento sigue sin punto, y los goals hacen la retirada de siempre.
5. **Dónde se guarda:** en `PlanLifecycle`, junto con el tick de inicio. `regroupStartTick()` se reemplaza por `regrouping()`, que devuelve el record nuevo `Regrouping(startTick, rallyPoint)`. Con `rallyAt(Vec3)`, `PlanLifecycle` queda en **20 métodos públicos**, el umbral de alerta: el próximo cambio del reagrupamiento tiene que sacar ese estado a una clase propia.
6. **En la orden:** `RoleAssignment` suma un componente `Optional<Vec3> rallyPoint`. Lo llevan las órdenes `RETREAT` del reagrupamiento; las demás (incluida la retirada individual durante un plan) van con `Optional.empty()`.
7. **Configuración:** `retreat.rally-distance-blocks: 12.0` y `retreat.rally-arrival-blocks: 3.0`. La segunda la usa el WP-32B; va acá para tocar `RetreatSettings` una sola vez.
8. **Incidentes:** `LifecycleCapture` suma `rallyPoint`. Los incidentes grabados antes de este WP no se pueden abrir.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `BrainParts.java`, `RegroupRule.java`, `RetreatRule.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java`, `LifecycleCapture.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/RetreatSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Vec3.java`
- `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `MobSnapshot.java`, `PlayerSnapshot.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (`retreat`), `src/main/resources/config.yml` (sección `retreat`)
- Pruebas: `domain/brain/BrainRegroupingTest`, `domain/brain/BrainObservingTest`, `domain/group/GroupLifecycleTest`, `domain/group/LifecycleCaptureTest`, `domain/settings/SettingsValidationTest`, `testsupport/BrainFixture`, `testsupport/GroupSnapshotBuilder`, `testsupport/PlayerSnapshotBuilder`, `testsupport/TestSettings`

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/Regrouping.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/LifecycleCapture.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/brain/RallyPointRule.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/RetreatSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `docs/plan/config-de-prueba.yml` (las dos claves nuevas, igual que en `config.yml`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/RallyPointRuleTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainRegroupingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/group/GroupLifecycleTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/group/LifecycleCaptureTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar | las pruebas que construyen `RoleAssignment`, `RetreatSettings`, `LifecycleCapture` o `BrainParts`, o que leen `regroupStartTick()` (lista abajo; solo el cambio mecánico) |
| Modificar | `docs/arquitectura.md` (sección «Retirada táctica y reagrupamiento» y tabla «Nombres en el código») |

**Cambios mecánicos en pruebas existentes.** Antes de empezar, corré:

```bash
grep -rln "new RoleAssignment(\|new RetreatSettings(\|new LifecycleCapture(\|new BrainParts(\|regroupStartTick()" src/
```

Hoy la lista de `src/test` es: `adapter/goal/RoleRegistryTest`, `adapter/scheduler/DecisionApplierTest`, `domain/decision/GroupDecisionTest`, `simulation/OutcomeModelTest`, `simulation/LearningSimulation`, `testsupport/TestSettings`, `adapter/config/ConfigLoaderTest`, `domain/settings/SettingsValidationTest`, `domain/brain/RegroupWindowTest`, `domain/group/LifecycleCaptureTest`, `domain/group/GroupLifecycleTest`, `domain/brain/BrainRegroupingTest` y `domain/brain/BrainObservingTest`. Si aparece otro archivo, frená y reportá. En esos archivos el cambio es solo:
- `new RoleAssignment(…, recovering)` → `new RoleAssignment(…, recovering, Optional.empty())`;
- `new RetreatSettings(a, b, c, d, e, f)` → `new RetreatSettings(a, b, c, d, e, f, 12.0, 3.0)`;
- `new LifecycleCapture(…, planSequence)` → `new LifecycleCapture(…, planSequence, Optional.empty())`;
- `new BrainParts(…, regroupWindow)` → `new BrainParts(…, regroupWindow, new RallyPointRule(<el mismo supplier de retreat que usa su RegroupRule>))`;
- `assertThat(x.regroupStartTick()).hasValue(N)` → `assertThat(x.regrouping().map(Regrouping::startTick)).contains(N)` (con `N` como `long`, por ejemplo `700L`), y `.isEmpty()` → `assertThat(x.regrouping()).isEmpty()`.

## Especificación

### `Regrouping.java` (dominio, `domain.group`)

```java
/** A regroup in progress: when it started and where the group meets (CT-29). */
public record Regrouping(long startTick, Optional<Vec3> rallyPoint) {
  public Regrouping { … }
}
```

- `startTick < 0` → `IllegalArgumentException("Regrouping.startTick must be zero or positive, got " + startTick)`
- `Objects.requireNonNull(rallyPoint, "Regrouping.rallyPoint")`

### `PlanLifecycle.java`

- **Campo nuevo:** `private Vec3 rallyPoint;` con el comentario `// null outside REGROUPING or while no rally point was chosen; exposed only as Optional`.
- **`regroupStartTick()` se borra** y lo reemplaza:

```java
public Optional<Regrouping> regrouping() {
  if (regroupStartTick == NO_REGROUP) {
    return Optional.empty();
  }
  return Optional.of(new Regrouping(regroupStartTick, Optional.ofNullable(rallyPoint)));
}
```

- **Método nuevo**, debajo de `restartRegroupWindow`:

```java
public void rallyAt(Vec3 point) {
  requireState(GroupState.REGROUPING, "set a rally point");
  rallyPoint = Objects.requireNonNull(point, "PlanLifecycle.rallyPoint");
}
```

- **`finishRegrouping`** también pone `rallyPoint = null`.
- **`regroupWithoutPlan`** y la rama de `finishEvaluation` que entra en `REGROUPING` también ponen `rallyPoint = null` (un punto viejo nunca pasa a otro reagrupamiento). `restartRegroupWindow` no lo toca.
- **`capture()`** pasa `regroupStartTick()` → `regrouping().stream().mapToLong(Regrouping::startTick).findFirst()` (un `OptionalLong`) y suma `Optional.ofNullable(rallyPoint)` al final.
- **`restore`** suma `rallyPoint = capture.rallyPoint().orElse(null);`.

### `LifecycleCapture.java`

- Componente nuevo **al final**: `Optional<Vec3> rallyPoint`, con `Objects.requireNonNull(rallyPoint, "LifecycleCapture.rallyPoint")`.
- Validación nueva, después de `requireRegroupStartMatchesState`, en un método privado `requireRallyOnlyWhileRegrouping(GroupState state, Optional<Vec3> rallyPoint)`:
  - si `rallyPoint.isPresent()` y `state != REGROUPING` → `IllegalArgumentException("LifecycleCapture.rallyPoint must be absent outside REGROUPING, got " + state)`.
  - Puede faltar durante `REGROUPING` (decisión 4).

### `RoleAssignment.java`

Componente nuevo **al final**: `Optional<Vec3> rallyPoint`, con `Objects.requireNonNull(rallyPoint, "RoleAssignment.rallyPoint")`. En el javadoc, sumá `@param rallyPoint where a regrouping mob meets the rest of its group (CT-29)`.

### `RetreatSettings.java`

Dos componentes nuevos **al final**: `double rallyDistanceBlocks, double rallyArrivalBlocks`, con
`SettingsChecks.requirePositive("RetreatSettings.rallyDistanceBlocks", rallyDistanceBlocks);` y
`SettingsChecks.requirePositive("RetreatSettings.rallyArrivalBlocks", rallyArrivalBlocks);` al final del constructor.

### `ConfigLoader.java`, `config.yml` y `config-de-prueba.yml`

`retreat(...)` suma, al final, `number(root, "retreat.rally-distance-blocks")` y `number(root, "retreat.rally-arrival-blocks")`.

En la sección `retreat` de `config.yml` y de `docs/plan/config-de-prueba.yml`, después de `regroup-step-ticks`:

```yaml
  # Al reagruparse, el grupo se junta en un punto a esta distancia de su centro, lejos del jugador (CT-29).
  rally-distance-blocks: 12.0
  # A esta distancia del punto de reunión, el mob ya llegó.
  rally-arrival-blocks: 3.0
```

### `RallyPointRule.java` (dominio, `domain.brain`)

```java
/** Where a regrouping group meets: its center, moved away from the danger (CT-29). */
public final class RallyPointRule {
  private final Supplier<RetreatSettings> settings;

  public RallyPointRule(Supplier<RetreatSettings> settings) { … requireNonNull(settings, "RallyPointRule.settings") … }

  public Optional<Vec3> pointFor(GroupSnapshot snapshot, Optional<PlayerId> committedTarget) { … }
}
```

`pointFor`, coordinando funciones privadas de una tarea cada una:
1. Sin mobs en la foto → `Optional.empty()`.
2. `center`: promedio de `mob.position()` de todos los mobs (x, y, z).
3. `danger`: la posición (`pose().position()`) del jugador `committedTarget` si está en la foto con `health() > 0`; si no, la del jugador con `health() > 0` más cercano a `center` (distancia 3D, `distanceTo`); si no hay ninguno, vacío.
4. Sin `danger` → `Optional.of(center)`.
5. `away = center.minus(danger).horizontal()`. Si `away.length() == 0` → `Optional.of(center)`.
6. Si no: `Optional.of(center.plus(away.normalized().times(settings.get().rallyDistanceBlocks())))`.

La configuración se lee en cada llamada (`/mobai reload`).

### `BrainParts.java`

Componente nuevo **al final**: `RallyPointRule rallyPointRule`, con `requireNonNull(rallyPointRule, "BrainParts.rallyPointRule")`. En `standard`, al final: `new RallyPointRule(() -> settings.get().retreat())`.

### `Brain.java`

1. **Método privado nuevo:**

```java
// CT-29: chosen once per regroup; recomputed every decision, it would drift with the mobs.
private void rally(Turn turn) {
  parts
      .rallyPointRule()
      .pointFor(turn.snapshot(), turn.lifecycle().committedTarget())
      .ifPresent(turn.lifecycle()::rallyAt);
}
```

2. **Dónde se llama** (siempre después de la transición y antes de armar las órdenes):
   - `holdBack`: después de `regroupWithoutPlan(...)`;
   - `evaluate`: después de `finishEvaluation()`, solo si `turn.lifecycle().state() == GroupState.REGROUPING`;
   - `keepRegrouping`: después de `restartRegroupWindow(...)`.
   En ningún otro lugar.
3. **`detectRegroupEnd`:** `turn.lifecycle().regroupStartTick().orElseThrow()` → `turn.lifecycle().regrouping().orElseThrow().startTick()`.
4. **Órdenes del reagrupamiento:** `regroupOrders` deja de usar `retreatOrder` y usa un método privado nuevo:

```java
private RoleAssignment regroupOrder(Turn turn, MobSnapshot mob) {
  return new RoleAssignment(
      mob.id(),
      Role.RETREAT,
      turn.lifecycle().committedTarget(),
      Optional.empty(),
      parts.retreatRule().canRecover(mob, turn.snapshot()),
      turn.lifecycle().regrouping().flatMap(Regrouping::rallyPoint));
}
```

   `regroupOrders` queda: `return turn.snapshot().mobs().stream().map(mob -> regroupOrder(turn, mob)).toList();`
5. **`retreatOrder`** (la retirada individual durante un plan) y las demás construcciones de `RoleAssignment` suman `Optional.empty()` al final.

### `docs/arquitectura.md`

- En «Retirada táctica y reagrupamiento», al final del punto **Del grupo**, agregá: `Al empezar a reagrupar, el grupo elige un punto de reunión: su centro, 12 bloques más lejos del jugador. Cada mob, fuera de peligro, camina hasta ahí sin entrar en el alcance del jugador ni cruzarle por delante, y se cura con los demás (CT-29).`
- Tabla «Nombres en el código», fila nueva debajo de la de `RegroupWindow`:

| Punto de reunión, reagrupamiento en curso | `RallyPointRule`, `Regrouping`, `PlanLifecycle.rallyAt`, `RoleAssignment.rallyPoint` | Dominio |

## Pruebas obligatorias

Constante para las pruebas del cerebro: con el grupo de catálogo de `BrainFixture` (9 mobs en x = 1…9, y = 64, z = 5) y Alice en (0, 64, 0), el centro es (5, 64, 5) y el punto es `RALLY = (5 + 6√2, 64, 5 + 6√2)`, es decir (13,485281374…, 64, 13,485281374…). Compará cada coordenada con `within(1e-9)`.

### `RallyPointRuleTest` (11)

Mobs y jugadores con `MobSnapshotBuilder`/`PlayerSnapshotBuilder` (o `GroupSnapshotBuilder`), configuración `TestSettings.defaults().retreat()` (distancia 12) salvo que se diga otra.

| Prueba | Verifica |
| --- | --- |
| `pointLiesBeyondTheCenterAwayFromThePlayer` | mobs en (0,64,0) y (4,64,0), objetivo comprometido en (-10,64,0) → (14, 64, 0) |
| `diagonalDirectionIsNormalized` | un mob en (0,64,0), objetivo en (-3,64,-4), distancia 10 → (6, 64, 8) |
| `heightIsTheMobsAverageAndTheShiftIsHorizontal` | mobs en (0,64,0) y (0,70,4), objetivo en (0,80,-10) → (0, 67, 14) |
| `distanceComesFromTheSettings` | como la primera, con distancia 5 → (7, 64, 0) |
| `withoutACommittedTargetTheNearestPlayerIsTheDanger` | mobs en (0,64,0) y (4,64,0); sin objetivo comprometido; jugadores vivos en (-10,64,0) y (40,64,0) → (14, 64, 0) |
| `aDeadCommittedTargetFallsBackToTheNearestLivingPlayer` | como la anterior, pero el objetivo comprometido es un tercer jugador en (2,64,1) con vida 0 → (14, 64, 0) |
| `aLivingCommittedTargetWinsOverACloserPlayer` | mobs en (0,64,0) y (4,64,0); objetivo comprometido vivo en (2,64,-30); otro jugador vivo, más cerca, en (-5,64,0) → (2, 64, 12) |
| `withoutLivingPlayersTheCenterIsThePoint` | mobs en (0,64,0) y (4,64,0), un solo jugador con vida 0 → (2, 64, 0) |
| `aPlayerOnTheCenterLeavesTheCenter` | mobs en (0,64,0) y (4,64,0), objetivo en (2,50,0) → (2, 64, 0) |
| `noMobsNoPoint` | foto sin mobs → vacío |
| `settingsAreReadOnEveryCall` | supplier sobre un `AtomicReference<RetreatSettings>`: con distancia 12 da (14, 64, 0); cambiada a 5, (7, 64, 0) |

### `GroupLifecycleTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `rallyPointLivesOnlyWhileRegrouping` | `regroupWithoutPlan(500)` → `regrouping()` es `Regrouping(500, vacío)`; `rallyAt(P)` → `Regrouping(500, P)`; `restartRegroupWindow(900)` conserva `P`; `finishRegrouping()` → `regrouping()` vacío; `regroupWithoutPlan(1200)` → `Regrouping(1200, vacío)` |
| `rallyPointNeedsRegrouping` | `rallyAt(P)` en `OBSERVING` lanza `IllegalStateException` con mensaje `Group 00000000 cannot set a rally point while OBSERVING` |

### `LifecycleCaptureTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `rallyPointOutsideRegroupingIsRejected` | `OBSERVING` con `rallyPoint` presente lanza con mensaje `LifecycleCapture.rallyPoint must be absent outside REGROUPING, got OBSERVING` |
| `rallyPointSurvivesCaptureAndRestore` | un grupo en `REGROUPING` con `rallyAt(P)`; `capture()` lo trae; otro grupo hace `restore` de esa captura y su `regrouping()` es `Regrouping(start, P)` |

### `BrainRegroupingTest` (+3)

| Prueba | Verifica |
| --- | --- |
| `regroupingOrdersCarryTheRallyPoint` | `decide(START_TICK + 100, wounded, alice())`: todas las órdenes tienen `rallyPoint` = `RALLY`; `lifecycle().regrouping()` también |
| `rallyPointStaysWhileRegrouping` | con el mob 0 movido a (100, 64, 100) (`withPosition` sobre `wounded`), `decide(START_TICK + 100, …)`: las órdenes siguen con `RALLY` |
| `restartingTheWindowRecomputesTheRallyPoint` | con el mob 0 movido a (-89, 64, 5) (centro (-5, 64, 5)), `decide(REGROUP_START_TICK + INITIAL_WINDOW_TICKS, …)`: el punto nuevo es (-5 - 6√2, 64, 5 + 6√2) |

Y en `recoveredMajorityEndsRegroupingAndLengthensTheWindow`, la aserción de `regroupStartTick()` pasa a `regrouping()` vacío (cambio mecánico).

### `BrainObservingTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `holdingBackRalliesAwayFromTheNearestPlayer` | el caso de `badlyHurtGroupRegroupsInsteadOfPlanning` (sin objetivo comprometido): todas las órdenes tienen `rallyPoint` = `RALLY` |
| `retreatingDuringAPlanHasNoRallyPoint` | el caso de `lowHealthMobsStartThePlanRetreating`: la orden `RETREAT` del mob herido tiene `rallyPoint` vacío |

### `SettingsValidationTest` (+2 casos en la tabla parametrizada)

- `"RetreatSettings.rallyDistanceBlocks must be a positive number, got 0.0"` con `new RetreatSettings(0.6, 12.0, 600, 200, 1200, 50, 0.0, 3.0)`
- `"RetreatSettings.rallyArrivalBlocks must be a positive number, got 0.0"` con `new RetreatSettings(0.6, 12.0, 600, 200, 1200, 50, 12.0, 0.0)`

Verificá con `SettingsChecks.requirePositive` el texto exacto («must be a positive number, got»). Si es otro, usá el real y avisalo.

Total: **20 pruebas nuevas** (11 + 2 + 2 + 3 + 2) y 2 casos de validación. `ConfigLoaderTest` compara contra el `config.yml` incluido: su `RetreatSettings` esperado suma `12.0, 3.0`.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Llamar a `rally(turn)` también al principio de `regroup(turn)` (recalcular en cada decisión) | el punto se mueve con el mob 0 | `rallyPointStaysWhileRegrouping` |
| 2 | En `RallyPointRule`, sin `normalized()` | (30, 64, 40) | `diagonalDirectionIsNormalized` |
| 3 | `finishRegrouping` sin borrar `rallyPoint` | el punto viejo aparece en el reagrupamiento siguiente | `rallyPointLivesOnlyWhileRegrouping` |
| 4 | Sacar `rally(turn)` de `keepRegrouping` | sigue `RALLY` | `restartingTheWindowRecomputesTheRallyPoint` |
| 5 | En `RallyPointRule`, ignorar el objetivo comprometido y usar siempre el jugador vivo más cercano | (14, 64, 0) | `aLivingCommittedTargetWinsOverACloserPlayer` |

## Verificación en el juego (Nico, después del merge del WP-32B)

Este WP no cambia nada visible: los goals todavía no usan el punto. Se verifica junto con el WP-32B.

**Antes de levantar el server con este WP:** sumá `rally-distance-blocks` y `rally-arrival-blocks` a la sección `retreat` del `config.yml` del server de prueba (`run/plugins/MobAI/config.yml`). Si falta una clave, el plugin se deshabilita.

## Procedimiento

1. Rama `wp-32a-punto-de-reunion` desde `origin/main` actualizado.
2. `RetreatSettings`, `ConfigLoader`, `config.yml`, `config-de-prueba.yml` y los cambios mecánicos de `RetreatSettings` en las pruebas; los 2 casos de validación. Commit: `feat: rally distance and arrival settings`.
3. `Regrouping`, `PlanLifecycle`, `LifecycleCapture` y sus pruebas (con los cambios mecánicos de `regroupStartTick()` y `LifecycleCapture`). Commit: `feat: the lifecycle keeps the rally point while regrouping`.
4. `RallyPointRule` y su prueba; `RoleAssignment`, `BrainParts`, `Brain` y sus pruebas (con los cambios mecánicos de `RoleAssignment` y `BrainParts`). Commit: `feat: regrouping groups pick a rally point (CT-29)`.
5. `docs/arquitectura.md`. Commit: `docs: rally point names and regroup section`.
6. Pruebas que muerden, de a una y sin commit.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-32A: regrouping groups pick a rally point`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Formateá a mano con el estilo de google-java-format: 2 espacios, 100 columnas y la misma forma de partir líneas que el código vecino.
- **El dominio y sus pruebas compilan con el JDK 21 del contenedor.** Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central (si da 429, de `https://repo.maven.apache.org/maven2/`) a una carpeta fuera del repo. Compilá con `javac --release 21` todo `src/main/java/io/github/nicodoou/mobai/domain` y, encima, `src/test/java/io/github/nicodoou/mobai/testsupport` (lo que compile sin Paper) y las pruebas de `domain/`. Corré `java -jar junit-platform-console-standalone-1.11.4.jar execute -cp <clases>:<jars explícitos, no lib/*> --select-package io.github.nicodoou.mobai.domain`. Así corrés las pruebas del dominio y las roturas.
- `ConfigLoader`, `ConfigLoaderTest`, `RoleRegistryTest` y `DecisionApplierTest` usan Paper: los verifica el CI del PR (check `build`). Esperá a que termine; si falla, leé el log, corregí y volvé a empujar.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si `testsupport` no compila sin Paper con el JDK 21, compilá solo los archivos que necesiten las pruebas del dominio.

## Fuera de alcance

- Que los goals caminen al punto (WP-32B).
- Mostrar el punto en el `mobai-debug.log` (WP-32B).
- Sacar el estado del reagrupamiento de `PlanLifecycle` a una clase propia.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 20 pruebas y los 2 casos de validación con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las roturas mordieron.
- [ ] `PlanLifecycle` con 20 métodos públicos como máximo (ArchUnit).
- [ ] Build, cobertura y CI en verde.
