# WP-17 — Traductor de versión y fotos

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | Puertas E1 (pasada) y E4 |
| Modelo | Sonnet |
| Rama | `wp-17-traductor-y-fotos` |

## Objetivo

1. **`VersionTranslator`**: la única clase que toca constantes de Paper que cambian entre versiones (`EntityType`, `PotionEffectType`, `Attribute`, `Enchantment` y los modificadores de daño). Todo lo que necesitan los WPs de adaptadores pasa por acá.
2. **`MovementTracker`**: el movimiento real de cada jugador por tick. El spike mostró que `getVelocity()` da 0 al caminar (`hallazgos-api.md`, sección 5).
3. **`SnapshotFactory`**: arma la foto de un grupo (`GroupSnapshot`) con sus mobs cargados y los jugadores cercanos.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 3, 4, 5 y 6)
- `src/main/java/io/github/nicodoou/mobai/domain/snapshot/PlayerSnapshot.java`, `MobSnapshot.java`, `GroupSnapshot.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/PlayerPose.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Vec3.java`, `EffectKind.java`, `MobKind.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `Member.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/GroupSettings.java`
- `src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java` (la regla de `VersionTranslator`)

## Reglas de negocio

1. **Tipos de mob:** `ZOMBIE`, `SKELETON` y `SPIDER` de `EntityType` corresponden a los de `MobKind`; cualquier otro tipo no es un mob del plugin.
2. **Efectos:** el nivel de un efecto es `amplifier + 1` (Fuerza I tiene amplifier 0). Solo cuentan los de `EffectKind`; si un efecto aparece dos veces, queda el nivel más alto.
3. **Vida máxima** con el atributo `MAX_HEALTH`; una entidad viva sin ese atributo es un estado imposible (`IllegalStateException`). **Armadura y dureza** con `ARMOR` y `ARMOR_TOUGHNESS` (0 si falta el atributo). **Factor de protección:** la suma de los niveles de `Enchantment.PROTECTION` en las 4 piezas de armadura (en vanilla, cada nivel de Protección suma 1 al factor; el dominio aplica el tope de 20).
4. **Daño absorbido y bloqueo** (hallazgos 3 y 4): absorbido = `-getDamage(ABSORPTION)` si el modificador aplica, si no 0; bloqueado = el modificador `BLOCKING` aplica y es distinto de 0. Esa API está deprecada sin reemplazo: se lee en dos métodos de `VersionTranslator`, con `@SuppressWarnings("deprecation")` y el motivo comentado.
5. **Movimiento por tick:** diferencia de posición dividida por los ticks entre muestras. La primera muestra da movimiento 0. Si el salto supera 10 bloques por tick (el límite de «se movió demasiado rápido» de Minecraft), es un teleport: el movimiento queda en 0 en vez de un valor absurdo que arruinaría el tiro anticipado. Dos muestras en el mismo tick no cambian el movimiento.
6. **Mirada del jugador:** sale del `yaw`, no de `getDirection()`. Mirando derecho arriba o abajo, `getDirection()` no tiene componente horizontal y `PlayerPose` lo rechaza; el `yaw` siempre la tiene. Fórmula de Minecraft: `(-sin(yaw), 0, cos(yaw))` con `yaw` en radianes (yaw 0 mira a +Z).
7. **Vida en la foto** acotada a `[0, vida máxima]`: el server puede reportar por un tick una vida mayor a la máxima (por ejemplo, al cambiar el atributo), y la foto la rechazaría.
8. **La foto de un grupo:**
   - **Mobs:** los miembros, en orden de ingreso, cuya entidad está cargada, es un `Mob` y está viva (`isValid()` y no `isDead()`). Si no hay ninguno, no hay foto (`Optional.empty()`): el grupo está entero en chunks descargados.
   - **Jugadores:** los que están en el mismo mundo que algún mob cargado, a `GroupSettings.detectionRadiusBlocks` o menos de ese mob, vivos y en modo `SURVIVAL` o `ADVENTURE` (los mobs vanilla tampoco atacan en creativo ni espectador). Sin repetidos y ordenados por UUID, para que la foto sea determinista.
   - Cada jugador lleva su movimiento de `MovementTracker`, absorción (`getAbsorptionAmount()`) y `isBlocking()` (la intención de bloquear; la clasificación usa el modificador, regla 4).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/MovementTracker.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/EntityReadings.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslatorTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/snapshot/MovementTrackerTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/snapshot/EntityReadingsTest.java` |

