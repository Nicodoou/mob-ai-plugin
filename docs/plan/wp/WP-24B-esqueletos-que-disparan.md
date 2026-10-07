# WP-24B — Esqueletos que disparan

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24A (rastreo de flechas, mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24b-esqueletos-que-disparan` |

## Objetivo

Que los esqueletos dejen la IA vanilla y **jueguen con el grupo**. Hoy conservan sus goals vanilla (WP-19): no siguen las órdenes ni registran nada. Por eso, en la puerta E5, un grupo que quedó solo con esqueletos cerró planes con éxito 0.

1. `ShootGoal` (rol `SHOOT`): mantiene al objetivo entre 8 y 15 bloques y a la vista, y dispara el tiro que eligió el cerebro.
2. `BowShooter`: lanza la flecha con nuestra puntería y abre el intento en el rastreador (WP-24A).
3. Los tres tiros del catálogo:
   - **directo:** apunta a donde está el objetivo;
   - **anticipado:** apunta a donde va a estar;
   - **oportuno:** espera hasta 3 s a que el objetivo mire para otro lado o esté peleando.
4. `GoalInstaller` les pone a los esqueletos `ShootGoal` y `RetreatGoal`.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 1, 5 y 7)
- `docs/actualizar-paper.md` (sección 3, para actualizar las filas)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/` (todos, en especial `PressGoal`, `PatientWait`, `MeleeRhythm`, `GoalTools`, `GoalInstaller`, `Waypoints`)
- `src/main/java/io/github/nicodoou/mobai/adapter/tracker/AttackTracker.java`, `ProjectileOpening.java`, `TargetChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/MovementTracker.java`, `PoseReader.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` (`predictedAimPoint`, `leadShotVelocity`, `isOutOfSight`, `retreatPoint`)
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`, `Attack.java`, `domain/settings/AttackSettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller` y `SharedParts`)
- Pruebas: `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`, `PatientWaitTest.java`, `MeleeRhythmTest.java`

## Reglas de negocio

1. **Distancia** (`attack.shoot-min-distance-blocks` 8 y `shoot-max-distance-blocks` 15, distancia horizontal), cada 10 ticks del reloj:
   - si el objetivo **no está a la vista** del esqueleto (`Mob.hasLineOfSight`) o está a más de 15 bloques: se acerca;
   - si está a menos de 8: retrocede hasta quedar a 8, en la dirección opuesta al jugador;
   - entre 8 y 15, los dos incluidos: se queda quieto.
2. **Ritmo de tiro:** como máximo un tiro cada `MinecraftConstants.SKELETON_ATTACK_INTERVAL_TICKS` (40) ticks del reloj (regla B-01).
3. **Cuándo dispara:** si le toca por ritmo, ve al objetivo y lo tiene a 15 bloques o menos. A menos de 8 también dispara mientras retrocede.
4. **Qué tiro:** el de `suggestedAttack` de la orden. Si la orden no trae un tiro de esqueleto, dispara el directo.
5. **Puntería** (`ShotAim`, hallazgo 5): el centro del cuerpo del jugador (pies más la mitad de su altura), con la velocidad de flecha y el arco de `CombatGeometry.leadShotVelocity`:
   - **directo:** a donde está;
   - **anticipado:** a `predictedAimPoint` con el movimiento por tick del jugador (`MovementTracker`, no `getVelocity()`);
   - **oportuno:** igual que el anticipado. Lo que cambia es el momento del tiro, no la puntería.
6. **Tiro oportuno** (catálogo: «espera a que el objetivo esté peleando con otro mob o mirando hacia otro lado, con espera máxima de 3 s»):
   - el objetivo está **distraído** si el esqueleto está fuera de su vista (más de 120° de su mirada, `isOutOfSight`, CT-16) o si el jugador acaba de pegar (`getAttackCooldown()` menor que 1, como en el golpe paciente);
   - distraído: dispara y registra `SKELETON_OPPORTUNISTIC_SHOT`;
   - si no, espera hasta `attack.opportunistic-shot-max-wait-ticks` (60). Al vencer, no se abre un intento oportuno: dispara el directo y lo registra como `SKELETON_DIRECT_SHOT`, igual que el abandono del golpe paciente (CT-17). Después empieza una espera nueva;
   - si no puede disparar (ritmo, vista o distancia), la espera se reinicia.
7. **La flecha:** `launchProjectile(Arrow.class, velocidad)` desde el esqueleto. No se puede juntar (`PickupStatus.DISALLOWED`, a través de `VersionTranslator`). Recién después se abre el intento con el UUID de la flecha: si el lanzamiento falla, no queda ningún intento abierto.
8. **Instalación:** el esqueleto recibe `ShootGoal` y `RetreatGoal` (la retirada a cubierto funciona igual para él). Zombies y arañas, como hasta ahora.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RangeMove.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RangeSituation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShotRhythm.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/TargetFocus.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/OpportunisticMove.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/OpportunisticWait.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShotRequest.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShotAim.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShotParts.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/BowShooter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Weapons.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTools.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` y `FlankGoal.java` (solo `tools().attacker()` → `tools().weapons().melee()`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RangeSituationTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/ShotRhythmTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/OpportunisticWaitTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/ShotAimTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |

Antes de empezar, buscá con grep `tools().attacker()` y `new GoalTools(` en `src/`: si aparecen en un archivo que no está en la tabla, frená y reportá.

**Métodos públicos nuevos en clases existentes:**
- `Waypoints.backOffPoint` (6 en total): los destinos de los goals viven ahí.
- `VersionTranslator.forbidPickup` (11 en total): es una constante de Paper (`PickupStatus`) y la regla manda que vaya en el traductor.

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`:
- `ProjectileSource.launchProjectile(Class, Vector)`;
- `AbstractArrow.setPickupStatus(PickupStatus)`, sin deprecar; el deprecado es `setPickupRule`;
- `PickupStatus.DISALLOWED`;
- `LivingEntity.getEyeLocation()` y `hasLineOfSight(Entity)`;
- `Entity.getHeight()`;
- `HumanEntity.getAttackCooldown()`;
- `org.bukkit.util.Vector(double, double, double)`.

### Decisión de distancia (Java puro)

```java
/** Where a shooter moves to keep its target in bow range. */
public enum RangeMove {
  APPROACH,
  BACK_OFF,
  HOLD
}
```

```java
/**
 * What a shooter knows about its range at one look around.
 *
 * @param distanceBlocks horizontal distance to the target
 * @param targetInSight the shooter has a line of sight to the target
 */
public record RangeSituation(double distanceBlocks, boolean targetInSight) {
  public RangeMove nextMove(double minBlocks, double maxBlocks) {
    if (!targetInSight || distanceBlocks > maxBlocks) {
      return RangeMove.APPROACH;
    }
    if (distanceBlocks < minBlocks) {
      return RangeMove.BACK_OFF;
    }
    return RangeMove.HOLD;
  }
}
```

### `ShotRhythm.java` (Java puro, como `MeleeRhythm`)

```java
/** When a shooter may loose its next arrow, measured in game ticks on the plugin's clock. */
public final class ShotRhythm {
  private final ServerClock clock;
  private long nextShotTick = Long.MIN_VALUE;

  public ShotRhythm(ServerClock clock) { … requireNonNull(clock, "ShotRhythm.clock") … }

  public boolean canShoot() {
    return clock.currentTick() >= nextShotTick;
  }

  public void markShot() {
    nextShotTick = clock.currentTick() + MinecraftConstants.SKELETON_ATTACK_INTERVAL_TICKS;
  }
}
```

### Tiro oportuno (Java puro)

```java
/** Whether the target is busy with something other than this shooter. */
public enum TargetFocus {
  ON_SHOOTER,
  ELSEWHERE
}
```

```java
/** What a shooter told to be opportunistic does this tick. */
public enum OpportunisticMove {
  WAIT,
  SHOOT_OPPORTUNISTIC,
  GIVE_UP
}
```

```java
/** When an opportunistic shot is loosed: as soon as the target is busy, or never if the wait runs out. */
final class OpportunisticWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;

  OpportunisticMove next(TargetFocus focus, long now, long maxWaitTicks) {
    if (focus == TargetFocus.ELSEWHERE) {
      waitStartTick = NOT_WAITING;
      return OpportunisticMove.SHOOT_OPPORTUNISTIC;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
      return OpportunisticMove.WAIT;
    }
    if (now - waitStartTick >= maxWaitTicks) {
      waitStartTick = NOT_WAITING;
      return OpportunisticMove.GIVE_UP;
    }
    return OpportunisticMove.WAIT;
  }

  void reset() {
    waitStartTick = NOT_WAITING;
  }
}
```

### Puntería (Java puro)

```java
public record ShotRequest(Attack attack, Vec3 eye, Vec3 targetCenter, Vec3 movementPerTick) {
  // requireNonNull de los cuatro con "ShotRequest.<campo>"
}
```

```java
/** The arrow velocity of each shot: where the target is, or where it is going to be. */
public final class ShotAim {
  private final CombatGeometry geometry;

  public ShotAim(CombatGeometry geometry) { … requireNonNull(geometry, "ShotAim.geometry") … }

  public Vec3 velocity(ShotRequest request) {
    return geometry.leadShotVelocity(request.eye(), aimPoint(request));
  }

  // The opportunistic shot is about when to shoot, not how: it aims like the lead shot.
  private Vec3 aimPoint(ShotRequest request) {
    return switch (request.attack()) {
      case SKELETON_DIRECT_SHOT -> request.targetCenter();
      case SKELETON_LEAD_SHOT, SKELETON_OPPORTUNISTIC_SHOT ->
          geometry.predictedAimPoint(request.eye(), request.targetCenter(), request.movementPerTick());
      default ->
          throw new IllegalArgumentException(
              "ShotAim: " + request.attack() + " is not a skeleton shot");
    };
  }
}
```

`Attack` es un enum del dominio, no de Paper: el `switch` está permitido.

### `ShotParts.java`, `BowShooter.java` y `Weapons.java`

```java
/** What the bow shooter needs to aim and to set up the arrow. */
public record ShotParts(ShotAim aim, MovementTracker movement, VersionTranslator translator) {
  // requireNonNull de los tres
}
```

```java
/** The only place that looses arrows; every arrow opens an attempt in the attack tracker. */
public final class BowShooter {
  // The middle of the player's body: arrows aimed at the feet hit the ground first.
  private static final double BODY_CENTER_FRACTION = 0.5;

  private final AttackTracker tracker;
  private final ServerClock clock;
  private final ShotParts parts;

  public BowShooter(AttackTracker tracker, ServerClock clock, ShotParts parts) { … }

  public void shoot(Mob shooter, Player target, Attack attack) {
    PlayerId targetId = new PlayerId(target.getUniqueId());
    Vec3 velocity =
        parts.aim().velocity(
            new ShotRequest(
                attack,
                PoseReader.positionOf(shooter.getEyeLocation()),
                centerOf(target),
                parts.movement().movementPerTick(targetId)));
    Arrow arrow =
        shooter.launchProjectile(Arrow.class, new Vector(velocity.x(), velocity.y(), velocity.z()));
    parts.translator().forbidPickup(arrow);
    tracker.openProjectile(
        new ProjectileOpening(
            arrow.getUniqueId(),
            new MobId(shooter.getUniqueId()),
            targetId,
            attack,
            clock.currentTick(),
            TargetChecks.isInvulnerable(target)));
  }

  private static Vec3 centerOf(Player target) {
    Location feet = target.getLocation();
    return new Vec3(
        feet.getX(), feet.getY() + target.getHeight() * BODY_CENTER_FRACTION, feet.getZ());
  }
}
```

```java
/** How our goals hurt the target: melee hits and arrows, both through the attack tracker. */
public record Weapons(MeleeAttacker melee, BowShooter bow) {
  // requireNonNull de los dos con "Weapons.<campo>"
}
```

`GoalTools` queda `record GoalTools(Weapons weapons, GoalTiming timing, Waypoints waypoints)`, con `requireNonNull(weapons, "GoalTools.weapons")` y el Javadoc «What our goals use besides the orders: the weapons, the timing and the waypoints.». En `PressGoal` y `FlankGoal`, `context.tools().attacker()` pasa a `context.tools().weapons().melee()`; nada más cambia en esos archivos.

### `VersionTranslator.java`

```java
  // Skeleton arrows cannot be picked up in vanilla either; launched arrows default to allowed.
  public void forbidPickup(AbstractArrow arrow) {
    arrow.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
  }
```

### `Waypoints.java`: un método nuevo

```java
  /** Only for a mob closer than the minimum bow range: the point that takes it back to it. */
  public Vec3 backOffPoint(Vec3 mobPosition, Vec3 dangerPosition) {
    double missing =
        settings.get().shootMinDistanceBlocks() - horizontalDistance(mobPosition, dangerPosition);
    return geometry.retreatPoint(mobPosition, dangerPosition, missing);
  }
```

`horizontalDistance` ya existe (WP-22C).

### `ShootGoal.java`

Javadoc: `/** SHOOT: keep the target in bow range and in sight, and loose the shot the brain chose. */`. Clave `"shoot"`, tipos `MOVE` y `LOOK`, `WALK_SPEED = 1.0` con el mismo comentario que `PressGoal`. Constructor `ShootGoal(Mob mob, GoalContext context)`.

Campos: `mob`, `key`, `context`, `rhythm = new MeleeRhythm(context.tools().timing().clock())` (solo para recalcular el camino), `shots = new ShotRhythm(context.tools().timing().clock())`, `opportunism = new OpportunisticWait()`.

Constante: `// Paper's attack cooldown is 1 once the player's weapon has fully recharged.` `private static final float FULL_ATTACK_COOLDOWN = 1.0f;`

```java
  @Override
  public boolean shouldActivate() {
    return currentTarget().isPresent();
  }

  @Override
  public boolean shouldStayActive() {
    return shouldActivate();
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    currentOrder()
        .ifPresent(order -> GoalOrders.validTarget(order, mob).ifPresent(target -> engage(order, target)));
  }
```

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<RoleAssignment> currentOrder()` | `GoalOrders.orderFor(mob, context.roles(), Role.SHOOT)` |
| `Optional<Player> currentTarget()` | `currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob))` |
| `void engage(RoleAssignment order, Player target)` | `mob.lookAt(target)`; `keepInRangeIfDue(target)`; `shootIfReady(order, target)` |
| `void keepInRangeIfDue(Player target)` | Si `rhythm.shouldRepath()`: `switch (new RangeSituation(distanceTo(target), mob.hasLineOfSight(target)).nextMove(min, max))`: `APPROACH -> moveTo(target, WALK_SPEED)`; `BACK_OFF -> moveTo(new Location(mob.getWorld(), …backOffPoint(mobPosition(), position(target))…), WALK_SPEED)`; `HOLD -> stopPathfinding()`. Después `rhythm.markRepath()` |
| `void shootIfReady(RoleAssignment order, Player target)` | Si `!shots.canShoot() \|\| !mob.hasLineOfSight(target) \|\| distanceTo(target) > max`: `opportunism.reset()` y vuelve. Si no: `shotNow(order, target).ifPresent(attack -> { context.tools().weapons().bow().shoot(mob, target, attack); shots.markShot(); })` |
| `Optional<Attack> shotNow(RoleAssignment order, Player target)` | `Attack chosen = order.suggestedAttack().filter(attack -> attack.mobKind() == MobKind.SKELETON).orElse(Attack.SKELETON_DIRECT_SHOT)`. Si no es `SKELETON_OPPORTUNISTIC_SHOT`: `opportunism.reset()` y `Optional.of(chosen)`. Si es oportuno: `switch (opportunism.next(focusOf(target), ahora, attack().opportunisticShotMaxWaitTicks()))`: `WAIT -> Optional.empty()`; `SHOOT_OPPORTUNISTIC -> Optional.of(Attack.SKELETON_OPPORTUNISTIC_SHOT)`; `GIVE_UP -> Optional.of(Attack.SKELETON_DIRECT_SHOT)`, con el comentario `// The wait ran out: no opportunistic attempt is opened (catalog); it shoots straight instead.` |
| `TargetFocus focusOf(Player target)` | `ELSEWHERE` si `context.tools().waypoints().isOutOfSight(PoseReader.poseOf(target), mobPosition())` o `target.getAttackCooldown() < FULL_ATTACK_COOLDOWN`; si no, `ON_SHOOTER` |
| `double distanceTo(Player target)` | Distancia **horizontal** entre `mobPosition()` y `position(target)` |
| `Vec3 mobPosition()` | `PoseReader.positionOf(mob.getLocation())` |
| `static Vec3 position(Player player)` | `PoseReader.positionOf(player.getLocation())` |

