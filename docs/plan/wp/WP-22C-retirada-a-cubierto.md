# WP-22C — Retirada a cubierto

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-22B (`Waypoints`, `GoalTools`, `GoalOrders`, `PoseReader`, curación) |
| Modelo | Sonnet |
| Rama | `wp-22c-retirada-a-cubierto` |

## Objetivo

Que el rol `RETREAT` haga lo que pidió Nico (CT-15): **el mob que se retira a curarse busca un lugar donde el jugador no lo vea**. Si no encuentra ninguno, se aleja en línea recta hasta la distancia de retirada (16 bloques) y se queda ahí.

1. `CombatGeometry.coverCandidates`: los lugares a revisar, del mejor al peor (Java puro).
2. `RetreatSituation` y `RetreatMove`: qué hace el mob en cada momento (Java puro, probado).
3. `CoverFinder`: de esos candidatos, el primero que el jugador no ve **y** al que el mob puede llegar (Paper).
4. `RetreatGoal`: junta todo, con los ritmos medidos en el reloj del plugin (regla B-01).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3, para actualizar las filas)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/` (todos, en especial `FlankGoal`, `Waypoints`, `GoalOrders`, `GoalTools`, `MeleeRhythm`, `GoalInstaller`)
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`

## Reglas de negocio

1. **`RetreatGoal`** se instala en zombies y arañas, junto con `PressGoal` y `FlankGoal`. Se activa si la orden del mob es `RETREAT`, con objetivo o sin él. El **peligro** es el objetivo de la orden, si es válido (`TargetChecks.isValidTarget`). No golpea ni mira al jugador.
2. **Sin peligro válido:** se queda quieto y olvida el cubierto que tenía.
3. **Con peligro**, cada 10 ticks del reloj el mob elige un movimiento (`RetreatSituation.nextMove`), en este orden:
   1. **`HOLD`:** si el jugador **no lo ve** y está a la distancia de retirada o más (en horizontal): se queda quieto. Es el lugar ideal: escondido y lejos (a más de 12 bloques, el dominio ya lo cura).
   2. **`KEEP_COVER`:** si va camino a un cubierto y el jugador **sigue sin ver ese lugar**: sigue el camino que ya tiene, sin recalcular.
   3. **`SEARCH_COVER`:** si pasaron 40 ticks (2 s) desde la última búsqueda (o nunca buscó): busca cubierto. Si encuentra, camina hasta ahí; si no, se aleja en línea recta.
   4. **`RETREAT_STRAIGHT`:** en cualquier otro caso, se aleja en línea recta hasta la distancia de retirada (16 bloques) y, si ya está ahí o más lejos, se queda quieto.
