# WP-24F — Andanada: los goals

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24E (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24f-andanada-goals` |

## Objetivo

Que los mobs **ejecuten** las órdenes de la andanada (CT-23) que arma el cerebro desde el WP-24E:

1. `FallBackGoal` (zombies y arañas, rol `FALL_BACK`): se alejan hasta quedar fuera del alcance del jugador, con un margen, y se quedan mirándolo.
2. `ShootGoal` acepta `HOLD_FIRE` (se ubica pero no dispara) y `VOLLEY` (dispara ya).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/goal/` (`ShootGoal`, `FlankGoal`, `RetreatGoal`, `GoalOrders`, `GoalTiming`, `GoalInstaller`, `Waypoints`, `Weapons`, `PlayerReach`)
- `src/main/java/io/github/nicodoou/mobai/domain/group/Role.java`, `domain/settings/VolleySettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`)
- Prueba: `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`

## Reglas de negocio

1. **`FALL_BACK`** (cuerpo a cuerpo):
   - Al activarse, y después cada 10 ticks del reloj, camina hasta quedar a `alcance del jugador + volley.fall-back-margin-blocks` (1,5) de él, en la dirección opuesta (el alcance sale de `Weapons.playerReach`, CT-24).
   - Si ya está a esa distancia o más, se queda quieto.
   - Mira al jugador todo el tiempo y no pega.
   - El primer movimiento sale **en el acto** al activarse, porque la apertura dura solo 20 ticks.
2. **`HOLD_FIRE`** (esqueleto): hace todo lo de `SHOOT` (puesto en el anillo, carril libre, altura) **menos disparar**. La espera oportuna se reinicia.
3. **`VOLLEY`** (esqueleto): dispara apenas el ritmo, la vista, la distancia y la línea limpia lo permiten. **No espera**:
   - si la sugerencia es el tiro oportuno, dispara con la puntería del anticipado y lo registra como `SKELETON_LEAD_SHOT`, que es lo ejecutado;
   - el directo y el anticipado, como siempre.
   - Como no disparó durante la presión, el ritmo está listo y todos los esqueletos disparan juntos.
4. **Instalación:** zombies y arañas suman `FallBackGoal`, con la misma prioridad que los demás.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTiming.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FallBackGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |

Antes de empezar, buscá con grep:
- `new GoalTiming(` en `src/`: si aparece fuera de `AdapterServices`, frená y reportá;
- que los nombres de las pruebas nuevas no existan.

**Métodos públicos nuevos:** `Waypoints.keepAwayPoint` (uno más en `Waypoints`; si pasa de 20, frená y reportá).

## Especificación

### `GoalTiming.java`

`record GoalTiming(ServerClock clock, Supplier<AttackSettings> attack, Supplier<VolleySettings> volley)`, con `requireNonNull(volley, "GoalTiming.volley")`. En `AdapterServices`: `new GoalTiming(core.clock(), core.settings().section(MobAiSettings::attack), core.settings().section(MobAiSettings::volley))`.

### `Waypoints.java`

```java
  /** Empty once the mob is already that far from the danger, horizontally. */
  public Optional<Vec3> keepAwayPoint(Vec3 mobPosition, Vec3 dangerPosition, double distanceBlocks) {
    double missing = distanceBlocks - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }
```

### `FallBackGoal.java`

Javadoc: `/** FALL_BACK: step out of the player's reach so the volley has clear lanes, and watch. */`. Clave `"fall_back"`, tipos `MOVE` y `LOOK`, `WALK_SPEED = 1.0` con el comentario de siempre. Constructor `FallBackGoal(Mob mob, GoalContext context)`.

```java
  // A volley's opening lasts 20 ticks: a mob that waits to repath arrives too late.
  private static final long MOVE_INTERVAL_TICKS = 10;

  private long nextMoveTick = Long.MIN_VALUE;

  @Override
  public boolean shouldActivate() {
    return currentTarget().isPresent();
  }

  @Override
  public boolean shouldStayActive() {
    return shouldActivate();
  }

  @Override
  public void start() {
    nextMoveTick = Long.MIN_VALUE;
  }

  @Override
  public void stop() {
    mob.getPathfinder().stopPathfinding();
  }

  @Override
  public void tick() {
    currentTarget().ifPresent(this::fallBackFrom);
  }
```