**Sin dependencias nuevas.** Sin Mockito ni MockBukkit (D20): `VersionTranslator` y `SnapshotFactory` se verifican en el server, en la puerta E5. Lo que se puede probar sin server (movimiento, mirada, vida acotada y el mapeo de tipos de mob) se prueba en JUnit.

## Especificación

Imports a tu criterio; Spotless decide el formato. Los nombres de las constantes de Paper están verificados con `javap` contra `paper-api 26.3.build.151-beta`: `Attribute.MAX_HEALTH`, `Attribute.ARMOR`, `Attribute.ARMOR_TOUGHNESS`, `Enchantment.PROTECTION`, `PotionEffectType.RESISTANCE`, `REGENERATION`, `POISON`, `WITHER`, `WEAKNESS`, `SLOWNESS`, `EntityDamageEvent.DamageModifier.ABSORPTION` y `BLOCKING`, `EntityType.ZOMBIE`, `SKELETON` y `SPIDER`.

### `VersionTranslator.java` (`adapter.translate`)

```java
/** The only class that reads Paper constants that change between Minecraft versions. */
public final class VersionTranslator {
  public Optional<MobKind> mobKindOf(EntityType type) {
    return switch (type) {
      case ZOMBIE -> Optional.of(MobKind.ZOMBIE);
      case SKELETON -> Optional.of(MobKind.SKELETON);
      case SPIDER -> Optional.of(MobKind.SPIDER);
      default -> Optional.empty();
    };
  }

  public EntityType entityTypeOf(MobKind kind) {
    return switch (kind) {
      case ZOMBIE -> EntityType.ZOMBIE;
      case SKELETON -> EntityType.SKELETON;
      case SPIDER -> EntityType.SPIDER;
    };
  }

  public PotionEffectType potionEffectOf(EffectKind kind) {
    return switch (kind) {
      case RESISTANCE -> PotionEffectType.RESISTANCE;
      case REGENERATION -> PotionEffectType.REGENERATION;
      case POISON -> PotionEffectType.POISON;
      case WITHER -> PotionEffectType.WITHER;
      case WEAKNESS -> PotionEffectType.WEAKNESS;
      case SLOWNESS -> PotionEffectType.SLOWNESS;
    };
  }

  public Map<EffectKind, Integer> effectLevels(LivingEntity entity) { … regla 2 … }

  public double maxHealth(LivingEntity entity) { … regla 3 … }

  public double armorPoints(LivingEntity entity) { … }

  public double armorToughness(LivingEntity entity) { … }

  public int protectionFactor(LivingEntity entity) { … }

  // Paper deprecated damage modifiers without a replacement; the spike showed they are the only
  // way to see damage eaten by absorption hearts.
  @SuppressWarnings("deprecation")
  public double absorbedDamage(EntityDamageEvent event) { … }

  // Same deprecated API: a blocked hit is not cancelled, only this modifier tells it apart.
  @SuppressWarnings("deprecation")
  public boolean wasBlocked(EntityDamageEvent event) { … }

  private Optional<EffectKind> effectKindOf(PotionEffectType type) { … }

  private static OptionalDouble attributeValue(LivingEntity entity, Attribute attribute) { … }
}
```

- `effectKindOf`: recorre `EffectKind.values()` y devuelve el primero cuyo `potionEffectOf(kind).equals(type)`. (`PotionEffectType` no es un enum: no se puede usar en un `switch`).
- `effectLevels`: recorre `entity.getActivePotionEffects()` y, para cada efecto con `EffectKind`, `merge(kind, amplifier + 1, Math::max)` en un `EnumMap`.
- `maxHealth`: `attributeValue(entity, Attribute.MAX_HEALTH).orElseThrow(() -> new IllegalStateException("Entity " + entity.getUniqueId() + " has no max health attribute"))`.
- `armorPoints` y `armorToughness`: `attributeValue(...).orElse(0)`.
- `protectionFactor`: si `getEquipment()` es `null`, 0; si no, suma `item.getEnchantmentLevel(Enchantment.PROTECTION)` de cada pieza no nula de `getArmorContents()`.
- `attributeValue`: `entity.getAttribute(attribute)`; `null` → vacío; si no, `getValue()`.
- `absorbedDamage`: `event.isApplicable(ABSORPTION) ? -event.getDamage(ABSORPTION) : 0`.
- `wasBlocked`: `event.isApplicable(BLOCKING) && event.getDamage(BLOCKING) != 0`.