`min` y `max` salen de `context.tools().timing().attack().get()` (`shootMinDistanceBlocks`, `shootMaxDistanceBlocks`).

### `GoalInstaller.java`

```java
  public boolean install(Mob mob) {
    Optional<MobKind> kind = translator.mobKindOf(mob.getType());
    if (kind.isEmpty()) {
      return false;
    }
    MobGoals goals = Bukkit.getMobGoals();
    goals.removeAllGoals(mob, GoalType.MOVE);
    goals.removeAllGoals(mob, GoalType.LOOK);
    goals.removeAllGoals(mob, GoalType.TARGET);
    // Only one runs at a time: each one stays active only while the order has its role.
    if (kind.get().isMelee()) {
      goals.addGoal(mob, GOAL_PRIORITY, new PressGoal(mob, kind.get(), context));
      goals.addGoal(mob, GOAL_PRIORITY, new FlankGoal(mob, kind.get(), context));
    } else {
      goals.addGoal(mob, GOAL_PRIORITY, new ShootGoal(mob, context));
    }
    goals.addGoal(mob, GOAL_PRIORITY, new RetreatGoal(mob, context));
    return true;
  }
```

`meleeKindOf` se borra. El Javadoc de la clase pasa a «Swaps the vanilla movement, look and target goals of a member for ours.» (el mismo de ahora).

