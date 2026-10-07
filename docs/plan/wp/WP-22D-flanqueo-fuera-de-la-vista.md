# WP-22D — Flanqueo fuera de la vista

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-22C (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-22d-flanqueo-fuera-de-la-vista` |

## Objetivo

Arreglar el bug B-02 (los flanqueadores nunca golpean) con el modelo de flanqueo de Nico (CT-16):

> El jugador tiene un rango efectivo en forma de abanico: lo que ve y el alcance que tiene. El mob que flanquea evita meterse en ese abanico. Primero sale de la vista del jugador por el camino más corto, sin alejarse de más. Una vez fuera de la vista, al atacar, busca la espalda. La vista son los 90° más un margen, para que un movimiento corto del mouse no lo descubra.

Por qué fallaba (`verificacion-e6.md`, corrida 1):

1. **H1, confirmada por lo que vio Nico** (los zombies se movían pero nunca parecían flanquear). El punto de flanqueo se calculaba según la mirada del jugador. Un jugador que gira para seguir al mob gira también el punto, así que el mob da vueltas sin quedar nunca afuera del arco del escudo.
2. **H4, encontrada al releer el código.** El punto de flanqueo estaba a 3 bloques del jugador y el alcance de golpe es de 2. Un flanqueador que llegaba a su punto **no podía golpear** aunque estuviera en la espalda.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`, `FlankFormation.java`, `FlankQuery.java`, `PlayerPose.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`, `Vec3.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java`, `FlankGoal.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`)
- `src/main/resources/config.yml` (sección `attack`)
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Vista del jugador:** el mob está **a la vista** si su ángulo respecto de la mirada del jugador (en horizontal) es de **120° o menos**: los 90° del arco del escudo más un margen de 30° (`VISION_MARGIN_DEGREES`). Más de 120°: **fuera de la vista**.
2. **Mientras el jugador lo ve, el flanqueador esquiva** (paso «triangular»). Cada 10 ticks del reloj camina hacia el punto que está:
   - **del mismo lado** en el que ya está (el lado de `CombatGeometry`), así nunca cruza por delante del jugador;
   - a un ángulo `φ = min(θ + 45°, 135°)`, donde `θ` es su ángulo actual. Gira de a 45° como máximo, y la salida es a 135°, 15° adentro de la zona fuera de la vista. 135° es el primer puesto de la formación (CT-14);
   - a una distancia `r = max(K, d · cos(φ − θ))` del jugador, donde `d` es su distancia actual y `K` es `attack.flank-distance-blocks`, la distancia que mantiene mientras lo ven (4 por defecto). `d · cos(φ − θ)` es el pie de la perpendicular: el camino más corto hacia esa dirección, sin alejarse. `K` impide meterse en el alcance del jugador (3 bloques en supervivencia).
   - Las patas sucesivas de giros de 45° arman el recorrido en triángulos alrededor del jugador. Ninguna pata pasa a menos de `r` del jugador, porque cada destino es el pie de la perpendicular.
3. **Fuera de la vista, busca la espalda:** camina a **su puesto de la formación** (CT-14: 135°, 165° o 180° según cuántos flanqueadores haya de su lado), a `CLOSE_IN_BLOCKS` = 1,5 bloques del jugador, **dentro del alcance de golpe** (2 bloques).
4. **Golpea** si está en alcance, pasó el intervalo de ataque y está **fuera de la vista** (regla 1). Antes alcanzaba con estar a más de 90°. El ataque registrado no cambia: `ZOMBIE_FLANK_STRIKE` o `SPIDER_BITE`.
5. **Todo se recalcula cada 10 ticks** con la mirada del momento. Si el jugador se da vuelta y lo vuelve a ver, el mob vuelve a esquivar desde donde está, hacia su propio lado.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/FlankStep.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/FlankManeuver.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FlankGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` (solo el `3.0` de `flankDistanceBlocks` del valor esperado pasa a `4.0`: compara contra el `config.yml` incluido) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/geometry/FlankManeuverTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |

**Métodos públicos:**
- `CombatGeometry.isOutOfSight` es nuevo (8 en total): es geometría de combate, como `isInShieldArc`.
- `Waypoints` cambia `flankPoint` por `flankStep` y `isOutsideTheShieldArc` por `isOutOfSight`. Sigue con 4.

## Especificación

### `CombatGeometry.java`

```java
  // 90° of shield arc plus a margin, so a small turn of the mouse does not reveal a flanker.
  private static final double VISION_MARGIN_DEGREES = 30.0;
  static final double VISION_HALF_ANGLE_DEGREES =
      MinecraftConstants.SHIELD_HALF_ARC_DEGREES + VISION_MARGIN_DEGREES;

  public boolean isOutOfSight(PlayerPose pose, Vec3 mobPosition) {
    return angleFromFacingDegrees(pose, mobPosition) > VISION_HALF_ANGLE_DEGREES;
  }
