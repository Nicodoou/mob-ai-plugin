# WP-06 — Fotos, amenaza y geometría

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | Puerta E2 (WP-02 a WP-05) |
| Modelo | Sonnet |
| Rama | `wp-06-fotos-amenaza-geometria` |

## Objetivo

Crear las tres piezas que usan el selector de objetivo, el grupo, el cerebro y los goals:

- **Fotos:** los datos del mundo que el adaptador le pasa al dominio en cada decisión: jugadores, mobs y la foto del grupo.
- **Registro de amenaza:** cuánto daño hizo cada jugador al grupo en la ventana de 30 s.
- **Geometría de combate:** funciones puras para el arco del escudo, el punto de flanqueo, el punto de retirada y el tiro anticipado.

Además, los builders de prueba de las fotos.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/Vec3.java`, `MinecraftConstants.java`, `EffectKind.java`, `MobKind.java`, `MobId.java`, `PlayerId.java`, `GroupId.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/TargetSettings.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `AttackFactsBuilder.java` (como ejemplo de estilo de builder)

## Reglas de negocio

**Fotos.** Son records inmutables que describen lo que el adaptador ve en un tick. No calculan nada: lo único permitido además de los componentes son búsquedas (`effectLevel`, `player`, `mob`). Las colecciones se copian en el constructor, para que nadie las cambie desde afuera, y conservan un orden determinista: las trazas de depuración tienen que salir iguales en cada repetición.

- La foto del jugador lleva el **movimiento por tick** medido por el adaptador. `player.getVelocity()` no sirve: caminando da 0 en horizontal (hallazgo del spike).
- La foto del jugador lleva la **dirección hacia la que mira**, solo horizontal y de largo 1. Sin ella no se puede saber si un mob está dentro del arco del escudo.
- La foto del jugador lleva la **absorción** aparte de la vida: los corazones dorados también hay que sacarlos.
- La foto del mob **no** lleva su rol ni su grupo: el rol es estado del dominio (lo guarda el grupo, WP-08), no algo que el adaptador ve. El grupo es el de la foto que lo contiene.
- La foto del grupo **no** lleva la amenaza: la amenaza es memoria del grupo (el `ThreatLedger` que guarda `Group`, WP-08), y el adaptador no la conoce.

**Amenaza** (RF-04.2). Amenaza de un jugador = suma del daño que le hizo al grupo en los últimos `TargetSettings.threatWindowTicks` ticks (600 por defecto, 30 s). Un golpe del tick `t` cuenta mientras `tickActual − t < ventana`: con ventana 600, un golpe del tick 100 cuenta en el tick 699 y deja de contar en el 700. La ventana se lee de la configuración en cada uso, porque `/mobai reload` la puede cambiar. El piso de amenaza y la debilidad **no** van acá: los aplica el selector de objetivo (WP-07).

**Geometría** (D13). Funciones puras, sin estado. Los goals las llaman en cada tick con datos frescos de la entidad, por eso reciben vectores y no fotos.

- **Arco del escudo:** el escudo cubre 90° a cada lado de la dirección hacia la que mira el jugador, medido solo en horizontal (spike: bloqueados a 8°, 10°, 43° y 82°; no bloqueados a 98°, 107° y 111°). Exactamente 90° cuenta como adentro: el golpe de flanco exige «más de 90°». Un atacante en la misma columna que el jugador (sin separación horizontal) cuenta como adentro: ángulo 0.
- **Punto de flanqueo:** a `distanceBlocks` (3 por defecto) del jugador, a 135° de su frente, del lado del que ya está el mob. 135° = el arco del escudo (90°) más un margen de 45°. Si el mob está alineado con el frente o la espalda del jugador, va al lado positivo (fórmula abajo). La altura es la del jugador.
- **Punto de retirada:** a `distanceBlocks` (16 por defecto) del mob, en la dirección horizontal que lo aleja del peligro. Si no hay separación horizontal, va hacia +x: cualquier dirección fija sirve y deja el resultado determinista. La altura es la del mob.
- **Tiro anticipado:** es la fórmula que acertó 8 de 9 flechas en el spike.
  - Ticks de vuelo = distancia del ojo del esqueleto al punto de mira ÷ 1,6.
  - Punto predicho = punto de mira + movimiento por tick × ticks de vuelo.
  - Velocidad de la flecha = dirección al punto predicho, levantada un 20 % de la distancia horizontal (compensa la gravedad, como el esqueleto vanilla) y escalada a 1,6 bloques por tick.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Modificar | `domain/shared/MinecraftConstants.java` (agregar una constante) |
