# WP-23D — Esquivo calculado

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | B-04 (#47, mergeado) |
| Modelo | Sonnet |
| Rama | `wp-23d-esquivo-calculado` |

## Objetivo

CT-25, diseño de Nico; arregla el B-05. El zombie esquivo (`zombie.evasive_strike`) deja de usar un umbral fijo (0,8) y una espera agotada (3 s). Pasa a decidir **con tiempos calculados**:

- **Cuánto le falta al arma del jugador** para estar al 100 %. Sale de la carga actual y de lo que tarda esa arma en cargar.
- **Cuánto tarda el zombie** en salir del alcance del jugador, o en entrar, pegar y salir. Sale de **su velocidad real**: el atributo de velocidad, que ya incluye pociones y buffs.

| Situación | Qué hace |
| --- | --- |
| El jugador no lo mira | Entra y pega |
| El jugador lo mira, cubierto con el escudo y con el arma al 100 % | El golpe es inevitable: se pone **a un costado de la mira**, a su alcance, y le pega al escudo para desgastarlo |
| Le sobra tiempo para salir antes del 100 % | Entra y pega (o sigue pegando) |
| Dentro del alcance, con el tiempo justo para salir | Retrocede: al 100 % ya está afuera |
| Dentro del alcance, sin tiempo para salir | Pega igual: el golpe del jugador le llega de todos modos (Nico) |
| Afuera, sin tiempo para entrar, pegar y salir | Espera en el borde, mirando |

La carga por espera agotada (`CHARGE`) desaparece: era la causa del B-05.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` (`flankPoint`, `sideOf`, `rotateAroundVertical`)
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (método `attack`), `src/main/resources/config.yml` (sección `attack`)
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/`: `PressGoal`, `EvasiveWait`, `EvasiveMove`, `PlayerThreat`, `PlayerReach`, `Weapons`, `Waypoints`, `MeleeRhythm`, `FlankGoal`, `FallBackGoal`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo donde se arma `Weapons`)
- Pruebas: `EvasiveWaitTest`, `WaypointsTest`, `CombatGeometryTest`, `SettingsValidationTest`, `ConfigLoaderTest`, `testsupport/TestSettings`

## Reglas de negocio

1. **Tiempo de carga que le falta al jugador** (ticks): `(1 − getAttackCooldown()) × getCooldownPeriod()`. Con el arma al 100 % da 0.
2. **Tiempo para cubrir una distancia** (`EscapeTiming`): física de Minecraft para un mob que camina en el suelo y **arranca quieto**. Cada tick:
   - `v += a`, el mob avanza `v` y después `v *= 0,546`;
   - `a = 0,98 × s²`, donde `s` es el atributo de velocidad del mob por el multiplicador del pathfinder (`WALK_SPEED`, 1,0);
   - `0,98` escala la entrada de avance de un mob (`LivingEntity.aiStep`);
   - `0,546` es la fricción del suelo, `0,6 × 0,91`.
   - Un zombie (`s = 0,23`) cubre 1 bloque en 10 ticks y 2 bloques en 19.
   - El tope es de 200 ticks: un mob que no se mueve "nunca llega".
3. **Distancia de peligro:** alcance del jugador (`PlayerReach`, CT-24) + `evasive-margin-blocks` (0,5), en horizontal.
4. **Tiempo que necesita el zombie:**
   - **dentro** de la distancia de peligro: lo que tarda en salir, `peligro − distancia`;
   - **afuera:** lo que tarda en entrar hasta su alcance de golpe (`distancia − MELEE_REACH_BLOCKS`), más lo que tarda en salir desde ahí (`peligro − MELEE_REACH_BLOCKS`).
