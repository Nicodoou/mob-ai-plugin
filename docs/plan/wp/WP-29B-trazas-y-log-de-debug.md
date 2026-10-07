# WP-29B — Trazas y log de debug

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo (cierra la etapa) |
| Depende de | WP-29A y WP-21 (mergeados) |
| Modelo | Sonnet |
| Rama | `wp-29b-trazas-y-log-de-debug` |

## Objetivo

Completar el modo debug (arquitectura, «Trazabilidad y depuración», puntos 3 y 4; catálogo, «Qué cuenta como acierto»):

1. **`TraceLevels`**: el nivel de traza de cada grupo (`OFF`, `DECISIONS` o `FULL`), con el valor por defecto de la configuración y cambios por comando.
2. **`TraceWriter`**: escribe los eventos que permite el nivel en JSON Lines (`plugins/MobAI/debug/trace-<tick de arranque>.jsonl`), en otro hilo.
3. **`DebugLog`**: una línea legible por cada plan cerrado y cada ataque, **siempre** (sin importar el nivel), en `plugins/MobAI/debug/mobai-debug.log`. Es lo que se cuenta en la validación del MVP (WP-27).
4. **`LineFileWriter`**: el escritor de líneas en un hilo propio que comparten los dos anteriores.
5. **`/mobai debug <grupo|all> <nivel>`**.
6. **`TraceHub`** (WP-29A) pasa a mandar cada evento a la caja negra (siempre), al `TraceWriter` (según el nivel) y al `DebugLog` (planes y ataques).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/debug/` (todo: lo dejó el WP-29A)
- `src/main/java/io/github/nicodoou/mobai/adapter/command/MobAiCommand.java`, `Subcommand.java`, `StatusCommand.java` (como modelo)
- `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/DebugSettings.java`, `TraceLevel.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`, `domain/attack/AttackFacts.java`, `domain/shared/MobId.java`, `PlayerId.java`, `GroupId.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java`
- `docs/actualizar-paper.md`

## Reglas de negocio

1. **Nivel de un grupo:** el que se le fijó por comando; si no, el fijado para todos con `debug all`; si no, `DebugSettings.defaultTraceLevel` vigente (una recarga de la configuración vale enseguida para los grupos sin nivel propio).
2. **`debug all <nivel>`** fija el nivel para todos y **borra** los niveles propios de cada grupo (si no, `all` no sería «todos»).
3. **Qué escribe el `TraceWriter` según el nivel:** `OFF` nada; `DECISIONS` decisiones y cierres de plan; `FULL` además los ataques. La caja negra recibe todo siempre (WP-29A).
4. **JSON Lines:** una línea por evento, `{"kind":"<DecisionEvent|AttackEvent|PlanEvent>","event":{…}}`, con el `Gson` de `DebugGson` sin pretty printing (una línea por evento). El archivo se llama `trace-<tick del reloj al arrancar>.jsonl` para no mezclar sesiones.
5. **`DebugLog`** escribe siempre, en este formato exacto (`Locale.ROOT`):
   - plan: `PLAN tick=<tick> group=<id corto> plan=<número> strategy=<estrategia> target=<id corto del jugador> reason=<motivo> success=<éxito con 2 decimales> damage=<daño con 1 decimal>`
   - ataque: `ATTACK tick=<tick> group=<id corto> mob=<id corto> attack=<id del ataque> target=<id corto del jugador> outcome=<HIT|PARTIAL|MISS|NEUTRAL:causa> rule=<regla> damage=<daño real con 1 decimal>`
   Ejemplo: `PLAN tick=12500 group=1a2b3c4d plan=3 strategy=FLANK target=9f8e7d6c reason=TARGET_DIED success=1.00 damage=20.0`.
6. **Escritura:** cada archivo con un único hilo propio, en modo agregar (`APPEND`, `CREATE`), UTF-8, una línea por llamada. Un error de disco se registra con `logger.error` **una sola vez por archivo** (después se cuentan en silencio y se informa el total al cerrar), para no inundar la consola. Al deshabilitar, se espera hasta 10 s.
7. **Comando** `/mobai debug <grupo|all> <nivel>` (permiso `mobai.admin`, como todo `/mobai`):
   - nivel desconocido: `UNKNOWN_LEVEL` con los válidos;
   - grupo desconocido: `GROUP_NOT_FOUND` (ya existe);
   - éxito: `DEBUG_SET` (grupo y nivel) o `DEBUG_SET_ALL` (nivel);
   - autocompletado: `all` y los ids cortos; después, los niveles.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceLevels.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/LineFileWriter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceWriter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceDestinations.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/TraceHub.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugGson.java` (variante compacta) |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/DebugCommand.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/command/MobAiCommand.java` (si el mapa de subcomandos se arma ahí) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java` |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/TraceLevelsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/LineFileWriterTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/DebugLogTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/debug/TraceHubTest.java` |

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `TraceLevels.java`

```java
/** The trace level of each group: its own, the one set for all, or the configured default. */
public final class TraceLevels {
  private final Supplier<DebugSettings> settings;
  private final Map<GroupId, TraceLevel> ownLevels = new HashMap<>();
  private Optional<TraceLevel> levelForAll = Optional.empty();