Si `-Xlint:all -Werror` marca otra advertencia de deprecación en estos dos métodos (por ejemplo `removal`), agregala al mismo `@SuppressWarnings` y avisalo.

### `MovementTracker.java` (`adapter.snapshot`)

```java
/** Real player movement per tick; the server's velocity reads zero while a player walks. */
public final class MovementTracker {
  // Minecraft flags a player who moves more than this in one tick ("moved too quickly"):
  // such a jump is a teleport, not movement a skeleton should lead.
  private static final double MOVED_TOO_QUICKLY_BLOCKS_PER_TICK = 10.0;

  private final Map<PlayerId, Sample> lastSamples = new HashMap<>();
  private final Map<PlayerId, Vec3> movements = new HashMap<>();

  public void sample(PlayerId player, Vec3 position, long tick) { … regla 5 … }

  public Vec3 movementPerTick(PlayerId player) {
    return movements.getOrDefault(player, Vec3.ZERO);
  }

  public void forget(PlayerId player) {
    lastSamples.remove(player);
    movements.remove(player);
  }

  private record Sample(Vec3 position, long tick) {}
}
```

`sample`, en orden:
1. Si hay muestra anterior y `tick <= anterior.tick`: no hace nada.
2. Si hay muestra anterior: `movimiento = (position − anterior.position) × (1 / (tick − anterior.tick))`; si su `length()` supera `MOVED_TOO_QUICKLY_BLOCKS_PER_TICK`, el movimiento es `Vec3.ZERO`. Lo guarda.
3. Guarda la muestra nueva.

Partilo en funciones chicas (por ejemplo `movementSince(Sample, Vec3, long)`).

### `EntityReadings.java` (`adapter.snapshot`, package-private)

```java
final class EntityReadings {
  private EntityReadings() {}

  // Minecraft's yaw: 0 faces +Z (south) and grows clockwise seen from above.
  static Vec3 facingFromYaw(float yawDegrees) {
    double radians = Math.toRadians(yawDegrees);
    return new Vec3(-Math.sin(radians), 0, Math.cos(radians));
  }

  // The server can report health above the maximum for a tick, which a snapshot would reject.
  static double clampHealth(double health, double maxHealth) {
    return Math.clamp(health, 0, maxHealth);
  }
}
```

### `SnapshotFactory.java` (`adapter.snapshot`)

```java
public final class SnapshotFactory {
  private final VersionTranslator translator;
  private final MovementTracker movement;
  private final Supplier<GroupSettings> settings;

  public SnapshotFactory(
      VersionTranslator translator, MovementTracker movement, Supplier<GroupSettings> settings) { … }

  public Optional<GroupSnapshot> snapshotOf(Group group, long tick) { … regla 8 … }
}
```

Funciones privadas sugeridas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `List<LoadedMob> loadedMobs(Group group)` | Miembros en orden de ingreso con su entidad cargada y viva (`Bukkit.getEntity(member.id().value())`) |
| `MobSnapshot mobSnapshot(LoadedMob mob)` | Id y tipo del miembro, posición, vida acotada y vida máxima |
| `List<Player> nearbyPlayers(List<LoadedMob> mobs)` | Regla 8, jugadores; recorre `mob.getWorld().getPlayers()` de cada mob |
| `boolean canBeTargeted(Player player)` | Vivo y en `SURVIVAL` o `ADVENTURE` |
| `boolean isWithinReach(Player player, Mob mob, double radius)` | Mismo mundo y `distanceSquared <= radius * radius` |
| `PlayerSnapshot playerSnapshot(Player player)` | Todos los campos de `PlayerSnapshot` |
| `static Vec3 position(Location location)` | `new Vec3(getX(), getY(), getZ())` |

`LoadedMob` es un record privado `(Member member, Mob entity)`.

## Pruebas obligatorias

### `VersionTranslatorTest` (2)