5. **Decisión** (`EvasiveRules`, pura), en este orden:
   1. el jugador no lo mira (fuera de su vista, `isOutOfSight`) → `STRIKE_EVASIVE`;
   2. lo mira, se cubre (`isBlocking`) y tiene el arma al 100 % → `SIDE_STEP`;
   3. `falta de carga > tiempo necesario + evasive-safety-ticks` → `STRIKE_EVASIVE` (le sobra tiempo);
   4. afuera de la distancia de peligro → `HOLD`;
   5. dentro, con `falta de carga >= tiempo necesario` → `BACK_OFF` (llega justo);
   6. dentro, con `falta de carga < tiempo necesario` → `STRIKE_EVASIVE`: ya no llega a salir y el golpe le va a llegar igual, así que pega primero.

   `evasive-safety-ticks` (4) cubre la demora de reacción: el goal decide uno sí y uno no de los ticks del juego, y el camino tarda en arrancar. No depende de la velocidad del mob, que sí sale del cálculo.
6. **Al costado de la mira** (`SIDE_STEP`):
   - el zombie va a un punto a `MELEE_REACH_BLOCKS − 0,5` (1,5 bloques) del jugador, girado desde la mira hacia el lado donde ya está;
   - el ángulo es `atan(medio ancho del mob / 1,5)` + `evasive-aim-margin-degrees` (15). Para un zombie da 26,3°;
   - dentro de su alcance, pega (`ZOMBIE_EVASIVE_STRIKE`) con el ritmo de siempre: el golpe da en el escudo y lo desgasta.
7. **Reacción:** la decisión se toma en cada tick del goal.
   - `moveTo` sale en el acto cuando cambia el movimiento, y después cada 10 ticks (`MeleeRhythm`), como hoy el `BACK_OFF`.
   - Al pasar de `BACK_OFF`, `HOLD` o `SIDE_STEP` a `STRIKE_EVASIVE`, persigue en el acto (`chaseOn`).
8. **Configuración:**
   - se va `evasive-charge-threshold`;
   - llegan `evasive-safety-ticks: 4` y `evasive-aim-margin-degrees: 15.0`.
   - `patient-strike-max-wait-ticks` lo sigue usando el golpe paciente; el esquivo ya no.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/SideStep.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MobSpeed.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Bodies.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Weapons.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EscapeTiming.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveReading.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveRules.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveMove.java` |
| Borrar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveWait.java` |
| Borrar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PlayerThreat.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FlankGoal.java` (una línea) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FallBackGoal.java` (una línea) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (una línea) |
| Modificar | `docs/catalogo-mvp.md`, `docs/arquitectura.md`, `docs/actualizar-paper.md` |
| Borrar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/EvasiveWaitTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/EscapeTimingTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/EvasiveRulesTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |

Antes de empezar, buscá con grep:
- `new AttackSettings(`, `new Weapons(`, `playerReach()`, `EvasiveWait`, `PlayerThreat`, `EvasiveMove.CHARGE` y `evasiveChargeThreshold` en `src/`. Si alguno aparece fuera de los archivos de la tabla, frená y reportá.
- Que los nombres de las pruebas nuevas no existan ya.

**Métodos públicos nuevos:**
- `CombatGeometry.sideStepPoint`: 12 en total;
- `Waypoints.sideStepPoint`: 13 en total;
- `VersionTranslator.movementSpeed`.

Si alguno pasa de 20, frená y reportá.

## Especificación

**API de Paper nueva:**
- `HumanEntity.getCooldownPeriod()`: los ticks que tarda en cargar el arma en la mano.
- `Entity.getWidth()`.
- `Attribute.MOVEMENT_SPEED`, solo en `VersionTranslator`.

Si `getCooldownPeriod()` no existe en la API de Paper 26.3, **frená y reportá**: no la reemplaces por un valor fijo.

### `MinecraftConstants.java`

```java
  // LivingEntity.aiStep scales a mob's forward input before it moves.
  public static final double MOB_MOVE_INPUT_SCALE = 0.98;
  // On ordinary ground a walking entity keeps this share of its speed each tick (friction 0.6 × 0.91).
  public static final double GROUND_DRAG_PER_TICK = 0.546;
  // A zombie's base movement speed; every mob has the attribute, so it only guards a missing one.
  public static final double DEFAULT_MOB_MOVEMENT_SPEED = 0.23;
```

