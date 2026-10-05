# WP-02 — Tipos base, puertos y configuración

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E2 Dominio: aprendizaje |
| Depende de | WP-00 |
| Modelo | Haiku |
| Rama | `wp-02-tipos-base` |

## Objetivo

Crear los tipos que usa todo el dominio: identificadores, tipos de mob y de ataque, vector 3D, constantes de Minecraft, los dos puertos de tiempo y azar, la configuración validada, y los fakes de prueba que usarán todos los WPs siguientes.

## Contexto a leer

`docs/plan/reglas-para-agentes.md` y este WP. Nada más.

## Archivos

Todas las rutas son relativas a `src/main/java/io/github/nicodoou/mobai/` (código) o `src/test/java/io/github/nicodoou/mobai/` (pruebas).

| Acción | Ruta |
| --- | --- |
| Crear | `domain/shared/ShortId.java` |
| Crear | `domain/shared/MobId.java`, `PlayerId.java`, `GroupId.java`, `StrategyId.java` |
| Crear | `domain/shared/MobKind.java`, `Attack.java`, `EffectKind.java` |
| Crear | `domain/shared/Vec3.java`, `MinecraftConstants.java` |
| Crear | `domain/port/ServerClock.java`, `RandomSource.java` |
| Crear | `domain/selection/SelectionPolicyType.java` |
| Crear | `domain/settings/SettingsChecks.java`, `TraceLevel.java` |
| Crear | `domain/settings/GroupSettings.java`, `MemorySettings.java`, `SelectionSettings.java`, `TargetSettings.java`, `PlanSettings.java`, `AttackSettings.java`, `SpiderSettings.java`, `PersistenceSettings.java`, `DebugSettings.java`, `MobAiSettings.java` |
| Crear (prueba) | `domain/shared/IdentifiersTest.java`, `AttackTest.java`, `MobKindTest.java`, `Vec3Test.java` |
| Crear (prueba) | `domain/settings/SettingsValidationTest.java` |
| Crear (prueba) | `testsupport/FakeServerClock.java`, `SeededRandomSource.java`, `ScriptedRandomSource.java`, `TestSettings.java`, `TestSupportTest.java` |

## Especificación

Todas las clases son `public final` (o `public record` / `public enum` / `public interface`) salvo que se diga otra cosa. Los paquetes son `io.github.nicodoou.mobai.domain.<subpaquete>`.

### 1. `domain.shared`

**`ShortId`** (package-private, `final class`, constructor privado). Una sola responsabilidad: la forma corta de un UUID que se muestra en logs y comandos.

```java
final class ShortId {
  private static final int LENGTH = 8;

  private ShortId() {}

  static String of(UUID value) {
    return value.toString().substring(0, LENGTH);
  }
}
```

**`MobId`, `PlayerId`, `GroupId`**: los tres iguales salvo el nombre.

```java
public record MobId(UUID value) {
  public MobId {
    Objects.requireNonNull(value, "MobId.value");
  }

  public String shortId() {
    return ShortId.of(value);
  }
}
```

(`PlayerId` usa el mensaje `"PlayerId.value"`; `GroupId`, `"GroupId.value"`.)

**`StrategyId`**: el código de una estrategia de grupo (`DIRECT_ASSAULT`, `FLANK`, `PIN_AND_SHOOT`).

```java
public record StrategyId(String value) {
  private static final Pattern UPPER_SNAKE_CASE = Pattern.compile("[A-Z][A-Z_]*");

  public StrategyId {
    Objects.requireNonNull(value, "StrategyId.value");
    if (!UPPER_SNAKE_CASE.matcher(value).matches()) {
      throw new IllegalArgumentException(
          "StrategyId must be UPPER_SNAKE_CASE, got '" + value + "'");
    }
  }
}
```

**`MobKind`**

```java
public enum MobKind {
  ZOMBIE,
  SKELETON,
  SPIDER;

  public boolean isMelee() {
    return this != SKELETON;
  }
}
```