4. **Candidatos a cubierto:** dos anillos alrededor del jugador, a la distancia de retirada (16) y 4 bloques más lejos (20). En cada anillo, 7 direcciones: la que se aleja del jugador pasando por el mob y, alternando, 30°, 60° y 90° a cada lado (primero el positivo). Nunca más de 90°: el mob tendría que volver a pasar cerca del jugador. Los candidatos van a la **altura del mob**; el orden es anillo interior primero y, dentro del anillo, el abanico.
5. **«El jugador ve un lugar»:** `Player.hasLineOfSight(Location)` hacia la **altura de los ojos** del mob en ese lugar (el jugador lo vería por la cabeza, no por los pies). «El jugador ve al mob»: `Player.hasLineOfSight(mob)`. Los bloques con colisión tapan la vista (también el vidrio y las hojas: es lo que hace Minecraft).
6. **Búsqueda (`CoverFinder`):** recorre los candidatos en orden; salta los que el jugador ve (barato: un rayo); para los otros pide un camino (`Pathfinder.findPath`), que es lo caro, **hasta 3 caminos por búsqueda**. Acepta el primer camino que llega a su punto final (`canReachFinalPoint`) y cuyo punto final el jugador tampoco ve (el candidato puede estar dentro de una loma y el camino terminar arriba, a la vista).
7. **La curación no cambia** (CT-07): depende de la distancia (sin jugadores a menos de 12 bloques), no de la vista. Esconderse sirve para que el jugador no lo encuentre ni le dispare; la curación la decide el dominio como siempre.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatMove.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatSituation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/CoverFinder.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RetreatSituationTest.java` |

**Métodos públicos nuevos en clases existentes:** `CombatGeometry.coverCandidates` (7 en total: es geometría de combate, como `retreatPoint`) y `Waypoints.retreatPoint` y `coverCandidates` (4 en total: los destinos de los goals viven ahí).

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `LivingEntity.hasLineOfSight(Entity)`, `LivingEntity.hasLineOfSight(Location)`, `LivingEntity.getEyeHeight()`, `Pathfinder.findPath(Location)` (puede devolver `null`), `Pathfinder.PathResult.canReachFinalPoint()` y `getFinalPoint()` (puede devolver `null`), `Pathfinder.moveTo(PathResult, double)`.

### `CombatGeometry.java`

Constantes y un método nuevo; `awayDirection` no cambia.

```java
  // Past 90° from straight away, the mob would walk back past the danger.
  private static final List<Double> COVER_FAN_DEGREES =
      List.of(0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0);
  // A second, wider ring for when the first one is all open ground.
  private static final double COVER_OUTER_RING_EXTRA_BLOCKS = 4.0;

  public List<Vec3> coverCandidates(Vec3 mobPosition, Vec3 dangerPosition, double radiusBlocks) {
    requirePositiveDistance(radiusBlocks);
    Vec3 away = awayDirection(mobPosition, dangerPosition);
    Vec3 center = new Vec3(dangerPosition.x(), mobPosition.y(), dangerPosition.z());
    List<Vec3> candidates = new ArrayList<>(coverRing(away, center, radiusBlocks));
    candidates.addAll(coverRing(away, center, radiusBlocks + COVER_OUTER_RING_EXTRA_BLOCKS));
    return List.copyOf(candidates);
  }

  private static List<Vec3> coverRing(Vec3 away, Vec3 center, double radiusBlocks) {
    return COVER_FAN_DEGREES.stream()
        .map(degrees -> center.plus(rotateAroundVertical(away, degrees).times(radiusBlocks)))
        .toList();
  }
```

### `Waypoints.java`: dos métodos nuevos

```java
  // Empty once the mob is far enough: it holds there instead of running on forever.
  public Optional<Vec3> retreatPoint(Vec3 mobPosition, Vec3 dangerPosition) {
    double missing = settings.get().retreatDistanceBlocks() - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }

  public List<Vec3> coverCandidates(Vec3 mobPosition, Vec3 dangerPosition) {
    return geometry.coverCandidates(
        mobPosition, dangerPosition, settings.get().retreatDistanceBlocks());
  }

  private static double horizontalDistance(Vec3 from, Vec3 to) {
    return from.minus(to).horizontal().length();
  }
```

### `RetreatMove.java` y `RetreatSituation.java`

```java
/** What a retreating mob does until its next look around. */
public enum RetreatMove {
  HOLD,
  KEEP_COVER,
  SEARCH_COVER,
  RETREAT_STRAIGHT
}
```

```java
/**
 * What a retreating mob knows at one look around (CT-15).
 *
 * @param hidden the danger cannot see the mob
 * @param farEnough the mob is at the retreat distance or beyond, horizontally
 * @param coverStillHidden the mob is heading for a cover spot the danger still cannot see
 * @param searchDue the cover search interval has passed since the last search
 */