| Crear | `domain/geometry/PlayerPose.java`, `CombatGeometry.java` |
| Crear | `domain/snapshot/SnapshotChecks.java`, `PlayerSnapshot.java`, `MobSnapshot.java`, `GroupSnapshot.java` |
| Crear | `domain/threat/ThreatLedger.java` |
| Crear (prueba) | `testsupport/PlayerSnapshotBuilder.java`, `MobSnapshotBuilder.java`, `GroupSnapshotBuilder.java` |
| Crear (prueba) | `domain/geometry/PlayerPoseTest.java`, `CombatGeometryTest.java` |
| Crear (prueba) | `domain/snapshot/PlayerSnapshotTest.java`, `MobSnapshotTest.java`, `GroupSnapshotTest.java` |
| Crear (prueba) | `domain/threat/ThreatLedgerTest.java` |

## Especificación

### 1. `MinecraftConstants`

Agregar, después de `ARROW_SPEED_BLOCKS_PER_TICK`, una línea en blanco y:

```java
  // Vanilla skeletons raise their aim by this fraction of the horizontal distance to make up for
  // the arrow's drop.
  public static final double ARROW_ARC_FACTOR = 0.2;
```

### 2. `domain.geometry.PlayerPose`

Posición y dirección hacia la que mira un jugador.

```java
public record PlayerPose(Vec3 position, Vec3 facing) {
  public PlayerPose {
    Objects.requireNonNull(position, "PlayerPose.position");
    Objects.requireNonNull(facing, "PlayerPose.facing");
    Vec3 horizontalFacing = facing.horizontal();
    if (horizontalFacing.length() == 0) {
      throw new IllegalArgumentException(
          "PlayerPose.facing must have a horizontal component, got " + facing);
    }
    facing = horizontalFacing.normalized();
  }
}
```

Copialo tal cual. El constructor guarda `facing` sin componente vertical y con largo 1, así que ninguna función de geometría tiene que volver a normalizarlo.

### 3. `domain.geometry.CombatGeometry`

```java
public final class CombatGeometry {
  public double angleFromFacingDegrees(PlayerPose pose, Vec3 point) { ... }
  public boolean isInShieldArc(PlayerPose pose, Vec3 attackerPosition) { ... }
  public Vec3 flankPoint(PlayerPose pose, Vec3 mobPosition, double distanceBlocks) { ... }
  public Vec3 retreatPoint(Vec3 mobPosition, Vec3 dangerPosition, double distanceBlocks) { ... }
  public Vec3 predictedAimPoint(Vec3 shooterEye, Vec3 aimPoint, Vec3 movementPerTick) { ... }
  public Vec3 leadShotVelocity(Vec3 shooterEye, Vec3 predictedAimPoint) { ... }
}
```

Sin estado, sin constructor declarado y sin dependencias. Constantes privadas:

```java
  // A flanker stands well outside the shield arc so a small turn of the player does not cover it.
  private static final double FLANK_MARGIN_DEGREES = 45.0;
  private static final double FLANK_ANGLE_DEGREES =
      MinecraftConstants.SHIELD_HALF_ARC_DEGREES + FLANK_MARGIN_DEGREES;
  // Without horizontal separation there is no "away"; any fixed direction keeps it deterministic.
  private static final Vec3 DEFAULT_RETREAT_DIRECTION = new Vec3(1, 0, 0);
  private static final int POSITIVE_SIDE = 1;
  private static final int NEGATIVE_SIDE = -1;
```

Cada método, exactamente así:

| Método | Cuerpo |
| --- | --- |
| `angleFromFacingDegrees` | `Vec3 offset = point.minus(pose.position()).horizontal();` si `offset.length() == 0`, devuelve `0`; si no, `pose.facing().angleDegreesTo(offset)` |
| `isInShieldArc` | `angleFromFacingDegrees(pose, attackerPosition) <= MinecraftConstants.SHIELD_HALF_ARC_DEGREES` |
| `flankPoint` | `requirePositiveDistance(distanceBlocks)`; `Vec3 offset = mobPosition.minus(pose.position()).horizontal();` `int side = sideOf(pose.facing(), offset);` `Vec3 direction = rotateAroundVertical(pose.facing(), side * FLANK_ANGLE_DEGREES);` devuelve `pose.position().plus(direction.times(distanceBlocks))` |
| `retreatPoint` | `requirePositiveDistance(distanceBlocks)`; `Vec3 away = awayDirection(mobPosition, dangerPosition);` devuelve `mobPosition.plus(away.times(distanceBlocks))` |
| `predictedAimPoint` | `double flightTicks = shooterEye.distanceTo(aimPoint) / MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK;` devuelve `aimPoint.plus(movementPerTick.times(flightTicks))` |
| `leadShotVelocity` | `Vec3 delta = predictedAimPoint.minus(shooterEye);` `double horizontalDistance = delta.horizontal().length();` `Vec3 raised = new Vec3(delta.x(), delta.y() + horizontalDistance * MinecraftConstants.ARROW_ARC_FACTOR, delta.z());` devuelve `raised.normalized().times(MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK)` |