**`Attack`**: los 7 ataques del catálogo, en este orden exacto. El `id` es la clave con la que la memoria se guarda en disco: no se puede cambiar nunca.

| Constante | `id` | `mobKind` |
| --- | --- | --- |
| `ZOMBIE_FRONT_STRIKE` | `zombie.front_strike` | `ZOMBIE` |
| `ZOMBIE_FLANK_STRIKE` | `zombie.flank_strike` | `ZOMBIE` |
| `ZOMBIE_PATIENT_STRIKE` | `zombie.patient_strike` | `ZOMBIE` |
| `SKELETON_DIRECT_SHOT` | `skeleton.direct_shot` | `SKELETON` |
| `SKELETON_LEAD_SHOT` | `skeleton.lead_shot` | `SKELETON` |
| `SKELETON_OPPORTUNISTIC_SHOT` | `skeleton.opportunistic_shot` | `SKELETON` |
| `SPIDER_BITE` | `spider.bite` | `SPIDER` |

Campos privados `final String id` y `final MobKind mobKind`, constructor `Attack(String id, MobKind mobKind)` y estos métodos públicos:

| Firma | Comportamiento |
| --- | --- |
| `String id()` | Devuelve el `id` |
| `MobKind mobKind()` | Devuelve el `mobKind` |
| `static Optional<Attack> fromId(String id)` | La constante con ese `id`, o `Optional.empty()` si no existe |
| `static List<Attack> forKind(MobKind kind)` | Las constantes de ese tipo de mob, en el orden de la tabla, como lista inmutable (`Stream.toList()`) |

**`EffectKind`**: `RESISTANCE`, `REGENERATION`, `POISON`, `WITHER`, `WEAKNESS`, `SLOWNESS`, en ese orden. Sin métodos.

**`Vec3`**: vector o posición en bloques.

```java
public record Vec3(double x, double y, double z) {
  public static final Vec3 ZERO = new Vec3(0, 0, 0);

  public Vec3 {
    if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
      throw new IllegalArgumentException(
          "Vec3 components must be finite, got (" + x + ", " + y + ", " + z + ")");
    }
  }
}
```

Métodos públicos (todos devuelven un `Vec3` nuevo o un `double`; ninguno modifica nada):

| Firma | Resultado |
| --- | --- |
| `Vec3 plus(Vec3 other)` | Suma componente a componente |
| `Vec3 minus(Vec3 other)` | Resta componente a componente |
| `Vec3 times(double factor)` | Cada componente por `factor` |
| `double dot(Vec3 other)` | Producto escalar |
| `double length()` | `Math.sqrt(dot(this))` |
| `Vec3 horizontal()` | `new Vec3(x, 0, z)` |
| `double distanceTo(Vec3 other)` | `minus(other).length()` |
| `Vec3 normalized()` | `times(1 / length())`. Si `length()` es 0, lanza `IllegalArgumentException("cannot normalize a zero-length vector")` |
| `double angleDegreesTo(Vec3 other)` | Ángulo entre los dos vectores, de 0 a 180. Si alguno mide 0, lanza `IllegalArgumentException("cannot measure an angle with a zero-length vector")`. Cálculo: `Math.toDegrees(Math.acos(Math.clamp(dot(other) / (length() * other.length()), -1.0, 1.0)))`. El `clamp` evita `NaN` cuando el redondeo deja el coseno apenas fuera de [-1, 1] |

**`MinecraftConstants`**: `final class` con constructor privado. Solo constantes `public static final`, cada una con un comentario de una línea que diga de dónde sale el valor.

| Constante | Valor | Comentario |
| --- | --- | --- |
| `int TICKS_PER_SECOND` | `20` | `// Minecraft runs at a fixed 20 ticks per second.` |
| `int SHIELD_WARMUP_TICKS` | `5` | `// Measured in the WP-01 spike: isBlocking() turns true 5 ticks after raising the shield.` |
| `double SHIELD_HALF_ARC_DEGREES` | `90.0` | `// Measured in the WP-01 spike: hits from more than 90 degrees off the facing are not blocked.` |
| `double ARROW_SPEED_BLOCKS_PER_TICK` | `1.6` | `// Vanilla skeleton arrow launch speed.` |