| Prueba | Verifica |
| --- | --- |
| `mobKindsRoundTripThroughEntityTypes` | para cada `MobKind`, `mobKindOf(entityTypeOf(kind))` = `Optional.of(kind)` |
| `otherEntityTypesAreNotMobKinds` | `mobKindOf(EntityType.CREEPER)` vacío |

### `MovementTrackerTest` (6)

| Prueba | Verifica |
| --- | --- |
| `firstSampleHasNoMovement` | una muestra: `Vec3.ZERO` |
| `movementIsTheStepBetweenConsecutiveTicks` | `(0, 64, 0)` en el tick 10 y `(0.2, 64, -0.1)` en el 11: `(0.2, 0, -0.1)` con `within(1e-9)` por componente |
| `movementIsDividedByTheTicksBetweenSamples` | `(0, 64, 0)` en el tick 10 y `(0.6, 64, 0)` en el 13: `x` = 0.2 |
| `teleportIsNotMovement` | `(0, 64, 0)` en el tick 10 y `(100, 64, 0)` en el 11: `Vec3.ZERO` |
| `sameTickSampleKeepsThePreviousMovement` | muestras en los ticks 10 y 11 (movimiento 0.2 en `x`), y otra en el 11 en `(5, 64, 0)`: sigue 0.2 |
| `forgetDropsThePlayer` | después de dos muestras y `forget`: `Vec3.ZERO`, y una muestra nueva vuelve a ser «primera» (`Vec3.ZERO`) |

### `EntityReadingsTest` (5)

| Prueba | Verifica (tolerancia `1e-9` por componente) |
| --- | --- |
| `yawZeroFacesSouth` | `facingFromYaw(0)` = `(0, 0, 1)` |
| `yawNinetyFacesWest` | `facingFromYaw(90)` = `(-1, 0, 0)` |
| `yawMinusNinetyFacesEast` | `facingFromYaw(-90)` = `(1, 0, 0)` |
| `yawOneEightyFacesNorth` | `facingFromYaw(180)` = `(0, 0, -1)` |
| `healthIsClampedToTheValidRange` | `clampHealth(21, 20)` = 20, `clampHealth(-1, 20)` = 0 y `clampHealth(7.5, 20)` = 7.5 |

Total: **13 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `facingFromYaw`, `Math.sin` sin el signo menos | yaw 90 da `(1, 0, 0)` | `yawNinetyFacesWest` |
| 2 | En `sample`, no dividir por los ticks entre muestras | `x` = 0.6 | `movementIsDividedByTheTicksBetweenSamples` |
| 3 | En `sample`, sacar el límite de teleport | `x` = 100 | `teleportIsNotMovement` |
| 4 | En `clampHealth`, devolver `health` | 21 | `healthIsClampedToTheValidRange` |

## Procedimiento

1. Rama `wp-17-traductor-y-fotos` desde `origin/main` actualizado.
2. `VersionTranslator` con su prueba. Commit: `feat: add version translator for paper constants`.
3. `MovementTracker` y `EntityReadings` con sus pruebas. Commit: `feat: track player movement per tick`.
4. `SnapshotFactory`. Commit: `feat: build group snapshots from loaded entities`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde (la cobertura mínima es solo del dominio).
7. Push, PR `WP-17: version translator and group snapshots`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Otra advertencia de deprecación en `absorbedDamage` o `wasBlocked` (ver arriba).
3. Si `EntityType` no se puede inicializar en JUnit sin server (error de inicialización de clase al correr `VersionTranslatorTest`), borrá `VersionTranslatorTest` del todo, sacala de la tabla de aceptación y avisalo con el error exacto. Esas dos verificaciones pasan a la puerta E5.

## Fuera de alcance

- Agendar `sample` cada tick para los jugadores conectados y `forget` al desconectarse (WP-20).
- Usar la foto (`DecisionScheduler`, WP-20) y los modificadores de daño (rastreador, WP-18).
- Spawnear mobs por tipo (WP-21) y efectos de la araña (WP-25): usan los métodos de este WP.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Solo `VersionTranslator` usa `EntityType`, `PotionEffectType`, `Attribute` y `Enchantment` (lo verifica `ArchitectureTest`).
- [ ] Las 13 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build y CI en verde.