public record RetreatSituation(
    boolean hidden, boolean farEnough, boolean coverStillHidden, boolean searchDue) {

  public RetreatMove nextMove() {
    if (hidden && farEnough) {
      return RetreatMove.HOLD;
    }
    if (coverStillHidden) {
      return RetreatMove.KEEP_COVER;
    }
    if (searchDue) {
      return RetreatMove.SEARCH_COVER;
    }
    return RetreatMove.RETREAT_STRAIGHT;
  }
}
```

### `CoverFinder.java` (package-private)

```java
/** Finds a spot the danger cannot see and the mob can reach, among the cover candidates. */
final class CoverFinder {
  // Pathfinding is the costly part: a few paths per search keep many retreating mobs cheap.
  static final int MAX_PATHS_PER_SEARCH = 3;

  Optional<PathResult> find(Mob mob, Player danger, List<Vec3> candidates) {
    int paths = 0;
    for (Vec3 candidate : candidates) {
      if (paths == MAX_PATHS_PER_SEARCH) {
        return Optional.empty();
      }
      if (isSeenBy(danger, mob, candidate)) {
        continue;
      }
      paths++;
      Optional<PathResult> path = hiddenPath(mob, danger, candidate);
      if (path.isPresent()) {
        return path;
      }
    }
    return Optional.empty();
  }

  // The candidate can be inside a hill while the path ends on top of it, in plain sight.
  private static Optional<PathResult> hiddenPath(Mob mob, Player danger, Vec3 candidate) {
    PathResult path = mob.getPathfinder().findPath(locationIn(mob, candidate));
    if (path == null || !path.canReachFinalPoint() || path.getFinalPoint() == null) {
      return Optional.empty();
    }
    if (isSeenBy(danger, mob, PoseReader.positionOf(path.getFinalPoint()))) {
      return Optional.empty();
    }
    return Optional.of(path);
  }

  // The player would spot the mob's head there, not its feet.
  static boolean isSeenBy(Player danger, Mob mob, Vec3 spot) {
    return danger.hasLineOfSight(
        new Location(mob.getWorld(), spot.x(), spot.y() + mob.getEyeHeight(), spot.z()));
  }

  private static Location locationIn(Mob mob, Vec3 spot) {
    return new Location(mob.getWorld(), spot.x(), spot.y(), spot.z());
  }
}
```

### `RetreatGoal.java`

Javadoc: `/** RETREAT: hide from the danger if there is cover, otherwise walk straight away and hold. */`. Clave `"retreat"`, tipos `MOVE` y `LOOK`, `WALK_SPEED = 1.0` con el mismo comentario que `PressGoal`. Constructor `RetreatGoal(Mob mob, GoalContext context)`.

```java
  // Looking for cover costs a few rays and up to three paths; every 2 seconds is enough.
  static final long COVER_SEARCH_INTERVAL_TICKS = 40;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm;
  private final CoverFinder finder = new CoverFinder();
  // The cover spot the mob is walking to; empty when it has none.
  private Optional<Vec3> coverSpot = Optional.empty();
  private long nextCoverSearchTick = Long.MIN_VALUE;
```

Métodos del goal:

```java
  @Override
  public boolean shouldActivate() {
    return currentOrder().isPresent();
  }

  @Override
  public boolean shouldStayActive() {
    return shouldActivate();
  }

  @Override
  public void stop() {
    hold();
  }

  @Override
  public void tick() {
    if (!rhythm.shouldRepath()) {
      return;
    }
    rhythm.markRepath();
    danger().ifPresentOrElse(this::retreatFrom, this::hold);
  }