### 2. `domain.port`

Cada puerto lleva Javadoc de una línea con lo que garantiza (la política lo pide: se implementan en otra capa).

```java
/** Server time in ticks: advances only while the server runs and survives restarts. */
public interface ServerClock {
  long currentTick();
}
```

```java
/** Every random draw of the domain goes through here, so tests and replays are reproducible. */
public interface RandomSource {
  /** Uniform value in [0, 1). */
  double nextUnit();

  /** Standard normal value (mean 0, standard deviation 1). */
  double nextGaussian();

  /** Uniform integer in [0, bound); bound must be positive. */
  int nextIndex(int bound);
}
```

### 3. `domain.selection.SelectionPolicyType`

```java
public enum SelectionPolicyType {
  THOMPSON_SAMPLING,
  EXPLORE_FIRST,
  EPSILON_GREEDY,
  RANDOM
}
```

### 4. `domain.settings`

**`TraceLevel`**: `OFF`, `DECISIONS`, `FULL`, en ese orden.

**`SettingsChecks`** (package-private, `final class`, constructor privado, métodos `static`). Los mensajes tienen que ser **exactamente** estos (las pruebas los comparan textualmente). Los `double` se imprimen con `String.valueOf` (por ejemplo `0.0`, `1.5`, `NaN`).

| Firma | Falla cuando | Mensaje |
| --- | --- | --- |
| `static void requireAtLeast(String field, long value, long minimum)` | `value < minimum` | `field + " must be at least " + minimum + ", got " + value` |
| `static void requirePositive(String field, double value)` | `!(value > 0) \|\| !Double.isFinite(value)` | `field + " must be a positive number, got " + value` |
| `static void requireNonNegative(String field, double value)` | `!(value >= 0) \|\| !Double.isFinite(value)` | `field + " must be zero or positive, got " + value` |
| `static void requireBetween(String field, double value, double minimum, double maximum)` | `!(value >= minimum && value <= maximum)` (incluye `NaN`) | `field + " must be between " + minimum + " and " + maximum + ", got " + value` |
| `static void requireNotAbove(String lowerField, double lower, String upperField, double upper)` | `lower > upper` | `lowerField + " must not exceed " + upperField + ", got " + lower + " > " + upper` |

Todas lanzan `IllegalArgumentException`.

**Los 10 records de configuración.** Cada uno valida en su constructor compacto, en el orden de la tabla, usando `SettingsChecks`. El nombre del campo en los mensajes es `"<Record>.<componente>"`, por ejemplo `"GroupSettings.maxGroupSize"`. Los records de configuración están exentos del límite de 3 parámetros: son valores, no funciones.

