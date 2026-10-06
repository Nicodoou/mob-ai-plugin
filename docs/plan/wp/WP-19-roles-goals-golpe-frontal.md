# WP-19 — Roles, goals y golpe frontal

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-18 (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-19-roles-goals-golpe-frontal` |

## Objetivo

Que los mobs del grupo **hagan** lo que el cerebro decide, empezando por el rol `PRESS` con el golpe frontal:

1. `RoleRegistry`: la orden vigente de cada mob (`RoleAssignment`), que escribe el aplicador de decisiones (WP-20) y leen los goals.
2. `GoalInstaller`: saca los goals vanilla de movimiento, mirada y objetivo, y pone los nuestros.
3. `PressGoal`: va hacia el objetivo y golpea de frente cuando está en alcance, respetando el ritmo de ataque.
4. `MeleeAttacker`: el único que golpea cuerpo a cuerpo; abre y cierra el intento del rastreador (WP-18) alrededor de `mob.attack(target)`.
5. `MeleeRhythm`: cuándo recalcular el camino y cuándo se puede golpear (Java puro, probado).
6. Listeners de ciclo de vida (`EntityLifecycleListener`: reinstalar goals al volver a cargarse el mob y cancelar intentos al descargarse) y de objetivo (`TargetListener`: red de seguridad contra cambios de objetivo vanilla).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 1, 2, 6 y 8)
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`, `domain/group/Role.java`, `domain/shared/Attack.java`, `MinecraftConstants.java`, `MobKind.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/tracker/AttackTracker.java`, `MeleeOpening.java`, `TargetChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`mobKindOf`)
- `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java`
- `src/main/java/io/github/nicodoou/mobai/domain/port/ServerClock.java`

## Reglas de negocio

1. **Qué mobs reciben nuestros goals en este WP:** zombies y arañas (cuerpo a cuerpo). Los esqueletos **conservan sus goals vanilla** hasta el WP-24 (`ShootGoal`): si se los sacáramos ahora, se quedarían quietos.
2. **Instalar** (hallazgo 1): quitar todos los goals de tipo `MOVE`, `LOOK` y `TARGET` (el zombie queda sin ninguno, incluidos los de búsqueda de objetivo y `hurt_by`), y agregar un `PressGoal` nuevo para ese mob. Se instala al sumarse al grupo (lo llama el WP-20 o el WP-21) y **cada vez que el mob vuelve a cargarse** (`EntityAddToWorldEvent`), porque los goals propios se pierden al descargarse el chunk.
3. **El goal solo pregunta su orden** al `RoleRegistry`; no calcula puntajes ni lee memoria.
4. **`PressGoal` se activa** si la orden del mob es `PRESS` con objetivo, y el objetivo es un jugador conectado, vivo y en el mismo mundo. Se desactiva apenas eso deja de valer. Al desactivarse, frena el pathfinding.
5. **Mientras está activo**, en cada tick:
   - mira al objetivo;
   - recalcula el camino hacia el objetivo cada `REPATH_INTERVAL_TICKS` = 10 ticks (recalcular en todos los ticks es caro y no cambia nada a la velocidad de un zombie);
   - si está a `MinecraftConstants.MELEE_REACH_BLOCKS` o menos del objetivo y pasó el intervalo de ataque (`MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS` desde el último golpe), golpea con `MeleeAttacker`.
6. **El ataque que se registra es el que se ejecutó, no el sugerido.** En este WP, el único comportamiento de golpe es el frontal: un zombie registra `ZOMBIE_FRONT_STRIKE` aunque el cerebro le haya sugerido el de flanco o el paciente (esos comportamientos llegan en el WP-23). Una araña registra `SPIDER_BITE`. Si registráramos el sugerido, la memoria acreditaría al golpe paciente resultados de golpes frontales.
7. **`MeleeAttacker.strike`**: abre el intento (`MeleeOpening` con el tick del reloj y `TargetChecks.isInvulnerable(target)`), llama a `mob.attack(target)` y cierra (`closeMelee` con `TargetChecks.isValidTarget(target, mob)`). Si `attack` lanza una excepción, cancela el intento y la vuelve a lanzar: ningún intento queda abierto.
8. **Descarga del chunk** (`EntityRemoveFromWorldEvent` con la entidad no muerta): se cancela el intento abierto del mob; el mob sigue en su grupo y conserva su orden.
9. **Objetivos vanilla:** un miembro no debería cambiar de objetivo (no tiene goals de objetivo), pero si algo lo intenta (`EntityTargetEvent`), se cancela. Es una red de seguridad (hallazgo 8).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeRhythm.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalContext.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/EntityLifecycleListener.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/TargetListener.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistryTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/MeleeRhythmTest.java` |

`GoalContext` y `MeleeRhythm` son nuevos respecto del mapa del código: `GoalContext` agrupa lo que comparten los goals (así cada constructor tiene 3 parámetros como máximo) y `MeleeRhythm` saca los tiempos del goal para poder probarlos sin server.

## Especificación

Imports a tu criterio; Spotless decide el formato. API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `Goal<T extends Mob>` (`shouldActivate`, `shouldStayActive`, `start`, `stop`, `tick`, `getKey`, `getTypes`), `GoalKey.of(Class, NamespacedKey)`, `GoalType.MOVE/LOOK/TARGET`, `Bukkit.getMobGoals()` con `removeAllGoals(mob, type)` y `addGoal(mob, priority, goal)`, `mob.getPathfinder().moveTo(Entity, double)` y `stopPathfinding()`, `mob.lookAt(Entity)`, `LivingEntity.attack(Entity)`, `EntityAddToWorldEvent` y `EntityRemoveFromWorldEvent` (`com.destroystokyo.paper.event.entity`), `EntityTargetEvent`.

### `RoleRegistry.java`

```java
/** The order each mob follows until the next decision; goals only read it. */
public final class RoleRegistry {
  private final Map<MobId, RoleAssignment> assignments = new HashMap<>();

  public void assign(RoleAssignment assignment) {
    assignments.put(assignment.mob(), assignment);
  }

  public Optional<RoleAssignment> assignmentOf(MobId mob) {
    return Optional.ofNullable(assignments.get(mob));
  }

  public void clear(MobId mob) {
    assignments.remove(mob);
  }

  public int size() {
    return assignments.size();
  }
}
```

### `MeleeRhythm.java`

```java
/** When a melee goal repaths and when it may strike again, counted in its own ticks. */
public final class MeleeRhythm {
  // Repathing every tick costs CPU and changes nothing at a zombie's walking speed.
  static final int REPATH_INTERVAL_TICKS = 10;

  private int ticksSinceRepath = REPATH_INTERVAL_TICKS;
  private int ticksSinceStrike = MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;

  public void advance() {
    ticksSinceRepath++;
    ticksSinceStrike++;
  }

  public boolean shouldRepath() {
    return ticksSinceRepath >= REPATH_INTERVAL_TICKS;
  }

  public void markRepath() {
    ticksSinceRepath = 0;
  }

  public boolean canStrike(double distanceBlocks) {
    return distanceBlocks <= MinecraftConstants.MELEE_REACH_BLOCKS
        && ticksSinceStrike >= MinecraftConstants.MELEE_ATTACK_INTERVAL_TICKS;
  }

  public void markStrike() {
    ticksSinceStrike = 0;
  }
}
```

Arranca «listo»: la primera vez recalcula el camino y, si está en alcance, golpea enseguida. Los contadores no se desbordan en la práctica (un `int` cuenta 3 años de ticks); no hace falta acotarlos.

### `MeleeAttacker.java`

```java
/** The only place that lands melee hits; every hit goes through the attack tracker. */
public final class MeleeAttacker {
  private final AttackTracker tracker;
  private final ServerClock clock;

  public MeleeAttacker(AttackTracker tracker, ServerClock clock) { … }

  public Optional<Classification> strike(Mob mob, Player target, Attack attack) {
    MobId mobId = new MobId(mob.getUniqueId());
    long tick = clock.currentTick();
    tracker.openMelee(
        new MeleeOpening(mobId, new PlayerId(target.getUniqueId()), attack, tick, TargetChecks.isInvulnerable(target)));
    attackOrCancel(mob, target, mobId);
    return tracker.closeMelee(mobId, TargetChecks.isValidTarget(target, mob), tick);
  }

  // The damage event arrives inside attack(); if attack() fails, no attempt may stay open.
  private void attackOrCancel(Mob mob, Player target, MobId mobId) {
    try {
      mob.attack(target);
    } catch (RuntimeException exception) {
      tracker.cancel(mobId);
      throw exception;
    }
  }
}
```

### `GoalContext.java`

```java
/** What every goal of ours shares: the orders, the attacker and the plugin's namespace. */
public record GoalContext(Plugin plugin, RoleRegistry roles, MeleeAttacker attacker) {
  public GoalContext { … requireNonNull de los tres … }
}
```

### `PressGoal.java`

```java
/** PRESS: walk to the target and strike it head-on once in reach. */
public final class PressGoal implements Goal<Mob> {
  private static final double WALK_SPEED = 1.0;

  private final Mob mob;
  private final GoalKey<Mob> key;
  private final GoalContext context;
  private final MeleeRhythm rhythm = new MeleeRhythm();

  public PressGoal(Mob mob, GoalContext context) {
    this.mob = Objects.requireNonNull(mob, "PressGoal.mob");
    this.context = Objects.requireNonNull(context, "PressGoal.context");
    this.key = GoalKey.of(Mob.class, new NamespacedKey(context.plugin(), "press"));
  }

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
    currentTarget().ifPresent(this::pressOn);
  }

  @Override
  public GoalKey<Mob> getKey() {
    return key;
  }

  @Override
  public EnumSet<GoalType> getTypes() {
    return EnumSet.of(GoalType.MOVE, GoalType.LOOK);
  }

  …
}
```

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<Player> currentTarget()` | La orden del mob en `context.roles()`; si es `PRESS` y tiene objetivo, el `Player` con ese UUID (`Bukkit.getPlayer`) si `TargetChecks.isValidTarget(player, mob)` |
| `void pressOn(Player target)` | `rhythm.advance()`, `mob.lookAt(target)`, `followIfDue(target)`, `strikeIfReady(target)` |
| `void followIfDue(Player target)` | Si `rhythm.shouldRepath()`: `moveTo(target, WALK_SPEED)` y `rhythm.markRepath()` |
| `void strikeIfReady(Player target)` | Si `rhythm.canStrike(distancia)`: `context.attacker().strike(mob, target, executedAttack())` y `rhythm.markStrike()` |
| `Attack executedAttack()` | Regla 6: `SPIDER_BITE` si `mob` es `Spider`, si no `ZOMBIE_FRONT_STRIKE` |

La distancia es `mob.getLocation().distance(target.getLocation())`. `WALK_SPEED` 1.0 es la velocidad normal del pathfinder (multiplicador, no bloques); comentalo en una línea.

`executedAttack` usa `instanceof Spider` (una interfaz de entidad de Paper, no una constante sensible a la versión): no hace falta `VersionTranslator`.

### `GoalInstaller.java`

```java
/** Swaps the vanilla movement, look and target goals of a member for ours. */
public final class GoalInstaller {
  // Lower numbers run first; 1 keeps ours ahead of any goal another plugin adds later.
  private static final int GOAL_PRIORITY = 1;

  private final GoalContext context;
  private final VersionTranslator translator;

  public GoalInstaller(GoalContext context, VersionTranslator translator) { … }

  public boolean install(Mob mob) {
    if (!receivesOurGoals(mob)) {
      return false;
    }
    MobGoals goals = Bukkit.getMobGoals();
    goals.removeAllGoals(mob, GoalType.MOVE);
    goals.removeAllGoals(mob, GoalType.LOOK);
    goals.removeAllGoals(mob, GoalType.TARGET);
    goals.addGoal(mob, GOAL_PRIORITY, new PressGoal(mob, context));
    return true;
  }

  // Skeletons keep their vanilla goals until ShootGoal exists (WP-24).
  private boolean receivesOurGoals(Mob mob) {
    return translator.mobKindOf(mob.getType()).filter(MobKind::isMelee).isPresent();
  }
}
```

`install` devuelve si instaló, para que quien lo llame pueda registrarlo en el log de debug.

### `EntityLifecycleListener.java`

```java
/** Members come back without our goals after a chunk reload, and lose open attempts on unload. */
public final class EntityLifecycleListener implements Listener {
  private final ActiveGroups activeGroups;
  private final GoalInstaller installer;
  private final AttackTracker tracker;

  public EntityLifecycleListener(ActiveGroups activeGroups, GoalInstaller installer, AttackTracker tracker) { … }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onAdd(EntityAddToWorldEvent event) {
    if (event.getEntity() instanceof Mob mob && isMember(mob)) {
      installer.install(mob);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemove(EntityRemoveFromWorldEvent event) {
    if (!event.getEntity().isDead()) {
      tracker.cancel(new MobId(event.getEntity().getUniqueId()));
    }
  }

  private boolean isMember(Mob mob) {
    return activeGroups.groupOf(new MobId(mob.getUniqueId())).isPresent();
  }
}
```

### `TargetListener.java`

```java
/** Safety net: members have no target goals, so any vanilla target change is cancelled. */
public final class TargetListener implements Listener {
  private final ActiveGroups activeGroups;

  public TargetListener(ActiveGroups activeGroups) { … }

  @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
  public void onTarget(EntityTargetEvent event) {
    if (activeGroups.groupOf(new MobId(event.getEntity().getUniqueId())).isPresent()) {
      event.setCancelled(true);
    }
  }
}
```

(`HIGHEST` y no `MONITOR`: en `MONITOR` no se puede cancelar).

## Pruebas obligatorias

### `RoleRegistryTest` (4)

| Prueba | Verifica |
| --- | --- |
| `assignedOrderIsReadBack` | `assign(RoleAssignment(mob(1), PRESS, Optional.of(player), Optional.of(ZOMBIE_FRONT_STRIKE), false))`: `assignmentOf(mob(1))` la devuelve |
| `newOrderReplacesTheOldOne` | dos `assign` del mismo mob: queda la segunda y `size()` 1 |
| `unknownMobHasNoOrder` | `assignmentOf(mob(9))` vacío |
| `clearRemovesTheOrder` | `clear(mob(1))`: vacío y `size()` 0 |

### `MeleeRhythmTest` (6)

| Prueba | Verifica |
| --- | --- |
| `startsReadyToRepathAndStrike` | nuevo: `shouldRepath()` `true` y `canStrike(1.5)` `true` |
| `repathsEveryTenTicks` | `markRepath()`, 9 `advance()`: `false`; uno más: `true` |
| `strikeWaitsTheAttackInterval` | `markStrike()`, 19 `advance()`: `canStrike(1.0)` `false`; uno más: `true` |
| `outOfReachNeverStrikes` | nuevo: `canStrike(2.01)` `false` |
| `reachIsInclusive` | nuevo: `canStrike(2.0)` `true` |
| `repathAndStrikeAreIndependent` | `markStrike()` no cambia `shouldRepath()`, y `markRepath()` no cambia `canStrike` |

Total: **10 pruebas**. `PressGoal`, `MeleeAttacker`, `GoalInstaller` y los listeners se verifican en el server en la puerta E5 (D20).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `canStrike`, `<` en vez de `<=` para el alcance | 2.0 no está en alcance | `reachIsInclusive` |
| 2 | En `canStrike`, no mirar el intervalo de ataque | golpea con 19 ticks | `strikeWaitsTheAttackInterval` |
| 3 | `ticksSinceRepath` arrancando en 0 | no recalcula el primer tick | `startsReadyToRepathAndStrike` |
| 4 | En `RoleRegistry.clear`, no hacer nada | la orden sigue | `clearRemovesTheOrder` |

## Procedimiento

1. Rama `wp-19-roles-goals-golpe-frontal` desde `origin/main` actualizado.
2. `RoleRegistry` y `MeleeRhythm` con sus pruebas. Commit: `feat: add role registry and melee rhythm`.
3. `MeleeAttacker`, `GoalContext`, `PressGoal`, `GoalInstaller`. Commit: `feat: press goal with tracked front strikes`.
4. `EntityLifecycleListener`, `TargetListener`. Commit: `feat: reinstall goals on reload and cancel vanilla targeting`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-19: roles, press goal and tracked front strike`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo), informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca una advertencia por un método de Paper usado acá (deprecación), frená y reportá cuál; no la suprimas.
3. Si `GoalKey.of(Mob.class, …)` con `Goal<Mob>` no compila por los genéricos, usá la forma que acepte el compilador sin cambiar el comportamiento, y avisalo.

## Fuera de alcance

- Escribir las órdenes en el registro (`DecisionApplier`, WP-20) e instalar los goals al crear el grupo (WP-20/21).
- `FlankGoal` y `RetreatGoal` (WP-22), golpes de flanco y paciente (WP-23), esqueletos (WP-24), lentitud de la araña (WP-25).
- Curación de los mobs en retirada (WP-22).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 10 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ningún intento puede quedar abierto (`MeleeAttacker` cancela si `attack` falla).
- [ ] Build, cobertura y CI en verde.