### `SideStep.java` y `CombatGeometry.sideStepPoint`

```java
package io.github.nicodoou.mobai.domain.geometry;

/** Where a mob stands beside the player's aim: how far from the player, and how far round. */
public record SideStep(double distanceBlocks, double angleDegrees) {}
```

`SideStep` no valida: la distancia la valida `sideStepPoint` con `requirePositiveDistance`, como `flankPoint`.

```java
  /** A point beside the player's aim, turned towards the side the mob is already on. */
  public Vec3 sideStepPoint(PlayerPose pose, Vec3 mobPosition, SideStep step) {
    requirePositiveDistance(step.distanceBlocks());
    Vec3 offset = mobPosition.minus(pose.position()).horizontal();
    int side = sideOf(pose.facing(), offset);
    Vec3 direction = rotateAroundVertical(pose.facing(), side * step.angleDegrees());
    return pose.position().plus(direction.times(step.distanceBlocks()));
  }
```

Es el mismo cálculo que `flankPoint` con otro ángulo. Dejá `flankPoint` como está; no lo reescribas en función de este método.

### `AttackSettings.java`, `ConfigLoader.java` y `config.yml`

**Componentes:**
- se borra `evasiveChargeThreshold` y su validación;
- al final, después de `evasiveMarginBlocks`, se suman `long evasiveSafetyTicks` y `double evasiveAimMarginDegrees`.

El orden queda: `projectileTimeoutTicks, patientStrikeMaxWaitTicks, opportunisticShotMaxWaitTicks, shootMinDistanceBlocks, shootMaxDistanceBlocks, flankMarginBlocks, retreatDistanceBlocks, evasiveMarginBlocks, evasiveSafetyTicks, evasiveAimMarginDegrees`.

```java
    SettingsChecks.requireAtLeast("AttackSettings.evasiveSafetyTicks", evasiveSafetyTicks, 0);
    SettingsChecks.requireBetween(
        "AttackSettings.evasiveAimMarginDegrees", evasiveAimMarginDegrees, 0, 90);
```

`ConfigLoader.attack` sigue el mismo orden: borra la lectura de `attack.evasive-charge-threshold` y suma `wholeNumber(root, "attack.evasive-safety-ticks")` y `number(root, "attack.evasive-aim-margin-degrees")`.

En `config.yml`, se borra `evasive-charge-threshold` con su comentario. Debajo de `evasive-margin-blocks`:

```yaml
  # Golpe esquivo: ticks de más para reaccionar (el goal decide uno sí y uno no, y el camino tarda en
  # arrancar) y grados de más, además del ancho del mob, que se aparta de la mira del jugador que se cubre.
  evasive-safety-ticks: 4
  evasive-aim-margin-degrees: 15.0
```

Revisá que el comentario de `evasive-margin-blocks` no hable del umbral; si habla, dejalo solo con lo del margen.

### `VersionTranslator.java`

```java
  public double movementSpeed(LivingEntity entity) {
    return attributeValue(entity, Attribute.MOVEMENT_SPEED)
        .orElse(MinecraftConstants.DEFAULT_MOB_MOVEMENT_SPEED);
  }
```

### `MobSpeed.java`, `Bodies.java` y `Weapons.java`

```java
/** The mob's movement speed attribute, potions and buffs included. */
@FunctionalInterface
public interface MobSpeed {
  double of(Mob mob);
}
```

```java
/** What the goals read of bodies on the field: how far the player hits, how fast a mob walks. */
public record Bodies(PlayerReach playerReach, MobSpeed mobSpeed) {
  public Bodies {
    Objects.requireNonNull(playerReach, "Bodies.playerReach");
    Objects.requireNonNull(mobSpeed, "Bodies.mobSpeed");
  }
}
```