| Record | Componentes (en este orden) | Validación |
| --- | --- | --- |
| `GroupSettings` | `int maxGroupSize`, `int decisionIntervalTicks`, `double detectionRadiusBlocks` | `requireAtLeast(maxGroupSize, 1)`; `requireAtLeast(decisionIntervalTicks, 1)`; `requirePositive(detectionRadiusBlocks)` |
| `MemorySettings` | `long halfLifeTicks`, `double learningSpeed`, `double partialHitWeight` | `requireAtLeast(halfLifeTicks, 1)`; `requireBetween(learningSpeed, 0, 1)`; `requireBetween(partialHitWeight, 0, 1)` |
| `SelectionSettings` | `SelectionPolicyType defaultPolicy`, `double memoryMultiplierMin`, `double memoryMultiplierMax`, `double epsilon`, `int exploreFirstAttempts` | `Objects.requireNonNull(defaultPolicy, "SelectionSettings.defaultPolicy")`; `requirePositive(memoryMultiplierMin)`; `requirePositive(memoryMultiplierMax)`; `requireNotAbove(memoryMultiplierMin, memoryMultiplierMax)`; `requireBetween(epsilon, 0, 1)`; `requireAtLeast(exploreFirstAttempts, 0)` |
| `TargetSettings` | `long threatWindowTicks`, `double commitmentBonus`, `double baseThreat`, `double approachSpeedBlocksPerSecond`, `double weaknessThreatMultiplierPerLevel` | `requireAtLeast(threatWindowTicks, 1)`; `requireNonNegative(commitmentBonus)`; `requirePositive(baseThreat)`; `requirePositive(approachSpeedBlocksPerSecond)`; `requirePositive(weaknessThreatMultiplierPerLevel)`; `requireBetween(weaknessThreatMultiplierPerLevel, 0, 1)` |
| `PlanSettings` | `long maxPlanDurationTicks`, `double targetLostDistanceBlocks`, `long targetLostTicks`, `double retreatHealthFraction`, `double fullSuccessDamageFraction` | `requireAtLeast(maxPlanDurationTicks, 1)`; `requirePositive(targetLostDistanceBlocks)`; `requireAtLeast(targetLostTicks, 1)`; `requireBetween(retreatHealthFraction, 0, 1)`; `requirePositive(fullSuccessDamageFraction)`; `requireBetween(fullSuccessDamageFraction, 0, 1)` |
| `AttackSettings` | `long projectileTimeoutTicks`, `long patientStrikeMaxWaitTicks`, `long opportunisticShotMaxWaitTicks`, `double shootMinDistanceBlocks`, `double shootMaxDistanceBlocks`, `double flankDistanceBlocks`, `double retreatDistanceBlocks` | `requireAtLeast(…, 1)` para los tres `long`; `requirePositive` para los cuatro `double`; después `requireNotAbove(shootMinDistanceBlocks, shootMaxDistanceBlocks)` |
| `SpiderSettings` | `long slownessDurationTicks`, `int slownessLevel` | `requireAtLeast(slownessDurationTicks, 1)`; `requireAtLeast(slownessLevel, 1)` |
| `PersistenceSettings` | `long saveIntervalTicks` | `requireAtLeast(saveIntervalTicks, 1)` |
| `DebugSettings` | `TraceLevel defaultTraceLevel`, `int flightRecorderEvents` | `Objects.requireNonNull(defaultTraceLevel, "DebugSettings.defaultTraceLevel")`; `requireAtLeast(flightRecorderEvents, 1)` |
| `MobAiSettings` | `GroupSettings group`, `MemorySettings memory`, `SelectionSettings selection`, `TargetSettings target`, `PlanSettings plan`, `AttackSettings attack`, `SpiderSettings spider`, `PersistenceSettings persistence`, `DebugSettings debug` | `Objects.requireNonNull(<componente>, "MobAiSettings.<componente>")` para los nueve |

En `requireNotAbove`, los nombres de campo también llevan el prefijo del record: `"SelectionSettings.memoryMultiplierMin"` y `"SelectionSettings.memoryMultiplierMax"`; `"AttackSettings.shootMinDistanceBlocks"` y `"AttackSettings.shootMaxDistanceBlocks"`.

### 5. Soporte de pruebas (`src/test/java/.../testsupport`)

Paquete `io.github.nicodoou.mobai.testsupport`. Son clases de prueba: pueden usar `java.util.SplittableRandom` (ArchUnit solo revisa el código de producción).

**`FakeServerClock implements ServerClock`**

| Firma | Comportamiento |
| --- | --- |
| `FakeServerClock(long startTick)` | Si `startTick < 0`, `IllegalArgumentException("startTick must be zero or positive, got " + startTick)` |
| `long currentTick()` | El tick actual |
| `void advance(long ticks)` | Suma `ticks`. Si `ticks < 0`, `IllegalArgumentException("ticks must be zero or positive, got " + ticks)` |

