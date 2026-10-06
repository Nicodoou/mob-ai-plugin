# WP-20A — Schedulers y aplicador de decisiones

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-19 (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-20a-schedulers-y-aplicador` |

## Objetivo

Las piezas que mueven el plugin en el tiempo. El armado en `MobAiPlugin` es del WP-20B.

1. **`DecisionCadence`**: qué grupos deciden en cada tick, repartidos dentro de la ventana de 10 ticks para no decidir todos juntos.
2. **`DecisionApplier`**: escribe en `RoleRegistry` la orden de cada miembro según la decisión de su grupo, y borra la de quien ya no tiene.
3. **`GroupDecider`**: foto → `TickGroups` → aplicador, para un grupo, atrapando la falla de ese grupo con su contexto en el log.
4. **`DecisionScheduler`**: en cada tick, decide los grupos que tocan y, una vez por ventana, limpia las órdenes de mobs que ya no son miembros.
5. **`PersistenceScheduler`**: cada `saveIntervalTicks`, copia las memorias en el hilo principal y las escribe en un hilo aparte; al apagar, espera la escritura pendiente y guarda por última vez.
6. **`MovementSampler`**: muestrea el movimiento de los jugadores conectados en cada tick y los olvida al desconectarse.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java` (solo la firma de `snapshotOf`), `MovementTracker.java`
- `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java`, `TickGroups.java`, `SaveMemories.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/GroupDecision.java`, `RoleAssignment.java`, `BrainResult.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `Member.java`
- `src/main/java/io/github/nicodoou/mobai/domain/port/StoredState.java`, `StoredMemories.java`, `ServerClock.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/InMemoryMemoryRepository.java`, `TestSettings.java`

## Reglas de negocio

1. **Reparto de la carga** (arquitectura, «Hilos y tiempo»): cada grupo tiene un desfase fijo `floorMod(id.value().hashCode(), intervalo)` y decide en los ticks donde `floorMod(tick + desfase, intervalo) == 0`. Así cada grupo decide exactamente una vez por ventana, siempre en el mismo lugar de la ventana, y los grupos se reparten. El intervalo se lee en cada tick de la configuración vigente.
2. **Aplicar una decisión:** para cada miembro del grupo, si la decisión trae una orden para él, se escribe; si no, se borra la suya (por ejemplo, un grupo que vuelve a observar no deja órdenes viejas).
3. **Una falla de un grupo no frena a los demás:** `GroupDecider` atrapa `RuntimeException` y la registra con `logger.error`, con el id corto del grupo y el tick en el mensaje y la excepción adjunta. El incidente con su copia completa es del WP-29.
4. **Grupo sin foto** (todos sus mobs en chunks descargados): no decide ni cambia sus órdenes.
5. **Órdenes huérfanas:** en los ticks múltiplos del intervalo, `RoleRegistry` se queda solo con los miembros activos. Así no crece con los mobs que murieron o se fueron.
6. **Guardado periódico:** en los ticks múltiplos de `saveIntervalTicks` (y mayores que 0). La copia (`SaveMemories.capture`) se hace en el hilo que llama, el principal; la escritura (`SaveMemories.write`), en un único hilo propio, así dos escrituras nunca se pisan.
7. **Una escritura que falla no tumba nada:** se registra con `logger.error` («Could not save MobAI memories») y la próxima vuelve a intentar. Incluye el candado de guardado (`IllegalStateException` del `GuardedMemoryRepository`).
8. **Al apagar:** se cierra el hilo de escritura, se espera hasta `SHUTDOWN_WAIT_SECONDS` = 10 a que termine lo pendiente y se hace la última copia y escritura en el hilo que apaga. Si la espera se interrumpe, se restaura la marca de interrupción del hilo y se sigue con la última escritura igual.
9. **Movimiento:** en cada tick, una muestra por jugador conectado con la posición de sus pies y el tick del reloj; al desconectarse, `forget`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java` (`retainOnly`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionCadence.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionApplier.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionParts.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/GroupDecider.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionScheduler.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/PersistenceScheduler.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/MovementSampler.java` |
| Modificar | `docs/actualizar-paper.md` (filas de `DecisionScheduler`, `GroupDecider` y `MovementSampler`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionCadenceTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/scheduler/DecisionApplierTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/scheduler/PersistenceSchedulerTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistryTest.java` (una prueba más) |

`RoleRegistry` pasa de 4 a 5 métodos públicos: `retainOnly` va ahí porque el registro es el dueño del mapa.

`DecisionParts`, `GroupDecider` y `DecisionCadence` son nuevos respecto del mapa del código: separan el reparto (puro, probado), el decidir de un grupo (con su manejo de fallas) y la vuelta por todos los grupos, con 3 dependencias como máximo por clase.

El logger es `org.slf4j.Logger` (lo trae Paper; `JavaPlugin.getSLF4JLogger()` lo devuelve). En las pruebas: `org.slf4j.helpers.NOPLogger.NOP_LOGGER`.

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `RoleRegistry.retainOnly`

```java
  public void retainOnly(Set<MobId> members) {
    assignments.keySet().retainAll(members);
  }
```

### `DecisionCadence.java`

```java
/** Spreads group decisions over the decision window so they do not all land on one tick. */
public final class DecisionCadence {
  private DecisionCadence() {}

  public static boolean isDue(GroupId group, long tick, int intervalTicks) {
    return Math.floorMod(tick + offsetOf(group, intervalTicks), intervalTicks) == 0;
  }

  public static boolean isWindowStart(long tick, int intervalTicks) {
    return Math.floorMod(tick, intervalTicks) == 0;
  }

  static int offsetOf(GroupId group, int intervalTicks) {
    return Math.floorMod(group.value().hashCode(), intervalTicks);
  }
}
```

### `DecisionApplier.java`

```java
/** Writes each member's order from its group's decision; members without one lose theirs. */
public final class DecisionApplier {
  private final RoleRegistry roles;

  public DecisionApplier(RoleRegistry roles) { … }

  public void apply(GroupDecision decision, List<Member> members) {
    Map<MobId, RoleAssignment> orders = ordersByMob(decision);
    for (Member member : members) {
      applyTo(member.id(), orders);
    }
  }

  public void retainOnly(Set<MobId> members) {
    roles.retainOnly(members);
  }
  …
}
```

`ordersByMob`: las órdenes de `decision.assignments()` indexadas por mob. `applyTo`: si hay orden, `roles.assign`; si no, `roles.clear`.

### `DecisionParts.java`

```java
/** What deciding for one group needs: its snapshot, the use case and where the orders go. */
public record DecisionParts(SnapshotFactory snapshots, TickGroups tickGroups, DecisionApplier applier) {
  public DecisionParts { … requireNonNull de los tres … }
}
```

### `GroupDecider.java`

```java
/** Decides for one group; a failure is logged with its context and does not stop the others. */
public final class GroupDecider {
  private final DecisionParts parts;
  private final Logger logger;

  public GroupDecider(DecisionParts parts, Logger logger) { … }

  public void decide(Group group, long tick) {
    try {
      parts.snapshots().snapshotOf(group, tick).ifPresent(snapshot -> decideWith(group, snapshot));
    } catch (RuntimeException exception) {
      logger.error("MobAI group {} failed to decide at tick {}", group.id().shortId(), tick, exception);
    }
  }

  public void retainOrdersOf(ActiveGroups activeGroups) {
    parts.applier().retainOnly(memberIds(activeGroups));
  }

  private void decideWith(Group group, GroupSnapshot snapshot) {
    parts.tickGroups().execute(snapshot)
        .ifPresent(result -> parts.applier().apply(result.decision(), group.roster().members()));
  }

  private static Set<MobId> memberIds(ActiveGroups activeGroups) { … ids de todos los miembros de todos los grupos … }
}
```

### `DecisionScheduler.java`

```java
/** Every tick, lets the groups whose turn it is decide. */
public final class DecisionScheduler {
  private final ActiveGroups activeGroups;
  private final GroupDecider decider;
  private final Supplier<GroupSettings> settings;

  public DecisionScheduler(ActiveGroups activeGroups, GroupDecider decider, Supplier<GroupSettings> settings) { … }

  public void tick(long tick) {
    int interval = settings.get().decisionIntervalTicks();
    for (Group group : activeGroups.groups()) {
      if (DecisionCadence.isDue(group.id(), tick, interval)) {
        decider.decide(group, tick);
      }
    }
    if (DecisionCadence.isWindowStart(tick, interval)) {
      decider.retainOrdersOf(activeGroups);
    }
  }
}
```

`activeGroups.groups()` es una copia: si un grupo se disuelve durante la vuelta, la vuelta sigue sin problema (`TickGroups` devuelve vacío para un grupo que ya no está).

### `PersistenceScheduler.java`

```java
/** Copies memories on the main thread and writes them on its own thread, one write at a time. */
public final class PersistenceScheduler {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;

  private final SaveMemories saveMemories;
  private final Supplier<StoredState> state;
  private final Logger logger;
  private final ExecutorService writer =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("MobAI-memory-writer").daemon().factory());

  public PersistenceScheduler(SaveMemories saveMemories, Supplier<StoredState> state, Logger logger) { … }

  public void tick(long tick, long saveIntervalTicks) {
    if (tick > 0 && tick % saveIntervalTicks == 0) {
      saveInBackground();
    }
  }

  public void saveInBackground() {
    StoredMemories memories = saveMemories.capture(state.get());
    writer.execute(() -> writeLogged(memories));
  }

  public void shutdown() {
    writer.shutdown();
    awaitPendingWrite();
    writeLogged(saveMemories.capture(state.get()));
  }

  private void awaitPendingWrite() { … awaitTermination(SHUTDOWN_WAIT_SECONDS, SECONDS); InterruptedException → Thread.currentThread().interrupt() y logger.warn … }

  // A failed write is retried on the next cycle; it must never take the server down.
  private void writeLogged(StoredMemories memories) {
    try {
      saveMemories.write(memories);
    } catch (RuntimeException exception) {
      logger.error("Could not save MobAI memories", exception);
    }
  }
}
```

(Si `awaitTermination` devuelve `false`, `logger.warn("MobAI memory writer did not finish within {} s", SHUTDOWN_WAIT_SECONDS)`.)

### `MovementSampler.java` (Paper)

```java
/** Feeds the movement tracker every tick and forgets players who leave. */
public final class MovementSampler implements Listener {
  private final MovementTracker movement;
  private final ServerClock clock;

  public MovementSampler(MovementTracker movement, ServerClock clock) { … }

  public void sampleOnlinePlayers() {
    long tick = clock.currentTick();
    for (Player player : Bukkit.getOnlinePlayers()) {
      Location feet = player.getLocation();
      movement.sample(new PlayerId(player.getUniqueId()), new Vec3(feet.getX(), feet.getY(), feet.getZ()), tick);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onQuit(PlayerQuitEvent event) {
    movement.forget(new PlayerId(event.getPlayer().getUniqueId()));
  }
}
```

### `docs/actualizar-paper.md`

Agregar a la tabla de la sección 3:

| Clase | Qué usa de Paper | Riesgo | Qué revisar al actualizar |
| --- | --- | --- | --- |
| `adapter/scheduler/MovementSampler` | `Bukkit.getOnlinePlayers()`, `Player.getLocation()`, `PlayerQuitEvent` | Bajo | — |
| `adapter/scheduler/GroupDecider`, `PersistenceScheduler` | `org.slf4j.Logger` (viene con Paper) | Bajo | Que Paper siga exponiendo SLF4J |

## Pruebas obligatorias

### `RoleRegistryTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `retainOnlyDropsOrdersOfOtherMobs` | órdenes de `mob(1)` y `mob(2)`; `retainOnly(Set.of(mob(1)))`: queda solo `mob(1)` |

### `DecisionCadenceTest` (4)

| Prueba | Verifica |
| --- | --- |
| `eachGroupIsDueExactlyOncePerWindow` | para 20 grupos (`new GroupId(new UUID(0, n))`, n de 1 a 20) y los ticks 1000 a 1009: cada grupo está «due» exactamente una vez |
| `aGroupIsDueAtTheSamePlaceInEveryWindow` | si un grupo está «due» en el tick t, también lo está en t + 10 y t + 20, y no en t + 1 |
| `groupsAreSpreadOverTheWindow` | de esos 20 grupos, los ticks «due» dentro de 1000–1009 cubren al menos 4 valores distintos (con el `hashCode` de `UUID` real; calculalo y, si da otra cantidad, poné en la prueba el número exacto que da y avisalo) |
| `windowStartsAtMultiplesOfTheInterval` | `isWindowStart(1000, 10)` `true`, `isWindowStart(1005, 10)` `false` |

### `DecisionApplierTest` (3)

| Prueba | Verifica |
| --- | --- |
| `membersWithAnOrderGetIt` | decisión con orden `PRESS` para `mob(1)`; miembros `mob(1)` y `mob(2)`: `mob(1)` tiene la orden y `mob(2)` no |
| `membersWithoutAnOrderLoseTheirOldOne` | `mob(2)` tenía una orden; la decisión no trae ninguna para él: queda sin orden |
| `retainOnlyKeepsActiveMembers` | órdenes de `mob(1)` y `mob(9)`; `retainOnly(Set.of(mob(1)))`: queda `mob(1)` |

La `GroupDecision` se arma a mano: `new GroupDecision(groupId(1), 100, GroupState.EXECUTING, Optional.empty(), Optional.empty(), Optional.of(player), List.of(orden))`.

### `PersistenceSchedulerTest` (5)

Armado: `ActiveGroups` con un grupo con `mob(1)`; `InMemoryMemoryRepository` (o el que diga la prueba); `SaveMemories`; `state = () -> new StoredState(500, 600)`; `NOPLogger.NOP_LOGGER`.

| Prueba | Verifica |
| --- | --- |
| `savesOnlyOnMultiplesOfTheInterval` | `tick(5999, 6000)`, `tick(0, 6000)` y `tick(6000, 6000)`, después `shutdown()`: el repositorio tiene `saveCount()` 2 (el de 6000 y el final) |
| `backgroundSaveReachesTheRepository` | `saveInBackground()` y `shutdown()`: la carga del repositorio tiene el grupo 1 y el estado `StoredState(500, 600)` |
| `shutdownSavesOneLastTime` | solo `shutdown()`: `saveCount()` 1 |
| `failedWriteIsLoggedNotThrown` | repositorio de prueba cuyo `save` lanza `UncheckedIOException`: `saveInBackground()` y `shutdown()` no lanzan |
| `copyHappensOnTheCallingThread` | repositorio de prueba que anota `Thread.currentThread().getName()` en `save`; `saveInBackground()` y `shutdown()`: la primera escritura fue en `"MobAI-memory-writer"` y la última, en el hilo de la prueba |

Total: **13 pruebas nuevas**. `GroupDecider`, `DecisionScheduler` y `MovementSampler` usan Paper (la foto, `Bukkit`) y se verifican en el server en la puerta E5.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `isDue`, `tick % intervalTicks == 0` (sin desfase) | todos los grupos en el mismo tick: 1 valor distinto | `groupsAreSpreadOverTheWindow` |
| 2 | En `DecisionApplier.applyTo`, no borrar cuando no hay orden | `mob(2)` conserva la orden vieja | `membersWithoutAnOrderLoseTheirOldOne` |
| 3 | En `PersistenceScheduler.tick`, sacar `tick > 0` | el tick 0 también guarda: `saveCount()` 3 | `savesOnlyOnMultiplesOfTheInterval` |
| 4 | En `shutdown`, no hacer la última escritura | `saveCount()` 0 | `shutdownSavesOneLastTime` |

## Procedimiento

1. Rama `wp-20a-schedulers-y-aplicador` desde `origin/main` actualizado.
2. `RoleRegistry.retainOnly`, `DecisionCadence`, `DecisionApplier` con sus pruebas. Commit: `feat: spread group decisions and apply their orders`.
3. `DecisionParts`, `GroupDecider`, `DecisionScheduler`, `MovementSampler` y `docs/actualizar-paper.md`. Commit: `feat: schedule group decisions and movement sampling`.
4. `PersistenceScheduler` con su prueba. Commit: `feat: save memories on a background writer`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-20A: schedulers and decision applier`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo), informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. El número exacto de `groupsAreSpreadOverTheWindow` (ver la tabla).
3. Si `org.slf4j` no está en el classpath de compilación, frená y reportá (no agregues dependencias).

## Fuera de alcance

- Armar todo en `MobAiPlugin`, el `tick` único que llama a estas piezas, registrar listeners y cargar memorias (WP-20B).
- Incidentes con copia completa (WP-29).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 13 pruebas nuevas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] `docs/actualizar-paper.md` actualizado.
- [ ] Build, cobertura y CI en verde.
