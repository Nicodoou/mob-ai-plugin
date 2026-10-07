# WP-29A — Incidentes en el server

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-20B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-29a-incidentes-en-el-server` |

## Objetivo

Que una falla del cerebro en el server deje un **incidente reproducible** (requisito obligatorio del modo debug; CT-12):

1. **`DecisionWitness`**: antes de cada decisión publica los eventos pendientes del grupo, limpia el azar grabado y copia el grupo; si la decisión sale bien, la anota en la caja negra; si falla, arma el `IncidentReport` (WP-28B).
2. **`FlightRecorder`** (caja negra): los últimos eventos de traza de cada grupo, siempre, aunque el nivel sea `OFF`.
3. **`TraceHub`**: el único punto por donde entran los eventos de traza (decisiones, ataques, cierres de plan). En este WP solo los manda a la caja negra; el WP-29B le suma el escritor de trazas y el log legible.
4. **`IncidentWriter`**: escribe `incident-<id>.json` (reproducible con `TraceReplay`) y `incident-<id>-blackbox.json` en `plugins/MobAI/debug/`, en un hilo propio, y deja una línea en la consola.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/GroupDecider.java`, `DecisionParts.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/IncidentJson.java`, `OptionalTypeAdapterFactory.java`, `IncidentFile.java`
- `src/main/java/io/github/nicodoou/mobai/application/IncidentReport.java`, `IncidentLocation.java`, `IncidentFailure.java`, `GroupCapture.java`, `GroupCaptureMapper.java`, `RecordingRandomSource.java`, `GroupEvents.java`, `TickGroups.java`, `SettingsHolder.java`
- `src/main/java/io/github/nicodoou/mobai/domain/attack/Classification.java`, `ClassificationTrace.java`, `AttackOutcome.java`; `domain/decision/BrainResult.java`, `ClosedPlan.java`; `domain/event/PlanClosed.java`; `domain/settings/DebugSettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java`, `AdapterServices.java`, `PluginRuntime.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/TraceReplay.java`, `IncidentFixture.java`, `BrainFixture.java`, `TestSettings.java`
- `docs/actualizar-paper.md`

## Reglas de negocio

1. **Antes de cada decisión** (solo si hay foto): publicar los eventos pendientes del grupo (`GroupEvents.publishPending`), limpiar el azar grabado (`RecordingRandomSource.clear`), copiar el grupo (`GroupCaptureMapper.capture`) y anotar la ventana de reagrupamiento. Se publica **antes** de copiar para que la copia no arrastre eventos que la repetición no generaría (CT-12).
2. **Si la decisión sale bien:** un `DecisionEvent` a `TraceHub`, y recién después se aplican las órdenes. Aplicar las órdenes queda **fuera** del bloque que arma incidentes: una falla del aplicador no es una falla del cerebro y no debe producir un incidente que no se reproduce.
3. **Si la decisión falla** (`RuntimeException` de `TickGroups.execute`): `IncidentReport` con id `<id corto del grupo>-<tick>`, ubicación `IncidentLocation("domain", "Brain", "decide", "TickGroups")`, la falla, la copia de antes, la ventana de antes, la foto, la configuración vigente, los números grabados, sin resultado, la copia de después y la ventana de después. Se escribe con su caja negra. En la consola, una sola línea de error con el grupo, el tick, el id del incidente y la excepción.
4. **Una falla al armar la foto** (Paper) no es un incidente del cerebro: se registra como hasta ahora.
5. **Caja negra:** por grupo, los últimos `DebugSettings.flightRecorderEvents` eventos (leído al anotar, así una recarga vale enseguida). Para que no crezca con grupos disueltos, guarda como máximo `MAX_GROUPS` = 256 grupos y descarta el menos usado.
6. **Eventos de traza** (`TraceEvent`, sellado), cada uno con grupo y tick:
   - `DecisionEvent`: la `GroupDecision` y la `DecisionTrace`;
   - `AttackEvent`: el ataque, el resultado como texto (`HIT`, `PARTIAL`, `MISS` o `NEUTRAL:<causa>`), la regla del rastreador y los hechos (`AttackFacts`);
   - `PlanEvent`: el `ClosedPlan`.
   Los ataques llegan desde `MeleeAttacker` (con la clasificación que devuelve el rastreador) y se atribuyen al grupo del mob; los cierres de plan, por suscripción a `PlanClosed`.
