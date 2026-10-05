# WP-09 — Estrategias

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-08 |
| Modelo | Sonnet |
| Rama | `wp-09-estrategias` |

## Objetivo

Implementar las tres estrategias de grupo del catálogo: cuándo es viable cada una y qué rol le toca a cada mob. Además, el catálogo que las agrupa. El cerebro (WP-10) va a filtrar las viables, elegir una con la política de selección y pedirle los roles.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/StrategyId.java`, `MobId.java`, `PlayerId.java`, `MobKind.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/group/Role.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `MobSnapshot.java`, `PlayerSnapshot.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` (solo la firma de `angleFromFacingDegrees`), `PlayerPose.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/GroupSnapshotBuilder.java`, `MobSnapshotBuilder.java`, `PlayerSnapshotBuilder.java`

## Reglas de negocio

| Estrategia (`StrategyId`) | Viable si | Roles |
| --- | --- | --- |
| `DIRECT_ASSAULT` | Siempre; es la estrategia por defecto | Zombies y arañas: `PRESS`; esqueletos: `SHOOT` |
| `FLANK` | Al menos 3 mobs cuerpo a cuerpo (zombies + arañas) | La mitad de los cuerpo a cuerpo, redondeada para abajo, `FLANK`; el resto, `PRESS`; esqueletos: `SHOOT` |
| `PIN_AND_SHOOT` | Al menos 2 zombies y al menos 2 esqueletos | Zombies: `PRESS`; esqueletos: `SHOOT`; arañas: `FLANK` |

- **Composición:** se cuentan los mobs de la foto (`snapshot.mobs()`); los miembros que no están en la foto (chunk descargado) no cuentan ni reciben rol.
- **Quiénes flanquean en `FLANK`** (D8: arañas primero). Se elige a los cuerpo a cuerpo más «de costado»: el orden de candidatos es primero las arañas, después los zombies; dentro de cada tipo, de mayor a menor ángulo respecto del frente del objetivo (`CombatGeometry.angleFromFacingDegrees` con la pose del objetivo). Los primeros `mitad` de esa lista flanquean. Así, un zombie que ya está detrás del jugador flanquea y uno que está de frente presiona.
  - Si el objetivo no está en la foto, no hay ángulo: el orden dentro de cada tipo es el de la foto.
  - Empate de ángulo: el orden de la foto (el ordenamiento de Java es estable).
- **Mapa de roles:** un rol para **cada** mob de la foto, en el orden de la foto. El cambio a `RETREAT` por vida baja lo hace el cerebro después (D8), no la estrategia.
- **Requisito en palabras:** cada estrategia describe su requisito en un texto fijo, en inglés, para la traza de las estrategias descartadas.
- Los números de los requisitos (3 cuerpo a cuerpo, 2 zombies, 2 esqueletos) son la definición de cada estrategia en el catálogo, no valores de balance: van como constantes con nombre en su clase.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/strategy/GroupStrategy.java`, `GroupComposition.java` |
| Crear | `domain/strategy/DirectAssaultStrategy.java`, `FlankStrategy.java`, `PinAndShootStrategy.java` |
| Crear | `domain/strategy/StrategyCatalog.java` |
| Crear (prueba) | `domain/strategy/GroupCompositionTest.java`, `DirectAssaultStrategyTest.java`, `FlankStrategyTest.java`, `PinAndShootStrategyTest.java`, `StrategyCatalogTest.java` |

## Especificación

### 1. `GroupStrategy`

```java
public interface GroupStrategy {
  StrategyId id();

  /** The minimum composition, in words, for the trace of discarded strategies. */
  String requirement();

  boolean isViable(GroupSnapshot snapshot);