| Función | Hace |
| --- | --- |
| `Optional<Player> currentTarget()` | `GoalOrders.orderFor(mob, context.roles(), Role.FALL_BACK).flatMap(order -> GoalOrders.validTarget(order, mob))` |
| `void fallBackFrom(Player target)` | `mob.lookAt(target)`. Si `ahora < nextMoveTick` vuelve. Si no: `nextMoveTick = ahora + MOVE_INTERVAL_TICKS`; `distance = context.tools().weapons().playerReach().blocksOf(target) + context.tools().timing().volley().get().fallBackMarginBlocks()`; `keepAwayPoint(posición del mob, posición del jugador, distance)`: con punto, `moveTo(new Location(mob.getWorld(), …), WALK_SPEED)`; sin punto, `stopPathfinding()` |

### `ShootGoal.java`

- Constante nueva: `// SHOOT, and the two volley orders (CT-23): HOLD_FIRE positions without shooting, VOLLEY shoots at once.` `private static final Set<Role> SHOOTER_ROLES = Set.of(Role.SHOOT, Role.HOLD_FIRE, Role.VOLLEY);`
- `currentOrder()` pasa a `context.roles().assignmentOf(new MobId(mob.getUniqueId())).filter(order -> SHOOTER_ROLES.contains(order.role()))`. `GoalOrders` no cambia.
- `shootIfReady(order, target)`: al principio, `if (order.role() == Role.HOLD_FIRE) { opportunism.reset(); return; }`.
- `shotNow(order, target)`: después de calcular `chosen`, si `order.role() == Role.VOLLEY`: `opportunism.reset()` y `Optional.of(chosen == Attack.SKELETON_OPPORTUNISTIC_SHOT ? Attack.SKELETON_LEAD_SHOT : chosen)`, con el comentario `// A volley does not wait: the opportunistic shot fires at once, aimed like the lead shot.`
- `shooterPositions` sigue contando solo `Role.SHOOT`. Para la formación también hay que contar a los esqueletos en `HOLD_FIRE` y `VOLLEY`: reemplazá `mobsWith(Role.SHOOT, …)` por la unión de `mobsWith` de los tres roles de `SHOOTER_ROLES`.

### `GoalInstaller.java`

En la rama de cuerpo a cuerpo, después de `FlankGoal`: `goals.addGoal(mob, GOAL_PRIORITY, new FallBackGoal(mob, context));`.

## Pruebas obligatorias

### `WaypointsTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `keepAwayPointStepsOutToTheDistance` | mob `(0,64,2)`, peligro `(0,64,0)`, distancia 4.5: `(0, 64, 4.5)` |
| `keepAwayPointIsEmptyWhenAlreadyThatFar` | mob `(0,64,4.5)`: vacío; `(0,64,6)`: vacío |

Total: **2 pruebas**. `FallBackGoal` y los cambios de `ShootGoal` usan Paper y se verifican en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `keepAwayPoint`, `missing < 0` | con 4,5 justos `CombatGeometry` lanza por distancia 0 | `keepAwayPointIsEmptyWhenAlreadyThatFar` |
| 2 | En `keepAwayPoint`, `missing = distanceBlocks` (sin restar la distancia actual) | `(0, 64, 6.5)` | `keepAwayPointStepsOutToTheDistance` |

## Verificación en el server (Nico, después del merge)

Opus suma la sección `volley` al `config.yml` del server de prueba al mergear el WP-24E. Con `/mobai debug all full`, de noche, repetí `spawngroup` hasta que `/mobai status` muestre `VOLLEY`:

1. Unos 3 s los zombies y arañas te presionan y los esqueletos no disparan.
2. Después los cuerpo a cuerpo se apartan a unos 4,5 bloques (con espada) y te miran.
3. Enseguida los esqueletos disparan **todos juntos**. En el `mobai-debug.log` aparecen varios `skeleton.*` en el mismo tick o casi, y casi ningún `NEUTRAL:ALLY_HIT`.
4. Cuando caen las flechas, los cuerpo a cuerpo vuelven a presionar.

## Procedimiento

1. Rama `wp-24f-andanada-goals` desde `origin/main` actualizado (con el WP-24E mergeado).
2. `GoalTiming`, `Waypoints.keepAwayPoint` y sus pruebas. Commit: `feat: keep-away point and volley timing for the goals`.
3. `FallBackGoal`, `ShootGoal`, `GoalInstaller`, `AdapterServices`. Commit: `feat: melee fall back and skeletons fire together in a volley (CT-23)`.
4. Pruebas que muerden (de a una; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-24F: volley goals (CT-23)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- El cerebro y las fases (WP-24E).
- Coordinar hacia qué lado se abre cada cuerpo a cuerpo.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 2 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 2 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