7. **Escritura de incidentes** en `plugins/MobAI/debug/`, en un único hilo propio (`MobAI-incident-writer`). Un error de disco se registra y no tumba nada. Al deshabilitar el plugin se espera hasta 10 s a que termine lo pendiente.
8. **Costo:** copiar el grupo en cada decisión es lo que permite reproducir; se mide en la validación (WP-27, Spark). Si pesa, el ajuste será copiar solo con debug activo (no en este WP).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceEvent.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugGson.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/IncidentJson.java` (usa `DebugGson`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/FlightRecorder.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceHub.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/IncidentWriter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/WitnessParts.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/Observation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DecisionWitness.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/GroupDecider.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java` (anota cada ataque en `TraceHub`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java` (armado, suscripción a `PlanClosed`, apagado del escritor) |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/FlightRecorderTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/DecisionWitnessTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/IncidentWriterTest.java` |

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `TraceEvent.java`

```java
/** One entry of a group's trace: what the brain decided, how an attack went, how a plan closed. */
public sealed interface TraceEvent {
  GroupId group();

  long tick();

  record DecisionEvent(GroupId group, long tick, GroupDecision decision, DecisionTrace trace)
      implements TraceEvent { … requireNonNull … }

  record AttackEvent(GroupId group, long tick, String outcome, int rule, AttackFacts facts)
      implements TraceEvent { … requireNonNull … }

  record PlanEvent(GroupId group, long tick, ClosedPlan plan) implements TraceEvent { … }

  static String outcomeLabel(AttackOutcome outcome) {
    return switch (outcome) {
      case AttackOutcome.Hit hit -> "HIT";
      case AttackOutcome.Partial partial -> "PARTIAL";
      case AttackOutcome.Miss miss -> "MISS";
      case AttackOutcome.Neutral neutral -> "NEUTRAL:" + neutral.cause();
    };
  }
}
```

(El resultado va como texto porque `Hit`, `Partial` y `Miss` no tienen campos: en JSON se verían todos como `{}`).

### `DebugGson.java` (package-private)

Mueve acá la construcción de `Gson` que hoy está en `IncidentJson` (mismas opciones, sin cambios): `static Gson create()`. `IncidentJson` la usa. Así los incidentes y la caja negra se escriben igual.

### `FlightRecorder.java`

```java
/** The last trace events of each group, kept in memory whatever the trace level. */
public final class FlightRecorder {
  // Disbanded groups would otherwise stay here forever.
  static final int MAX_GROUPS = 256;

  private final Supplier<DebugSettings> settings;
  private final Map<GroupId, ArrayDeque<TraceEvent>> events = new LinkedHashMap<>(16, 0.75f, true) { … removeEldestEntry: size() > MAX_GROUPS … };

  public FlightRecorder(Supplier<DebugSettings> settings) { … }

  public void record(TraceEvent event) { … agregar al final y descartar del principio hasta flightRecorderEvents … }

  public List<TraceEvent> recent(GroupId group) { … copia en orden, vacía si no hay … }
}
```

El `LinkedHashMap` anónimo genera una advertencia de `serial` con `-Xlint:all`: si aparece, agregá `@SuppressWarnings("serial")` en el campo con un comentario de una línea (nunca se serializa).

### `TraceHub.java`

```java
/** The single entry for trace events; later stages add more destinations here (WP-29B). */
public final class TraceHub {
  private final ActiveGroups activeGroups;
  private final FlightRecorder recorder;

  public TraceHub(ActiveGroups activeGroups, FlightRecorder recorder) { … }

  public void decided(GroupId group, long tick, BrainResult result) {
    recorder.record(new TraceEvent.DecisionEvent(group, tick, result.decision(), result.trace()));
  }

  public void attacked(MobId mob, long tick, Classification classification) {
    activeGroups.groupOf(mob).ifPresent(group -> recorder.record(attackEvent(group.id(), tick, classification)));
  }

  public void planClosed(PlanClosed event) {
    recorder.record(new TraceEvent.PlanEvent(event.groupId(), event.tick(), event.plan()));
  }

  public List<TraceEvent> recent(GroupId group) {
    return recorder.recent(group);
  }
  …
}
```

### `IncidentWriter.java`

```java
/** Writes incident files on its own thread, so a failing decision never waits for the disk. */
public final class IncidentWriter {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;

  private final Path folder;
  private final Logger logger;
  private final IncidentJson json = new IncidentJson();
  private final Gson gson = DebugGson.create();
  private final ExecutorService writer = … un hilo de plataforma, daemon, llamado "MobAI-incident-writer" …;

  public IncidentWriter(Path folder, Logger logger) { … }

  public Path write(IncidentReport report, List<TraceEvent> blackBox) { … devuelve la ruta del incidente; la escritura va al hilo … }

  public void shutdown() { … como PersistenceScheduler: shutdown, esperar, avisar si no terminó … }
}
```

- Archivos: `folder/incident-<id>.json` (con `json.write(report)`) y `folder/incident-<id>-blackbox.json`: una lista de `{ "kind": "<DecisionEvent|AttackEvent|PlanEvent>", "event": {…} }` (un record privado `BlackBoxEntry(String kind, TraceEvent event)` con `kind` = nombre simple de la clase).
- Crear la carpeta si falta. Al terminar: `logger.info("MobAI incident {} written to {}", id, ruta)`. Un `IOException`: `logger.error("Could not write MobAI incident {}", id, excepción)`.

### `WitnessParts.java` y `Observation.java`

```java
/** What the witness needs from the core to copy a group and the brain's draws. */
public record WitnessParts(
    GroupEvents groupEvents, RecordingRandomSource draws, RegroupWindow regroupWindow, SettingsHolder settings) { … requireNonNull … }

/** The state of a group just before a decision, kept in case the decision fails. */
public record Observation(GroupSnapshot snapshot, GroupCapture before, long regroupWindowTicks) { … }
```

### `DecisionWitness.java`

```java
/** Watches each decision: copies the group before it and turns a failure into an incident. */
public final class DecisionWitness {
  private final WitnessParts parts;
  private final TraceHub hub;
  private final IncidentWriter writer;
  private final GroupCaptureMapper mapper = new GroupCaptureMapper();

  public DecisionWitness(WitnessParts parts, TraceHub hub, IncidentWriter writer) { … }

  public Observation before(Group group, GroupSnapshot snapshot) {
    parts.groupEvents().publishPending(group);
    parts.draws().clear();
    return new Observation(snapshot, mapper.capture(group), parts.regroupWindow().currentTicks());
  }

  public void succeeded(Group group, Observation observation, BrainResult result) {
    hub.decided(group.id(), observation.snapshot().tick(), result);
  }

  public IncidentReport failed(Group group, Observation observation, RuntimeException failure) {
    IncidentReport report = incident(group, observation, failure);
    writer.write(report, hub.recent(group.id()));
    return report;
  }
  …
}
```

`incident` arma el `IncidentReport` de la regla 3.

### `GroupDecider` (modificar)

Constructor `GroupDecider(DecisionParts parts, DecisionWitness witness, Logger logger)`.

```java
  public void decide(Group group, long tick) {
    Optional<GroupSnapshot> snapshot;
    try {
      snapshot = parts.snapshots().snapshotOf(group, tick);
    } catch (RuntimeException exception) {
      logger.error("MobAI group {} could not be photographed at tick {}", group.id().shortId(), tick, exception);
      return;
    }
    snapshot.flatMap(found -> decideWatched(group, found))
        .ifPresent(result -> parts.applier().apply(result.decision(), group.roster().members()));
  }

  // Applying orders stays outside: an applier failure is not a brain failure.
  private Optional<BrainResult> decideWatched(Group group, GroupSnapshot snapshot) {
    Observation observation = witness.before(group, snapshot);
    try {
      Optional<BrainResult> result = parts.tickGroups().execute(snapshot);
      result.ifPresent(found -> witness.succeeded(group, observation, found));
      return result;
    } catch (RuntimeException exception) {
      IncidentReport report = witness.failed(group, observation, exception);
      logger.error("MobAI group {} failed to decide at tick {}; incident {}", group.id().shortId(), snapshot.tick(), report.id(), exception);
      return Optional.empty();
    }
  }
```

(Una variable sin asignar antes del `try` y asignada adentro está bien; si preferís extraer la foto a un método que devuelva `Optional` y loguee, también: una tarea por función).

### `MeleeAttacker` (modificar)

Tercer parámetro `TraceHub hub`. Después de `closeMelee`, si hay clasificación, `hub.attacked(mobId, tick, clasificación)`. Devuelve lo mismo que antes.

### Armado

- `AdapterServices`: `FlightRecorder(core.settings().section(MobAiSettings::debug))`, `TraceHub(core.activeGroups(), recorder)`, `IncidentWriter(dataFolder.resolve("debug"), logger)` (la carpeta llega por parámetro o con `plugin.getDataFolder().toPath()`), `DecisionWitness(new WitnessParts(core.groupEvents(), core.randomDraws(), core.regroupWindow(), core.settings()), hub, incidentWriter)`, `MeleeAttacker(tracker, core.clock(), hub)`, `GroupDecider(…, witness, logger)`; suscribir `core.events().subscribe(PlanClosed.class, hub::planClosed)`; exponer `IncidentWriter` y `TraceHub` en el record.
- `PluginRuntime.stop()`: después del guardado final, `incidentWriter.shutdown()`.

### `docs/actualizar-paper.md`

Agregar `adapter/debug/*` (sin Paper: solo Gson y `org.slf4j.Logger`, que vienen con Paper; riesgo bajo).

## Pruebas obligatorias

### `FlightRecorderTest` (3)

| Prueba | Verifica |
| --- | --- |
| `keepsTheLastEventsOfEachGroup` | con `flightRecorderEvents` 3, 5 eventos del grupo 1 (ticks 1 a 5): `recent` = los de ticks 3, 4, 5 en orden |
| `groupsAreKeptApart` | eventos del grupo 1 y del 2: cada uno ve solo los suyos |
| `forgetsTheLeastUsedGroupBeyondTheLimit` | 257 grupos con un evento cada uno, en orden: el primero ya no está (`recent` vacío) y el último sí |

Eventos de prueba: `PlanEvent` con un `ClosedPlan` cualquiera válido.

### `DecisionWitnessTest` (4)

Armado como `DecisionRepeatTest` (WP-28A): `TestSettings.defaults()`, `RecordingRandomSource` sobre `SeededRandomSource(7)`, `RegroupWindow`, `Brain` con `BrainParts.standard`, el grupo del catálogo en `ActiveGroups`, `DomainEventPublisher` + `GroupEvents`, `TickGroups`, `FlightRecorder`, `TraceHub`, `IncidentWriter` sobre `@TempDir` con `NOPLogger.NOP_LOGGER`, y `DecisionWitness`. Dos decisiones previas como en el WP-28A, hechas a través de `before`/`TickGroups`/`succeeded`.

| Prueba | Verifica |
| --- | --- |
| `pendingEventsArePublishedBeforeTheCopy` | se saca un miembro con `group.removeMember` (queda un `LeaderDied` pendiente); `before(...)`: el suscriptor recibió el `LeaderDied` y `group.drainEvents()` está vacío |
| `successIsRecordedInTheFlightRecorder` | decisión en `START_TICK + 20`: `hub.recent(GROUP_ID)` termina con un `DecisionEvent` de ese tick |
| `failureWritesAnIncidentThatReproduces` | foto en `START_TICK + 20` con un mob que no es miembro (como `IncidentFixture.provokedFailure`); `TickGroups.execute` lanza; `failed(...)`; `writer.shutdown()`; el archivo `incident-<id>.json` existe, se lee con `IncidentJson.read` y **`TraceReplay.assertReproduces` no lanza** |
| `failureWritesTheBlackBox` | mismo caso: existe `incident-<id>-blackbox.json` y su texto contiene `"DecisionEvent"` (las decisiones previas) |

### `IncidentWriterTest` (2)

| Prueba | Verifica |
| --- | --- |
| `createsTheFolderAndBothFiles` | carpeta `debug` inexistente dentro de `@TempDir`; `write(IncidentFixture.provokedFailure(), List.of())` y `shutdown()`: los dos archivos existen |
| `diskErrorIsLoggedNotThrown` | carpeta que en realidad es un archivo (no se puede crear adentro): `write` y `shutdown` no lanzan |

Total: **9 pruebas**.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `before`, no publicar los eventos pendientes | `pendingEventsArePublishedBeforeTheCopy` (el `LeaderDied` sigue pendiente) |
| 2 | En `before`, no limpiar el azar grabado | `failureWritesAnIncidentThatReproduces` (sobran números de las decisiones previas: «unused draws») |
| 3 | En `FlightRecorder.record`, no descartar los viejos | `keepsTheLastEventsOfEachGroup` |
| 4 | En `incident`, usar la copia de después como `before` | `failureWritesAnIncidentThatReproduces` |

## Procedimiento

1. Rama `wp-29a-incidentes-en-el-server` desde `origin/main` actualizado.
2. `TraceEvent`, `DebugGson` (y `IncidentJson` usándolo; sus pruebas siguen verdes), `FlightRecorder`, `TraceHub` con `FlightRecorderTest`. Commit: `feat: keep a flight recorder of trace events per group`.
3. `IncidentWriter`, `WitnessParts`, `Observation`, `DecisionWitness` con sus pruebas. Commit: `feat: turn failed decisions into reproducible incidents`.
4. `GroupDecider`, `MeleeAttacker`, armado y `docs/actualizar-paper.md`. Commit: `feat: watch every decision and trace attacks and plans`.
5. Roturas (de a una), `spotlessApply`, `build jacocoTestReport jacocoTestCoverageVerification`, push, PR `WP-29A: incidents in the server`, check `build` en verde, informe con la prueba que falló en cada rotura. **Nunca** `runServer`.

## Correcciones permitidas

1. Formato de Spotless.
2. `@SuppressWarnings("serial")` en el mapa de `FlightRecorder` (ver arriba).
3. Si `DecisionWitnessTest.failureWritesAnIncidentThatReproduces` falla sin romper nada, **no la ajustes**: hay estado que no se copia o algo que la repetición no ve. Frená y reportá qué difiere (`TraceReplay` lo dice).

## Fuera de alcance

- Niveles de traza por grupo, archivo de trazas JSON Lines, log legible y `/mobai debug` (WP-29B).
- Incidentes del rastreador o de los goals (no tienen azar ni estado del cerebro; si fallan, se registran como error con contexto).

## Aceptación

- [ ] Los archivos de la tabla; 9 pruebas en verde; 4 roturas mordieron.
- [ ] Un incidente provocado se escribe y se reproduce con `TraceReplay` (prueba `failureWritesAnIncidentThatReproduces`).
- [ ] `docs/actualizar-paper.md` actualizado.
- [ ] Build, cobertura y CI en verde.
