# WP-32B — Punto de reunión: los goals

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-32A (mergeado: `RoleAssignment.rallyPoint`, `RetreatSettings.rallyArrivalBlocks`) |
| Modelo | Sonnet |
| Rama | `wp-32b-reunion-goals` |

## Objetivo

CT-29, segunda mitad: cada mob que se reagrupa, **una vez fuera de peligro**, camina al punto de reunión que le manda el cerebro (WP-32A), **sin entrar en el alcance del jugador** y rodeándolo por el camino más corto. En el punto se queda quieto y se cura.

## Decisiones tomadas en este WP

1. **Cuándo camina al punto:** cuando la retirada de siempre (CT-15) diría `HOLD`, es decir, cuando el jugador no lo ve y ya está a la distancia de retirada, si la orden trae un punto y todavía no llegó. Si el jugador lo ve, sigue la retirada de siempre (cubierto o en línea recta) y retoma el camino cuando vuelve a estar fuera de peligro.
2. **Sin jugador del que cuidarse** (la orden no trae objetivo, o ya no es válido): camina derecho al punto. Si no hay punto, se queda quieto, como hoy.
3. **Llegó:** a `retreat.rally-arrival-blocks` (3) o menos del punto, en horizontal. Ahí se queda quieto.
4. **No entrar en el alcance:** la recta del mob al punto no puede pasar a menos de *alcance del jugador + `attack.flank-margin-blocks`* (como el flanqueador). Se mide en horizontal, con el punto de la recta más cercano al jugador.
5. **Si la recta pasa por el alcance, rodea al jugador:** el próximo paso es un punto sobre un círculo alrededor del jugador, a la distancia actual del mob (o al alcance más el margen, si está más cerca), girado hacia el ángulo del punto de reunión **por el camino más corto**, de a 45° como máximo (como `FlankManeuver`). Si el giro más corto es exactamente media vuelta, gira hacia el lado en el que ya está el mob, para pasar por la espalda.
6. **La vista:** el sector que el jugador no ve (más de 120° de su mirada) es convexo. Si el mob y el punto están los dos ahí, la recta entre ellos también, y el arco más corto entre los dos pasa por la espalda. Por eso alcanza con el chequeo del alcance: no hace falta mirar la vista en cada tramo.
7. **Dónde va cada cosa:**
   - la geometría pura va al dominio: `RallyDetour` y `RallyLeg` en `domain.geometry`, porque usa `CombatGeometry.sideOf` y `rotateAroundVertical`, que son del paquete;
   - la configuración se lee en el adaptador: `RallyRoute` en `adapter.goal`, puro y sin Paper, como `Waypoints`;
   - `GoalTools` suma `rallyRoute` (es un record: la regla de 3 parámetros no aplica a su constructor canónico).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatGoal.java`, `RetreatSituation.java`, `RetreatMove.java`, `GoalTools.java`, `GoalOrders.java`, `PlayerTarget.java`, `Waypoints.java`, `FlankGoal.java` (cómo arma `PlayerTarget` con `reachOf`)
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`, `FlankManeuver.java`, `PlayerPose.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`, `RetreatSettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (`goalInstaller`)
- Pruebas de referencia: `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`, `RetreatSituationTest.java`
- `docs/actualizar-paper.md` (sección 3, fila `adapter/goal/*`) y `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/RallyLeg.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/RallyDetour.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RallyRoute.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTools.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatMove.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatSituation.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/geometry/RallyDetourTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RallyRouteTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RetreatSituationTest.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

Antes de empezar, buscá con grep `new GoalTools(` y `new RetreatSituation(` en `src/`. Si aparecen fuera de `AdapterServices.java`, `RetreatGoal.java` y `RetreatSituationTest.java`, frená y reportá.

## Especificación

### `RallyLeg.java` (dominio, `domain.geometry`)

```java
/** One walk to the rally point: the player to keep clear of, where the mob is and where it goes. */
public record RallyLeg(PlayerPose pose, Vec3 from, Vec3 to, double keepOutBlocks) {
  public RallyLeg { … }
}
```

`requireNonNull` con `"RallyLeg.pose"`, `"RallyLeg.from"` y `"RallyLeg.to"`. Si `!(keepOutBlocks > 0) || Double.isInfinite(keepOutBlocks)` → `IllegalArgumentException("RallyLeg.keepOutBlocks must be a positive number, got " + keepOutBlocks)`.

### `RallyDetour.java` (dominio, `domain.geometry`)

```java
/** The next step to the rally point: straight if the way keeps out of reach, else round the player (CT-29). */
public final class RallyDetour {
  // At most this much further round per step, as in the flank.
  private static final double MAX_TURN_DEGREES = 45.0;
  private static final double FULL_TURN_DEGREES = 360.0;
  private static final double HALF_TURN_DEGREES = 180.0;