```

`FLANK_ANGLE_DEGREES` (135) queda como está. El constructor estático no cambia.

### `FlankStep.java`

```java
/** Where a flanker walks next, and whether it already is out of the player's sight. */
public record FlankStep(Vec3 waypoint, boolean outOfSight) {
  public FlankStep {
    Objects.requireNonNull(waypoint, "FlankStep.waypoint");
  }
}
```

### `FlankManeuver.java`

```java
/**
 * The flank in two phases (CT-16): while the player sees it, the flanker sidesteps out of sight
 * by the shortest way; once out of sight, it closes in on its slot behind the player.
 */
public final class FlankManeuver {
  // At most this much further round per step: each leg stays outside the keep-out distance.
  private static final double EVADE_STEP_DEGREES = 45.0;
  // Inside melee reach, with room for the player stepping away.
  static final double CLOSE_IN_BLOCKS = MinecraftConstants.MELEE_REACH_BLOCKS * 0.75;

  private final CombatGeometry geometry;
  private final FlankFormation formation;

  public FlankManeuver(CombatGeometry geometry, FlankFormation formation) {
    this.geometry = Objects.requireNonNull(geometry, "FlankManeuver.geometry");
    this.formation = Objects.requireNonNull(formation, "FlankManeuver.formation");
  }

  /** {@code query.distanceBlocks()} is how far the flanker keeps while the player sees it. */
  public FlankStep next(FlankQuery query) {
    Vec3 position = query.flankers().get(query.self());
    if (geometry.isOutOfSight(query.pose(), position)) {
      return new FlankStep(closeIn(query), true);
    }
    return new FlankStep(sidestep(query.pose(), position, query.distanceBlocks()), false);
  }

  private Vec3 closeIn(FlankQuery query) {
    return formation.pointFor(
        new FlankQuery(query.pose(), query.self(), query.flankers(), CLOSE_IN_BLOCKS));
  }

  // The foot of the perpendicular on the next direction: the shortest way round, never closer
  // than the keep-out distance.
  private Vec3 sidestep(PlayerPose pose, Vec3 position, double keepOutBlocks) {
    double angle = geometry.angleFromFacingDegrees(pose, position);
    double nextAngle = Math.min(angle + EVADE_STEP_DEGREES, CombatGeometry.FLANK_ANGLE_DEGREES);
    double distance = position.minus(pose.position()).horizontal().length();
    double radius =
        Math.max(keepOutBlocks, distance * Math.cos(Math.toRadians(nextAngle - angle)));
    int side = CombatGeometry.sideOf(pose.facing(), position.minus(pose.position()).horizontal());
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), side * nextAngle);
    return pose.position().plus(direction.times(radius));
  }
}
```

El `0.75` del `CLOSE_IN_BLOCKS` lleva el comentario de arriba. Si Spotless o la regla de números mágicos lo marcan, sacalo a una constante `private static final double CLOSE_IN_FRACTION_OF_REACH = 0.75;` con ese comentario.

### `Waypoints.java`

- El constructor pasa a ser `Waypoints(CombatGeometry geometry, FlankManeuver maneuver, Supplier<AttackSettings> settings)`, con el campo `maneuver` en lugar de `formation` y el mensaje `"Waypoints.maneuver"`.
- `flankPoint` se reemplaza por:

```java
  public FlankStep flankStep(PlayerPose pose, MobId self, Map<MobId, Vec3> flankers) {
    return maneuver.next(
        new FlankQuery(pose, self, flankers, settings.get().flankDistanceBlocks()));
  }