### `AdapterServices.java`

En `goalInstaller`: `BowShooter bow = new BowShooter(parts.tracker(), core.clock(), new ShotParts(new ShotAim(geometry), parts.movement(), parts.translator()));` y `new GoalTools(new Weapons(attacker, bow), new GoalTiming(…), waypoints)`. `parts.movement()` ya existe en `SharedParts`.

### `docs/actualizar-paper.md`

- Fila `adapter/goal/*`: sumar `ProjectileSource.launchProjectile(Class, Vector)`, `Arrow`, `org.bukkit.util.Vector`, `LivingEntity.getEyeLocation()`, `Entity.getHeight()` y `Mob.hasLineOfSight(Entity)`. En «Qué revisar», sumar: «que una flecha lanzada con `launchProjectile` desde un esqueleto siga haciendo el daño de un tiro de esqueleto (unos 4 de daño a 1,6 bloques por tick)».
- Fila `adapter/translate/VersionTranslator`: sumar `AbstractArrow.PickupStatus.DISALLOWED` (`forbidPickup`). En «Qué revisar», sumar: «`setPickupStatus` no está deprecado en 26.3 (sí `setPickupRule`); si cambia, solo cambia `forbidPickup`».

## Pruebas obligatorias

### `RangeSituationTest` (5)