  public TraceLevels(Supplier<DebugSettings> settings) { … }

  public TraceLevel levelOf(GroupId group) { … regla 1 … }

  public void set(GroupId group, TraceLevel level) { ownLevels.put(group, level); }

  public void setAll(TraceLevel level) { ownLevels.clear(); levelForAll = Optional.of(level); }

  public boolean writes(TraceEvent event) { … regla 3, con levelOf(event.group()) … }
}
```

### `LineFileWriter.java`

```java
/** Appends lines to one file on its own thread; a disk error is reported once, then counted. */
public final class LineFileWriter {
  private static final long SHUTDOWN_WAIT_SECONDS = 10;

  public LineFileWriter(Path file, Logger logger) { … el hilo se llama "MobAI-" + nombre del archivo … }

  public void append(String line) { … al hilo: crear carpetas, Files.writeString(file, line + "\n", UTF_8, CREATE, APPEND) … }

  public void shutdown() { … esperar; si hubo errores, logger.warn con el total … }
}
```

El primer `IOException` de un archivo se registra con `logger.error("Could not write {}", file, excepción)`; los siguientes solo suman a un contador (un `AtomicInteger`, porque lo toca el hilo de escritura y se lee al cerrar).

### `TraceWriter.java` y `DebugLog.java`

```java
/** Writes trace events as JSON Lines, one event per line. */
public final class TraceWriter {
  private final LineFileWriter lines;
  private final Gson gson = DebugGson.compact();

  public TraceWriter(LineFileWriter lines) { … }

  public void write(TraceEvent event) { lines.append(gson.toJson(new TraceLine(kindOf(event), event))); }

  public void shutdown() { lines.shutdown(); }
  …
}

/** One readable line per closed plan and per attack, always on: the MVP is validated by counting them. */
public final class DebugLog {
  private final LineFileWriter lines;

  public DebugLog(LineFileWriter lines) { … }

  public void record(TraceEvent event) { … PlanEvent y AttackEvent con el formato de la regla 5; DecisionEvent se ignora … }

  public void shutdown() { lines.shutdown(); }

  static String planLine(TraceEvent.PlanEvent event) { … }

