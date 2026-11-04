# WP-36A — Grabación del combate: muestras periódicas

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-34C (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-36a-grabacion-muestras` |

## Objetivo

Primera parte del CT-36. Con el nivel de traza `full`, cada 5 ticks se escribe una línea por cada mob, grupo y jugador relevante, en el mismo archivo que el resto de la traza. Así se puede comparar lo que el cerebro le ordenó a cada mob con lo que hizo.

Este WP no toca el dominio ni cambia el comportamiento de los mobs.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceWriter.java`, `TraceEvent.java`, `TraceLevels.java`, `DebugGson.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/MovementTracker.java`, `PoseReader.java`, `SnapshotFactory.java` (modelo de cómo leer mobs y jugadores)
- `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionCadence.java` (modelo de cadencia)
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/DebugSettings.java`, `domain/group/Group.java`, `PlanLifecycle.java`, `GroupStrength.java`, `GroupRoster.java`, `Member.java`, `domain/strategy/RecipeBase.java`, `RecipePlay.java`, `domain/decision/RoleAssignment.java`
- Pruebas: `adapter/debug/TraceLevelsTest.java`, `TraceHubTest.java` (modelo con archivos temporales), `domain/settings/SettingsValidationTest.java`, `testsupport/TestSettings.java`
- Los que encuentre `grep -rln "new DebugSettings(" src/test`

## Reglas de negocio

1. **Cuándo se graba un grupo:** cuando su nivel de traza (`TraceLevels.levelOf`) es `FULL`. No hay comando nuevo: `/mobai debug all full` y `/mobai debug <grupo> full` lo activan, y cualquier otro nivel lo apaga. También vale `debug.default-trace-level: "FULL"`.
2. **Grabación activa:** cuando hay al menos un grupo grabado, o cuando el nivel para todos (o el de la configuración, si no se fijó uno) es `FULL`, aunque no haya ningún grupo. Esto último permite grabar a un jugador en entrenamiento antes de que aparezca un grupo.
3. **Cadencia:** se muestrea en los ticks múltiplos de `debug.recording-interval-ticks` (5 por defecto; mínimo 1). Se lee en cada tick, así que `/mobai reload` lo cambia.
4. **Sin grabación activa, el costo es solo comprobarlo:** nada lee entidades ni escribe.
5. **Mobs:** una muestra por cada miembro cargado de un grupo grabado. Un miembro descargado, muerto o inválido no genera muestra, igual que en `SnapshotFactory.loadedMobs`.
6. **Grupos:** una muestra por grupo grabado con al menos un mob cargado.
7. **Jugadores:** una muestra por jugador conectado que cumpla al menos una:
   - es objetivo de la orden de un mob grabado;
   - está en entrenamiento (`RecipeBase.isTraining`), si la grabación está activa.
8. **Orden de escritura dentro de un mismo tick:** por cada grupo, su `GroupSample` y después sus `MobSample`; al final, los `PlayerSample`.
9. **Las muestras no pasan por `TraceHub`.** Se escriben directo con `TraceWriter`, para no llenar la caja negra (`FlightRecorder`) ni el log legible.
10. **Sin límite de tamaño:** decisión de Nico (CT-36). No hay rotación ni borrado.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/CombatSample.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/PackShape.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/RecordingSelection.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/recording/CombatSampler.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/recording/SamplerParts.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceWriter.java`, `TraceLevels.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`itemName`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/DebugSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/PackShapeTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/RecordingSelectionTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/CombatSampleLinesTest.java` |
| Modificar | `TraceLevelsTest`, `SettingsValidationTest`, y los de `grep -rln "new DebugSettings(" src/test` solo por el constructor |
| Modificar | `docs/arquitectura.md` (sección «Trazabilidad y depuración» y tabla «Nombres en el código»), `docs/actualizar-paper.md` |

`AdapterServices` y `PluginRuntime` solo se tocan para armar y llamar al muestreador. Antes de empezar, buscá con grep que `CombatSample`, `PackShape`, `RecordingSelection`, `CombatSampler`, `SamplerParts` e `itemName` no existan en `src/`.

## Especificación

### `DebugSettings.java`

```java
public record DebugSettings(
    TraceLevel defaultTraceLevel, int flightRecorderEvents, int recordingIntervalTicks) {
  public DebugSettings {
    Objects.requireNonNull(defaultTraceLevel, "DebugSettings.defaultTraceLevel");
    SettingsChecks.requireAtLeast("DebugSettings.flightRecorderEvents", flightRecorderEvents, 1);
    SettingsChecks.requireAtLeast(
        "DebugSettings.recordingIntervalTicks", recordingIntervalTicks, 1);
  }
}
```

En las pruebas: `5` como tercer argumento.

### `config.yml` y `ConfigLoader.java`

Debajo de `flight-recorder-events`:

```yaml
  # Con el nivel "full", cada cuántos ticks se graba cada mob, grupo y jugador en el trace (CT-36).
  # Sin límite de tamaño: una sesión larga puede pesar mucho.
  recording-interval-ticks: 5
```

`ConfigLoader.debug` suma `Math.toIntExact(wholeNumber(root, "debug.recording-interval-ticks"))`.

### `CombatSample.java`

```java
package io.github.nicodoou.mobai.adapter.debug;

/** One line of the combat recording (CT-36): what an entity was doing at a given tick. */
public sealed interface CombatSample {
  long tick();

  record MobSample(
      long tick,
      GroupId group,
      MobId mob,
      MobKind kind,
      Vec3 position,
      Vec3 velocityPerTick,
      double health,
      double maxHealth,
      boolean onGround,
      int fireTicks,
      Optional<RoleAssignment> order,
      List<String> runningGoals,
      Optional<Vec3> pathEnd)
      implements CombatSample {}

  record GroupSample(
      long tick,
      GroupId group,
      GroupState state,
      Optional<PlanId> plan,
      Optional<PlayerId> target,
      int loadedMembers,
      PackShape shape,
      Optional<PlanRecipe> recipe,
      Optional<Double> planStrength)
      implements CombatSample {}

  record PlayerSample(
      long tick,
      PlayerId player,
      String name,
      Vec3 position,
      Vec3 movementPerTick,
      float yaw,
      float pitch,
      double health,
      double absorption,
      double maxHealth,
      double armorPoints,
      boolean onGround,
      boolean sprinting,
      boolean blocking,
      String mainHand,
      String offHand,
      Optional<String> itemInUse,
      int itemInUseTicks,
      boolean training)
      implements CombatSample {}
}
```

Cada record valida con `Objects.requireNonNull` sus referencias, con el mensaje `"MobSample.<campo>"` (y lo mismo para los otros). No hace falta ninguna otra validación. `runningGoals` se copia con `List.copyOf`.

### `PackShape.java`

```java
package io.github.nicodoou.mobai.adapter.debug;

/** Where a group's loaded mobs are and what they were ordered to do. */
public record PackShape(Vec3 center, double spreadBlocks, Map<Role, Long> roleCounts) {
  public static PackShape of(List<CombatSample.MobSample> mobs)
}
```

- Lista vacía: `IllegalArgumentException` con `"PackShape needs at least one mob"`.
- `center`: promedio de las posiciones.
- `spreadBlocks`: la mayor distancia **horizontal** de un mob al centro.
- `roleCounts`: cuántos mobs hay por `order.role()`, en un `EnumMap` envuelto en `Collections.unmodifiableMap`. Un mob sin orden no cuenta.

### `RecordingSelection.java`

```java
package io.github.nicodoou.mobai.adapter.debug;

/** Which groups are being recorded right now, and whether anything is (CT-36). */
public record RecordingSelection(List<GroupId> groups, boolean isActive) {
  public static RecordingSelection of(TraceLevels levels, List<GroupId> candidates)

  /** The recording samples on the ticks that are multiples of the interval. */
  public static boolean isSampleTick(long tick, int intervalTicks)
}
```

- `groups`: los candidatos con `levels.isFull(id)`, en el mismo orden.
- `isActive`: `!groups.isEmpty() || levels.isFullForAll()`.
- `isSampleTick`: `tick % intervalTicks == 0`.

### `TraceLevels.java`

Dos métodos nuevos:

```java
  public boolean isFull(GroupId group) {
    return levelOf(group) == TraceLevel.FULL;
  }

  /** Whether a group with no level of its own would be recorded. */
  public boolean isFullForAll() {
    return levelForAll.orElseGet(() -> settings.get().defaultTraceLevel()) == TraceLevel.FULL;
  }
```

### `TraceWriter.java`

- `TraceLine` pasa a `private record TraceLine(String kind, Object event) {}`.
- Método nuevo, con el mismo formato de línea que los eventos:

  ```java
  public void write(CombatSample sample) {
    Objects.requireNonNull(sample, "TraceWriter.sample");
    lines.append(gson.toJson(new TraceLine(sample.getClass().getSimpleName(), sample)));
  }
  ```

  La línea queda como `{"kind":"MobSample","event":{...}}`.

### `VersionTranslator.java`

```java
  /** The item's name without its namespace, such as iron_sword; air for an empty hand. */
  public String itemName(ItemStack item) {
    return item.getType().getKey().getKey();
  }
```

Va acá porque `Material` solo se usa en esta clase (ArchUnit).

### `SamplerParts.java`

```java
package io.github.nicodoou.mobai.adapter.recording;

/** What the sampler reads and where it writes. */
public record SamplerParts(
    ActiveGroups activeGroups,
    RoleRegistry roles,
    TraceLevels levels,
    TraceWriter writer,
    MovementTracker movement,
    VersionTranslator translator,
    RecipeBase recipeBase,
    Supplier<DebugSettings> settings) {}
```

Con `requireNonNull` en cada componente (`"SamplerParts.<campo>"`).

### `CombatSampler.java`

```java
package io.github.nicodoou.mobai.adapter.recording;

/** Writes what every recorded mob, group and player is doing, every few ticks (CT-36). */
public final class CombatSampler {
  public CombatSampler(SamplerParts parts)

  public void tick(long now)
}
```

`tick`:

1. Si `!RecordingSelection.isSampleTick(now, intervalo)`, termina.
2. Arma la `RecordingSelection` con los IDs de `activeGroups.groups()`. Si no está activa, termina.
3. Para cada grupo grabado (`activeGroups.group(id)`):
   - lee sus miembros cargados y arma un `MobSample` por cada uno;
   - si hay al menos uno, escribe el `GroupSample` y después los `MobSample`;
   - junta los objetivos de las órdenes.
4. Para cada `Bukkit.getOnlinePlayers()` que cumpla la regla 7, escribe su `PlayerSample`.

**Lectura de un mob** (`Mob mob`, `Member member`):
- `position`: `PoseReader.positionOf(mob.getLocation())`;
- `velocityPerTick`: de `mob.getVelocity()`;
- `health`: `EntityReadings.clampHealth(mob.getHealth(), maxHealth)`, con `translator.maxHealth(mob)`;
- `onGround`: `mob.isOnGround()`; `fireTicks`: `mob.getFireTicks()`;
- `order`: `roles.assignmentOf(member.id())`;
- `runningGoals`: `Bukkit.getMobGoals().getRunningGoals(mob)`, cada uno como `goal.getKey().getNamespacedKey().getKey()`, ordenados alfabéticamente;
- `pathEnd`: `mob.getPathfinder().getCurrentPath()` puede ser `null` y su `getFinalPoint()` también; vacío en esos casos.

**Lectura de un grupo:**
- `state`, `plan` (`Plan::id`) y `recipe` (`play.recipe()`) de `group.lifecycle()`;
- `target`: el de `plan`; sin plan, `committedTarget()`;
- `planStrength`: `GroupStrength.ofPlan(plan)` si hay plan;
- `shape`: `PackShape.of(muestras)`.

**Lectura de un jugador:**
- `position`: `PoseReader.positionOf`; `movementPerTick`: `movement.movementPerTick(id)`. No uses `getVelocity`: en jugadores no refleja lo que caminan;
- `yaw` y `pitch`: de `getLocation()`;
- `health`: como en los mobs; `absorption`: `getAbsorptionAmount()`; `armorPoints`: `translator.armorPoints(player)`;
- `onGround`: `isOnGround()`; `sprinting`: `isSprinting()`; `blocking`: `isBlocking()`;
- `mainHand` y `offHand`: `translator.itemName` de cada mano;
- `itemInUse`: vacío si `getItemInUse()` es `null`; si no, su nombre. `itemInUseTicks`: `getItemInUseTicks()`;
- `training`: `recipeBase.isTraining(id)`.

Separá en funciones chicas, con una tarea cada una y hasta 3 parámetros (`codeUnitsHaveAtMostThreeParameters`).

### `AdapterServices.java` y `PluginRuntime.java`

- `AdapterServices` suma el componente `CombatSampler combatSampler`, armado con `new SamplerParts(...)` en `create`. `TraceLevels` y `TraceWriter` están en `parts.debug().outputs()`.
- `PluginRuntime.runTick`, después de `decisionScheduler().tick(now)`:

  ```java
  adapters.combatSampler().tick(now);
  ```

  Va después de decidir, para que cada muestra muestre la orden de ese tick.

### Documentos

- **`docs/arquitectura.md`, «Trazabilidad y depuración»,** después del punto 3 («Escritura»), un párrafo:

  > **Grabación del combate (CT-36).** Con el nivel `FULL`, `CombatSampler` escribe en el mismo archivo una muestra de cada mob, grupo y jugador relevante cada `debug.recording-interval-ticks`. No pasa por `TraceHub`, para no llenar la caja negra.
- **Tabla «Nombres en el código»,** fila nueva después de «Trazas por nivel y log legible»:

  | Grabación del combate | `CombatSample`, `PackShape`, `RecordingSelection` (depuración), `CombatSampler`, `SamplerParts` (grabación) | Adaptadores |
- **`docs/actualizar-paper.md`, sección 3,** fila nueva:

  | `adapter/recording/CombatSampler` | `Bukkit.getEntity`, `getOnlinePlayers`, `getMobGoals().getRunningGoals`, `GoalKey.getNamespacedKey`, `Pathfinder.getCurrentPath`, `PathResult.getFinalPoint`, `Entity.getVelocity`, `isOnGround`, `getFireTicks`, `LivingEntity.getItemInUse`, `getItemInUseTicks`, `Player.isSprinting`, `isBlocking`, `getAbsorptionAmount`, `Location.getYaw`/`getPitch` | Bajo | Que `getRunningGoals` siga incluyendo nuestros goals |
- **Misma sección:** suma `itemName` (`ItemStack.getType`, `Material.getKey`) a la fila de `VersionTranslator`.

## Pruebas obligatorias

### `PackShapeTest` (5)

Con un helper que arma `MobSample` con la posición y la orden indicadas.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `centerIsTheAverageOfThePositions` | `(0, 64, 0)`, `(4, 64, 0)`, `(2, 70, 6)` | `(2, 66, 2)`, `within(1e-9)` por componente |
| `spreadIsTheFarthestHorizontalDistance` | las mismas | 4, `within(1e-9)` (las distancias horizontales son 2,83, 2,83 y 4) |
| `aLoneMobHasNoSpread` | un mob | 0 |
| `roleCountsSkipMobsWithoutAnOrder` | `PRESS`, `PRESS`, `SHOOT`, sin orden | `{PRESS=2, SHOOT=1}` |
| `noMobsIsRejected` | lista vacía | el mensaje exacto |

**Cuenta de la rotura de altura:** con distancia 3D, el tercero queda a √(0² + 4² + 4²) ≈ 5,66, y los otros a √(4 + 4 + 4) ≈ 3,46.

### `RecordingSelectionTest` (7)

Con `TraceLevels` real.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `offRecordsNothing` | configuración `OFF`, dos grupos | sin grupos, inactiva |
| `decisionsDoNotRecord` | `setAll(DECISIONS)` | sin grupos, inactiva |
| `fullForAllRecordsEveryGroup` | `setAll(FULL)` | los dos, en orden; activa |
| `fullForAllIsActiveWithoutGroups` | `setAll(FULL)`, sin candidatos | sin grupos; activa |
| `aGroupAtFullRecordsOnlyThatGroup` | `set(g2, FULL)` | solo `g2`; activa |
| `theConfiguredDefaultCounts` | configuración `FULL` | los dos; activa |
| `samplesOnMultiplesOfTheInterval` | intervalo 5: ticks 10, 11, 14, 15 | `true`, `false`, `false`, `true` |

### `CombatSampleLinesTest` (3)

Con archivo temporal, como en `TraceHubTest`.

| Prueba | Verifica |
| --- | --- |
| `aMobSampleIsOneLineWithItsKind` | la línea empieza con `{"kind":"MobSample","event":{"tick":100` y es una sola |
| `anEmptyOptionalIsWrittenAsNull` | con `order` vacío, se puede leer como JSON y `event.order` es `null` o no está (decidilo con lo que dé `DebugGson` y dejalo fijado en la prueba) |
| `samplesAndEventsShareTheFile` | un `PlanEvent` y un `GroupSample` en el mismo `TraceWriter`: dos líneas, en orden, con su `kind` |

### `TraceLevelsTest` (2 nuevas)

- `isFullFollowsTheGroupLevel`: `set(g, FULL)` da `true`; `set(g, DECISIONS)` da `false`.
- `isFullForAllIgnoresOwnLevels`: `set(g, FULL)` con configuración `OFF` da `false`; `setAll(FULL)` da `true`.

### `SettingsValidationTest`

Caso nuevo en `outOfRangeValuesProvider`: `new DebugSettings(TraceLevel.OFF, 200, 0)` → `"DebugSettings.recordingIntervalTicks must be at least 1, got 0"`.

**Si cambia alguna prueba existente más allá de los constructores, frená y reportá.**

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `PackShape`, distancia 3D (5,66 en lugar de 4) | `spreadIsTheFarthestHorizontalDistance` |
| 2 | En `RecordingSelection.isActive`, sacar `levels.isFullForAll()` | `fullForAllIsActiveWithoutGroups` |
| 3 | En `TraceLevels.isFull`, `!= TraceLevel.OFF` | `decisionsDoNotRecord` |
| 4 | En `PackShape`, contar un mob sin orden como `PRESS` | `roleCountsSkipMobsWithoutAnOrder` |

## Verificación en el server (Opus, después del merge)

`CombatSampler` no tiene prueba unitaria, porque lee entidades de Paper, como los goals. Opus lo verifica en el server de prueba con un bot: `/mobai debug all full`, una pelea, y lectura de las líneas. Mide además el tamaño real por hora.

Opus suma `recording-interval-ticks` al `config.yml` de prueba y a `docs/plan/config-de-prueba.yml`.

## Procedimiento

1. Rama `wp-36a-grabacion-muestras` desde `origin/main` actualizado.
2. `DebugSettings`, `ConfigLoader`, `config.yml` y las pruebas por el constructor. Commit: `feat: recording interval setting (CT-36)`.
3. `CombatSample`, `PackShape`, `RecordingSelection`, `TraceLevels`, `TraceWriter` y sus pruebas. Commit: `feat: combat samples in the trace file (CT-36)`.
4. `VersionTranslator.itemName`, `CombatSampler`, `SamplerParts`, `AdapterServices` y `PluginRuntime`. Commit: `feat: sample mobs, groups and players while recording (CT-36)`.
5. Documentos. Commit: `docs: combat recording in the architecture and the Paper map`.
6. Pruebas que muerden (de a una; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`: Nico puede tener su server corriendo.
8. Push, PR `WP-36A: combat recording samples (CT-36)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá en un record privado, y avisalo.
3. Si la cobertura de `adapter` baja por `CombatSampler` y falla la verificación, frená y reportá el número: no cambies los umbrales.

## Fuera de alcance

- Daño, muertes y acciones del jugador (WP-36B).
- Límite de tamaño, rotación o compresión.
- Mensaje nuevo en `/mobai debug` o cambios en `messages.yml`.
- Pasar las muestras por `TraceHub`, `FlightRecorder` o `DebugLog`.
- Herramientas para analizar los archivos.
- Tocar el dominio, salvo `DebugSettings`.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 17 pruebas nuevas y el caso de validación, en verde; la suite completa en verde, sin cambios en pruebas existentes salvo los constructores.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