Métodos privados (uno por tarea):

| Método | Cuerpo |
| --- | --- |
| `private static int sideOf(Vec3 facing, Vec3 offset)` | `double determinant = facing.x() * offset.z() - facing.z() * offset.x();` devuelve `determinant >= 0 ? POSITIVE_SIDE : NEGATIVE_SIDE` |
| `private static Vec3 rotateAroundVertical(Vec3 direction, double degrees)` | `double radians = Math.toRadians(degrees);` `double cosine = Math.cos(radians);` `double sine = Math.sin(radians);` devuelve `new Vec3(direction.x() * cosine - direction.z() * sine, direction.y(), direction.x() * sine + direction.z() * cosine)` |
| `private static Vec3 awayDirection(Vec3 mobPosition, Vec3 dangerPosition)` | `Vec3 away = mobPosition.minus(dangerPosition).horizontal();` si `away.length() == 0`, devuelve `DEFAULT_RETREAT_DIRECTION`; si no, `away.normalized()` |
| `private static void requirePositiveDistance(double distanceBlocks)` | si `!(distanceBlocks > 0) \|\| !Double.isFinite(distanceBlocks)`: `IllegalArgumentException("CombatGeometry.distanceBlocks must be a positive number, got " + distanceBlocks)` |

`leadShotVelocity` con el punto predicho igual al ojo lanza la excepción de `Vec3.normalized()` (`cannot normalize a zero-length vector`); no la atrapes.

### 4. `domain.snapshot.SnapshotChecks`

Clase **package-private** (`final class SnapshotChecks`), con constructor privado. Mismos mensajes que `SettingsChecks`, que no se puede usar desde otro paquete:

```java
  static void requirePositive(String field, double value)
  // si !(value > 0) || !Double.isFinite(value):
  //   IllegalArgumentException(field + " must be a positive number, got " + value)

  static void requireNonNegative(String field, double value)
  // si !(value >= 0) || !Double.isFinite(value):
  //   IllegalArgumentException(field + " must be zero or positive, got " + value)

  static void requireBetween(String field, double value, double maximum)
  // si !(value >= 0 && value <= maximum):
  //   IllegalArgumentException(field + " must be between 0.0 and " + maximum + ", got " + value)

  static void requireAtLeast(String field, long value, long minimum)
  // si value < minimum:
  //   IllegalArgumentException(field + " must be at least " + minimum + ", got " + value)
```

### 5. `domain.snapshot.PlayerSnapshot`

```java
public record PlayerSnapshot(
    PlayerId id,
    PlayerPose pose,
    Vec3 movementPerTick,
    double health,
    double absorption,
    double maxHealth,
    double armorPoints,
    double armorToughness,
    int protectionFactor,
    Map<EffectKind, Integer> effectLevels,
    boolean blocking) {

  public int effectLevel(EffectKind kind) { ... }
}
```

Es un record de valor: está exento del límite de 3 parámetros.

| Componente | Significado |
| --- | --- |
| `pose` | Posición de los pies y dirección hacia la que mira |
| `movementPerTick` | Desplazamiento medido entre los dos últimos ticks |
| `health`, `maxHealth` | Vida y vida máxima, en puntos (20 = 10 corazones) |
| `absorption` | Puntos de absorción (corazones dorados) |
| `armorPoints`, `armorToughness` | Valores de los atributos de armadura y dureza |
| `protectionFactor` | Suma de los niveles de Protección del equipo puesto (full Protección IV = 16) |
| `effectLevels` | Nivel de cada efecto activo (nivel = amplificador + 1); los efectos ausentes no están en el mapa |
| `blocking` | `isBlocking()`: el escudo está activo (sirve para la intención, nunca para clasificar) |

**Validación en el constructor compacto**, en este orden:

| Condición que falla | Excepción y mensaje |
| --- | --- |
| `id`, `pose`, `movementPerTick` o `effectLevels` en `null` | `NullPointerException` con mensaje `"PlayerSnapshot.<componente>"` |
| `maxHealth` | `SnapshotChecks.requirePositive("PlayerSnapshot.maxHealth", maxHealth)` |
| `health` | `SnapshotChecks.requireBetween("PlayerSnapshot.health", health, maxHealth)` |
| `absorption` | `SnapshotChecks.requireNonNegative("PlayerSnapshot.absorption", absorption)` |
| `armorPoints` | `SnapshotChecks.requireNonNegative("PlayerSnapshot.armorPoints", armorPoints)` |
| `armorToughness` | `SnapshotChecks.requireNonNegative("PlayerSnapshot.armorToughness", armorToughness)` |
| `protectionFactor` | `SnapshotChecks.requireAtLeast("PlayerSnapshot.protectionFactor", protectionFactor, 0)` |
| Cada nivel de `effectLevels` | Nivel `null`: `NullPointerException("PlayerSnapshot.effectLevels." + kind)`. Nivel menor a 1: `SnapshotChecks.requireAtLeast("PlayerSnapshot.effectLevels." + kind, level, 1)` |

Después de validar: `effectLevels = copyEffectLevels(effectLevels);`.

Métodos privados:
- `private static void requireValidLevels(Map<EffectKind, Integer> effectLevels)`: recorre `effectLevels.entrySet()` con un `for` y aplica la última fila de la tabla.
- `private static Map<EffectKind, Integer> copyEffectLevels(Map<EffectKind, Integer> effectLevels)`: `EnumMap<EffectKind, Integer> copy = new EnumMap<>(EffectKind.class); copy.putAll(effectLevels); return Collections.unmodifiableMap(copy);`. **No uses `Map.copyOf`:** su orden de iteración cambia entre ejecuciones de la JVM, y las trazas tienen que salir iguales. Tampoco `new EnumMap<>(effectLevels)`: lanza una excepción si el mapa está vacío y no es un `EnumMap`.

`effectLevel(kind)` devuelve `effectLevels.getOrDefault(kind, 0)`.

### 6. `domain.snapshot.MobSnapshot`

```java
public record MobSnapshot(MobId id, MobKind kind, Vec3 position, double health, double maxHealth) { ... }
```

Validación, en este orden:
1. `id`, `kind`, `position` en `null`: `NullPointerException("MobSnapshot.<componente>")`.
2. `SnapshotChecks.requirePositive("MobSnapshot.maxHealth", maxHealth)`.
3. `SnapshotChecks.requireBetween("MobSnapshot.health", health, maxHealth)`.

### 7. `domain.snapshot.GroupSnapshot`

```java
public record GroupSnapshot(
    GroupId groupId, long tick, List<MobSnapshot> mobs, List<PlayerSnapshot> players) {

  public Optional<MobSnapshot> mob(MobId id) { ... }
  public Optional<PlayerSnapshot> player(PlayerId id) { ... }
}
```

`players` son los jugadores que el adaptador encontró dentro del radio de detección. Las dos listas pueden estar vacías.

Validación, en este orden:
1. `groupId`, `mobs`, `players` en `null`: `NullPointerException("GroupSnapshot.<componente>")`.
2. `SnapshotChecks.requireAtLeast("GroupSnapshot.tick", tick, 0)`.
3. `mobs = List.copyOf(mobs);` y `players = List.copyOf(players);` (conservan el orden y rechazan elementos `null`).
4. IDs repetidos: `requireDistinctMobIds(mobs)` y `requireDistinctPlayerIds(players)`. Cada uno arma un `HashSet` de los IDs con un `for` y, en el primer repetido, lanza `IllegalArgumentException("GroupSnapshot.mobs has a duplicate id " + id.value())` (o `"GroupSnapshot.players has a duplicate id " + id.value()`). Se usa el UUID completo, no el corto: el mensaje tiene que permitir encontrar la entidad.

`mob(id)` y `player(id)`: `stream().filter(...).findFirst()`.

### 8. `domain.threat.ThreatLedger`

```java
public final class ThreatLedger {
  public ThreatLedger(Supplier<TargetSettings> settings) { ... }

  public void recordDamage(PlayerId player, double damage, long tick) { ... }
  public double threatOf(PlayerId player, long currentTick) { ... }
  public void prune(long currentTick) { ... }
  public void forget(PlayerId player) { ... }
  public List<PlayerId> trackedPlayers() { ... }
  int entryCount() { ... }

  private record ThreatEntry(long tick, double damage) {}
}
```

Estado:
- `private final Supplier<TargetSettings> settings;` (`Objects.requireNonNull(settings, "ThreatLedger.settings")`)
- `private final Map<PlayerId, ArrayDeque<ThreatEntry>> entries = new LinkedHashMap<>();`: el orden es el del primer daño de cada jugador.
- `private long lastTick = 0;`