  static String attackLine(TraceEvent.AttackEvent event) { … }
}
```

`TraceLine` es un record privado `(String kind, TraceEvent event)`; `kindOf` devuelve el nombre simple de la clase. `DebugGson.compact()` es `create()` sin pretty printing (las dos comparten el armado en una función privada).

En `attackLine`, el daño es `facts().realDamage()`, el ataque `facts().attack().id()`, el mob `facts().mob().shortId()` y el objetivo `facts().target().shortId()`.

### `TraceDestinations.java` y `TraceHub`

```java
/** Where trace events go: the black box always, the trace file by level, the debug log for plans and attacks. */
public record TraceDestinations(FlightRecorder recorder, TraceLevels levels, TraceWriter writer, DebugLog debugLog) { … }
```

`TraceHub(ActiveGroups activeGroups, TraceDestinations destinations)`: los métodos públicos no cambian de firma; cada evento pasa por una única función privada `route(TraceEvent event)`: `recorder.record(event)`; si `levels.writes(event)`, `writer.write(event)`; `debugLog.record(event)`. Agregar `public TraceLevels levels()` para el comando (o pasar `TraceLevels` al comando desde el armado: elegí lo segundo si deja a `TraceHub` con menos métodos, y avisalo).

### `DebugCommand.java`

`DebugCommand(TraceLevels levels, DescribeGroup describeGroup, Messages messages)` implementa `Subcommand`. Busca el grupo por id corto con `describeGroup.find`. Registrar `"debug"` en el mapa de subcomandos.

### `MessageKey` y `messages.yml`

| Clave | Ruta | Texto |
| --- | --- | --- |
| `UNKNOWN_LEVEL` | `unknown-level` | `<red>Nivel desconocido: <gray><level></gray>. Usá uno de: <gray><valid>` |
| `DEBUG_SET` | `debug-set` | `<green>Grupo <white><group></white>: traza en <white><level></white>.` |
| `DEBUG_SET_ALL` | `debug-set-all` | `<green>Todos los grupos: traza en <white><level></white>.` |

### Armado

- `AdapterServices`: `TraceLevels(core.settings().section(MobAiSettings::debug))`, `TraceWriter(new LineFileWriter(debugFolder.resolve("trace-" + core.clock().currentTick() + ".jsonl"), logger))` (después de cargar las memorias, así el tick es el restaurado), `DebugLog(new LineFileWriter(debugFolder.resolve("mobai-debug.log"), logger))`, `TraceHub(core.activeGroups(), new TraceDestinations(recorder, levels, traceWriter, debugLog))`, `DebugCommand`.
- `PluginRuntime.stop()`: después del guardado y del escritor de incidentes, `traceWriter.shutdown()` y `debugLog.shutdown()`.

### `docs/actualizar-paper.md`

`adapter/debug/*` sigue sin Paper; agregar `DebugCommand` a la fila de `adapter/command/*`.

## Pruebas obligatorias

### `TraceLevelsTest` (5)

| Prueba | Verifica |
| --- | --- |
| `defaultComesFromTheSettings` | con `defaultTraceLevel` `OFF`, `levelOf(groupId(1))` = `OFF`; con un supplier que pasa a `DECISIONS`, `DECISIONS` |
| `ownLevelWins` | `set(groupId(1), FULL)`: `FULL` para el 1 y el default para el 2 |
| `levelForAllReplacesOwnLevels` | `set(groupId(1), FULL)` y `setAll(DECISIONS)`: los dos en `DECISIONS` |
| `decisionsLevelWritesDecisionsAndPlansButNotAttacks` | nivel `DECISIONS`: `writes` `true` para `DecisionEvent` y `PlanEvent`, `false` para `AttackEvent` |
| `offWritesNothingAndFullWritesEverything` | `OFF`: los tres `false`; `FULL`: los tres `true` |

### `LineFileWriterTest` (3)

| Prueba | Verifica |
| --- | --- |
| `linesAreAppendedInOrder` | `append("a")`, `append("b")`, `shutdown()`: el archivo (en una carpeta que no existía) es `"a\nb\n"` |
| `appendsToAnExistingFile` | archivo con `"x\n"`; `append("y")`, `shutdown()`: `"x\ny\n"` |
| `diskErrorDoesNotThrow` | ruta cuyo padre es un archivo: `append` y `shutdown` no lanzan |

### `DebugLogTest` (3)

| Prueba | Verifica |
| --- | --- |
| `planLineHasTheExactFormat` | `PlanEvent` con `ClosedPlan(new PlanId(groupId(1), 3), new StrategyId("FLANK"), player, TARGET_DIED, 1.0, 20.0, 100, 12500)` en el tick 12500: `"PLAN tick=12500 group=" + groupId(1).shortId() + " plan=3 strategy=FLANK target=" + player.shortId() + " reason=TARGET_DIED success=1.00 damage=20.0"` |
| `attackLineHasTheExactFormat` | `AttackEvent` con resultado `"PARTIAL"`, regla 7 y hechos de `AttackFactsBuilder` (mob, objetivo, `ZOMBIE_FRONT_STRIKE`, daño real 0): la línea exacta con `attack=zombie.front_strike outcome=PARTIAL rule=7 damage=0.0` |
| `decisionsAreNotLogged` | `record(DecisionEvent …)` y `shutdown()`: el archivo no existe o está vacío |

(Si `AttackFactsBuilder` no tiene lo necesario, armá `AttackFacts` a mano y avisalo).

### `TraceHubTest` (3)

`TraceHub` con un `FlightRecorder`, `TraceLevels` y `TraceWriter`/`DebugLog` sobre `@TempDir`; leer los archivos después de `shutdown()`.

| Prueba | Verifica |
| --- | --- |
| `everythingReachesTheBlackBox` | con nivel `OFF`, un `planClosed`: `recent(groupId(1))` lo tiene y el `.jsonl` está vacío o no existe |
| `traceFileFollowsTheLevel` | nivel `DECISIONS`: un `planClosed` y un `attacked` (mob miembro del grupo 1): el `.jsonl` tiene una línea y empieza con `{"kind":"PlanEvent"` |
| `debugLogGetsPlansAndAttacksAtAnyLevel` | nivel `OFF`: el `mobai-debug.log` tiene dos líneas, una `PLAN` y una `ATTACK` |

Total: **14 pruebas**.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `setAll`, no borrar los niveles propios | `levelForAllReplacesOwnLevels` |
| 2 | En `writes`, `DECISIONS` deja pasar también los ataques | `decisionsLevelWritesDecisionsAndPlansButNotAttacks` |
| 3 | En `LineFileWriter.append`, sin `APPEND` (pisa el archivo) | `appendsToAnExistingFile` |
| 4 | En `planLine`, `success` con `%s` en vez de `%.2f` | `planLineHasTheExactFormat` (`success=1.0`) |

## Procedimiento

1. Rama `wp-29b-trazas-y-log-de-debug` desde `origin/main` actualizado.
2. `TraceLevels`, `LineFileWriter` con sus pruebas. Commit: `feat: add trace levels and a background line writer`.
3. `DebugGson.compact`, `TraceWriter`, `DebugLog`, `TraceDestinations`, `TraceHub` con `DebugLogTest` y `TraceHubTest`. Commit: `feat: write traces by level and a readable debug log`.
4. `DebugCommand`, mensajes, armado y `docs/actualizar-paper.md`. Commit: `feat: add /mobai debug`.
5. Roturas (de a una), `spotlessApply`, `build jacocoTestReport jacocoTestCoverageVerification`, push, PR `WP-29B: traces and debug log`, check `build` en verde, informe con la prueba que falló en cada rotura. **Nunca** `runServer`.

## Correcciones permitidas

1. Formato de Spotless.
2. `TraceLevels` al comando por el armado en vez de `TraceHub.levels()` (ver arriba).
3. `AttackFacts` a mano en `DebugLogTest`.

## Fuera de alcance

- Métricas para contar automáticamente y `/mobai memory` (WP-26).
- Rotar o limpiar archivos de debug viejos (fase 2; se anota como riesgo).

## Aceptación

- [ ] Los archivos de la tabla; 14 pruebas en verde; 4 roturas mordieron.
- [ ] `docs/actualizar-paper.md` actualizado.
- [ ] Build, cobertura y CI en verde.