- **`Weapons`:** el tercer componente pasa a ser `Bodies bodies`, con `requireNonNull(bodies, "Weapons.bodies")`.
- **`AdapterServices`:** `new Weapons(attacker, bow, new Bodies(parts.translator()::playerReach, parts.translator()::movementSpeed))`.
- **`FlankGoal`, `FallBackGoal` y `PressGoal`:** `weapons().playerReach()` pasa a `weapons().bodies().playerReach()`.

### `EscapeTiming.java` (pura)

```java
/** How long a mob walking on the ground takes to cover a distance, starting from standing still. */
final class EscapeTiming {
  // Longer than any weapon's recharge: a mob this slow never makes it in time.
  static final long MAX_TICKS = 200;

  private EscapeTiming() {}

  static long ticksToCover(double distanceBlocks, double speed) {
    if (distanceBlocks <= 0) {
      return 0;
    }
    double acceleration = MinecraftConstants.MOB_MOVE_INPUT_SCALE * speed * speed;
    double velocity = 0;
    double covered = 0;
    for (long tick = 1; tick <= MAX_TICKS; tick++) {
      velocity += acceleration;
      covered += velocity;
      if (covered >= distanceBlocks) {
        return tick;
      }
      velocity *= MinecraftConstants.GROUND_DRAG_PER_TICK;
    }
    return MAX_TICKS;
  }
}
```

Con `speed <= 0` el lazo llega al tope y devuelve `MAX_TICKS`, y está bien.

### `EvasiveMove.java`, `EvasiveReading.java` y `EvasiveRules.java` (puras)

```java
/** What an evasive zombie does this tick (CT-25). */
public enum EvasiveMove {
  STRIKE_EVASIVE,
  SIDE_STEP,
  BACK_OFF,
  HOLD
}
```

```java
/**
 * What an evasive zombie reads of the player this tick: whether it is watched, whether a hit is
 * unavoidable (shield up and weapon charged), how many ticks the weapon still needs to recharge,
 * how many the zombie needs (to get out, or to come in, strike and get out) and whether it is
 * within the player's reach.
 */
record EvasiveReading(
    boolean watched,
    boolean shieldedAndCharged,
    double chargeTicksLeft,
    long ticksNeeded,
    boolean withinReach) {}
```

```java
/** Dodges the player's charged hits with the timing worked out, not guessed (CT-25). */
final class EvasiveRules {
  private EvasiveRules() {}

  static EvasiveMove next(EvasiveReading reading, long safetyTicks) {
    if (!reading.watched()) {
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (reading.shieldedAndCharged()) {
      return EvasiveMove.SIDE_STEP;
    }
    if (reading.chargeTicksLeft() > reading.ticksNeeded() + safetyTicks) {
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (!reading.withinReach()) {
      return EvasiveMove.HOLD;
    }
    // Too late to get out: the player's hit lands anyway, so it lands one of its own first.
    return reading.chargeTicksLeft() >= reading.ticksNeeded()
        ? EvasiveMove.BACK_OFF
        : EvasiveMove.STRIKE_EVASIVE;
  }
}
```

### `Waypoints.java`

```java
  // Half a block inside the zombie's reach, so a short step of the player does not take it out.
  private static final double SIDE_STEP_SLACK_BLOCKS = 0.5;

  /** Beside the player's aim: its hitbox clear of the crosshair, plus a margin. */
  public Vec3 sideStepPoint(PlayerPose pose, Vec3 mobPosition, double mobHalfWidthBlocks) {
    double distance = MinecraftConstants.MELEE_REACH_BLOCKS - SIDE_STEP_SLACK_BLOCKS;
    double angle =
        Math.toDegrees(Math.atan(mobHalfWidthBlocks / distance))
            + settings.get().evasiveAimMarginDegrees();
    return geometry.sideStepPoint(pose, mobPosition, new SideStep(distance, angle));
  }
```

### `PressGoal.java`