  private final CombatGeometry geometry;

  public RallyDetour(CombatGeometry geometry) { … requireNonNull(geometry, "RallyDetour.geometry") … }

  public Vec3 next(RallyLeg leg) { … }
}
```

`next(leg)`: si `isClear(leg)`, devuelve `leg.to()`; si no, `detour(leg)`.

**`isClear(RallyLeg leg)`** (privado): la distancia horizontal del jugador al punto más cercano de la recta es `>= leg.keepOutBlocks()`.

```java
Vec3 start = leg.from().minus(leg.pose().position()).horizontal();
Vec3 way = leg.to().minus(leg.from()).horizontal();
double along = way.length() == 0 ? 0 : Math.clamp(-start.dot(way) / way.dot(way), 0.0, 1.0);
return start.plus(way.times(along)).length() >= leg.keepOutBlocks();
```

**`detour(RallyLeg leg)`** (privado):

```java
PlayerPose pose = leg.pose();
double from = signedAngle(pose, leg.from());
double turn = Math.clamp(shortestTurn(from, signedAngle(pose, leg.to())), -MAX_TURN_DEGREES, MAX_TURN_DEGREES);
double radius = Math.max(leg.keepOutBlocks(), leg.from().minus(pose.position()).horizontal().length());
Vec3 point = pose.position().plus(CombatGeometry.rotateAroundVertical(pose.facing(), from + turn).times(radius));
return new Vec3(point.x(), leg.from().y(), point.z());
```

Si `detour` pasa las 20 líneas o hace más de una cosa, separá `radiusFor(RallyLeg leg)`.

**`signedAngle(PlayerPose pose, Vec3 point)`** (privado): `CombatGeometry.sideOf(pose.facing(), point.minus(pose.position()).horizontal()) * geometry.angleFromFacingDegrees(pose, point)`.

**`shortestTurn(double from, double to)`** (privado, estático):

```java
double turn =
    ((to - from) % FULL_TURN_DEGREES + FULL_TURN_DEGREES + HALF_TURN_DEGREES) % FULL_TURN_DEGREES
        - HALF_TURN_DEGREES;
// A half turn either way is as short; going on the mob's own side passes behind the player.
if (turn == -HALF_TURN_DEGREES) {
  return from >= 0 ? HALF_TURN_DEGREES : -HALF_TURN_DEGREES;
}
return turn;
```

`PlayerPose` ya normaliza `facing` en horizontal: se usa tal cual. Los valores esperados de las pruebas se verificaron con una réplica de `CombatGeometry`.

### `RallyRoute.java` (adaptador, puro)

```java
/** Where a regrouping mob walks next on its way to the rally point; empty once it is there. */
public final class RallyRoute {
  private final RallyDetour detour;
  private final Supplier<AttackSettings> attack;
  private final Supplier<RetreatSettings> retreat;