  /** One role for every mob in the snapshot, in snapshot order. */
  Map<MobId, Role> assignRoles(GroupSnapshot snapshot, PlayerId target);
}
```

### 2. `GroupComposition`

```java
public record GroupComposition(int zombies, int skeletons, int spiders) {
  public static GroupComposition of(GroupSnapshot snapshot) { ... }
  public int melee() { ... }
}
```

- Constructor compacto: si algún conteo es negativo, `IllegalArgumentException("GroupComposition counts must be zero or positive, got " + zombies + "/" + skeletons + "/" + spiders)`.
- `of`: cuenta con `count(snapshot, kind)`, un `private static int count(GroupSnapshot snapshot, MobKind kind)` que hace `(int) snapshot.mobs().stream().filter(mob -> mob.kind() == kind).count()`.
- `melee()`: `zombies + spiders`.

### 3. `DirectAssaultStrategy`

```java
public final class DirectAssaultStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("DIRECT_ASSAULT");
  ...
}
```

| Método | Comportamiento |
| --- | --- |
| `id()` | `ID` |
| `requirement()` | `"none"` |
| `isViable` | `true` |
| `assignRoles` | Recorre `snapshot.mobs()` y pone `baseRole(mob.kind())` en un `LinkedHashMap`; devuelve `Collections.unmodifiableMap(...)` |
| `private static Role baseRole(MobKind kind)` | `switch` con flechas: `SKELETON -> Role.SHOOT`; `ZOMBIE, SPIDER -> Role.PRESS` |

### 4. `FlankStrategy`

```java
public final class FlankStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("FLANK");
  private static final int MIN_MELEE = 3;
  private static final int FLANKER_DIVISOR = 2;

  public FlankStrategy(CombatGeometry geometry) { ... }   // requireNonNull: "FlankStrategy.geometry"
  ...
}
```

| Método | Comportamiento |
| --- | --- |
| `id()` | `ID` |
| `requirement()` | `"at least " + MIN_MELEE + " melee mobs"` (da `at least 3 melee mobs`) |
| `isViable` | `GroupComposition.of(snapshot).melee() >= MIN_MELEE` |
| `assignRoles` | Coordina: `Set<MobId> flankers = flankers(snapshot, target);` y después arma el `LinkedHashMap` recorriendo `snapshot.mobs()` con `roleFor(mob, flankers)`; devuelve `Collections.unmodifiableMap(...)` |
| `private Set<MobId> flankers(GroupSnapshot snapshot, PlayerId target)` | `int count = GroupComposition.of(snapshot).melee() / FLANKER_DIVISOR;` devuelve `flankerCandidates(snapshot, target).stream().limit(count).map(MobSnapshot::id).collect(Collectors.toSet())` |
| `private List<MobSnapshot> flankerCandidates(GroupSnapshot snapshot, PlayerId target)` | `Optional<PlayerPose> pose = snapshot.player(target).map(PlayerSnapshot::pose);` arma una lista con `sideFirst(mobsOfKind(snapshot, MobKind.SPIDER), pose)` y después `sideFirst(mobsOfKind(snapshot, MobKind.ZOMBIE), pose)` |
| `private static List<MobSnapshot> mobsOfKind(GroupSnapshot snapshot, MobKind kind)` | Los mobs de ese tipo, en el orden de la foto (`stream().filter(...).toList()`) |
| `private List<MobSnapshot> sideFirst(List<MobSnapshot> mobs, Optional<PlayerPose> pose)` | Si `pose` está vacío, devuelve `mobs`. Si no: copia en un `ArrayList`, la ordena con `Comparator.comparingDouble((MobSnapshot mob) -> geometry.angleFromFacingDegrees(pose.get(), mob.position())).reversed()` y la devuelve |
| `private static Role roleFor(MobSnapshot mob, Set<MobId> flankers)` | `SKELETON` → `SHOOT`; si `flankers.contains(mob.id())` → `FLANK`; si no → `PRESS` |

### 5. `PinAndShootStrategy`

```java
public final class PinAndShootStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("PIN_AND_SHOOT");
  private static final int MIN_ZOMBIES = 2;
  private static final int MIN_SKELETONS = 2;
  ...
}
```

| Método | Comportamiento |
| --- | --- |
| `id()` | `ID` |
| `requirement()` | `"at least " + MIN_ZOMBIES + " zombies and " + MIN_SKELETONS + " skeletons"` (da `at least 2 zombies and 2 skeletons`) |
| `isViable` | `GroupComposition composition = GroupComposition.of(snapshot); return composition.zombies() >= MIN_ZOMBIES && composition.skeletons() >= MIN_SKELETONS;` |
| `assignRoles` | Igual que `DirectAssaultStrategy`, con `private static Role roleFor(MobKind kind)`: `ZOMBIE -> PRESS`, `SKELETON -> SHOOT`, `SPIDER -> FLANK` |

### 6. `StrategyCatalog`

```java
public final class StrategyCatalog {
  public StrategyCatalog(CombatGeometry geometry) { ... }   // requireNonNull: "StrategyCatalog.geometry"