```

- `isOutsideTheShieldArc` se reemplaza por:

```java
  public boolean isOutOfSight(PlayerPose pose, Vec3 mobPosition) {
    return geometry.isOutOfSight(pose, mobPosition);
  }
```

- `retreatPoint` y `coverCandidates` no cambian.

### `FlankGoal.java`

- `walkRoundIfDue`: el punto pasa a ser `context.tools().waypoints().flankStep(pose, self(), flankerPositions(target)).waypoint()`. El resto no cambia.
- `strikeIfOutsideTheShield` se renombra `strikeIfOutOfSight` y usa `waypoints.isOutOfSight(pose, position)`.
- El Javadoc de la clase pasa a: `/** FLANK: sidestep out of the player's sight, then close in on its slot and strike from behind. */`.
- El comentario de `executedAttack` pasa a: `// A flanker strikes from out of the player's sight, which is what the flank strike is (CT-16).`

### `AdapterServices.java`

En `goalInstaller`: `new Waypoints(geometry, new FlankManeuver(geometry, new FlankFormation(geometry)), core.settings().section(MobAiSettings::attack))`.

### `config.yml` y `TestSettings.java`

- `config.yml`, sección `attack`: `flank-distance-blocks: 4.0`, con el comentario de una línea `# Distancia que mantiene el flanqueador mientras el jugador lo ve (el alcance del jugador es 3).`
- `TestSettings`: el `3.0` de `AttackSettings` (sexto argumento, `flankDistanceBlocks`) pasa a `4.0`.

## Pruebas obligatorias

Jugador en el origen mirando a +Z, distancia de esquive `K` = 4, tolerancia `1e-9`. `mob(n)` = `new MobId(new UUID(0, n))`. `maneuver = new FlankManeuver(geometry, new FlankFormation(geometry))`. Cada consulta lleva solo al flanqueador que pregunta, salvo que se diga otra cosa.

### `FlankManeuverTest` (8)

| Prueba | Flanqueador en | Verifica |
| --- | --- | --- |
| `seenStraightAheadSidestepsFortyFiveDegrees` | `(0,0,5)` (0°, 5 bloques) | `outOfSight` `false`; punto `(-2.8284271247461903, 0, 2.8284271247461903)` (45°, `r` = máx(4; 3,54) = 4) |
| `sidestepStaysOnTheMobsSide` | `(3,0,4)` (36,87°, lado −1) | punto `(3.959797974644666, 0, 0.5656854249492387)` |
| `farFlankerTakesTheShortestWay` | `(0,0,20)` | punto `(-10, 0, 10)` (`r` = 20 · cos 45° = 14,14) |
| `lastSidestepExitsAtTheFlankAngle` | `(-5.908846518073248, 0, -1.0418890660015818)` (100°, 6 bloques) | `outOfSight` `false`; punto `(-3.4753677920374146, 0, -3.475367792037414)` (135°, `r` = 6 · cos 35° = 4,9149) |
| `sidestepNeverGoesInsideTheKeepOutDistance` | `(0,0,2)` (0°, 2 bloques) | la distancia horizontal del punto al jugador es 4 |
| `outOfSightClosesInOnItsSlot` | `(2,0,-2)` (135°) | `outOfSight` `true`; punto `(1.0606601717798214, 0, -1.0606601717798212)` |
| `closeInPointIsWithinStrikeReach` | `(0,0,-5)` (180°) | la distancia del punto al jugador es 1,5, menor que `MinecraftConstants.MELEE_REACH_BLOCKS`. **Es la regresión de la H4** |
| `closeInUsesTheFormation` | `mob(1)` en `(2,0,-1)` (116,6°, a la vista) y `mob(2)` en `(2,0,-3)` (146,3°, fuera de la vista) | `next` para `mob(2)`: `outOfSight` `true` y punto igual a `formation.pointFor` con los dos flanqueadores y distancia 1,5 |