Borrá `evasion`, el uso de `EvasiveWait`, `threatOf` y todo lo de `CHARGE`. `lastEvasiveMove` se queda (arranca en `HOLD`).

```java
  private void evade(Player target) {
    EvasiveMove move = EvasiveRules.next(readingOf(target), attack().evasiveSafetyTicks());
    switch (move) {
      case STRIKE_EVASIVE -> engage(target);
      case SIDE_STEP -> sideStep(target);
      case BACK_OFF -> backOff(target);
      case HOLD -> mob.getPathfinder().stopPathfinding();
    }
    lastEvasiveMove = move;
  }
```

| Función | Hace |
| --- | --- |
| `EvasiveReading readingOf(Player target)` | Posición del mob y del jugador. `watched = !waypoints().isOutOfSight(PoseReader.poseOf(target), mobPosition)`. `distance` en horizontal. `danger = reachOf(target) + attack().evasiveMarginBlocks()`. Devuelve `new EvasiveReading(watched, isShieldedAndCharged(target), chargeTicksLeft(target), ticksNeeded(distance, danger), distance <= danger)` |
| `static boolean isShieldedAndCharged(Player player)` | `player.isBlocking() && player.getAttackCooldown() >= FULL_ATTACK_COOLDOWN` |
| `static double chargeTicksLeft(Player player)` | `(FULL_ATTACK_COOLDOWN - player.getAttackCooldown()) * player.getCooldownPeriod()`, con piso en 0 (`Math.max`) |
| `long ticksNeeded(double distance, double danger)` | `speed = context.tools().weapons().bodies().mobSpeed().of(mob) * WALK_SPEED`. Dentro (`distance <= danger`): `EscapeTiming.ticksToCover(danger - distance, speed)`. Afuera: `ticksToCover(distance - MELEE_REACH_BLOCKS, speed) + ticksToCover(danger - MELEE_REACH_BLOCKS, speed)` |
| `void engage(Player target)` | Como el `engage` actual pero sin el parámetro del ataque: `chaseOn(target)` y `strikeEvasiveIfInReach(target)` |
| `void strikeEvasiveIfInReach(Player target)` | Si `rhythm.canStrike(distancia 3D)`: `melee().strike(mob, target, Attack.ZOMBIE_EVASIVE_STRIKE)` y `rhythm.markStrike()` |
| `void sideStep(Player target)` | Si `lastEvasiveMove != SIDE_STEP` o `rhythm.shouldRepath()`: punto `waypoints().sideStepPoint(PoseReader.poseOf(target), posición del mob, mob.getWidth() / 2)`, `moveTo(new Location(...), WALK_SPEED)` y `rhythm.markRepath()`. Después, `strikeEvasiveIfInReach(target)` |
| `chaseOn` | Persigue en el acto si `lastEvasiveMove` es `BACK_OFF`, `HOLD` o `SIDE_STEP` |

`backOff` no cambia. Ninguna función pasa de 20 líneas; si alguna pasa, separala y avisá.

### Documentos

- **`docs/catalogo-mvp.md`, fila del golpe esquivo**, columna de qué hace: "Calcula cuánto le falta al arma del jugador para cargar y cuánto tarda él, con su velocidad real, en salir de su alcance o en entrar, pegar y salir. Pega si le da el tiempo; si no, retrocede o espera en el borde. Si el jugador lo mira cubierto con el escudo y el arma al 100 %, se pone al costado de la mira y le pega al escudo (CT-25)".
- **`docs/arquitectura.md`, fila "Golpe esquivo":** `EvasiveRules`, `EvasiveReading`, `EvasiveMove`, `EscapeTiming`, `Waypoints.evadePoint` y `sideStepPoint`.
- **`docs/actualizar-paper.md`, sección 3:**
  - fila del traductor: suma `Attribute.MOVEMENT_SPEED` (`movementSpeed`);
  - fila de `adapter/goal/*`: suma `HumanEntity.getCooldownPeriod()` y `Entity.getWidth()`, con el riesgo "que `getCooldownPeriod` siga dando los ticks de carga del arma en la mano, y que la física del suelo (`MOB_MOVE_INPUT_SCALE`, `GROUND_DRAG_PER_TICK`) no cambie".