| Método | Comportamiento |
| --- | --- |
| `recordDamage` | Coordina, en este orden: `requireNonNull(player, "ThreatLedger.player")`; `requireValidDamage(damage)`; `requireValidTick(tick)`; `lastTick = tick;` agrega `new ThreatEntry(tick, damage)` al final de la cola del jugador (`computeIfAbsent(player, ignored -> new ArrayDeque<>())`); `dropExpired(queue, tick)`. Así la cola de cada jugador nunca guarda más golpes que los de la ventana |
| `threatOf` | Suma el `damage` de las entradas del jugador con `isActive(entry, currentTick)`. Jugador desconocido: `0` |
| `prune` | Para cada cola, `dropExpired(queue, currentTick)`; después `entries.values().removeIf(ArrayDeque::isEmpty)` |
| `forget` | `entries.remove(player)` (al morir o desconectarse el jugador) |
| `trackedPlayers` | `List.copyOf(entries.keySet())` |
| `entryCount` | Package-private, para las pruebas: suma de los tamaños de todas las colas |

Métodos privados:

| Método | Cuerpo |
| --- | --- |
| `private long windowTicks()` | `settings.get().threatWindowTicks()`; se lee en cada uso, nunca se guarda en un campo |
| `private boolean isActive(ThreatEntry entry, long currentTick)` | `currentTick - entry.tick() < windowTicks()` |
| `private void dropExpired(ArrayDeque<ThreatEntry> queue, long currentTick)` | `queue.removeIf(entry -> !isActive(entry, currentTick))`; sin bucles propios |
| `private static void requireValidDamage(double damage)` | si `!(damage > 0) \|\| !Double.isFinite(damage)`: `IllegalArgumentException("ThreatLedger.damage must be a positive number, got " + damage)` |
| `private void requireValidTick(long tick)` | si `tick < 0`: `IllegalArgumentException("ThreatLedger.tick must be zero or positive, got " + tick)`; si `tick < lastTick`: `IllegalArgumentException("ThreatLedger.tick must not go back, got " + tick + " after " + lastTick)` |

La regla de que el tick no retrocede es de todo el registro, no por jugador, y `forget` no la reinicia: los ticks del server solo avanzan, y un tick que retrocede es un bug del adaptador.

### 9. Builders de prueba (`testsupport`)

Solo de pruebas. Un método `withX(valor)` por componente, que devuelve `this`, y `build()`. Cada valor fijo es una constante privada con nombre (por ejemplo `private static final double FULL_HEALTH = 20.0;`).

**`PlayerSnapshotBuilder`**: un jugador quieto, sin armadura, mirando hacia +z.

| Campo | Valor inicial |
| --- | --- |
| `id` | `new PlayerId(new UUID(0, 2))` (el mismo objetivo que `AttackFactsBuilder`) |
| `position` | `new Vec3(0, 64, 0)` |
| `facing` | `new Vec3(0, 0, 1)` |
| `movementPerTick` | `Vec3.ZERO` |
| `health`, `maxHealth` | `20.0`, `20.0` |
| `absorption`, `armorPoints`, `armorToughness` | `0.0` |
| `protectionFactor` | `0` |
| `effectLevels` | un `EnumMap` vacío, guardado en un campo |
| `blocking` | `false` |

- `withPosition(Vec3)` y `withFacing(Vec3)` cambian los campos sueltos; `build()` arma el `PlayerPose`.
- `withEffect(EffectKind kind, int level)` hace `effectLevels.put(kind, level)`.
- `fullDiamondProtectionFour()`: `armorPoints = 20.0`, `armorToughness = 8.0`, `protectionFactor = 16`.
- `build()` le pasa al constructor **el mismo `EnumMap` del builder, sin copiarlo**: así la prueba `effectLevelsCannotBeChangedFromOutside` verifica la copia del record.

**`MobSnapshotBuilder`**: un zombie con vida completa.

| Campo | Valor inicial |
| --- | --- |
| `id` | `new MobId(new UUID(0, 1))` (el mismo mob que `AttackFactsBuilder`) |
| `kind` | `MobKind.ZOMBIE` |
| `position` | `new Vec3(0, 64, 5)` |
| `health`, `maxHealth` | `20.0`, `20.0` |

**`GroupSnapshotBuilder`**

| Campo | Valor inicial |
| --- | --- |
| `groupId` | `new GroupId(new UUID(0, 3))` |
| `tick` | `1000` |
| `mobs`, `players` | `ArrayList` vacías |

- `withGroupId(GroupId)`, `withTick(long)`, `withMob(MobSnapshot)` y `withPlayer(PlayerSnapshot)` (agregan al final).
- `withZombies(int count)`, `withSkeletons(int count)` y `withSpiders(int count)` agregan `count` mobs de ese tipo con `addGeneratedMob(MobKind kind, double maxHealth)`. Vida máxima: 20 los zombies y los esqueletos, 16 las arañas.
- `private void addGeneratedMob(MobKind kind, double maxHealth)`: incrementa un contador `generatedMobs` (arranca en 0) y agrega `new MobSnapshot(new MobId(new UUID(1, generatedMobs)), kind, new Vec3(generatedMobs, 64, 5), maxHealth, maxHealth)`. El contador es compartido entre tipos: los IDs nunca se repiten.
- `build()` devuelve `new GroupSnapshot(groupId, tick, mobs, players)`.