Con mínimo 8 y máximo 15.

| Prueba | Situación | Movimiento |
| --- | --- | --- |
| `targetOutOfSightIsApproached` | 10 bloques, sin vista | `APPROACH` |
| `farTargetIsApproached` | 15.1, con vista | `APPROACH` |
| `closeTargetMakesItBackOff` | 7.9, con vista | `BACK_OFF` |
| `bothEndsOfTheRangeHold` | 8 y 15, con vista | `HOLD` las dos |
| `closeButHiddenTargetIsApproached` | 5, sin vista | `APPROACH` |

### `ShotRhythmTest` (3)

`FakeServerClock(1000)`.

| Prueba | Verifica |
| --- | --- |
| `startsReadyToShoot` | `canShoot()` `true` |
| `waitsTheSkeletonAttackInterval` | `markShot()`; avanzar 39: `false`; 1 más: `true` |
| `ticksEveryOtherGameTickStillShootEveryFortyTicks` | `markShot()`; 19 veces avanzar 2: `false`; una más: `true` (regla B-01) |

### `OpportunisticWaitTest` (5)

Espera máxima 60.

| Prueba | Secuencia | Verifica |
| --- | --- | --- |
| `shootsWhenTheTargetIsBusy` | `ELSEWHERE`, 1000 | `SHOOT_OPPORTUNISTIC` |
| `waitsWhileTheTargetWatches` | `ON_SHOOTER` en 1000, 1030 y 1059 | `WAIT` las tres |
| `givesUpWhenTheWaitRunsOut` | `ON_SHOOTER` en 1000, 1060, 1062 y 1122 | `WAIT`, `GIVE_UP`, `WAIT` (espera nueva), `GIVE_UP` |
| `resetStartsTheWaitAgain` | `ON_SHOOTER`, 1000; `reset()`; `ON_SHOOTER` en 1050, 1100 y 1110 | `WAIT`; `WAIT`, `WAIT`, `GIVE_UP` |
| `aBusyTargetEndsTheWait` | `ON_SHOOTER`, 1000; `ELSEWHERE`, 1030; `ON_SHOOTER` en 1080 y 1139 | `WAIT`; `SHOOT_OPPORTUNISTIC`; `WAIT` (espera nueva, desde 1080); `WAIT` (1139 − 1080 = 59) |