**`SeededRandomSource implements RandomSource`**: envuelve `new SplittableRandom(seed)`.

| Firma | Comportamiento |
| --- | --- |
| `SeededRandomSource(long seed)` | Crea el generador |
| `double nextUnit()` | `random.nextDouble()` |
| `double nextGaussian()` | `random.nextGaussian()` |
| `int nextIndex(int bound)` | Si `bound <= 0`, `IllegalArgumentException("bound must be positive, got " + bound)`; si no, `random.nextInt(bound)` |

**`ScriptedRandomSource implements RandomSource`**: devuelve valores fijados por la prueba, en orden. Sirve para pruebas exactas y, más adelante, para reproducir incidentes.

| Firma | Comportamiento |
| --- | --- |
| `ScriptedRandomSource()` | Sin valores |
| `ScriptedRandomSource withUnits(double... values)` | Agrega al final de la cola de `nextUnit`; devuelve `this` |
| `ScriptedRandomSource withGaussians(double... values)` | Ídem, cola de `nextGaussian` |
| `ScriptedRandomSource withIndexes(int... values)` | Ídem, cola de `nextIndex` |
| `double nextUnit()` | Saca el primero de su cola. Cola vacía: `IllegalStateException("no scripted unit value left")` |
| `double nextGaussian()` | Ídem. Mensaje: `"no scripted gaussian value left"` |
| `int nextIndex(int bound)` | Ídem. Mensaje: `"no scripted index value left"`. Si el valor no está en `[0, bound)`: `IllegalStateException("scripted index " + value + " is outside [0, " + bound + ")")` |
| `boolean isExhausted()` | `true` si las tres colas están vacías |

Las colas son `ArrayDeque`.

**`TestSettings`**: `final class`, constructor privado, un solo método `public static MobAiSettings defaults()` que devuelve la configuración del catálogo:

```java
new MobAiSettings(
    new GroupSettings(12, 10, 24.0),
    new MemorySettings(12_000, 0.7, 0.5),
    new SelectionSettings(SelectionPolicyType.THOMPSON_SAMPLING, 0.5, 1.5, 0.1, 10),
    new TargetSettings(600, 0.2, 1.0, 3.0, 0.5),
    new PlanSettings(600, 32.0, 200, 0.3, 0.5),
    new AttackSettings(60, 60, 60, 8.0, 15.0, 3.0, 16.0),
    new SpiderSettings(60, 1),
    new PersistenceSettings(6_000),
    new DebugSettings(TraceLevel.OFF, 200));
```

## Pruebas obligatorias

Nombres exactos. Tolerancia `within(1e-9)` en todos los `double`.

**`IdentifiersTest`** (`domain.shared`)

| Prueba | Dado / cuando / entonces |
| --- | --- |
| `mobIdRejectsNullUuid` | `new MobId(null)` lanza `NullPointerException` con mensaje `MobId.value` |
| `playerIdRejectsNullUuid` | Ídem con `PlayerId` |
| `groupIdRejectsNullUuid` | Ídem con `GroupId` |
| `shortIdIsTheFirstEightCharactersOfTheUuid` | Con `UUID.fromString("123e4567-e89b-12d3-a456-426614174000")`, `shortId()` de los tres tipos da `"123e4567"` |
| `strategyIdAcceptsUpperSnakeCase` | `new StrategyId("PIN_AND_SHOOT").value()` es `"PIN_AND_SHOOT"` |
| `strategyIdRejectsBlankValue` | `new StrategyId("")` lanza `IllegalArgumentException` con mensaje `StrategyId must be UPPER_SNAKE_CASE, got ''` |
| `strategyIdRejectsLowerCase` | `new StrategyId("flank")` lanza `IllegalArgumentException` con mensaje `StrategyId must be UPPER_SNAKE_CASE, got 'flank'` |

**`MobKindTest`**