## Pruebas obligatorias

Tolerancia de los `double`: `within(1e-9)`. Para comparar un `Vec3`, compará sus tres componentes con esa tolerancia, con un método privado de la clase de prueba: `assertVec(Vec3 actual, double x, double y, double z)`.

**`PlayerPoseTest`**

| Prueba | Verificación |
| --- | --- |
| `facingIsFlattenedAndNormalized` | `new PlayerPose(Vec3.ZERO, new Vec3(3, 5, 4)).facing()` es (0,6; 0; 0,8) |
| `rejectsVerticalFacing` | `new PlayerPose(Vec3.ZERO, new Vec3(0, -1, 0))` lanza `IllegalArgumentException` con mensaje `PlayerPose.facing must have a horizontal component, got Vec3[x=0.0, y=-1.0, z=0.0]` |

**`CombatGeometryTest`.** Salvo que se indique otra cosa, `pose = new PlayerPose(Vec3.ZERO, new Vec3(0, 0, 1))` y un atacante a θ grados está en `new Vec3(4 * Math.sin(Math.toRadians(θ)), 0, 4 * Math.cos(Math.toRadians(θ)))`.

| Prueba | Verificación |
| --- | --- |
| `attackersWithinNinetyDegreesAreInsideTheShieldArc` | `@ParameterizedTest` con `@ValueSource(doubles = {8, 10, 43, 82})`: `isInShieldArc` es `true` |
| `attackersBeyondNinetyDegreesAreOutsideTheShieldArc` | `@ValueSource(doubles = {98, 107, 111, 180})`: `isInShieldArc` es `false` |
| `attackerExactlyAtNinetyDegreesIsInsideTheShieldArc` | Atacante en `(4, 0, 0)`: `true` |
| `attackerDirectlyAboveCountsAsInFront` | Atacante en `(0, 3, 0)`: `angleFromFacingDegrees` es 0 e `isInShieldArc` es `true` |
| `angleFromFacingUsesThePlayerPosition` | `pose = new PlayerPose(new Vec3(10, 64, -5), new Vec3(1, 0, 0))`: el punto `(10, 64, -1)` da 90 y el punto `(6, 64, -5)` da 180 |
| `flankPointGoesBehindOnTheMobsSide` | Distancia 3. Mob en `(2, 0, 0)`: punto (2,121320343559643; 0; −2,1213203435596424). Mob en `(−2, 0, 0)`: punto (−2,121320343559643; 0; −2,1213203435596424) |
| `mobInLineWithTheFacingFlanksToThePositiveSide` | Distancia 3, mob en `(0, 0, 5)`: punto (−2,121320343559643; 0; −2,1213203435596424) |
| `flankPointUsesThePlayerPositionAndHeight` | `pose = new PlayerPose(new Vec3(10, 64, -5), new Vec3(1, 0, 0))`, mob en `(12, 70, -1)`, distancia 3: punto (7,878679656440358; 64; −2,878679656440357) |
| `flankPointIsOutsideTheShieldArc` | Con el caso anterior: `angleFromFacingDegrees(pose, punto)` es 135 e `isInShieldArc(pose, punto)` es `false` |
| `rejectsNonPositiveFlankDistance` | `flankPoint(pose, new Vec3(2, 0, 0), 0)` lanza `IllegalArgumentException` con mensaje `CombatGeometry.distanceBlocks must be a positive number, got 0.0` |
| `retreatPointMovesAwayFromTheDanger` | `retreatPoint(new Vec3(3, 64, 4), new Vec3(0, 60, 0), 16)` es (12,6; 64; 16,8) |
| `retreatPointWithoutHorizontalSeparationGoesTowardsPositiveX` | `retreatPoint(new Vec3(5, 64, 5), new Vec3(5, 70, 5), 16)` es (21; 64; 5) |
| `predictedAimPointAddsMovementDuringFlight` | `predictedAimPoint(Vec3.ZERO, new Vec3(12, 0, 16), new Vec3(0.2, 0, -0.1))` es (14,5; 0; 14,75) (20 bloques ÷ 1,6 = 12,5 ticks de vuelo) |
| `stationaryTargetIsAimedAtDirectly` | `predictedAimPoint(Vec3.ZERO, new Vec3(12, 0, 16), Vec3.ZERO)` es (12; 0; 16) |
| `leadShotVelocityRaisesTheAimAndKeepsArrowSpeed` | `leadShotVelocity(Vec3.ZERO, new Vec3(3, 0, 4))` es (0,9413574486632834; 0,31378581622109447; 1,2551432648843779) y su `length()` es 1,6 |
| `leadShotVelocityUsesTheShooterEye` | `leadShotVelocity(new Vec3(1, 65.5, 2), new Vec3(13, 64, -7))` es (1,2736476034687862; 0,15920595043359828; −0,9552357026015896) |

