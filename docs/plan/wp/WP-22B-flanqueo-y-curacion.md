# WP-22B — Goal de flanqueo y curación

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-22A (`FlankFormation`, `FlankQuery`) |
| Modelo | Sonnet |
| Rama | `wp-22b-flanqueo-y-curacion` |

## Objetivo

Que el rol `FLANK` **haga algo** y que los mobs en retirada se curen. En la puerta E5, los mobs con `FLANK` o `RETREAT` se quedaban quietos, y el grupo parecía roto la mitad del tiempo. El movimiento de la retirada (a cubierto, CT-15) llega en el WP-22C:

1. `FlankGoal`: el flanqueador camina a **su** punto de la formación (WP-22A), detrás o al costado del jugador, y golpea solo desde fuera del arco del escudo. Un zombie que flanquea registra `ZOMBIE_FLANK_STRIKE` (CT-08 y CT-14); una araña, `SPIDER_BITE`.
2. Curación (CT-07): cada mob con la orden `recovering` recupera 1 punto de vida cada 50 ticks (el ritmo de Regeneración I), sin efecto de poción.
3. Todos los ritmos se miden con el reloj del plugin, nunca contando llamadas a `tick()` (regla B-01: Paper llama a un goal activo cada dos ticks).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 1, 2 y 6)
- `docs/actualizar-paper.md` (sección 3, para actualizar las filas)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/` (todos: `PressGoal`, `GoalContext`, `MeleeTools`, `MeleeRhythm`, `MeleeAttacker`, `GoalInstaller`, `RoleRegistry`)
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java`, `EntityReadings.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/tracker/TargetChecks.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`, `FlankFormation.java`, `FlankQuery.java`, `PlayerPose.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`, `domain/settings/AttackSettings.java`, `domain/shared/MinecraftConstants.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistryTest.java`, `MeleeRhythmTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Qué mobs reciben los goals nuevos:** los mismos que hoy reciben `PressGoal` (zombies y arañas). Los esqueletos siguen con su IA vanilla hasta el WP-24, pero **la curación sí les llega**: no depende de los goals.
2. **Un goal activo por mob:** `PressGoal` y `FlankGoal` (y `RetreatGoal` en el WP-22C) se instalan juntos, con la misma prioridad y los tipos `MOVE` y `LOOK`; cada uno corre solo mientras la orden del mob tiene **su** rol.
3. **`FlankGoal`** se activa si la orden es `FLANK` con un objetivo válido (`TargetChecks.isValidTarget`). Mientras está activo:
   - mira al objetivo;
   - cada `REPATH_INTERVAL_TICKS` (10) ticks del reloj recalcula **su** punto con `FlankFormation`. Los flanqueadores son los mobs con orden `FLANK` y **el mismo objetivo** que están cargados en el mundo del objetivo, más él mismo. La distancia es `attack.flank-distance-blocks` (3). Después camina hacia ese punto;
   - golpea si está en alcance, pasó el intervalo de ataque **y está fuera del arco del escudo** del jugador (más de 90° de su mirada). Desde adelante no golpea: sigue rodeando.
4. **Ataque registrado por `FlankGoal`:** `ZOMBIE_FLANK_STRIKE` para el zombie (es el golpe que el catálogo define: «se mueve hasta quedar a más de 90° del frente y recién ahí golpea») y `SPIDER_BITE` para la araña. El esqueleto nunca recibe este goal: si llega, `IllegalStateException`.
5. **Curación** (CT-07): un mob con la orden `recovering = true` recupera `REGENERATION_HEAL_POINTS` (1) cada `REGENERATION_BASE_INTERVAL_TICKS` (50) ticks del reloj:
   - el primer punto llega 50 ticks después de que empieza a recuperarse, no enseguida;
   - si deja de recuperarse (un jugador se acercó o volvió a pelear), su cuenta se borra y vuelve a empezar de cero;
   - se cura con `LivingEntity.heal`, que nunca pasa la vida máxima;
   - sin poción, sin partículas: zombies y esqueletos son inmunes a Regeneración.
6. **Quién decide `recovering`:** el dominio (`RetreatRule.canRecover`: sin jugadores vivos a menos de 12 bloques), en cada decisión. El adaptador solo lo aplica.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java` (usa `PoseReader`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Borrar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeTools.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTools.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalContext.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalOrders.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FlankGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/HealSchedule.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/RecoveryHealer.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/PluginRuntime.java` |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/scheduler/HealScheduleTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistryTest.java` |

**Métodos públicos nuevos en clases existentes:** `RoleRegistry.mobsWith` y `recoveringMobs` (7 en total). `RoleRegistry` es el único que conoce todas las órdenes, así que la búsqueda vive ahí.

`MeleeTools` (del arreglo B-01) pasa a llamarse `GoalTools` porque ahora lleva más que el cuerpo a cuerpo.

## Especificación

Imports a tu criterio; Spotless decide el formato. API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `Pathfinder.moveTo(Location, double)` y `stopPathfinding()`; `LivingEntity.heal(double)`; `new Location(World, double, double, double)`; `Location.getYaw()`. La API de goals ya se usa en `PressGoal`.

### `PoseReader.java` (`adapter.snapshot`)

```java
/** Positions and the player's pose read from Paper, for snapshots and goals alike. */
public final class PoseReader {
  private PoseReader() {}

  public static Vec3 positionOf(Location location) {
    return new Vec3(location.getX(), location.getY(), location.getZ());
  }

  public static PlayerPose poseOf(Player player) {
    Location location = player.getLocation();
    return new PlayerPose(positionOf(location), EntityReadings.facingFromYaw(location.getYaw()));
  }
}
```

En `SnapshotFactory`: `playerSnapshot` usa `PoseReader.poseOf(player)` para la pose, `mobSnapshot` usa `PoseReader.positionOf`, y se borra el método privado `position(Location)`. Nada más cambia.

### `Waypoints.java` (`adapter.goal`, Java puro, sin Paper)

```java
/** Where a goal walks to, computed without Paper. */
public final class Waypoints {
  private final CombatGeometry geometry;
  private final FlankFormation formation;
  private final Supplier<AttackSettings> settings;

  public Waypoints(
      CombatGeometry geometry, FlankFormation formation, Supplier<AttackSettings> settings) {
    … requireNonNull de los tres ("Waypoints.geometry", "Waypoints.formation", "Waypoints.settings") …
  }

  public Vec3 flankPoint(PlayerPose pose, MobId self, Map<MobId, Vec3> flankers) {
    return formation.pointFor(
        new FlankQuery(pose, self, flankers, settings.get().flankDistanceBlocks()));
  }

  public boolean isOutsideTheShieldArc(PlayerPose pose, Vec3 mobPosition) {
    return !geometry.isInShieldArc(pose, mobPosition);
  }
}
```

### `GoalTools.java` y `GoalContext.java`

```java
/** What our goals use besides the orders: the attacker, the plugin's clock and the waypoints. */
public record GoalTools(MeleeAttacker attacker, ServerClock clock, Waypoints waypoints) {
  public GoalTools { … requireNonNull de los tres ("GoalTools.attacker", …) … }
}
```

`GoalContext` queda `record GoalContext(Plugin plugin, RoleRegistry roles, GoalTools tools)`, con `requireNonNull(tools, "GoalContext.tools")` y el Javadoc «What every goal of ours shares: the orders, the tools and the plugin's namespace.».

### `GoalOrders.java` (`adapter.goal`, package-private)

```java
/** How every goal of ours reads its order: its own role only, and a target that is still valid. */
final class GoalOrders {
  private GoalOrders() {}

  static Optional<RoleAssignment> orderFor(Mob mob, RoleRegistry roles, Role role) {
    return roles.assignmentOf(new MobId(mob.getUniqueId())).filter(order -> order.role() == role);
  }

  static Optional<Player> validTarget(RoleAssignment order, Mob mob) {
    return order
        .target()
        .map(PlayerId::value)
        .map(Bukkit::getPlayer)
        .filter(player -> TargetChecks.isValidTarget(player, mob));
  }
}
```

`PressGoal.currentTarget()` pasa a ser `GoalOrders.orderFor(mob, context.roles(), Role.PRESS).flatMap(order -> GoalOrders.validTarget(order, mob))`, y `context.melee()` pasa a `context.tools()`. El resto de `PressGoal` no cambia.

### `FlankGoal.java`

Misma forma que `PressGoal` (campos `mob`, `kind`, `key`, `context`, `rhythm = new MeleeRhythm(context.tools().clock())`; `WALK_SPEED = 1.0` con el mismo comentario; clave `"flank"`; tipos `MOVE` y `LOOK`; `shouldActivate`, `shouldStayActive`, `stop` y `getKey` iguales salvo el rol). Javadoc: `/** FLANK: walk round to this flanker's slot and strike only from outside the shield arc. */`.

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<Player> currentTarget()` | `GoalOrders.orderFor(mob, context.roles(), Role.FLANK).flatMap(order -> GoalOrders.validTarget(order, mob))` |
| `void flank(Player target)` | `PlayerPose pose = PoseReader.poseOf(target)`; `mob.lookAt(target)`; `walkRoundIfDue(target, pose)`; `strikeIfOutsideTheShield(target, pose)` |
| `void walkRoundIfDue(Player target, PlayerPose pose)` | Si `rhythm.shouldRepath()`: punto = `context.tools().waypoints().flankPoint(pose, self(), flankerPositions(target))`; `mob.getPathfinder().moveTo(new Location(target.getWorld(), x, y, z), WALK_SPEED)`; `rhythm.markRepath()` |
| `Map<MobId, Vec3> flankerPositions(Player target)` | Para cada `MobId` de `context.roles().mobsWith(Role.FLANK, new PlayerId(target.getUniqueId()))`: si `Bukkit.getEntity(id.value())` es un `Mob` válido (`isValid()`) en el mundo del objetivo, su posición (`PoseReader.positionOf`). Al final, siempre `self()` con la posición de este mob. Comentario: `// Every flanker of this target loaded in its world, this mob included.` |
| `void strikeIfOutsideTheShield(Player target, PlayerPose pose)` | Si `rhythm.canStrike(distancia)` **y** `waypoints.isOutsideTheShieldArc(pose, posición del mob)`: `context.tools().attacker().strike(mob, target, executedAttack())` y `rhythm.markStrike()` |
| `Attack executedAttack()` | `switch` sobre `kind`: `ZOMBIE -> Attack.ZOMBIE_FLANK_STRIKE`, `SPIDER -> Attack.SPIDER_BITE`, `SKELETON -> throw new IllegalStateException("Skeleton " + mob.getUniqueId() + " has no melee attack to strike with")`. Comentario de una línea: `// A flanker strikes from outside the shield arc, which is what the flank strike is (CT-08).` |
| `MobId self()` | `new MobId(mob.getUniqueId())` |

`tick()` es `currentTarget().ifPresent(this::flank)`. La distancia, como en `PressGoal`: `mob.getLocation().distance(target.getLocation())`.

### `RoleRegistry.java`: dos métodos nuevos

```java
  public Set<MobId> mobsWith(Role role, PlayerId target) {
    return assignments.values().stream()
        .filter(order -> order.role() == role && order.target().equals(Optional.of(target)))
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }

  public Set<MobId> recoveringMobs() {
    return assignments.values().stream()
        .filter(RoleAssignment::recovering)
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }
```

### `GoalInstaller.java`

`install` agrega los dos goals, con la misma prioridad:

```java
    // Only one runs at a time: each one stays active only while the order has its role.
    goals.addGoal(mob, GOAL_PRIORITY, new PressGoal(mob, kind.get(), context));
    goals.addGoal(mob, GOAL_PRIORITY, new FlankGoal(mob, kind.get(), context));
```

### `MinecraftConstants.java`

Junto a `REGENERATION_BASE_INTERVAL_TICKS`:

```java
  // Regeneration I heals this much every REGENERATION_BASE_INTERVAL_TICKS.
  public static final double REGENERATION_HEAL_POINTS = 1.0;
```

### `HealSchedule.java` (`adapter.scheduler`, Java puro)

```java
/** When each recovering mob gets its next point of health, on the plugin's clock. */
public final class HealSchedule {
  private final Map<MobId, Long> nextHealTick = new HashMap<>();

  // Mobs that stopped recovering lose their count; the order of the result is by mob id.
  public List<MobId> due(Set<MobId> recovering, long now) {
    nextHealTick.keySet().retainAll(recovering);
    List<MobId> due = new ArrayList<>();
    for (MobId mob : byId(recovering)) {
      if (claimIfDue(mob, now)) {
        due.add(mob);
      }
    }
    return due;
  }

  // A mob that just started recovering waits a whole interval, like a fresh Regeneration effect.
  private boolean claimIfDue(MobId mob, long now) {
    Long next = nextHealTick.get(mob);
    if (next != null && now < next) {
      return false;
    }
    nextHealTick.put(mob, now + MinecraftConstants.REGENERATION_BASE_INTERVAL_TICKS);
    return next != null;
  }

  private static List<MobId> byId(Set<MobId> mobs) {
    return mobs.stream().sorted(Comparator.comparing(MobId::value)).toList();
  }
}
```

### `RecoveryHealer.java` (`adapter.scheduler`)

```java
/** Heals recovering members at the Regeneration I rhythm, without the potion (CT-07). */
public final class RecoveryHealer {
  private final RoleRegistry roles;
  private final HealSchedule schedule;

  public RecoveryHealer(RoleRegistry roles, HealSchedule schedule) { … requireNonNull … }

  public void tick(long now) {
    for (MobId mob : schedule.due(roles.recoveringMobs(), now)) {
      heal(mob);
    }
  }

  // heal() never goes past the maximum health and fires EntityRegainHealthEvent.
  private void heal(MobId mob) {
    if (Bukkit.getEntity(mob.value()) instanceof Mob entity && entity.isValid()) {
      entity.heal(MinecraftConstants.REGENERATION_HEAL_POINTS);
    }
  }
}
```

### Armado

- `AdapterServices.goalInstaller`: `CombatGeometry geometry = new CombatGeometry();` y `Waypoints waypoints = new Waypoints(geometry, new FlankFormation(geometry), core.settings().section(MobAiSettings::attack));`; `GoalTools tools = new GoalTools(attacker, core.clock(), waypoints);`; `new GoalContext(plugin, parts.roles(), tools)`.
- `AdapterServices` suma el componente `RecoveryHealer recoveryHealer` **al final** del record, armado con `new RecoveryHealer(parts.roles(), new HealSchedule())`.
- `PluginRuntime.runTick`: `adapters.recoveryHealer().tick(now);` justo después de `adapters.decisionScheduler().tick(now);`.

### `docs/actualizar-paper.md`

- Fila `adapter/goal/*`: sumar `Pathfinder.moveTo(Location, double)`, `new Location(World, double, double, double)`, `Bukkit.getEntity`, `Bukkit.getPlayer`, `Entity.getWorld`/`isValid`. En «Qué revisar», sumar: «que `moveTo(Location)` siga aceptando un punto dentro de un bloque (busca el más cercano alcanzable)».
- Fila nueva `adapter/scheduler/RecoveryHealer`: `Bukkit.getEntity`, `LivingEntity.heal(double)` | **Medio** | Que `heal` siga sin pasar la vida máxima, que no cure a un mob muerto y que dispare `EntityRegainHealthEvent` (otro plugin podría cancelarlo).
- Fila nueva `adapter/snapshot/PoseReader`: `Location` (`getX/Y/Z`, `getYaw`), `Player.getLocation` | Bajo | Que el yaw siga midiéndose igual (0 mira al sur, crece en sentido horario).
- Fila `adapter/snapshot/SnapshotFactory`: sacar `getLocation().getYaw()` (ahora lo lee `PoseReader`).

## Pruebas obligatorias

### `WaypointsTest` (2)

`new Waypoints(new CombatGeometry(), new FlankFormation(new CombatGeometry()), () -> TestSettings.defaults().attack())` (flanqueo a 3 bloques). Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `flankPointUsesTheConfiguredDistance` | pose en el origen mirando a +Z, flanqueadores `{mob(1): (2,0,0)}`: `(2.1213203435596424, 0, -2.1213203435596424)` |
| `onlyOutsideTheShieldArcCountsAsFlank` | pose en el origen mirando a +Z: `(0,0,-2)` `true`; `(0,0,2)` `false`; `(2,0,0)` (90° justos, dentro del arco) `false` |

### `HealScheduleTest` (5)

`mob(n)` = `new MobId(new UUID(0, n))`.

| Prueba | Verifica |
| --- | --- |
| `firstHealComesAWholeIntervalAfterRecoveryStarts` | `due({mob(1)}, 1000)` vacío; `1049` vacío; `1050` `[mob(1)]` |
| `healsEveryFiftyTicks` | después de lo anterior: `1099` vacío; `1100` `[mob(1)]` |
| `stoppingRecoveryResetsTheCount` | `due({mob(1)}, 1000)`; `due({}, 1060)` vacío; `due({mob(1)}, 1070)` vacío; `1119` vacío; `1120` `[mob(1)]` |
| `dueMobsComeInIdOrder` | `due({mob(2), mob(1)}, 1000)`; en `1050`: `[mob(1), mob(2)]` |
| `aLateTickHealsOnlyOnce` | `due({mob(1)}, 1000)`; `due(…, 1200)` `[mob(1)]`; `1249` vacío; `1250` `[mob(1)]` |

### `RoleRegistryTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `mobsWithFiltersByRoleAndTarget` | `PRESS` de `mob(1)` sobre `player`; `FLANK` de `mob(2)` sobre `player`; `FLANK` de `mob(3)` sobre otro jugador; `FLANK` de `mob(4)` sin objetivo: `mobsWith(FLANK, player)` es exactamente `{mob(2)}` |
| `recoveringMobsAreTheOnesBeingHealed` | `RETREAT` de `mob(1)` con `recovering` `true`, `RETREAT` de `mob(2)` con `false`, `PRESS` de `mob(3)`: `recoveringMobs()` es exactamente `{mob(1)}` |

Total: **9 pruebas**. `FlankGoal`, `RecoveryHealer`, `GoalOrders` y `PoseReader` usan Paper y se verifican en el server (abajo).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `isOutsideTheShieldArc`, devolver `geometry.isInShieldArc(…)` sin negar | `(0,0,-2)` da `false` | `onlyOutsideTheShieldArcCountsAsFlank` |
| 2 | En `flankPoint`, distancia fija 1.0 en vez de la configurada | `(0.7071…, 0, -0.7071…)` | `flankPointUsesTheConfiguredDistance` |
| 3 | En `claimIfDue`, `return true` en vez de `return next != null` | cura en el tick 1000 | `firstHealComesAWholeIntervalAfterRecoveryStarts` |
| 4 | En `mobsWith`, no comparar el objetivo | `{mob(2), mob(3), mob(4)}` | `mobsWithFiltersByRoleAndTarget` |

## Verificación en el server (Opus, después de la revisión)

No la hace el subagente. Con `/mobai debug all full`, de noche:

1. Estrategia `FLANK` (repetir `spawngroup` hasta que salga): los flanqueadores van a puntos **distintos** a los costados y atrás; desde adelante no pegan; en el `mobai-debug.log`, los zombies flanqueadores registran `attack=zombie.flank_strike`.
2. Un mob herido (30 % o menos) queda quieto hasta el WP-22C, pero con vos a más de 12 bloques su vida sube 1 punto cada 2,5 s; con 60 % vuelve a pelear.
4. CT-13 (del WP-22A): con un grupo muy herido no aparecen líneas `PLAN … GROUP_RETREATED success=0.00` repetidas.

## Procedimiento

1. Rama `wp-22b-flanqueo-y-curacion` desde `origin/main` actualizado (con el WP-22A mergeado).
2. `PoseReader`, el cambio de `SnapshotFactory`, `Waypoints`, `GoalTools` (y borrar `MeleeTools`), `GoalContext`, `GoalOrders`, `PressGoal` y `WaypointsTest`. Commit: `refactor: goal tools, order reading and waypoints for every goal`.
3. `RoleRegistry` (con sus pruebas), `FlankGoal`, `GoalInstaller`. Commit: `feat: flank goal with the flank strike`.
4. `MinecraftConstants`, `HealSchedule` (con sus pruebas), `RecoveryHealer`, `AdapterServices`, `PluginRuntime`. Commit: `feat: heal recovering members at the Regeneration I rhythm`.
5. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the flank goal and healing`.
6. Pruebas que muerden (de a una, en secuencia; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-22B: flank goal and healing`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca una deprecación en un método de Paper usado acá, frená y reportá cuál; no la suprimas.
3. Si la regla de ArchUnit de métodos públicos o de capas se queja de una clase nueva, frená y reportá el mensaje.
4. Si la cobertura mínima no se alcanza por las clases que usan Paper, reportalo con el número; no agregues pruebas con mocks de Paper.

## Fuera de alcance

- `RetreatGoal` y la búsqueda de cubierto (WP-22C).
- El golpe paciente y la elección del golpe frontal o paciente en `PressGoal` (WP-23).
- Goals de esqueletos (WP-24) y lentitud de la araña (WP-25).
- Cambiar reglas del dominio (WP-22A ya hizo las suyas).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 9 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ningún ritmo cuenta llamadas a `tick()`: todos usan `MeleeRhythm` (reloj) o `HealSchedule` (reloj).
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