### `ShotAimTest` (5)

`aim = new ShotAim(geometry)` con `geometry = new CombatGeometry()`. Ojo `(0, 65.6, 0)`, centro del jugador `(10, 64.9, 0)`, movimiento `(0, 0, 0.2)` por tick. Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `directShotAimsWhereTheTargetIs` | igual a `geometry.leadShotVelocity(ojo, centro)` |
| `leadShotAimsWhereTheTargetWillBe` | igual a `geometry.leadShotVelocity(ojo, geometry.predictedAimPoint(ojo, centro, movimiento))`, y distinto del directo |
| `opportunisticShotAimsLikeTheLeadShot` | igual al anticipado |
| `everyShotFliesAtArrowSpeed` | el largo de las tres velocidades es `MinecraftConstants.ARROW_SPEED_BLOCKS_PER_TICK` |
| `meleeAttacksAreNotShots` | `ZOMBIE_FRONT_STRIKE`: `IllegalArgumentException` con `ShotAim: ZOMBIE_FRONT_STRIKE is not a skeleton shot` |

### `WaypointsTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `backOffPointReachesTheMinimumRange` | mob `(3,64,4)`, peligro `(0,64,0)` (5 bloques), mínimo 8: `(4.8, 64, 6.4)` |

Total: **19 pruebas**. `ShootGoal`, `BowShooter`, `GoalInstaller` y `forbidPickup` usan Paper y se verifican en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `nextMove`, `distanceBlocks >= maxBlocks` | 15 da `APPROACH` | `bothEndsOfTheRangeHold` |
| 2 | En `ShotRhythm.markShot`, `MELEE_ATTACK_INTERVAL_TICKS` (20) | a los 20 ya puede | `waitsTheSkeletonAttackInterval` |
| 3 | En `aimPoint`, el directo también anticipa | igual al anticipado | `directShotAimsWhereTheTargetIs` |
| 4 | En `OpportunisticWait`, al disparar no reiniciar la espera | en 1080 da `GIVE_UP` (desde 1000) | `aBusyTargetEndsTheWait` |