  public RallyRoute(RallyDetour detour, Supplier<AttackSettings> attack, Supplier<RetreatSettings> retreat) { … requireNonNull con "RallyRoute.detour", "RallyRoute.attack" y "RallyRoute.retreat" … }

  public Optional<Vec3> next(Vec3 mobPosition, Vec3 rallyPoint, Optional<PlayerTarget> danger) { … }
}
```

`next`, en este orden:
1. Si la distancia horizontal de `mobPosition` a `rallyPoint` es `<= retreat.get().rallyArrivalBlocks()` → `Optional.empty()`.
2. Sin `danger` → `Optional.of(rallyPoint)`.
3. Si no: `Optional.of(detour.next(new RallyLeg(danger.get().pose(), mobPosition, rallyPoint, danger.get().reachBlocks() + attack.get().flankMarginBlocks())))`.

### `RetreatMove.java` y `RetreatSituation.java`

- **`RetreatMove`:** valor nuevo `RALLY`, después de `HOLD`.
- **`RetreatSituation`:** componente nuevo **al final**, `boolean rallyPending`, con `@param rallyPending the order carries a rally point the mob has not reached yet` en el javadoc. En `nextMove`, la primera regla pasa a:

```java
if (hidden && farEnough) {
  return rallyPending ? RetreatMove.RALLY : RetreatMove.HOLD;
}
```

### `GoalTools.java`

Componente nuevo **al final**: `RallyRoute rallyRoute`, con `requireNonNull(rallyRoute, "GoalTools.rallyRoute")`. En el javadoc: `/** What our goals use besides the orders: the weapons, the timing, the waypoints and the rally route. */`

### `RetreatGoal.java`

1. **Javadoc de la clase:** `/** RETREAT: hide from the danger, or walk away and hold; while regrouping, then walk to the rally point (CT-29). */`
2. **`tick`:** `danger().ifPresentOrElse(this::retreatFrom, this::hold);` → `danger().ifPresentOrElse(this::retreatFrom, this::withoutDanger);`
3. **`retreatFrom`:** caso nuevo `case RALLY -> rally(Optional.of(danger));`.
4. **`situation(danger)`:** suma, al final, `rallyStep(Optional.of(danger)).isPresent()`.
5. **Métodos privados nuevos:**

```java
// No one to keep clear of: walk straight to the rally point, or hold if there is none.
private void withoutDanger() {
  rallyStep(Optional.empty()).ifPresentOrElse(this::walkToRally, this::hold);
}

private void rally(Optional<Player> danger) {
  rallyStep(danger).ifPresentOrElse(this::walkToRally, this::holdInPlace);
}

private void walkToRally(Vec3 step) {
  coverSpot = Optional.empty();
  walkTo(step);
}

private Optional<Vec3> rallyStep(Optional<Player> danger) {
  return currentOrder()
      .flatMap(RoleAssignment::rallyPoint)
      .flatMap(point -> context.tools().rallyRoute().next(mobPosition(), point, danger.map(this::targetOf)));
}

private PlayerTarget targetOf(Player player) {
  return new PlayerTarget(
      PoseReader.poseOf(player),
      context.tools().weapons().bodies().playerReach().blocksOf(player));
}

private void walkTo(Vec3 target) {
  mob.getPathfinder()
      .moveTo(new Location(mob.getWorld(), target.x(), target.y(), target.z()), WALK_SPEED);
}
```

6. **`retreatStraight`** usa `walkTo(point.get())` en vez de su propio `moveTo` (el comportamiento no cambia).

Si `PoseReader.poseOf` o `playerReach().blocksOf` se llaman distinto, usá lo que usa `FlankGoal` y avisalo.

### `AdapterServices.java` (`goalInstaller`)

`new GoalTools(...)` suma, al final:

```java
new RallyRoute(
    new RallyDetour(geometry),
    core.settings().section(MobAiSettings::attack),
    core.settings().section(MobAiSettings::retreat))