```

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<RoleAssignment> currentOrder()` | `GoalOrders.orderFor(mob, context.roles(), Role.RETREAT)` |
| `Optional<Player> danger()` | `currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob))` |
| `void retreatFrom(Player danger)` | `switch (situation(danger).nextMove())`: `HOLD -> holdInPlace()`; `KEEP_COVER -> { }` (con un comentario: `// Already on a path to a hidden spot.`); `SEARCH_COVER -> searchCover(danger)`; `RETREAT_STRAIGHT -> retreatStraight(danger)` |
| `RetreatSituation situation(Player danger)` | `new RetreatSituation(!danger.hasLineOfSight(mob), waypoints().retreatPoint(mobPosition(), position(danger)).isEmpty(), coverSpot.filter(spot -> !CoverFinder.isSeenBy(danger, mob, spot)).isPresent(), context.tools().clock().currentTick() >= nextCoverSearchTick)` |
| `void searchCover(Player danger)` | `nextCoverSearchTick = ahora + COVER_SEARCH_INTERVAL_TICKS`; `finder.find(mob, danger, waypoints().coverCandidates(mobPosition(), position(danger)))`: con camino, `coverSpot = Optional.of(PoseReader.positionOf(path.getFinalPoint()))` y `mob.getPathfinder().moveTo(path, WALK_SPEED)`; sin camino, `retreatStraight(danger)` |
| `void retreatStraight(Player danger)` | `coverSpot = Optional.empty()`; `waypoints().retreatPoint(mobPosition(), position(danger))`: con punto, `moveTo(new Location(mob.getWorld(), x, y, z), WALK_SPEED)`; sin punto, `stopPathfinding()` |
| `void holdInPlace()` | `mob.getPathfinder().stopPathfinding()` (conserva `coverSpot`: está escondido en él) |
| `void hold()` | `coverSpot = Optional.empty()` y `mob.getPathfinder().stopPathfinding()` |
| `Waypoints waypoints()` | `context.tools().waypoints()` |
| `Vec3 mobPosition()` | `PoseReader.positionOf(mob.getLocation())` |
| `static Vec3 position(Player player)` | `PoseReader.positionOf(player.getLocation())` |

### `GoalInstaller.java`

Suma `goals.addGoal(mob, GOAL_PRIORITY, new RetreatGoal(mob, context));` después de los otros dos, bajo el mismo comentario.

### `docs/actualizar-paper.md`

Fila `adapter/goal/*`: sumar `LivingEntity.hasLineOfSight(Entity)` y `hasLineOfSight(Location)`, `LivingEntity.getEyeHeight()`, `Pathfinder.findPath(Location)`, `PathResult` (`canReachFinalPoint`, `getFinalPoint`) y `moveTo(PathResult, double)`. En «Qué revisar», sumar: «que `hasLineOfSight` siga cortándose en los bloques con colisión (vidrio y hojas incluidos) y que `findPath` siga devolviendo `null` cuando no hay camino».

## Pruebas obligatorias

### `CombatGeometryTest` (+4)

Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `coverCandidatesFanOutFromStraightAway` | mob `(0,64,5)`, peligro `(0,64,0)`, radio 16: 14 candidatos; `[0]` `(0, 64, 16)`; `[1]` `(-8, 64, 13.85640646055102)`; `[2]` `(8, 64, 13.85640646055102)`; `[5]` `(-16, 64, 0)`; `[6]` `(16, 64, 0)`; `[7]` `(0, 64, 20)` |
| `coverCandidatesStayAtTheMobsHeight` | mob `(0,70,5)`, peligro `(0,64,0)`: los 14 tienen `y` 70 |
| `coverCandidatesWithoutSeparationGoTowardsPositiveX` | mob `(3,64,3)`, peligro `(3,60,3)`: `[0]` `(19, 64, 3)` |
| `rejectsNonPositiveCoverRadius` | radio 0: `IllegalArgumentException` con `CombatGeometry.distanceBlocks must be a positive number, got 0.0` |

### `WaypointsTest` (+4)

Con el mismo `Waypoints` de las pruebas del WP-22B (retirada a 16 bloques).

| Prueba | Verifica |
| --- | --- |
| `retreatPointStopsAtTheRetreatDistance` | mob `(3,64,4)`, peligro `(0,60,0)` (5 bloques en horizontal): `(9.6, 64, 12.8)` |
| `mobFarEnoughHoldsItsGround` | mob `(0,64,16)`, peligro `(0,64,0)`: vacío; mob `(0,64,15.9)`: `(0, 64, 16)` |
| `retreatDistanceIgnoresHeight` | mob `(0,80,10)`, peligro `(0,64,0)`: `(0, 80, 16)` |
| `coverCandidatesUseTheRetreatDistance` | mob `(0,64,5)`, peligro `(0,64,0)`: 14 candidatos, `[0]` `(0, 64, 16)` y `[7]` `(0, 64, 20)` |