## Pruebas obligatorias

### `EscapeTimingTest` (4)

| Prueba | Verifica |
| --- | --- |
| `zombieCoversOneBlockInTenTicks` | `ticksToCover(1.0, 0.23)` = 10 y `ticksToCover(2.0, 0.23)` = 19 |
| `fasterMobGetsOutSooner` | `ticksToCover(1.0, 0.322)` = 6 (zombie con Speed II) |
| `nothingToCoverTakesNoTime` | distancia 0 y −1 → 0 |
| `standingMobNeverGetsThere` | velocidad 0 → `MAX_TICKS` |

### `EvasiveRulesTest` (7), con `SAFETY_TICKS = 4`

| Prueba | Lectura | Esperado |
| --- | --- | --- |
| `unwatchedZombieStrikes` | `watched` false, lo demás como en el peor caso (cubierto y cargado, falta 0, necesita 10, dentro) | `STRIKE_EVASIVE` |
| `shieldedAndChargedPlayerIsSideStepped` | `watched`, `shieldedAndCharged`, falta 0, necesita 10; dentro y afuera | `SIDE_STEP` en los dos |
| `itStrikesWhileThereIsTimeToGetOut` | `watched`, falta 15, necesita 10, dentro | `STRIKE_EVASIVE` |
| `itBacksOffJustInTime` | `watched`, falta 14, necesita 10, dentro; y falta 10, necesita 10, dentro | `BACK_OFF` en los dos |
| `itStrikesWhenItCannotGetOutAnyway` | `watched`, sin escudo, falta 9, necesita 10, dentro; y falta 0, necesita 10, dentro | `STRIKE_EVASIVE` en los dos |
| `itHoldsAtTheEdgeWithoutTimeToComeIn` | `watched`, falta 20, necesita 25, afuera; y falta 30, necesita 25, afuera | `HOLD`; `STRIKE_EVASIVE` |
| `raisedShieldKeepsItBesideTheAim` | regresión del B-05: `watched`, `shieldedAndCharged`, falta 0, necesita 10, dentro, llamada 100 veces | `SIDE_STEP` todas las veces (nunca entra y sale) |

### `CombatGeometryTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `sideStepPointIsBesideTheAimOnTheMobsSide` | pose en `(0,64,0)` mirando a `+Z`, mob en `(1,64,1)`, `SideStep(1.5, 26.3)`: el punto está a 1,5 del jugador en horizontal, a 26,3° de la mira (`angleFromFacingDegrees`, tolerancia 1e-6) y con `x > 0`. Con el mob en `(-1,64,1)`, `x < 0` |
| `sideStepPointRejectsANonPositiveDistance` | `SideStep(0, 26.3)` lanza `IllegalArgumentException` |

### `WaypointsTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `sideStepPointClearsTheMobsWidthPlusTheMargin` | Con `TestSettings` (margen 15): pose en `(0,64,0)` mirando a `+Z`, mob en `(1,64,1)`, medio ancho 0,3. El punto está a 1,5 bloques y a `atan(0,3 / 1,5)` + 15 = 26,31° de la mira (tolerancia 1e-6) |

### Pruebas existentes que cambian