**`PlayerSnapshotTest`**

| Prueba | Verificación |
| --- | --- |
| `defaultBuilderSnapshotIsValid` | `new PlayerSnapshotBuilder().build()` no lanza nada |
| `rejectsZeroMaxHealth` | `withHealth(0).withMaxHealth(0)`: mensaje `PlayerSnapshot.maxHealth must be a positive number, got 0.0` |
| `rejectsHealthAboveMaxHealth` | `withHealth(25)`: mensaje `PlayerSnapshot.health must be between 0.0 and 20.0, got 25.0` |
| `rejectsNegativeAbsorption` | `withAbsorption(-1)`: mensaje `PlayerSnapshot.absorption must be zero or positive, got -1.0` |
| `rejectsNegativeProtectionFactor` | `withProtectionFactor(-1)`: mensaje `PlayerSnapshot.protectionFactor must be at least 0, got -1` |
| `rejectsEffectLevelBelowOne` | `withEffect(EffectKind.POISON, 0)`: mensaje `PlayerSnapshot.effectLevels.POISON must be at least 1, got 0` |
| `effectLevelReturnsTheLevelOrZero` | `withEffect(EffectKind.WEAKNESS, 2)`: `effectLevel(WEAKNESS)` es 2 y `effectLevel(POISON)` es 0 |
| `effectLevelsCannotBeChangedFromOutside` | `builder.withEffect(POISON, 1)`, `build()`, después `builder.withEffect(WITHER, 2)`: el snapshot ya armado tiene solo `POISON`. Además, `snapshot.effectLevels().put(WITHER, 1)` lanza `UnsupportedOperationException` |
| `effectLevelsIterateInEnumOrder` | `withEffect(WEAKNESS, 1).withEffect(RESISTANCE, 2)`: `effectLevels().keySet()` es `[RESISTANCE, WEAKNESS]` en ese orden (`containsExactly`) |
| `fullDiamondPresetHasProtectionFour` | `fullDiamondProtectionFour().build()`: armadura 20, dureza 8, protección 16 |

**`MobSnapshotTest`**

| Prueba | Verificación |
| --- | --- |
| `defaultBuilderSnapshotIsValid` | `new MobSnapshotBuilder().build()` no lanza nada |
| `rejectsHealthAboveMaxHealth` | `withHealth(21)`: mensaje `MobSnapshot.health must be between 0.0 and 20.0, got 21.0` |
| `rejectsNullKind` | `withKind(null)`: `NullPointerException` con mensaje `MobSnapshot.kind` |

**`GroupSnapshotTest`**

| Prueba | Verificación |
| --- | --- |
| `builderGeneratesMobsWithDistinctIds` | `withZombies(2).withSkeletons(1).withSpiders(1)`: 4 mobs; tipos `[ZOMBIE, ZOMBIE, SKELETON, SPIDER]` en orden; 4 IDs distintos; la araña tiene vida máxima 16 |
| `rejectsDuplicateMobIds` | Dos veces `withMob(new MobSnapshotBuilder().build())`: mensaje `GroupSnapshot.mobs has a duplicate id 00000000-0000-0000-0000-000000000001` |
| `rejectsDuplicatePlayerIds` | Dos veces `withPlayer(new PlayerSnapshotBuilder().build())`: mensaje `GroupSnapshot.players has a duplicate id 00000000-0000-0000-0000-000000000002` |
| `rejectsNegativeTick` | `withTick(-1)`: mensaje `GroupSnapshot.tick must be at least 0, got -1` |
| `findsMobsAndPlayersById` | Con un jugador y `withZombies(1)`: `player(idDelJugador)` y `mob(idDelZombie)` están presentes y son los mismos objetos (`isSameAs`) |
| `unknownIdsAreEmpty` | `player(new PlayerId(new UUID(9, 9)))` y `mob(new MobId(new UUID(9, 9)))` están vacíos |
| `listsCannotBeChangedFromOutside` | `snapshot.mobs().add(...)` lanza `UnsupportedOperationException`; agregar un mob al builder después de `build()` no cambia el snapshot ya armado |

