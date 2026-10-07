# WP-24D — Esqueletos en altura

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24C (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24d-esqueletos-en-altura` |

## Objetivo

CT-22, pedido de Nico: el esqueleto **busca altura** cerca de su puesto en el anillo (WP-24C). Desde arriba ve más, tiene distancia y le cuesta más al jugador llegarle. Si no hay un lugar más alto que valga la pena, se queda en su puesto como ahora.

1. `CombatGeometry.perchCandidates`: los lugares a revisar alrededor del puesto (Java puro).
2. `HighGroundRanking`: de esos lugares, con su altura de suelo, cuáles valen la pena y en qué orden (Java puro).
3. `HighGroundFinder`: mide el suelo y comprueba vista, línea de tiro y camino (Paper).
4. `ShootGoal` busca altura cada 2 s y camina a ese lugar en vez de a su puesto.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java`, `CoverFinder.java` (el mismo patrón de búsqueda), `Waypoints.java`, `RetreatGoal.java` (cómo usa `CoverFinder` con su intervalo)
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`

## Reglas de negocio

1. **Candidatos** (`perchCandidates`): alrededor del jugador, a partir del lugar al que iba a ir el esqueleto (su puesto o su carril libre), girado 0°, 15°, −15°, 30° y −30°. Primero a la misma distancia del jugador, después 4 bloques más lejos (`PERCH_OUTER_EXTRA_BLOCKS`): 10 lugares, en ese orden. Quedan afuera los que pasan la distancia máxima de tiro (`shoot-max-distance-blocks`, 30).
2. **Suelo:** para cada candidato, la altura en la que se para un mob es el bloque más alto de esa columna más 1 (`World.getHighestBlockYAt`). Lo mismo para el lugar original.
3. **Qué vale la pena** (`HighGroundRanking`): solo los candidatos al menos **2 bloques** más altos que el lugar original (`MIN_HEIGHT_GAIN_BLOCKS`: un bloque es un escalón, dos ya es una posición), ordenados del más alto al más bajo; a igual altura, en el orden de la regla 1.
4. **Comprobaciones** (`HighGroundFinder`), en el orden del ranking:
   - el jugador ve ese lugar a la altura de los ojos del esqueleto (`Player.hasLineOfSight`);
   - la línea de tiro desde ahí está limpia de aliados (`isLineOfFireClear`, CT-21);
   - el esqueleto puede llegar: `findPath` con `canReachFinalPoint`, y el punto final no queda más de 1 bloque por debajo del candidato (si el camino termina abajo, no subió). Como máximo **3 caminos por búsqueda**, igual que el cubierto.
   - Gana el primero que pasa las tres.
5. **Cuándo busca:** cada `PERCH_SEARCH_INTERVAL_TICKS` (40, 2 s) del reloj, y solo si ve al jugador. Entre búsquedas camina al lugar alto que eligió, mientras el jugador lo siga viendo desde ahí. Si deja de verlo, el lugar se olvida y vuelve a su puesto. Si no ve al jugador, se le acerca como siempre y también olvida el lugar.
6. **Disparar** no cambia: la línea limpia, el ritmo y la distancia se miden desde donde está.
7. **Limitación conocida:** el bloque más alto de la columna falla en cuevas o bajo árboles grandes: puede dar el techo o la copa. El camino lo descarta porque no llega, o llega más abajo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/HighGroundRanking.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PerchRequest.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/HighGroundFinder.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/HighGroundRankingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |

Antes de empezar, buscá con grep que los nombres de las pruebas nuevas no existan en esos archivos.

**Métodos públicos nuevos:** `CombatGeometry.perchCandidates` (11 en total) y `Waypoints.perchCandidates` y `rankPerches` (10 en total).

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `World.getHighestBlockYAt(int, int)` (sin `HeightMap`, que es una constante de Paper), `LivingEntity.hasLineOfSight(Location)`, `Pathfinder.findPath(Location)` y `PathResult`.

### `CombatGeometry.java`

```java
  // Turns tried round the target from a shooter's spot when looking for high ground.
  private static final List<Double> PERCH_TURNS_DEGREES = List.of(0.0, 15.0, -15.0, 30.0, -30.0);
  // A second ring a little farther out, where a hill or a wall often is.
  private static final double PERCH_OUTER_EXTRA_BLOCKS = 4.0;

  /** Spots round the target near {@code spot}, nearest ring first, at the spot's height. */
  public List<Vec3> perchCandidates(Vec3 spot, Vec3 target) {
    Vec3 offset = spot.minus(target).horizontal();
    double distance = offset.length();
    List<Vec3> candidates = new ArrayList<>(perchRing(offset, target, spot.y()));
    if (distance > 0) {
      Vec3 farther = offset.times((distance + PERCH_OUTER_EXTRA_BLOCKS) / distance);
      candidates.addAll(perchRing(farther, target, spot.y()));
    }
    return List.copyOf(candidates);
  }

  private static List<Vec3> perchRing(Vec3 offset, Vec3 target, double height) {
    return PERCH_TURNS_DEGREES.stream()
        .map(turn -> rotateAroundVertical(offset, turn))
        .map(turned -> new Vec3(target.x() + turned.x(), height, target.z() + turned.z()))
        .toList();
  }
```

### `HighGroundRanking.java` (Java puro)

```java
/** Which nearby spots are worth climbing to, highest first. */
public final class HighGroundRanking {
  // One block is a step; two is a position worth walking to.
  static final double MIN_HEIGHT_GAIN_BLOCKS = 2.0;

  /**
   * @param grounded candidates at the height a mob stands there, in search order
   * @param currentGroundY the height a mob stands at the spot it was going to
   */
  public List<Vec3> rank(List<Vec3> grounded, double currentGroundY) {
    return grounded.stream()
        .filter(spot -> spot.y() - currentGroundY >= MIN_HEIGHT_GAIN_BLOCKS)
        .sorted(Comparator.comparingDouble(Vec3::y).reversed())
        .toList();
  }
}
```

`Stream.sorted` es estable: a igual altura queda el orden de búsqueda.

### `PerchRequest.java` y `HighGroundFinder.java`

```java
/** Where a shooter was going, and who stands between it and the target. */
public record PerchRequest(Vec3 spot, List<Vec3> allies) {
  // requireNonNull de los dos; allies = List.copyOf(allies)
}
```

```java
/** Finds a higher spot near a shooter's place, with sight, a clear line of fire and a way up. */
final class HighGroundFinder {
  // Pathfinding is the costly part: a few paths per search keep the shooters cheap.
  static final int MAX_PATHS_PER_SEARCH = 3;
  // A path that ends this far below the spot did not climb it.
  private static final double CLIMB_TOLERANCE_BLOCKS = 1.0;

  private final Waypoints waypoints;

  HighGroundFinder(Waypoints waypoints) { … requireNonNull(waypoints, "HighGroundFinder.waypoints") … }

  Optional<Vec3> find(Mob shooter, Player target, PerchRequest request) {
    List<Vec3> ranked =
        waypoints.rankPerches(
            grounded(shooter, waypoints.perchCandidates(request.spot(), PoseReader.positionOf(target.getLocation()))),
            groundYAt(shooter, request.spot()));
    int paths = 0;
    for (Vec3 perch : ranked) {
      if (paths == MAX_PATHS_PER_SEARCH) {
        return Optional.empty();
      }
      if (!canShootFrom(shooter, target, perch, request.allies())) {
        continue;
      }
      paths++;
      if (canClimb(shooter, perch)) {
        return Optional.of(perch);
      }
    }
    return Optional.empty();
  }
}
```

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `static List<Vec3> grounded(Mob shooter, List<Vec3> candidates)` | Cada candidato con `y = groundYAt(shooter, candidato)` |
| `static double groundYAt(Mob shooter, Vec3 spot)` | `shooter.getWorld().getHighestBlockYAt((int) Math.floor(spot.x()), (int) Math.floor(spot.z())) + 1`, con el comentario `// A mob stands on top of the highest block of the column.` |
| `boolean canShootFrom(Mob shooter, Player target, Vec3 perch, List<Vec3> allies)` | Tiene 4 parámetros: agrupá `target` y `allies` en un record privado `Aim(Player target, List<Vec3> allies)`, así queda `canShootFrom(Mob shooter, Vec3 perch, Aim aim)`. Ojos = `perch` más `shooter.getEyeHeight()` en `y`. `aim.target().hasLineOfSight(new Location(mundo, ojos…))` **y** `waypoints.isLineOfFireClear(ojos, PoseReader.bodyCenterOf(aim.target()), aim.allies())` |
| `static boolean canClimb(Mob shooter, Vec3 perch)` | `PathResult path = shooter.getPathfinder().findPath(new Location(mundo, perch…))`; `path != null && path.canReachFinalPoint() && path.getFinalPoint() != null && path.getFinalPoint().getY() >= perch.y() - CLIMB_TOLERANCE_BLOCKS` |

### `Waypoints.java`

Campo nuevo `private final HighGroundRanking ranking = new HighGroundRanking();` (sin estado, como `formation`).

```java
  /** High-ground candidates near a shooter's spot that stay within bow range. */
  public List<Vec3> perchCandidates(Vec3 spot, Vec3 target) {
    double maxRange = settings.get().shootMaxDistanceBlocks();
    return geometry.perchCandidates(spot, target).stream()
        .filter(candidate -> horizontalDistance(candidate, target) <= maxRange)
        .toList();
  }

  public List<Vec3> rankPerches(List<Vec3> grounded, double currentGroundY) {
    return ranking.rank(grounded, currentGroundY);
  }
```

### `ShootGoal.java`

Campos nuevos:

```java
  // Looking for high ground costs a few columns, rays and up to three paths; every 2 s is enough.
  static final long PERCH_SEARCH_INTERVAL_TICKS = 40;

  private final HighGroundFinder highGround;   // new HighGroundFinder(context.tools().waypoints()) en el constructor
  // The higher spot the shooter is heading for; empty when it has none.
  private Optional<Vec3> perch = Optional.empty();
  private long nextPerchSearchTick = Long.MIN_VALUE;
```

Cambios:

| Función | Hace |
| --- | --- |
| `void keepPositionIfDue(Player target)` | Igual, pero cuando no ve al jugador también `perch = Optional.empty()` antes de acercarse |
| `void walkToFiringSpot(Player target)` | `Vec3 spot = firingSpotFor(target)`; `searchPerchIfDue(target, spot)`; `Vec3 destination = currentPerch(target).orElse(spot)`; el resto igual que ahora, con `destination` en lugar de `spot` |
| `void searchPerchIfDue(Player target, Vec3 spot)` | Si `ahora >= nextPerchSearchTick`: `nextPerchSearchTick = ahora + PERCH_SEARCH_INTERVAL_TICKS`; `perch = highGround.find(mob, target, new PerchRequest(spot, allyCenters(target)))` |
| `Optional<Vec3> currentPerch(Player target)` | Si el jugador ya no ve el lugar a la altura de los ojos del esqueleto, `perch = Optional.empty()`. Devuelve `perch` |

### `docs/actualizar-paper.md`

Fila `adapter/goal/*`: sumar `World.getHighestBlockYAt(int, int)`. En «Qué revisar», sumar: «que `getHighestBlockYAt` sin `HeightMap` siga dando el bloque más alto que bloquea el movimiento (en cuevas y bajo árboles da el techo o la copa: el camino lo descarta)».

## Pruebas obligatorias

### `CombatGeometryTest` (+2)

Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `perchCandidatesSurroundTheSpot` | lugar `(0,64,25)`, jugador `(0,64,0)`: 10 candidatos; `[0]` `(0, 64, 25)`; `[1]` `(-6.4704761275630185, 64, 24.148145657226706)`; `[2]` `(6.4704761275630185, 64, 24.148145657226706)`; `[3]` `(-12.499999999999998, 64, 21.65063509461097)`; `[5]` `(0, 64, 29)`; `[6]` `(-7.5057523079731014, 64, 28.01184896238298)` |
| `perchCandidatesStayAtTheSpotsHeight` | lugar `(0,70,25)`, jugador `(0,64,0)`: los 10 con `y` 70 |

### `HighGroundRankingTest` (4)

`rank(candidatos, 64)`.

| Prueba | Candidatos (`y`) | Verifica |
| --- | --- | --- |
| `onlyClearlyHigherSpotsCount` | `a` 65, `b` 66, `c` 70 | `[c, b]` |
| `highestComesFirst` | `a` 67, `b` 72, `c` 69 | `[b, c, a]` |
| `equalHeightsKeepTheSearchOrder` | `a` 68, `b` 68 | `[a, b]` |
| `noHigherSpotGivesNothing` | `a` 64, `b` 65.5 | vacío |

(`a`, `b` y `c` son `Vec3` con distinta `x` y la `y` indicada.)

### `WaypointsTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `perchCandidatesStayWithinBowRange` | Con `TestSettings` (máximo 15): lugar `(0,64,12)`, jugador `(0,64,0)`: los 5 del anillo de 12 bloques quedan; los del anillo de 16 no. Resultado de 5 candidatos, todos a 12 bloques en horizontal |

Total: **7 pruebas**. `HighGroundFinder` y los cambios de `ShootGoal` usan Paper y se verifican en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `MIN_HEIGHT_GAIN_BLOCKS` = 1.0 | `a` (65) también entra | `onlyClearlyHigherSpotsCount` |
| 2 | Ordenar de menor a mayor | `[a, c, b]` | `highestComesFirst` |
| 3 | En `perchCandidates`, sin el anillo exterior | 5 candidatos | `perchCandidatesSurroundTheSpot` |
| 4 | En `Waypoints.perchCandidates`, sin el filtro de distancia | 10 candidatos | `perchCandidatesStayWithinBowRange` |

## Verificación en el server (Nico, después del merge)

En un terreno con lomas, árboles o casas, de noche, con `spawngroup`:

1. Los esqueletos suben a lugares altos cerca de su puesto (una loma, un techo, una pila de bloques) y te disparan desde ahí.
2. En terreno plano se quedan en su puesto, como en el WP-24C.
3. Si te escondés de un esqueleto en altura, baja y se te acerca hasta verte.
4. Sin tirones del server con 3 esqueletos buscando a la vez.

## Procedimiento

1. Rama `wp-24d-esqueletos-en-altura` desde `origin/main` actualizado.
2. `CombatGeometry.perchCandidates`, `HighGroundRanking`, `Waypoints` y sus pruebas. Commit: `feat: high-ground candidates and their ranking (CT-22)`.
3. `PerchRequest`, `HighGroundFinder`, `ShootGoal`. Commit: `feat: skeletons climb to high ground near their place`.
4. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the high-ground search`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-24D: skeletons on high ground (CT-22)`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función de `ShootGoal` pasa las 20 líneas, separala en otra privada con una tarea y avisalo.
3. Si `-Xlint` marca una deprecación, frená y reportá.

## Fuera de alcance

- Usar `HeightMap` (constante de Paper) o mirar bloques uno por uno.
- Que el esqueleto se quede arriba si el jugador se acerca a menos de 20 (vuelve a su puesto como siempre: el puesto se recalcula alrededor del jugador).
- La andanada (CT-23).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 7 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Nunca más de 3 caminos por búsqueda; la búsqueda cada 40 ticks del reloj.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