### `CombatGeometryTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `beyondOneHundredTwentyDegreesIsOutOfSight` | mirando a +Z: `(-1.7143346014042247, 0, -1.0300761498201085)` (121°) `true`; `(0,0,-2)` `true` |
| `withinOneHundredTwentyDegreesIsInSight` | `(-1.7492394142787917, 0, -0.969619240492674)` (119°) `false`; `(2,0,0)` (90°) `false` |

### `WaypointsTest` (2 reemplazadas)

Con `new Waypoints(geometry, new FlankManeuver(geometry, new FlankFormation(geometry)), () -> TestSettings.defaults().attack())`.

| Prueba | Reemplaza a | Verifica |
| --- | --- | --- |
| `flankStepKeepsTheConfiguredDistanceWhileSeen` | `flankPointUsesTheConfiguredDistance` | flanqueador en `(0,0,2)`: punto a 4 bloques en horizontal |
| `onlyOutOfSightCountsAsFlank` | `onlyOutsideTheShieldArcCountsAsFlank` | `(0,0,-2)` `true`; `(2,0,0)` `false`; `(0,0,2)` `false` |

Las pruebas de retirada y de cubierto de `WaypointsTest` no cambian.

Total: **10 pruebas nuevas** y 2 reemplazadas.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `CLOSE_IN_BLOCKS` = 3.0 (el valor viejo) | punto a 3 bloques | `closeInPointIsWithinStrikeReach` |
| 2 | En `sidestep`, `radius = keepOutBlocks` (sin el pie de la perpendicular) | `(-2.83, 0, 2.83)` en vez de `(-10, 0, 10)` | `farFlankerTakesTheShortestWay` |
| 3 | En `sidestep`, sin el `Math.min` con `FLANK_ANGLE_DEGREES` | a 100° gira a 145° | `lastSidestepExitsAtTheFlankAngle` |
| 4 | `isOutOfSight` con `>=` y `SHIELD_HALF_ARC_DEGREES` (90°, la regla vieja) | 119° da `true` | `withinOneHundredTwentyDegreesIsInSight` |

## Verificación en el server (Nico, después del merge)

Con `/mobai debug all full`, de noche, repitiendo `spawngroup` hasta que `/mobai status` muestre `FLANK`:

1. Mirando a un flanqueador, se corre de costado en diagonales y nunca se te mete adelante a menos de 4 bloques.
2. Si lo seguís con la mirada, sigue corriéndose hacia el mismo lado.
3. Si lo perdés de vista (más de 120° de tu mirada), se te pega a la espalda y golpea: en `mobai-debug.log` aparece `attack=zombie.flank_strike`.

## Procedimiento

1. Rama `wp-22d-flanqueo-fuera-de-la-vista` desde `origin/main` actualizado.
2. `CombatGeometry`, `FlankStep`, `FlankManeuver` y sus pruebas. Commit: `feat: flank maneuver that sidesteps out of sight, then closes in (CT-16)`.
3. `Waypoints`, `FlankGoal`, `AdapterServices`, `config.yml`, `TestSettings` y `WaypointsTest`. Commit: `fix: flankers strike from out of sight and within reach (B-02)`.
4. Pruebas que muerden (de a una, en secuencia; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-22D: flank out of sight (B-02, CT-16)`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una prueba existente deja de pasar por el cambio de `TestSettings` (de 3.0 a 4.0), frená y reportá cuál y por qué; no la cambies.
3. Si una prueba existente usa `Waypoints.flankPoint` o `isOutsideTheShieldArc` fuera de `WaypointsTest`, frená y reportá.

## Fuera de alcance

- Cambiar la formación (CT-14), la retirada o la curación.
- Tener en cuenta los bloques en la vista del jugador para el flanqueo (es solo geometría; la línea de visión con bloques es de la retirada, CT-15).
- Más de un paso planeado por adelantado: el paso se recalcula cada 10 ticks.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 10 pruebas nuevas y las 2 reemplazadas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