| Archivo | Cambio |
| --- | --- |
| `TestSettings` | `new AttackSettings(60, 60, 60, 8.0, 15.0, 1.0, 16.0, 0.5, 4, 15.0)` |
| `ConfigLoaderTest` | `new AttackSettings(60, 60, 60, 20.0, 30.0, 1.0, 16.0, 0.5, 4, 15.0)` |
| `SettingsValidationTest`, valores límite | `(1, 1, 1, 8.0, 8.0, 0.0, 1.0, 0.0, 0, 0.0)` y `(1, 1, 1, 8.0, 8.0, 0.0, 1.0, 0.0, 0, 90.0)` |
| `SettingsValidationTest`, fuera de rango | el caso del umbral pasa a ser `evasiveSafetyTicks` −1 → `AttackSettings.evasiveSafetyTicks must be at least 0, got -1`; se suma `evasiveAimMarginDegrees` 91.0 → `AttackSettings.evasiveAimMarginDegrees must be between 0.0 and 90.0, got 91.0`; los demás casos de `AttackSettings` cambian `0.8, 0.5` por `0.5, 4, 15.0` (o su valor fuera de rango en la misma posición) |
| `EvasiveWaitTest` | se borra (la clase ya no existe) |

Total: **14 pruebas nuevas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `EscapeTiming`, sacar `velocity *= GROUND_DRAG_PER_TICK` | 1 bloque en 6 ticks | `zombieCoversOneBlockInTenTicks` |
| 2 | En `EscapeTiming`, `acceleration = MOB_MOVE_INPUT_SCALE * speed` (sin el cuadrado) | 1 bloque en 4 ticks | `zombieCoversOneBlockInTenTicks` |
| 3 | En `EvasiveRules`, `>=` en lugar de `>` en el primer corte | falta 14, necesita 10 + 4 → `STRIKE_EVASIVE` | `itBacksOffJustInTime` |
| 5 | En `EvasiveRules`, `>` en lugar de `>=` en el último corte | falta 10, necesita 10 → `STRIKE_EVASIVE` | `itBacksOffJustInTime` |
| 4 | En `Waypoints.sideStepPoint`, no sumar el margen | 11,31° | `sideStepPointClearsTheMobsWidthPlusTheMargin` |

## Verificación en el server (Nico, después del merge)

Opus actualiza el `config.yml` del server de prueba (borra `evasive-charge-threshold` y suma las dos claves nuevas). De noche, `/mobai debug all full`; repetí `spawngroup` hasta que un zombie tenga `zombie.evasive_strike`:

1. **Con espada:** desde afuera, el zombie se queda casi siempre en el borde (con espada no le da el tiempo). Si pegás al aire, entra, pega y sale antes de que vuelvas a tener el arma cargada. Si ya está encima y no llega a salir, pega igual en vez de huir.
2. **Con hacha:** entra y pega más seguido; sale antes de que el hacha llegue al 100 %.
3. **Con escudo arriba y el arma cargada:** el zombie se corre a un costado de la mira y le pega al escudo. El escudo pierde durabilidad (en dificultad normal; en fácil el golpe no llega a 3 de daño). Ya no entra y sale sin parar.
4. **Con Speed II** (`/effect give @e[type=zombie] speed 60 1`): sale más tarde y vuelve antes.

## Procedimiento

1. Rama `wp-23d-esquivo-calculado` desde `origin/main` actualizado.
2. Constantes, `SideStep`, `CombatGeometry.sideStepPoint`, `EscapeTiming`, `EvasiveMove`, `EvasiveReading`, `EvasiveRules` y sus pruebas. Commit: `feat: worked-out escape timing and side step for the evasive zombie`.
3. Configuración (`AttackSettings`, `ConfigLoader`, `config.yml` y sus pruebas). Commit: `feat: evasive safety ticks and aim margin replace the charge threshold`.
4. `VersionTranslator`, `MobSpeed`, `Bodies`, `Weapons`, `Waypoints.sideStepPoint`, `PressGoal`, `FlankGoal`, `FallBackGoal` y `AdapterServices`; borrar `EvasiveWait`, `PlayerThreat` y `EvasiveWaitTest`; documentos. Commit: `feat: the evasive zombie times its dodges and side steps a raised shield (CT-25)`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-23D: worked-out evasive strike (CT-25)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Coordinar a varios zombies esquivos (quién se pone de cada lado).
- El golpe paciente y su espera.
- Medir la velocidad real del mob en vez de calcularla.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 14 pruebas nuevas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
