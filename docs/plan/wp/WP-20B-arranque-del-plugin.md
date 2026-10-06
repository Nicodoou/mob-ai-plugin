# WP-20B — Arranque del plugin

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-20A (mergeado) |
| Modelo | **Opus** (une todas las piezas; un error de armado se ve solo en el server) |
| Rama | `wp-20b-arranque-del-plugin` |

## Objetivo

Armar el plugin completo y su ciclo de vida:

1. **`CoreServices`**: dominio y aplicación armados (Java puro, probado), con `ClosePlan` suscripto a `PlanClosed` (CT-11).
2. **`AdapterServices`**: adaptadores armados sobre el núcleo.
3. **`PluginRuntime`**: al habilitar, carga configuración y memorias, aplica el estado guardado, registra listeners, instala goals en los miembros ya cargados y agenda el tick; al deshabilitar, frena el tick y guarda por última vez.
4. **`MobAiPlugin`**: delega en `PluginRuntime`; si la configuración es inválida, avisa y se deshabilita.

Después de este WP el plugin arranca entero. Todavía no hay forma de crear un grupo desde el juego: eso es `/mobai spawngroup` (WP-21).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/bootstrap/MobAiPlugin.java`
- `src/main/java/io/github/nicodoou/mobai/application/` (todo: solo los constructores y las firmas públicas)
- `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` (`standard`), `Brain.java` (constructor), `RegroupWindow.java`
- `src/main/java/io/github/nicodoou/mobai/domain/event/DomainEventPublisher.java`, `PlanClosed.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/` (todo: solo los constructores y las firmas públicas)
- `src/main/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepository.java` (constructor)
- `src/test/java/io/github/nicodoou/mobai/testsupport/InMemoryMemoryRepository.java`, `TestSettings.java`

## Reglas de negocio

1. **Un único armado.** Todas las instancias se crean una vez, en `CoreServices.create` y `AdapterServices.create`; nadie más hace `new` de un servicio compartido. El cerebro se arma con `BrainParts.standard` (WP-28A).
2. **Configuración:** `saveDefaultConfig()` (copia `config.yml` la primera vez sin pisar uno existente), `reloadConfig()` y `ConfigLoader.load(getConfig())`. Si lanza `InvalidConfigException` o cualquier `RuntimeException` del cargador, se registra el mensaje con `logger.error` y el plugin se deshabilita: no arranca con valores a medias.
3. **Memorias** en `plugins/MobAI/memories/` (`getDataFolder()`), con `JsonMemoryRepository` envuelto en `GuardedMemoryRepository`.
4. **Carga:**
   - si sale bien: el reloj se restaura al tick guardado y `RegroupWindow` a su ventana; se informa con `logger.info` cuántos grupos se cargaron, cuántos se saltearon y qué archivos quedaron en cuarentena;
   - si falla (versión más nueva, disco): `logger.error` con el motivo y que **las memorias no se van a guardar** en esta sesión. El plugin sigue funcionando; el candado (WP-15) impide que un guardado borre los archivos que no se pudieron leer.
5. **Goals de los miembros ya cargados:** después de cargar, para cada mundo y cada `Mob` cargado que sea miembro, `GoalInstaller.install`. Los que se cargan después los cubre `EntityLifecycleListener`.
6. **Un solo tick por tick del server**, agendado con `runTaskTimer(plugin, …, 1, 1)`, que en este orden: avanza el reloj, muestrea el movimiento, decide los grupos que tocan y guarda si toca. Un solo `Runnable` deja el orden fijo y fácil de seguir.
7. **Semilla del azar:** `ThreadLocalRandom.current().nextLong()`, registrada con `logger.info` al arrancar (sirve para depurar; la reproducción exacta usa los números grabados del WP-29).
8. **Al deshabilitar:** cancelar el tick y `PersistenceScheduler.shutdown()` (espera la escritura pendiente y guarda por última vez). Si el plugin nunca terminó de habilitarse, no hay nada que apagar.
9. **Logs:** siempre con `getSLF4JLogger()`; nunca `System.out`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/bootstrap/PluginRuntime.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/MobAiPlugin.java` |
| Modificar | `docs/actualizar-paper.md` (filas de `PluginRuntime`, `AdapterServices` y `MobAiPlugin`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/bootstrap/CoreServicesTest.java` |

## Especificación

Imports a tu criterio; Spotless decide el formato. Una función, una tarea: los métodos de armado se parten en funciones privadas de 20 líneas como máximo.

### `CoreServices.java` (Java puro)

```java
/** The domain and the use cases, assembled once; no Paper here, so it is tested in JUnit. */
public record CoreServices(
    SettingsHolder settings,
    ServerTickCounter clock,
    RegroupWindow regroupWindow,
    ActiveGroups activeGroups,
    TickGroups tickGroups,
    RecordOutcome recordOutcome,
    RecordDamageTaken recordDamageTaken,
    RecordPlayerDeath recordPlayerDeath,
    RemoveMember removeMember,
    RecruitMob recruitMob,
    ResetMemories resetMemories,
    DescribeGroup describeGroup,
    DescribePlayerMemory describePlayerMemory,
    SaveMemories saveMemories,
    LoadMemories loadMemories) {

  public static CoreServices create(
      MobAiSettings initialSettings, MemoryRepository repository, RandomSource random) { … }
}
```

Orden de armado dentro de `create` (repartido en funciones privadas):

1. `SettingsHolder settings = new SettingsHolder(initialSettings)`; `ServerTickCounter clock = new ServerTickCounter()`; `RegroupWindow regroupWindow = new RegroupWindow(settings.section(MobAiSettings::retreat))`; `ActiveGroups activeGroups = new ActiveGroups()`.
2. `DomainEventPublisher publisher = new DomainEventPublisher()`; `GroupEvents groupEvents = new GroupEvents(publisher)`; `ClosePlan closePlan = new ClosePlan(activeGroups)`; `publisher.subscribe(PlanClosed.class, closePlan::execute)`.
3. `Brain brain = new Brain(settings::current, BrainParts.standard(settings::current, random, regroupWindow))`.
4. Casos de uso:
   - `TickGroups(activeGroups, brain, groupEvents)`;
   - `RecordOutcome(activeGroups, settings)`, `RecordDamageTaken(activeGroups)`, `RecordPlayerDeath(activeGroups, settings, groupEvents)`;
   - `DisbandGroup(activeGroups, groupEvents)` y `RemoveMember(activeGroups, disbandGroup, regroupWindow)`;
   - `RecruitMob(activeGroups, settings, new RandomGroupIdSource())`;
   - `ResetMemories(activeGroups)`, `DescribeGroup(activeGroups)`, `DescribePlayerMemory(activeGroups)`;
   - `SaveMemories(activeGroups, repository)`, `LoadMemories(activeGroups, settings, repository)`.

`ServerTickCounter` y `RandomGroupIdSource` son adaptadores sin Paper: `bootstrap` puede usarlos (ArchUnit lo permite) y `CoreServicesTest` corre sin server.

Agregá también:

```java
  public StoredState storedState() {
    return new StoredState(clock.currentTick(), regroupWindow.currentTicks());
  }

  public void restore(StoredState state) {
    clock.restore(state.serverTick());
    regroupWindow.restore(state.regroupWindowTicks());
  }
```

### `AdapterServices.java` (Paper)

```java
/** The Paper side, assembled once over the core. */
public record AdapterServices(
    VersionTranslator translator,
    GoalInstaller goalInstaller,
    MovementSampler movementSampler,
    DecisionScheduler decisionScheduler,
    PersistenceScheduler persistenceScheduler,
    List<Listener> listeners) {

  public static AdapterServices create(Plugin plugin, CoreServices core, Logger logger) { … }
}
```

Armado:
- `VersionTranslator translator = new VersionTranslator()`; `MovementTracker movement = new MovementTracker()`; `SnapshotFactory snapshots = new SnapshotFactory(translator, movement, core.settings().section(MobAiSettings::group))`.
- `AttackTracker tracker = new AttackTracker(core.recordOutcome(), new AttackClassifier())`; `MeleeAttacker attacker = new MeleeAttacker(tracker, core.clock())`.
- `RoleRegistry roles = new RoleRegistry()`; `GoalInstaller goalInstaller = new GoalInstaller(new GoalContext(plugin, roles, attacker), translator)`.
- `DecisionScheduler decisionScheduler = new DecisionScheduler(core.activeGroups(), new GroupDecider(new DecisionParts(snapshots, core.tickGroups(), new DecisionApplier(roles)), logger), core.settings().section(MobAiSettings::group))`.
- `PersistenceScheduler persistenceScheduler = new PersistenceScheduler(core.saveMemories(), core::storedState, logger)`.
- `MovementSampler movementSampler = new MovementSampler(movement, core.clock())`.
- Listeners, en este orden: `new DamageListener(tracker, translator)`, `new ThreatListener(core.recordDamageTaken(), core.clock())`, `new DeathListener(core.removeMember(), core.recordPlayerDeath(), core.clock())`, `new EntityLifecycleListener(core.activeGroups(), goalInstaller, tracker)`, `new TargetListener(core.activeGroups())`, `movementSampler`.

### `PluginRuntime.java` (Paper)

```java
/** What runs while the plugin is enabled: assembled on start, saved and stopped on stop. */
public final class PluginRuntime {
  private final CoreServices core;
  private final AdapterServices adapters;
  private final BukkitTask tick;

  private PluginRuntime(CoreServices core, AdapterServices adapters, BukkitTask tick) { … }

  public static PluginRuntime start(JavaPlugin plugin) { … reglas 2 a 7 … }

  public void stop() {
    tick.cancel();
    adapters.persistenceScheduler().shutdown();
  }

  private void runTick() {
    core.clock().advance();
    long now = core.clock().currentTick();
    adapters.movementSampler().sampleOnlinePlayers();
    adapters.decisionScheduler().tick(now);
    adapters.persistenceScheduler().tick(now, core.settings().current().persistence().saveIntervalTicks());
  }
}
```

Funciones privadas sugeridas para `start` (una tarea cada una): `loadSettings(JavaPlugin)`, `memoryRepository(JavaPlugin)`, `loadMemories(CoreServices, Logger)` (aplica el estado con `core.restore` y registra el informe, o registra la falla), `registerListeners(JavaPlugin, AdapterServices)`, `installGoalsOnLoadedMembers(CoreServices, AdapterServices)` (recorre `Bukkit.getWorlds()` y `world.getEntitiesByClass(Mob.class)`), `scheduleTick(JavaPlugin, PluginRuntime)`.

Como `runTick` necesita la instancia y la instancia necesita el `BukkitTask`, armá primero los servicios y después agendá con una referencia al método de la instancia (por ejemplo, el constructor recibe un `Function<Runnable, BukkitTask>` o se agenda dentro de un método de fábrica que construye en dos pasos). Elegí la forma más simple que compile sin campos nulos visibles, y explicala en el informe.

`loadSettings` lanza `InvalidConfigException` (envolviendo cualquier `RuntimeException` del cargador que no lo sea, con el mensaje `"config.yml: " + mensaje`).

### `MobAiPlugin.java`

```java
public final class MobAiPlugin extends JavaPlugin {
  private Optional<PluginRuntime> runtime = Optional.empty();

  @Override
  public void onEnable() {
    try {
      runtime = Optional.of(PluginRuntime.start(this));
      getSLF4JLogger().info("MobAI {} enabled", getPluginMeta().getVersion());
    } catch (InvalidConfigException exception) {
      getSLF4JLogger().error("MobAI disabled: {}", exception.getMessage());
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    runtime.ifPresent(PluginRuntime::stop);
    runtime = Optional.empty();
    getSLF4JLogger().info("MobAI disabled");
  }
}
```

(Un campo `Optional` es la excepción aceptada acá: el plugin existe antes de habilitarse y después de deshabilitarse, y Paper no deja inyectarlo por constructor).

### `docs/actualizar-paper.md`

Filas nuevas o actualizadas:

| Clase | Qué usa de Paper | Riesgo | Qué revisar al actualizar |
| --- | --- | --- | --- |
| `bootstrap/PluginRuntime` | `JavaPlugin` (`saveDefaultConfig`, `reloadConfig`, `getConfig`, `getDataFolder`, `getSLF4JLogger`), `BukkitScheduler.runTaskTimer`, `BukkitTask`, `PluginManager.registerEvents`, `Bukkit.getWorlds`, `World.getEntitiesByClass` | Bajo | — |
| `bootstrap/AdapterServices` | `Plugin`, `Listener` | Bajo | — |
| `bootstrap/MobAiPlugin` | `JavaPlugin` (`getPluginMeta`, `disablePlugin`) | Bajo | — |

## Pruebas obligatorias

### `CoreServicesTest` (5)

`CoreServices.create(TestSettings.defaults(), repository, new SeededRandomSource(7))` con un `InMemoryMemoryRepository`.

| Prueba | Verifica |
| --- | --- |
| `closePlanIsSubscribedToPlanClosed` | recluta `mob(1)` (`RecruitMob`); en su grupo, `beginPlanning` y `startPlan` contra `player` (como en el WP-13); `recordPlayerDeath().execute(player, 160)`: la memoria del grupo tiene un registro de estrategia de `player` con `successes` 1.0. **Es la prueba que pide CT-11**: sin la suscripción, el aprendizaje de estrategias se apagaría en silencio |
| `removingTheLastMemberWhileRegroupingShortensTheWindow` | grupo de un miembro llevado a reagrupar (`closePlan(GROUP_RETREATED, …)` y `finishEvaluation`); `removeMember().execute(mob(1), DIED, 300)`: `regroupWindow().currentTicks()` 550 |
| `storedStateReflectsClockAndWindow` | `clock().restore(1234)`: `storedState()` = `new StoredState(1234, 600)` |
| `restoreAppliesTheSavedState` | `restore(new StoredState(5000, 700))`: reloj 5000 y ventana 700 |
| `savedMemoriesLoadIntoAFreshCore` | recluta `mob(1)`; `saveMemories().write(saveMemories().capture(storedState()))`; un `CoreServices` nuevo con el mismo repositorio: `loadMemories().execute()` carga un grupo y `activeGroups().groupOf(mob(1))` está presente |

Total: **5 pruebas**. `AdapterServices`, `PluginRuntime` y `MobAiPlugin` se verifican en el server (sección de verificación).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `CoreServices.create`, no suscribir `ClosePlan` | la memoria no tiene registro de estrategia | `closePlanIsSubscribedToPlanClosed` |
| 2 | `RemoveMember` armado con otra `RegroupWindow` (una nueva) | la ventana del núcleo sigue en 600 | `removingTheLastMemberWhileRegroupingShortensTheWindow` |
| 3 | En `restore`, no restaurar la ventana | ventana 600 | `restoreAppliesTheSavedState` |

## Verificación en el server (la hago yo, Opus, al revisar; no el subagente)

1. `./gradlew runServer`: en el log, `MobAI … enabled`, la semilla y el informe de carga (0 grupos la primera vez); `plugins/MobAI/config.yml` creado.
2. `stop`: `MobAI disabled` y `plugins/MobAI/memories/state.json` escrito.
3. `config.yml` con `learning-speed: 2.0` y reinicio: el plugin se deshabilita con el mensaje del dominio.

## Procedimiento

1. Rama `wp-20b-arranque-del-plugin` desde `origin/main` actualizado.
2. `CoreServices` con `CoreServicesTest`. Commit: `feat: assemble the core services once`.
3. `AdapterServices`, `PluginRuntime`, `MobAiPlugin` y `docs/actualizar-paper.md`. Commit: `feat: enable the plugin with loading, ticking and saving`.
4. Pruebas que muerden (de a una, en secuencia; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-20B: plugin startup and lifecycle`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura y cómo resolviste el armado del tick.

**Nunca corras `./gradlew runServer`**: la verificación en el server la hago yo.

## Correcciones permitidas

1. Formato de Spotless.
2. La forma de agendar el tick sin campos nulos (ver `PluginRuntime`), explicada en el informe.
3. Si un constructor de una clase existente no coincide con lo que dice este WP, usá el real y avisalo; si falta una pieza, frená.

## Fuera de alcance

- Comandos, mensajes y spawn del grupo de prueba (WP-21).
- Trazas, caja negra e incidentes (WP-29).
- Curación de los mobs en retirada (WP-22).

## Aceptación

- [ ] Exactamente los archivos de la tabla.
- [ ] Las 5 pruebas con sus nombres exactos, en verde.
- [ ] Las 3 roturas mordieron.
- [ ] Ninguna instancia de un servicio compartido se crea fuera de `CoreServices.create` y `AdapterServices.create`.
- [ ] `docs/actualizar-paper.md` actualizado.
- [ ] Build, cobertura y CI en verde.