  public List<GroupStrategy> all() { ... }
  public List<GroupStrategy> viable(GroupSnapshot snapshot) { ... }
  public Optional<GroupStrategy> find(StrategyId id) { ... }
  public GroupStrategy defaultStrategy() { ... }
}
```

- El constructor arma `List.of(new DirectAssaultStrategy(), new FlankStrategy(geometry), new PinAndShootStrategy())` y la guarda en un campo `strategies`. Este orden es el orden de `all()` y `viable()`, y es el orden de las opciones para la política de selección: no lo cambies.
- `all()`: `strategies`.
- `viable(snapshot)`: `strategies.stream().filter(strategy -> strategy.isViable(snapshot)).toList()`.
- `find(id)`: búsqueda por `id()`.
- `defaultStrategy()`: la primera de `strategies` (`DIRECT_ASSAULT`). Por eso `viable()` nunca está vacía.

## Pruebas obligatorias

Foto común: `GroupSnapshotBuilder` con `withPlayer(target)`, donde `target` es el jugador por defecto de `PlayerSnapshotBuilder` (en `(0, 64, 0)` mirando hacia +z). Los mobs con posición explícita se arman con `MobSnapshotBuilder` y `withMob`, con IDs `new MobId(new UUID(1, n))`. Para comparar mapas de roles en orden usá `assertThat(roles).containsExactly(entry(...), ...)`.

**`GroupCompositionTest`**

| Prueba | Verificación |
| --- | --- |
| `countsEachKindAndMelee` | `withZombies(4).withSkeletons(3).withSpiders(2)`: 4, 3, 2 y `melee()` 6 |
| `rejectsNegativeCounts` | `new GroupComposition(-1, 0, 0)`: mensaje `GroupComposition counts must be zero or positive, got -1/0/0` |

**`DirectAssaultStrategyTest`**

| Prueba | Verificación |
| --- | --- |
| `isAlwaysViable` | Viable con una foto sin mobs y con un solo esqueleto |
| `meleePressAndSkeletonsShoot` | `withZombies(2).withSkeletons(1).withSpiders(1)`: roles `PRESS`, `PRESS`, `SHOOT`, `PRESS` en el orden de la foto |
| `describesItsRequirement` | `id().value()` es `DIRECT_ASSAULT` y `requirement()` es `none` |

**`FlankStrategyTest`**

| Prueba | Verificación |
| --- | --- |
| `needsThreeMeleeMobs` | 2 zombies: no viable. 2 zombies y 1 araña: viable. 2 zombies y 5 esqueletos: no viable |
| `spidersFlankFirstThenTheMostSidewaysZombie` | Mobs en este orden: zombie A en `(0, 64, 5)` (0°), zombie B en `(5, 64, 0)` (90°), zombie C en `(0, 64, -5)` (180°), zombie D en `(-5, 64, 5)` (45°), esqueletos E y F, arañas G en `(3, 64, 3)` y H en `(-3, 64, -3)`. 6 cuerpo a cuerpo → 3 flanquean: G, H y C. Roles en orden: A `PRESS`, B `PRESS`, C `FLANK`, D `PRESS`, E `SHOOT`, F `SHOOT`, G `FLANK`, H `FLANK` |
| `halfIsRoundedDown` | 3 zombies en `(0, 64, 5)`, `(5, 64, 0)` y `(0, 64, -5)`: flanquea solo el tercero (180°); los otros dos `PRESS` |
| `extraSpidersPressWhenThereAreMoreThanHalf` | Zombie en `(0, 64, 5)` y arañas I en `(0, 64, 4)` (0°), J en `(4, 64, 0)` (90°) y K en `(0, 64, -4)` (180°). 4 cuerpo a cuerpo → 2 flanquean: K y J. Roles: zombie `PRESS`, I `PRESS`, J `FLANK`, K `FLANK` |
| `targetMissingFromTheSnapshotUsesSnapshotOrder` | Los mismos mobs que `halfIsRoundedDown` pero `assignRoles` con un objetivo `new PlayerId(new UUID(9, 9))` que no está en la foto: flanquea el **primero** |
| `angleTiesKeepSnapshotOrder` | 3 zombies en `(3, 64, 0)`, `(-3, 64, 0)` y `(0, 64, 3)`: los dos primeros están a 90°; flanquea el primero |
| `describesItsRequirement` | `requirement()` es `at least 3 melee mobs` |

**`PinAndShootStrategyTest`**

| Prueba | Verificación |
| --- | --- |
| `needsTwoZombiesAndTwoSkeletons` | 2 zombies y 2 esqueletos: viable. 2 zombies y 1 esqueleto: no. 1 zombie y 3 esqueletos: no. 1 zombie, 2 arañas y 2 esqueletos: no (las arañas no cuentan como zombies) |
| `zombiesPinSkeletonsShootSpidersFlank` | `withZombies(2).withSkeletons(2).withSpiders(1)`: `PRESS`, `PRESS`, `SHOOT`, `SHOOT`, `FLANK` |
| `describesItsRequirement` | `requirement()` es `at least 2 zombies and 2 skeletons` |

**`StrategyCatalogTest`**

| Prueba | Verificación |
| --- | --- |
| `listsTheThreeStrategiesInFixedOrder` | `all()` tiene los IDs `DIRECT_ASSAULT`, `FLANK`, `PIN_AND_SHOOT` en ese orden |
| `testGroupMakesEveryStrategyViable` | `withZombies(4).withSkeletons(3).withSpiders(2)` (el grupo de prueba del catálogo): `viable()` tiene las tres |
| `loneZombieOnlyHasTheDefault` | `withZombies(1)`: `viable()` es solo `DIRECT_ASSAULT` |
| `findsStrategiesById` | `find(FlankStrategy.ID)` está presente; `find(new StrategyId("UNKNOWN"))` está vacío |
| `defaultIsDirectAssault` | `defaultStrategy().id()` es `DIRECT_ASSAULT` |

### Pruebas que muerden (obligatorio, va en el informe)

Calculadas antes de escribirlas: cada cambio da un resultado distinto con los números de su prueba.

| Cambio temporal | Tiene que fallar | Por qué cambia |
| --- | --- | --- |
| En `flankers`, redondear para arriba (`(melee + 1) / FLANKER_DIVISOR`) | `halfIsRoundedDown` | Con 3 cuerpo a cuerpo flanquean 2 en vez de 1 |
| En `flankerCandidates`, poner los zombies antes que las arañas | `spidersFlankFirstThenTheMostSidewaysZombie` | Flanquean C, B y D en vez de G, H y C |
| En `sideFirst`, quitar el `.reversed()` | `halfIsRoundedDown` | Flanquea el zombie de 0° en vez del de 180° |
| En `PinAndShootStrategy.isViable`, contar arañas como zombies (`melee()` en vez de `zombies()`) | `needsTwoZombiesAndTwoSkeletons` | 1 zombie y 2 arañas pasarían a ser viables |

## Procedimiento

1. Rama `wp-09-estrategias` desde `main`.
2. `GroupStrategy`, `GroupComposition`, `DirectAssaultStrategy`, `PinAndShootStrategy` y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add strategy contract, composition, direct assault and pin and shoot`.
3. `FlankStrategy` y `FlankStrategyTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add flank strategy that sends spiders and sideways zombies around`.
4. `StrategyCatalog` y `StrategyCatalogTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add strategy catalog`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-09: group strategies`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Elegir entre las estrategias viables con la política de selección y pasar a `RETREAT` a los mobs con poca vida (cerebro, WP-10).
- Repartir a los flanqueadores del mismo lado en puntos distintos (WP-22).
- Sub-escuadrones con varios objetivos (fase 2).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, textos de requisito y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite; ninguna clase con más de 20 métodos públicos.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 4 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