### `RetreatSituationTest` (5)

| Prueba | Situación (`hidden`, `farEnough`, `coverStillHidden`, `searchDue`) | Movimiento |
| --- | --- | --- |
| `hiddenAndFarHolds` | `true, true, false, true` | `HOLD` |
| `hiddenButCloseKeepsLooking` | `true, false, false, true` | `SEARCH_COVER` |
| `coverStillHiddenKeepsItsPath` | `false, false, true, true` | `KEEP_COVER` |
| `seenWithASearchDueLooksForCover` | `false, true, false, true` | `SEARCH_COVER` |
| `seenBetweenSearchesRetreatsStraight` | `false, false, false, false` | `RETREAT_STRAIGHT` |

Total: **13 pruebas**. `CoverFinder` y `RetreatGoal` usan Paper y se verifican en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `nextMove`, `HOLD` con solo `hidden` (sin `farEnough`) | `HOLD` | `hiddenButCloseKeepsLooking` |
| 2 | En `coverCandidates`, sin el anillo exterior | 7 candidatos | `coverCandidatesFanOutFromStraightAway` |
| 3 | En `COVER_FAN_DEGREES`, `-30.0` antes que `30.0` | `[1]` es `(8, 64, 13.86…)` | `coverCandidatesFanOutFromStraightAway` |
| 4 | En `Waypoints.retreatPoint`, `mobPosition.distanceTo(dangerPosition)` (con altura) | distancia 18,87: vacío | `retreatDistanceIgnoresHeight` |

## Verificación en el server (Opus, después de la revisión)

No la hace el subagente. Con `/mobai debug all full`, de noche, con un mob herido (30 % o menos):

1. Cerca de una pared, una casa o un desnivel: el mob se mete detrás, donde no lo ves, y se queda. Si das la vuelta y lo ves, en 2 s busca otro cubierto.
2. En campo abierto: se aleja a unos 16 bloques y se queda; cada 2 s vuelve a buscar.
3. Con vos a más de 12 bloques, su vida sube 1 punto cada 2,5 s; con 60 % vuelve a pelear.
4. Sin errores en la consola ni tirones del server con 4 o 5 mobs en retirada a la vez.

## Procedimiento

1. Rama `wp-22c-retirada-a-cubierto` desde `origin/main` actualizado (con el WP-22B mergeado).
2. `CombatGeometry`, `Waypoints` y sus pruebas. Commit: `feat: cover candidates and the straight retreat point`.
3. `RetreatMove`, `RetreatSituation` y su prueba. Commit: `feat: retreat moves decided from what the mob knows`.
4. `CoverFinder`, `RetreatGoal`, `GoalInstaller`. Commit: `feat: retreat goal that hides from the danger (CT-15)`.
5. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the retreat goal`.
6. Pruebas que muerden (de a una, en secuencia; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-22C: retreat to cover`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca una deprecación en un método de Paper usado acá, frená y reportá cuál; no la suprimas.
3. Si `moveTo(PathResult, double)` no compila con el `PathResult` de `findPath`, usá `moveTo(Location, double)` con su punto final y avisalo.
4. Si la cobertura mínima no se alcanza por las clases que usan Paper, reportalo con el número; no agregues pruebas con mocks de Paper.

## Fuera de alcance

- Cambiar la regla de curación (sigue siendo por distancia, CT-07).
- Esqueletos en retirada (siguen con su IA vanilla hasta el WP-24; la curación igual les llega).
- Recordar cubiertos entre mobs o entre retiradas.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 13 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ningún ritmo cuenta llamadas a `tick()`: repath con `MeleeRhythm`, búsqueda con el reloj.
- [ ] Nunca más de 3 caminos por búsqueda.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