| Prueba | Verificación |
| --- | --- |
| `zombiesAndSpidersAreMeleeAndSkeletonsAreNot` | `ZOMBIE.isMelee()` y `SPIDER.isMelee()` son `true`; `SKELETON.isMelee()` es `false` |

**`AttackTest`**

| Prueba | Verificación |
| --- | --- |
| `idsMatchTheCatalog` | Los `id()` de `Attack.values()`, en orden, son exactamente los 7 de la tabla |
| `fromIdFindsEveryAttack` | Para cada constante, `Attack.fromId(constante.id())` es `Optional.of(constante)` |
| `fromIdIsEmptyForUnknownId` | `Attack.fromId("zombie.unknown")` está vacío |
| `forKindListsAttacksInCatalogOrder` | `forKind(ZOMBIE)` es `[ZOMBIE_FRONT_STRIKE, ZOMBIE_FLANK_STRIKE, ZOMBIE_PATIENT_STRIKE]`; `forKind(SKELETON)` es `[SKELETON_DIRECT_SHOT, SKELETON_LEAD_SHOT, SKELETON_OPPORTUNISTIC_SHOT]`; `forKind(SPIDER)` es `[SPIDER_BITE]` |
| `forKindReturnsAnUnmodifiableList` | `forKind(ZOMBIE).add(SPIDER_BITE)` lanza `UnsupportedOperationException` |

**`Vec3Test`**

| Prueba | Verificación |
| --- | --- |
| `plusMinusAndTimesAreComponentWise` | `(1,2,3).plus(4,5,6)` = `(5,7,9)`; `(4,5,6).minus(1,2,3)` = `(3,3,3)`; `(1,2,3).times(2)` = `(2,4,6)` |
| `dotProductOfKnownVectors` | `(1,2,3)·(4,5,6)` = `32` |
| `lengthOfThreeFourZeroIsFive` | `(3,4,0).length()` = `5` |
| `horizontalDropsTheVerticalComponent` | `(1,2,3).horizontal()` = `(1,0,3)` |
| `distanceBetweenKnownPoints` | `(0,0,0).distanceTo(3,4,12)` = `13` |
| `normalizedVectorHasLengthOne` | `(3,4,12).normalized().length()` = `1` |
| `normalizingZeroVectorFails` | `Vec3.ZERO.normalized()` lanza `IllegalArgumentException` con mensaje `cannot normalize a zero-length vector` |
| `angleBetweenPerpendicularVectorsIsNinety` | `(1,0,0).angleDegreesTo(0,0,1)` = `90` |
| `angleBetweenSameDirectionIsZero` | `(1,0,0).angleDegreesTo(2,0,0)` = `0` |
| `angleBetweenOppositeVectorsIsOneHundredEighty` | `(1,0,0).angleDegreesTo(-1,0,0)` = `180` |
| `angleWithZeroVectorFails` | `(1,0,0).angleDegreesTo(Vec3.ZERO)` lanza `IllegalArgumentException` con mensaje `cannot measure an angle with a zero-length vector` |
| `rejectsNonFiniteComponents` | `new Vec3(Double.NaN, 0, 0)` y `new Vec3(0, Double.POSITIVE_INFINITY, 0)` lanzan `IllegalArgumentException`; el primero con mensaje `Vec3 components must be finite, got (NaN, 0.0, 0.0)` |

**`SettingsValidationTest`** (`domain.settings`)