**`ThreatLedgerTest`.** `settings` es un `AtomicReference<TargetSettings>` con `TestSettings.defaults().target()` (ventana de 600), y el registro se arma con `new ThreatLedger(settings::get)`. `ALICE = new PlayerId(new UUID(0, 10))`, `BOB = new PlayerId(new UUID(0, 11))`.

| Prueba | Verificación |
| --- | --- |
| `unknownPlayerHasNoThreat` | `threatOf(ALICE, 100)` es 0 |
| `damageAddsUpWithinTheWindow` | ALICE 4,0 en el tick 100 y 2,5 en el 400: `threatOf(ALICE, 699)` es 6,5 |
| `damageLeavesTheWindowAfterExactlyWindowTicks` | Mismos golpes: en el tick 699 es 6,5; en el 700, 2,5; en el 999, 2,5; en el 1000, 0 |
| `threatIsKeptPerPlayer` | ALICE 4,0 en el tick 100 y BOB 1,5 en el 200: en el tick 300, ALICE 4,0 y BOB 1,5 |
| `usesTheCurrentWindowFromSettings` | ALICE 4,0 en el tick 100. `settings.set(new TargetSettings(200, 0.2, 1.0, 3.0, 0.5))`. `threatOf(ALICE, 350)` es 0 |
| `recordingDamageDropsExpiredEntries` | ALICE 1,0 en el tick 0 y 1,0 en el 600: `entryCount()` es 1 |
| `pruneRemovesExpiredEntriesAndEmptyPlayers` | ALICE 1,0 en el tick 0, BOB 1,0 en el 500. `prune(600)`: `trackedPlayers()` es `[BOB]` y `entryCount()` es 1 |
| `forgetRemovesThePlayer` | ALICE 4,0 en el tick 100. `forget(ALICE)`: `threatOf(ALICE, 200)` es 0 y `trackedPlayers()` está vacío |
| `trackedPlayersKeepFirstDamageOrder` | BOB en el tick 10, ALICE en el 20, BOB en el 30: `trackedPlayers()` es `[BOB, ALICE]` en ese orden |
| `rejectsNonPositiveDamage` | `recordDamage(ALICE, 0, 10)`: mensaje `ThreatLedger.damage must be a positive number, got 0.0` |
| `rejectsNegativeTick` | `recordDamage(ALICE, 1, -1)`: mensaje `ThreatLedger.tick must be zero or positive, got -1` |
| `rejectsTicksThatGoBack` | ALICE en el tick 100 y BOB en el 99: mensaje `ThreatLedger.tick must not go back, got 99 after 100` |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `isInShieldArc`, `<` en vez de `<=` | `attackerExactlyAtNinetyDegreesIsInsideTheShieldArc` |
| En `sideOf`, invertir los lados (`determinant >= 0 ? NEGATIVE_SIDE : POSITIVE_SIDE`) | `flankPointGoesBehindOnTheMobsSide` |
| En `isActive`, `<=` en vez de `<` | `damageLeavesTheWindowAfterExactlyWindowTicks` |
| En el constructor de `ThreatLedger`, guardar `settings.get().threatWindowTicks()` en un campo y usarlo en `windowTicks()` | `usesTheCurrentWindowFromSettings` |
| En `PlayerSnapshot`, quitar la línea `effectLevels = copyEffectLevels(effectLevels);` | `effectLevelsCannotBeChangedFromOutside` |

## Procedimiento

1. Rama `wp-06-fotos-amenaza-geometria` desde `main`.
2. `MinecraftConstants`, `PlayerPose`, `CombatGeometry`, `PlayerPoseTest` y `CombatGeometryTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add combat geometry for shield arc, flanking, retreat and lead shots`.
3. `SnapshotChecks`, las tres fotos, los tres builders y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add player, mob and group snapshots with test builders`.
4. `ThreatLedger` y `ThreatLedgerTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add threat ledger with a sliding damage window`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-06: snapshots, threat ledger and combat geometry`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si un valor esperado de geometría difiere en la última cifra decimal, la tolerancia `within(1e-9)` ya lo cubre; no cambies los valores esperados. Si difiere en más de 1e-9, frená y reportá.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Armar las fotos desde Paper, medir el movimiento por tick y descartar a los jugadores en creativo o espectador (`SnapshotFactory` y `MovementTracker`, WP-17).
- El piso de amenaza, la debilidad y el tiempo para matarlo (WP-07).
- Quién guarda el `ThreatLedger` y cuándo se llama a `prune` y `forget` (WP-08 y WP-13).
- Usar la geometría en los goals (WP-22 a WP-24).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite (`ThreatLedger` usa `removeIf`, sin `while`).
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 5 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