## Verificación en el server (Nico, después del merge)

Con `/mobai debug all full`, de noche:

1. Los esqueletos se quedan entre 8 y 15 bloques; si te acercás, retroceden; si te escondés detrás de un bloque, se acercan hasta verte.
2. Disparan cada 2 s más o menos. En el `mobai-debug.log` aparecen `attack=skeleton.direct_shot`, `skeleton.lead_shot` y `skeleton.opportunistic_shot`, con `HIT rule=6`, `MISS rule=8` o `PARTIAL rule=7` (escudo).
3. Caminando de costado, el tiro anticipado te pega más que el directo (hallazgo 5: 8 de 9 contra 1 de 5).
4. Las flechas no se pueden juntar.
5. Un grupo de solo esqueletos ya no cierra planes con éxito 0 si te pegan (nota de la puerta E5).

## Procedimiento

1. Rama `wp-24b-esqueletos-que-disparan` desde `origin/main` actualizado (con el WP-24A mergeado).
2. `RangeMove`, `RangeSituation`, `ShotRhythm`, `TargetFocus`, `OpportunisticMove`, `OpportunisticWait`, `ShotRequest`, `ShotAim`, `Waypoints.backOffPoint` y sus pruebas. Commit: `feat: range, rhythm, opportunity and aim of a bow shooter`.
3. `ShotParts`, `BowShooter`, `Weapons`, `GoalTools`, `PressGoal`, `FlankGoal`, `VersionTranslator`, `ShootGoal`, `GoalInstaller`, `AdapterServices`. Commit: `feat: skeletons shoot with the group (shoot goal and bow shooter)`.
4. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the bow shooter`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-24B: skeletons that shoot`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless; si una lambda de `tick()` queda mal partida, extraela a una función privada con el mismo comportamiento y avisalo.
2. Si `-Xlint` marca una deprecación en un método de Paper usado acá, frená y reportá; no la suprimas.
3. Si ArchUnit se queja de `Arrow`, `Vector` o `AbstractArrow` fuera de `VersionTranslator`, frená y reportá el mensaje.
4. Si la cobertura mínima no se alcanza por las clases que usan Paper, reportalo con el número; no agregues pruebas con mocks de Paper.

## Fuera de alcance

- Darles arco a los esqueletos del grupo de prueba. `spawngroup` los crea sin equipo; la flecha sale igual con `launchProjectile`.
- El golpe esquivo (WP-23B) y la lentitud de la araña (WP-25).
- Que los esqueletos se muevan de costado mientras disparan.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 19 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ningún ritmo cuenta llamadas a `tick()`: repath con `MeleeRhythm`, tiros con `ShotRhythm`.
- [ ] Cada flecha abre su intento después del lanzamiento, con el ataque ejecutado.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