| Prueba | Verificación |
| --- | --- |
| `catalogDefaultsAreValid` | `TestSettings.defaults()` no lanza nada; y `group().maxGroupSize()` = 12, `memory().halfLifeTicks()` = 12 000, `memory().learningSpeed()` = 0,7, `selection().defaultPolicy()` = `THOMPSON_SAMPLING`, `attack().projectileTimeoutTicks()` = 60, `persistence().saveIntervalTicks()` = 6 000, `debug().defaultTraceLevel()` = `OFF` |
| `boundaryValuesAreAccepted` | No lanzan: `new MemorySettings(1, 0.0, 0.0)`, `new MemorySettings(1, 1.0, 1.0)`, `new SelectionSettings(RANDOM, 1.0, 1.0, 0.0, 0)`, `new SelectionSettings(RANDOM, 1.0, 1.0, 1.0, 0)`, `new AttackSettings(1, 1, 1, 8.0, 8.0, 1.0, 1.0)`, `new PlanSettings(1, 1.0, 1, 0.0, 1.0)`, `new TargetSettings(1, 0.0, 1.0, 1.0, 1.0)` |
| `outOfRangeValuesAreRejectedWithTheirFieldName` | `@ParameterizedTest` con `@MethodSource`: cada caso es (mensaje esperado, construcción). Cada construcción lanza `IllegalArgumentException` con **exactamente** ese mensaje. Casos en la tabla siguiente |
| `missingSectionIsRejected` | `new MobAiSettings(...)` con `memory` en `null` (y el resto de `TestSettings.defaults()`) lanza `NullPointerException` con mensaje `MobAiSettings.memory` |

Casos de `outOfRangeValuesAreRejectedWithTheirFieldName` (los demás argumentos de cada construcción son los de `TestSettings.defaults()`):

| Construcción con el valor inválido | Mensaje esperado |
| --- | --- |
| `GroupSettings` con `maxGroupSize = 0` | `GroupSettings.maxGroupSize must be at least 1, got 0` |
| `GroupSettings` con `decisionIntervalTicks = 0` | `GroupSettings.decisionIntervalTicks must be at least 1, got 0` |
| `GroupSettings` con `detectionRadiusBlocks = 0.0` | `GroupSettings.detectionRadiusBlocks must be a positive number, got 0.0` |
| `MemorySettings` con `halfLifeTicks = 0` | `MemorySettings.halfLifeTicks must be at least 1, got 0` |
| `MemorySettings` con `learningSpeed = 1.1` | `MemorySettings.learningSpeed must be between 0.0 and 1.0, got 1.1` |
| `MemorySettings` con `learningSpeed = Double.NaN` | `MemorySettings.learningSpeed must be between 0.0 and 1.0, got NaN` |
| `MemorySettings` con `partialHitWeight = -0.1` | `MemorySettings.partialHitWeight must be between 0.0 and 1.0, got -0.1` |
| `SelectionSettings` con `memoryMultiplierMin = 1.6` (y max 1,5) | `SelectionSettings.memoryMultiplierMin must not exceed SelectionSettings.memoryMultiplierMax, got 1.6 > 1.5` |
| `SelectionSettings` con `epsilon = 2.0` | `SelectionSettings.epsilon must be between 0.0 and 1.0, got 2.0` |
| `SelectionSettings` con `exploreFirstAttempts = -1` | `SelectionSettings.exploreFirstAttempts must be at least 0, got -1` |
| `TargetSettings` con `commitmentBonus = -0.2` | `TargetSettings.commitmentBonus must be zero or positive, got -0.2` |
| `TargetSettings` con `weaknessThreatMultiplierPerLevel = 0.0` | `TargetSettings.weaknessThreatMultiplierPerLevel must be a positive number, got 0.0` |
| `PlanSettings` con `retreatHealthFraction = 1.5` | `PlanSettings.retreatHealthFraction must be between 0.0 and 1.0, got 1.5` |
| `AttackSettings` con `shootMinDistanceBlocks = 16.0` (y max 15) | `AttackSettings.shootMinDistanceBlocks must not exceed AttackSettings.shootMaxDistanceBlocks, got 16.0 > 15.0` |
| `SpiderSettings` con `slownessLevel = 0` | `SpiderSettings.slownessLevel must be at least 1, got 0` |
| `PersistenceSettings` con `saveIntervalTicks = 0` | `PersistenceSettings.saveIntervalTicks must be at least 1, got 0` |
| `DebugSettings` con `flightRecorderEvents = 0` | `DebugSettings.flightRecorderEvents must be at least 1, got 0` |