```

(`geometry` es el `CombatGeometry` que ya existe en `goalInstaller`). Si `goalInstaller` pasa las 20 líneas, sacá el armado de `GoalTools` a un método privado `goalTools(CoreServices core, SharedParts parts, CombatGeometry geometry)` y avisalo.

### `docs/actualizar-paper.md`

En la fila `adapter/goal/*` no cambia lo que se usa de Paper (`Pathfinder.moveTo(Location, double)` ya está). Sumá al final de «Qué revisar»: `Que el pathfinder siga yendo al bloque alcanzable más cercano cuando el destino (el punto de reunión, CT-29) cae dentro de un cerro o en el aire.`

### `docs/arquitectura.md` (tabla «Nombres en el código»)

Fila nueva, debajo de la de `RallyPointRule`:

| Camino al punto de reunión, tramo, rodeo | `RallyRoute`, `RallyLeg`, `RallyDetour`, `RetreatMove.RALLY` | Adaptador y dominio |

## Pruebas obligatorias

En todas: el jugador está en `Vec3.ZERO` mirando a `(0, 0, 1)`; tolerancia `1e-9`.

### `RallyDetourTest` (6)

| Prueba | Tramo (`from` → `to`, `keepOut`) | Resultado |
| --- | --- | --- |
| `aClearLegGoesStraightToThePoint` | (-5,64,-5) → (5,64,-5), 4 | (5, 64, -5) |
| `aLegThroughTheReachGoesRoundTheBack` | (-5,64,-5) → (5,64,-5), 6 | (0, 64, -5√2) = (0, 64, -7,0710678118654755) |
| `aHalfTurnGoesRoundTheBack` | (-10,64,0) → (10,64,0), 4 | (-5√2, 64, -5√2) |
| `theShortWayRoundIsCappedAt45Degrees` | (-5, 64, -5√3) → (5, 64, -5√3), 9 | (10·sin 15°, 64, -10·cos 15°) = (2,588190451025208, 64, -9,659258262890683) |
| `aMobInsideTheReachStepsOutToIt` | (0,64,-3) → (0,64,10), 6 | (3√2, 64, -3√2) |
| `nonPositiveKeepOutIsRejected` | `new RallyLeg(pose, a, b, 0)` | `IllegalArgumentException` con `RallyLeg.keepOutBlocks must be a positive number, got 0.0` |

Antes de escribir las pruebas, verificá a mano el signo de `sideOf` con `(0, 0, 1)` y `(1, 0, 0)` (da -1) y que `rotateAroundVertical((0,0,1), -90)` da `(1, 0, 0)`. Si no, frená y reportá: los valores de la tabla dependen de esa convención.

### `RallyRouteTest` (4)

Con `new RallyRoute(new RallyDetour(new CombatGeometry()), () -> TestSettings.defaults().attack(), () -> TestSettings.defaults().retreat())`: margen de flanqueo 1,0 y llegada 3,0.

| Prueba | Verifica |
| --- | --- |
| `aMobAtThePointHolds` | mob en (10,64,0), punto en (12.5,64,0), con el jugador → vacío |
| `arrivalIsMeasuredHorizontally` | mob en (10,64,0), punto en (10,70,2), con el jugador → vacío |
| `withoutDangerTheMobWalksStraightToThePoint` | mob en (30,64,0), punto en (-30,64,0), sin jugador → (-30, 64, 0) |
| `keepOutIsTheReachPlusTheFlankMargin` | mob en (-5,64,-5), punto en (5,64,-5): con alcance 3 (fuera de 4) → (5, 64, -5); con alcance 4,5 (fuera de 5,5) → (0, 64, -5√2) |

### `RetreatSituationTest` (+2)

Las 5 pruebas existentes suman `false` al final del constructor.

| Prueba | Verifica |
| --- | --- |
| `hiddenAndFarWalksToAPendingRallyPoint` | `new RetreatSituation(true, true, false, false, true).nextMove()` es `RALLY` |
| `seenWithARallyPendingStillRetreats` | `new RetreatSituation(false, true, false, true, true).nextMove()` es `SEARCH_COVER` |

Total: **12 pruebas nuevas** (6 + 4 + 2).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Sin el `Math.clamp` del giro | gira 60°: (5, 64, -8,66) | `theShortWayRoundIsCappedAt45Degrees` |
| 2 | Sin el desempate de media vuelta (devolver `turn` tal cual) | gira -45° por delante: (-7,07, 64, 7,07) | `aHalfTurnGoesRoundTheBack` |
| 3 | Radio sin `Math.max(keepOut, …)` | radio 3: (2,12, 64, -2,12) | `aMobInsideTheReachStepsOutToIt` |
| 4 | En `RetreatSituation`, `HOLD` aunque haya punto pendiente | `HOLD` | `hiddenAndFarWalksToAPendingRallyPoint` |
| 5 | En `RallyRoute`, llegada fija en 2 bloques | (12.5, 64, 0) en vez de vacío | `aMobAtThePointHolds` |

## Verificación en el juego (Nico, después del merge)

**Antes:** el `config.yml` del server de prueba ya tiene que tener `rally-distance-blocks` y `rally-arrival-blocks` (WP-32A).

1. `/mobai spawngroup` de noche, `/mobai debug all full`. Peleá hasta que el grupo se retire en bloque (`/mobai status` muestra `REGROUPING`).
2. Quedate quieto mirando a los mobs: se alejan y se esconden como antes; cuando dejás de verlos, caminan hacia un mismo lugar, unos 12 bloques más allá del centro del grupo, y ahí se juntan.
3. Si caminan hacia el punto y te tienen que rodear, no te pasan por delante a menos de tu alcance: dan la vuelta por tu espalda.
4. Si los perseguís, vuelven a huir; cuando los perdés de vista, retoman el camino al punto.
5. Juntos y curados, el reagrupamiento termina y vuelven a atacar a la vez.
6. Si un punto cae dentro de un cerro, los mobs llegan lo más cerca que pueden y se quedan ahí.

## Procedimiento

1. Rama `wp-32b-reunion-goals` desde `origin/main` actualizado (con el WP-32A).
2. `RallyLeg`, `RallyDetour` y su prueba. Commit: `feat: rally detour round the player`.
3. `RallyRoute` y su prueba; `GoalTools`. Commit: `feat: rally route with arrival and keep-out`.
4. `RetreatMove`, `RetreatSituation` y su prueba; `RetreatGoal` y `AdapterServices`. Commit: `feat: regrouping mobs walk to the rally point (CT-29)`.
5. `docs/actualizar-paper.md` y `docs/arquitectura.md`. Commit: `docs: map and names for the rally route`.
6. Pruebas que muerden, de a una y sin commit.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-32B: regrouping mobs walk to the rally point`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Formateá a mano con el estilo de google-java-format: 2 espacios, 100 columnas y la misma forma de partir líneas que el código vecino.
- `RallyDetour`, `RallyRoute`, `RetreatSituation` y sus pruebas no usan Paper: compilan con el JDK 21 del contenedor (`javac --release 21`, junto con `domain/` y lo que haga falta de `testsupport`). Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central (si da 429, de `https://repo.maven.apache.org/maven2/`) y corré las pruebas y las roturas con los jars explícitos en `-cp`. Ojo: `Math.clamp` es de Java 21, así que compila.
- `RetreatGoal`, `GoalTools` y `AdapterServices` usan Paper: los verifica el CI del PR. Esperá a que termine; si falla, leé el log, corregí y volvé a empujar.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Buscar un punto alcanzable (en el suelo, fuera de un cerro) en vez del geométrico.
- Mostrar el punto en el `mobai-debug.log` o en `/mobai status`.
- Cambiar cuándo termina el reagrupamiento.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 12 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