**`TestSupportTest`** (`testsupport`)

| Prueba | Verificación |
| --- | --- |
| `seededRandomRepeatsTheSequenceForTheSameSeed` | Dos `SeededRandomSource(42)` dan los mismos 5 `nextUnit()`, los mismos 5 `nextGaussian()` y los mismos 5 `nextIndex(10)` |
| `seededRandomIndexStaysWithinBound` | 10 000 llamadas a `nextIndex(7)` con semilla 7: todas en `[0, 7)` y aparecen los 7 valores |
| `seededRandomRejectsNonPositiveBound` | `nextIndex(0)` lanza `IllegalArgumentException` con mensaje `bound must be positive, got 0` |
| `scriptedRandomReturnsValuesInOrder` | Con `withUnits(0.1, 0.2)`, `withGaussians(-1.0)` y `withIndexes(2)`: devuelve 0,1, 0,2, -1,0 y 2 (con `nextIndex(3)`); después `isExhausted()` es `true` |
| `scriptedRandomFailsWhenExhausted` | Sin valores, `nextUnit()` lanza `IllegalStateException` con mensaje `no scripted unit value left` |
| `scriptedRandomRejectsIndexOutsideBound` | Con `withIndexes(5)`, `nextIndex(3)` lanza `IllegalStateException` con mensaje `scripted index 5 is outside [0, 3)` |
| `fakeClockAdvances` | `new FakeServerClock(100)`, `advance(50)`: `currentTick()` es 150 |
| `fakeClockRejectsNegativeAdvance` | `advance(-1)` lanza `IllegalArgumentException` con mensaje `ticks must be zero or positive, got -1` |

### Pruebas que muerden (obligatorio, va en el informe)

Hacé cada cambio, corré `./gradlew test`, confirmá que falla al menos la prueba indicada y revertí:

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `Vec3.angleDegreesTo`, sacar el `Math.toDegrees` | `angleBetweenPerpendicularVectorsIsNinety` |
| En `SettingsChecks.requireBetween`, cambiar `<=` por `<` | `boundaryValuesAreAccepted` |
| En `Attack.fromId`, devolver siempre `Optional.empty()` | `fromIdFindsEveryAttack` |
| En `ScriptedRandomSource.nextUnit`, devolver `0` con la cola vacía en vez de lanzar | `scriptedRandomFailsWhenExhausted` |

## Procedimiento

1. Rama `wp-02-tipos-base` desde `main`.
2. `domain.shared` con sus 4 pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add shared identifiers and value types`.
3. `domain.port`. `./gradlew spotlessApply build`. Commit: `feat(domain): add server clock and random source ports`.
4. `SelectionPolicyType`, `domain.settings`, `TestSettings` y `SettingsValidationTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add validated settings records`.
5. `FakeServerClock`, `SeededRandomSource`, `ScriptedRandomSource` y `TestSupportTest`. `./gradlew spotlessApply build`. Commit: `test: add clock and random fakes`.
6. Pruebas que muerden (sección anterior).
7. `./gradlew jacocoTestReport jacocoTestCoverageVerification`: la cobertura del dominio tiene que ser al menos 80 %. Si no llega, frená y reportá qué clases quedan sin cubrir.
8. Push, PR con título `WP-02: shared types, ports and settings`, esperar el check `build` en verde.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- `PlanId`, `AttemptId`, trazas, memoria, políticas y cualquier lógica de juego: llegan en WPs posteriores.
- Cargar la configuración desde `config.yml` (WP-16).
- Métodos extra en `Vec3`, `Attack` u otros tipos que no estén en este WP.
- Constantes de `MinecraftConstants` que no estén en la tabla.

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres y mensajes de error idénticos a los de este WP.
- [ ] Todas las pruebas obligatorias existen con su nombre exacto y pasan.
- [ ] Las 4 pruebas que muerden fallaron con el cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 % (`jacocoTestCoverageVerification` pasa).
- [ ] 4 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde.
