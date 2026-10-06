# WP-28A — Estado completo del grupo y azar grabado

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia |
| Depende de | WP-15 (mergeado) |
| Modelo | **Opus** (toca el ciclo de planes y la amenaza, que usa el cerebro) |
| Rama | `wp-28a-estado-completo-y-azar-grabado` |

## Objetivo

Que una decisión del cerebro se pueda **repetir exactamente** (CT-12): con el mismo estado del grupo, la misma foto, la misma configuración y los mismos números al azar, el cerebro decide lo mismo. Para eso:

1. **Copiar y restaurar el estado completo de un grupo** (`GroupCapture`): lo que ya guarda el WP-15 (memoria, miembros, política, número de plan) más el ciclo de planes, la amenaza y los objetivos de las arañas.
2. **Grabar los números al azar** que consume una decisión (`RecordingRandomSource`) y **devolverlos en el mismo orden** al repetirla (`ReplayRandomSource`), avisando si la repetición pide algo distinto.
3. **Un único armado del cerebro** (`BrainParts.standard`) que usan el plugin, las pruebas y la reproducción, para que repetir una decisión use exactamente las mismas piezas.

El incidente, su JSON y la herramienta `TraceReplay` son del WP-28B.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java`, `GroupRoster.java`, `Group.java`, `Plan.java`, `GroupState.java`, `PlanEndReason.java`
- `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatLedger.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java`, `Brain.java` (solo el constructor y `decide`), `RegroupWindow.java`
- `src/main/java/io/github/nicodoou/mobai/domain/port/RandomSource.java`
- `src/main/java/io/github/nicodoou/mobai/application/StoredMemoriesMapper.java`, `SettingsHolder.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java`, `SeededRandomSource.java`, `ScriptedRandomSource.java`, `TestSettings.java`

## Reglas de negocio

1. **Qué entra en la copia de un grupo:**
   - lo del WP-15: `StoredGroup`;
   - el ciclo (`LifecycleCapture`): estado, plan en curso, objetivo comprometido, último motivo y tick de cierre, inicio del reagrupamiento y número de plan;
   - la amenaza (`ThreatCapture`): cada daño registrado con su jugador y tick, en orden, y el último tick visto;
   - los objetivos de las arañas, en orden de asignación.
2. **Coherencia de la copia del ciclo** (la valida el record):
   - nunca en `PLANNING`: el cerebro sale de ese estado dentro de la misma decisión, y la copia se toma antes de decidir;
   - plan presente si y solo si el estado es `EXECUTING` o `EVALUATING`;
   - inicio de reagrupamiento presente si y solo si el estado es `REGROUPING`;
   - en `EVALUATING` hay último motivo de cierre;
   - si hay plan, su número coincide con el número de plan de la copia.
3. **Restaurar** el ciclo y la amenaza solo en un grupo recién armado: el ciclo en `OBSERVING`, la amenaza vacía. El plan restaurado tiene que ser de este grupo y sus roles, de miembros actuales (los miembros se restauran antes).
4. **Copia y restauración son exactas:** copiar, restaurar en un grupo nuevo y volver a copiar da una copia igual (`equals`).
5. **Azar grabado:** cada número que pide el cerebro se graba con su tipo (`UNIT`, `GAUSSIAN` o `INDEX`) y, en `INDEX`, con su límite. Al repetir, cada pedido tiene que coincidir en tipo y límite con el grabado; si no, la repetición se desvió y se avisa con el número de pedido. Pedir más de lo grabado también es desvío.
6. **Una decisión repetida** desde la copia, con el azar grabado, da el mismo `BrainResult` (decisión, traza y plan cerrado, campo por campo), deja al grupo en una copia igual a la del original y consume todos los números grabados.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/LifecycleCapture.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` (`capture`, `restore`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/GroupRoster.java` (`spiderTargets`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatRecord.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatCapture.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatLedger.java` (`capture`, `restore`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/BrainParts.java` (`standard`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/GroupCapture.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/GroupCaptureMapper.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/DrawKind.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecordedDraw.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecordingRandomSource.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/ReplayRandomSource.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java` (`parts` usa `BrainParts.standard`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/LifecycleCaptureTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/threat/ThreatCaptureTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecordedRandomnessTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/GroupCaptureMapperTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/DecisionRepeatTest.java` |

**Métodos públicos nuevos en clases existentes** (CT-12: el modo debug tiene que reproducir un bug exactamente, y este estado es privado de cada clase):

| Clase | Agrega | Queda con |
| --- | --- | --- |
| `PlanLifecycle` | `capture()`, `restore(LifecycleCapture)` | 16 |
| `ThreatLedger` | `capture()`, `restore(ThreatCapture)` | 7 |
| `GroupRoster` | `spiderTargets()` | 9 |
| `BrainParts` | `standard(...)` (estático) | — |

**Por qué `GroupCapture` vive en `application` y no en `domain.group`:** incluye `StoredGroup` (`domain.port`), y `domain.port` ya depende de `domain.group` (por `Member`). Ponerla en `domain.group` crearía un ciclo de paquetes nuevo.

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `domain/group/LifecycleCapture.java`

```java
public record LifecycleCapture(
    GroupState state,
    Optional<Plan> plan,
    Optional<PlayerId> committedTarget,
    Optional<PlanEndReason> lastEndReason,
    long lastEndTick,
    OptionalLong regroupStartTick,
    long planSequence) {
  public LifecycleCapture {
    Objects.requireNonNull(state, "LifecycleCapture.state");
    Objects.requireNonNull(plan, "LifecycleCapture.plan");
    Objects.requireNonNull(committedTarget, "LifecycleCapture.committedTarget");
    Objects.requireNonNull(lastEndReason, "LifecycleCapture.lastEndReason");
    Objects.requireNonNull(regroupStartTick, "LifecycleCapture.regroupStartTick");
    requireNotPlanning(state);
    requirePlanMatchesState(state, plan);
    requireRegroupStartMatchesState(state, regroupStartTick);
    requireEndReasonWhileEvaluating(state, lastEndReason);
    requireCounters(lastEndTick, planSequence);
    plan.ifPresent(present -> requireSequenceMatches(present, planSequence));
  }
  …
}
```

Mensajes exactos (`IllegalArgumentException`):

| Regla | Mensaje |
| --- | --- |
| Estado `PLANNING` | `"LifecycleCapture.state cannot be PLANNING"` |
| Plan que no corresponde al estado | `"LifecycleCapture.plan must be present only while EXECUTING or EVALUATING, got " + state + " with plan " + (plan.isPresent() ? "present" : "absent")` |
| Inicio de reagrupamiento que no corresponde | `"LifecycleCapture.regroupStartTick must be present only while REGROUPING, got " + state` |
| `EVALUATING` sin motivo | `"LifecycleCapture.lastEndReason must be present while EVALUATING"` |
| Tick o número negativo | `"LifecycleCapture.lastEndTick must be zero or positive, got " + lastEndTick` y `"LifecycleCapture.planSequence must be zero or positive, got " + planSequence` |
| Número de plan distinto | `"LifecycleCapture.planSequence must match the plan, got " + planSequence + " for plan " + plan.id().sequence()` |

### `PlanLifecycle`: `capture` y `restore`

Agregar después de `restorePlanSequence`:

```java
  public LifecycleCapture capture() {
    return new LifecycleCapture(
        state,
        plan(),
        committedTarget(),
        Optional.ofNullable(lastEndReason),
        lastEndTick,
        regroupStartTick(),
        planSequence);
  }

  public void restore(LifecycleCapture capture) {
    requireState(GroupState.OBSERVING, "restore its lifecycle");
    requireNotBelowSequence(capture.planSequence());
    capture.plan().ifPresent(this::requireRestorablePlan);
    state = capture.state();
    plan = capture.plan().orElse(null);
    committedTarget = capture.committedTarget().orElse(null);
    lastEndReason = capture.lastEndReason().orElse(null);
    lastEndTick = capture.lastEndTick();
    regroupStartTick = capture.regroupStartTick().orElse(NO_REGROUP);
    planSequence = capture.planSequence();
  }
```

- `requireNotBelowSequence(long sequence)`: reutilizala desde `restorePlanSequence` (mismo mensaje: `"Group " + groupId.shortId() + " cannot restore plan sequence " + sequence + " below " + planSequence`). Refactorizá `restorePlanSequence` para que la use; su comportamiento y su prueba no cambian.
- `requireRestorablePlan(Plan restored)`:
  - si `restored.id().group()` no es `groupId`: `IllegalArgumentException("Group " + groupId.shortId() + " cannot restore a plan of group " + restored.id().group().shortId())`;
  - después, `roster.requireMembers(restored.roles().keySet())` (mensaje existente).

### `GroupRoster.spiderTargets`

```java
  public Map<MobId, PlayerId> spiderTargets() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(spiderTargets));
  }
```

### `domain/threat/ThreatRecord.java` y `ThreatCapture.java`

```java
public record ThreatRecord(PlayerId player, long tick, double damage) {
  public ThreatRecord {
    Objects.requireNonNull(player, "ThreatRecord.player");
    if (tick < 0) {
      throw new IllegalArgumentException("ThreatRecord.tick must be zero or positive, got " + tick);
    }
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException("ThreatRecord.damage must be a positive number, got " + damage);
    }
  }
}

public record ThreatCapture(List<ThreatRecord> records, long lastTick) {
  public ThreatCapture {
    records = List.copyOf(records);
    for (ThreatRecord record : records) {
      if (record.tick() > lastTick) {
        throw new IllegalArgumentException(
            "ThreatCapture.lastTick must not be before a record, got " + lastTick + " < " + record.tick());
      }
    }
  }
}
```

### `ThreatLedger`: `capture` y `restore`

```java
  public ThreatCapture capture() {
    List<ThreatRecord> records = new ArrayList<>();
    entries.forEach(
        (player, queue) ->
            queue.forEach(entry -> records.add(new ThreatRecord(player, entry.tick(), entry.damage()))));
    return new ThreatCapture(records, lastTick);
  }

  public void restore(ThreatCapture capture) {
    if (!entries.isEmpty() || lastTick != 0) {
      throw new IllegalStateException("ThreatLedger can only be restored while empty");
    }
    for (ThreatRecord record : capture.records()) {
      entries
          .computeIfAbsent(record.player(), ignored -> new ArrayDeque<>())
          .addLast(new ThreatEntry(record.tick(), record.damage()));
    }
    lastTick = capture.lastTick();
  }
```

La copia incluye las entradas vencidas que todavía no se podaron: es una copia fiel, no una vista de la amenaza activa.

### `BrainParts.standard`

```java
  /** The one way the plugin, the tests and incident replays assemble a brain. */
  public static BrainParts standard(
      Supplier<MobAiSettings> settings, RandomSource random, RegroupWindow regroupWindow) {
    return new BrainParts(
        new TargetSelector(
            () -> settings.get().target(),
            new KillTimeEstimator(() -> settings.get().target(), () -> settings.get().attack())),
        new SpiderTargetRule(() -> settings.get().target()),
        new StrategyCatalog(new CombatGeometry()),
        new SelectionPolicyFactory(() -> settings.get().selection(), random),
        new AttackSuggester(),
        new PlanEndDetector(() -> settings.get().plan()),
        new RetreatRule(() -> settings.get().plan(), () -> settings.get().retreat()),
        new RegroupRule(() -> settings.get().retreat(), regroupWindow),
        regroupWindow);
  }
```

Es la misma composición que hoy arma `BrainFixture.parts`. En `BrainFixture`, `parts(random)` pasa a ser `BrainParts.standard(() -> settings, random, regroupWindow)`. **Todas las pruebas existentes del cerebro tienen que seguir pasando sin cambios**: eso prueba que el armado es el mismo.

### `application/GroupCapture.java`

```java
public record GroupCapture(
    StoredGroup stored,
    LifecycleCapture lifecycle,
    ThreatCapture threat,
    Map<MobId, PlayerId> spiderTargets) {
  public GroupCapture {
    Objects.requireNonNull(stored, "GroupCapture.stored");
    Objects.requireNonNull(lifecycle, "GroupCapture.lifecycle");
    Objects.requireNonNull(threat, "GroupCapture.threat");
    spiderTargets = Collections.unmodifiableMap(new LinkedHashMap<>(spiderTargets));
  }
}
```

### `application/GroupCaptureMapper.java`

```java
public final class GroupCaptureMapper {
  private final StoredMemoriesMapper storedMapper = new StoredMemoriesMapper();

  public GroupCapture capture(Group group) {
    return new GroupCapture(
        storedMapper.toStored(group),
        group.lifecycle().capture(),
        group.threat().capture(),
        group.roster().spiderTargets());
  }

  public Group restore(GroupCapture capture, SettingsHolder settings) {
    Group group = storedMapper.toGroup(capture.stored(), settings);
    group.lifecycle().restore(capture.lifecycle());
    group.threat().restore(capture.threat());
    capture.spiderTargets().forEach(group.roster()::assignSpiderTarget);
    return group;
  }
}
```

### Azar grabado (`application`)

```java
public enum DrawKind {
  UNIT,
  GAUSSIAN,
  INDEX
}

public record RecordedDraw(DrawKind kind, double value, int bound) {
  public RecordedDraw {
    Objects.requireNonNull(kind, "RecordedDraw.kind");
    if (!Double.isFinite(value)) {
      throw new IllegalArgumentException("RecordedDraw.value must be finite, got " + value);
    }
    if (kind == DrawKind.INDEX) {
      requireIndexWithinBound(value, bound);
    } else if (bound != 0) {
      throw new IllegalArgumentException("RecordedDraw.bound must be 0 for " + kind + ", got " + bound);
    }
  }
  …
}
```

`requireIndexWithinBound`: `bound >= 1`, `value` entero (`value == Math.rint(value)`) y `0 <= value < bound`; si no, `IllegalArgumentException("RecordedDraw.value must be an index below " + bound + ", got " + value)` (con `bound < 1`: `"RecordedDraw.bound must be positive for INDEX, got " + bound`).

```java
/** Wraps the real random source and records every number the brain draws. */
public final class RecordingRandomSource implements RandomSource {
  private final RandomSource delegate;
  private final List<RecordedDraw> draws = new ArrayList<>();

  public RecordingRandomSource(RandomSource delegate) { … requireNonNull … }

  @Override public double nextUnit() { double value = delegate.nextUnit(); draws.add(new RecordedDraw(DrawKind.UNIT, value, 0)); return value; }
  @Override public double nextGaussian() { … GAUSSIAN … }
  @Override public int nextIndex(int bound) { int index = delegate.nextIndex(bound); draws.add(new RecordedDraw(DrawKind.INDEX, index, bound)); return index; }

  public List<RecordedDraw> draws() { return List.copyOf(draws); }

  public void clear() { draws.clear(); }
}

/** Hands back recorded numbers in order and fails as soon as a replay asks for something else. */
public final class ReplayRandomSource implements RandomSource {
  private final List<RecordedDraw> draws;
  private int next;

  public ReplayRandomSource(List<RecordedDraw> draws) { this.draws = List.copyOf(draws); }

  @Override public double nextUnit() { return take(DrawKind.UNIT, 0).value(); }
  @Override public double nextGaussian() { return take(DrawKind.GAUSSIAN, 0).value(); }
  @Override public int nextIndex(int bound) { return (int) take(DrawKind.INDEX, bound).value(); }

  public int remaining() { return draws.size() - next; }

  private RecordedDraw take(DrawKind kind, int bound) { … }
}
```

`take`:
- sin números: `IllegalStateException("Replay ran out of draws after " + draws.size())`;
- tipo o límite distintos: `IllegalStateException("Replay diverged at draw " + (next + 1) + ": recorded " + recorded.kind() + " with bound " + recorded.bound() + ", asked for " + kind + " with bound " + bound)`;
- si coincide, avanza y devuelve.

## Pruebas obligatorias

Convenciones: las de los WPs 12 a 15 (`mob(n)`, `player`, `otherPlayer`, `groupId(n)`, `newGroup(n)`). Un grupo «ejecutando» se arma como en el WP-13 (`PlanStart` con `DIRECT_ASSAULT` contra `player`, `mob(1)` en `PRESS`, `targetMaxHealth` 20, tick 100). Un grupo «reagrupando», como en el WP-13 (`closePlan(GROUP_RETREATED, 200, 0.5)` y `finishEvaluation()`).

### `LifecycleCaptureTest` (9, dominio)

| Prueba | Verifica |
| --- | --- |
| `planningIsRejected` | un `LifecycleCapture` en `PLANNING`: mensaje exacto |
| `planOutsideExecutionIsRejected` | `OBSERVING` con plan: `"LifecycleCapture.plan must be present only while EXECUTING or EVALUATING, got OBSERVING with plan present"` |
| `regroupStartOutsideRegroupingIsRejected` | `OBSERVING` con `OptionalLong.of(5)`: mensaje exacto |
| `planSequenceMustMatchThePlan` | plan con secuencia 1 y `planSequence` 2: mensaje exacto |
| `executingGroupRoundTrips` | grupo ejecutando al que se le sumó daño al plan (`recordPlanDamage(player, 4.0)`) y `markTargetSeen(150)`; grupo nuevo con el mismo id y los mismos miembros; `restore(capture())`: la copia del nuevo es igual a la del original |
| `regroupingGroupRoundTrips` | lo mismo con un grupo reagrupando (tiene objetivo comprometido, motivo `GROUP_RETREATED` y tick de inicio 200) |
| `restoreRejectsAPlanOfAnotherGroup` | la copia de un grupo ejecutando con id 1 restaurada en un grupo con id 2 (mismos miembros): `"Group " + groupId(2).shortId() + " cannot restore a plan of group " + groupId(1).shortId()` |
| `restoreRejectsAPlanWithANonMember` | grupo nuevo sin `mob(1)`: el mensaje existente de `requireMembers` |
| `restoreRequiresObserving` | restaurar en un grupo ejecutando: `IllegalStateException` con `"Group " + shortId + " cannot restore its lifecycle while EXECUTING"` |

### `ThreatCaptureTest` (4, dominio)

`ThreatLedger` con `() -> TestSettings.defaults().target()`.

| Prueba | Verifica |
| --- | --- |
| `captureKeepsEveryRecordInOrder` | `recordDamage(player, 2.0, 10)`, `(otherPlayer, 3.0, 12)`, `(player, 1.5, 15)`: `records()` = `[player/10/2.0, player/15/1.5, otherPlayer/12/3.0]` (agrupado por jugador en orden de primera aparición) y `lastTick()` 15 |
| `restoredLedgerHasTheSameThreat` | restaurado en un ledger nuevo: `threatOf(player, 20)` y `threatOf(otherPlayer, 20)` iguales a los del original, y `capture()` igual |
| `restoredLedgerKeepsTheLastTick` | después de restaurar, `recordDamage(player, 1.0, 14)` lanza el mensaje existente `"ThreatLedger.tick must not go back, got 14 after 15"` |
| `restoreRequiresAnEmptyLedger` | restaurar en un ledger con un daño: `IllegalStateException("ThreatLedger can only be restored while empty")` |

### `RecordedRandomnessTest` (6, aplicación)

| Prueba | Verifica |
| --- | --- |
| `recordingKeepsEveryDrawInOrder` | `RecordingRandomSource` sobre `new ScriptedRandomSource().withUnits(0.25).withGaussians(-1.5).withIndexes(2)`: pedidos `nextUnit`, `nextGaussian`, `nextIndex(3)` devuelven 0.25, -1.5 y 2, y `draws()` = `[UNIT 0.25 0, GAUSSIAN -1.5 0, INDEX 2.0 3]` |
| `clearForgetsTheDraws` | después de `clear()`, `draws()` vacío |
| `replayHandsBackTheSameNumbers` | `ReplayRandomSource` con esas 3: mismos valores en el mismo orden y `remaining()` 0 |
| `replayDetectsADifferentBound` | grabado `INDEX 2 3`; `nextIndex(5)`: `"Replay diverged at draw 1: recorded INDEX with bound 3, asked for INDEX with bound 5"` |
| `replayDetectsADifferentKind` | grabado `UNIT`; `nextGaussian()`: `"Replay diverged at draw 1: recorded UNIT with bound 0, asked for GAUSSIAN with bound 0"` |
| `replayDetectsRunningOut` | sin números: `nextUnit()` lanza `"Replay ran out of draws after 0"` |

(Si `ScriptedRandomSource` no tiene exactamente esos métodos, usá los que tenga para obtener esos valores y avisalo.)

### `GroupCaptureMapperTest` (2, aplicación)

| Prueba | Verifica |
| --- | --- |
| `groupMidPlanRoundTrips` | `BrainFixture.seeded(7)`, `catalogGroup()`; `decide` en `START_TICK` y en `START_TICK + 10` con `alice`; `threat().recordDamage(ALICE, 3.0, START_TICK + 12)`. `capture(restore(capture(g), holder))` = `capture(g)`, con `holder = new SettingsHolder(TestSettings.defaults())`. Además, la copia tiene estado `EXECUTING` y al menos un objetivo de araña (si no, la prueba no prueba nada: verificalo con un `assertThat`) |
| `spiderTargetsAreACopy` | `group.roster().spiderTargets()` no cambia si después se asigna otro objetivo de araña |

### `DecisionRepeatTest` (2, aplicación) — la prueba central

Armado común:
- `MobAiSettings settings = TestSettings.defaults()`, `RegroupWindow window = new RegroupWindow(settings::retreat)`.
- `RecordingRandomSource recorder = new RecordingRandomSource(new SeededRandomSource(7))` y `Brain brain = new Brain(() -> settings, BrainParts.standard(() -> settings, recorder, window))`.
- Un `Group` con id `BrainFixture.GROUP_ID`, política `THOMPSON_SAMPLING`, memoria y amenaza con las secciones de `settings`, y los mobs de `BrainFixture.catalogMobs()` como miembros.
- Dos decisiones previas (`START_TICK` y `START_TICK + 10`, con `alice`) y `threat().recordDamage(ALICE, 3.0, START_TICK + 12)`.
- Repetir: copiar `GroupCapture before` y `long windowTicks = window.currentTicks()`; `recorder.clear()`; decidir con la foto `S` → `original`; `draws = recorder.draws()`; `after = capture(group)`. Después, grupo nuevo `restore(before, new SettingsHolder(settings))`, `RegroupWindow` nuevo con `restore(windowTicks)`, `ReplayRandomSource(draws)` y un `Brain` nuevo con `BrainParts.standard`; decidir con `S` → `repeated`.

| Prueba | Verifica |
| --- | --- |
| `repeatedDecisionMidPlanIsIdentical` | `S` = foto en `START_TICK + 20`: `repeated` igual a `original` (`isEqualTo`), `capture(restaurado)` igual a `after`, `replay.remaining()` 0 y `draws` no vacío |
| `repeatedDecisionThatClosesThePlanIsIdentical` | `S` = foto en `START_TICK + 700` (vence el plan): resultado igual, copia después igual y `remaining()` 0; además `original.closedPlan()` presente, los eventos drenados de los dos grupos iguales y `draws` **vacío** (cerrar un plan no consume azar) |

Total: **23 pruebas** nuevas. Todas las existentes siguen pasando.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `PlanLifecycle.restore`, no restaurar `committedTarget` | la copia restaurada tiene objetivo comprometido vacío | `regroupingGroupRoundTrips` |
| 2 | En `ThreatLedger.restore`, no restaurar `lastTick` | `recordDamage(..., 14)` no lanza | `restoredLedgerKeepsTheLastTick` |
| 3 | En `ReplayRandomSource.take`, comparar solo el tipo | el pedido con límite 5 devuelve 2 sin avisar | `replayDetectsADifferentBound` |
| 4 | En `GroupCaptureMapper.restore`, no restaurar la amenaza | la copia después de decidir difiere en `threat` | `repeatedDecisionMidPlanIsIdentical` |

## Procedimiento

1. Rama `wp-28a-estado-completo-y-azar-grabado` desde `origin/main` actualizado.
2. `LifecycleCapture`, `PlanLifecycle.capture`/`restore`, `GroupRoster.spiderTargets`, `ThreatRecord`, `ThreatCapture`, `ThreatLedger.capture`/`restore`, con `LifecycleCaptureTest` y `ThreatCaptureTest`. Commit: `feat: capture and restore the full lifecycle and threat of a group`.
3. `BrainParts.standard` y `BrainFixture` usándolo; toda la suite en verde. Commit: `refactor: assemble the brain in one place`.
4. `DrawKind`, `RecordedDraw`, `RecordingRandomSource`, `ReplayRandomSource` con `RecordedRandomnessTest`. Commit: `feat: record and replay random draws`.
5. `GroupCapture`, `GroupCaptureMapper` con `GroupCaptureMapperTest` y `DecisionRepeatTest`. Commit: `feat: repeat a brain decision from a group capture`.
6. Pruebas que muerden (de a una, en secuencia; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-28A: full group capture and recorded randomness`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `DecisionRepeatTest` encuentra una diferencia, **no ajustes la prueba**: es exactamente lo que el WP busca detectar (estado que no se copia o una fuente de azar que no pasa por el puerto). Frená y reportá qué campo difiere.
3. Si la semilla 7 no deja el grupo en `EXECUTING` con un objetivo de araña en `GroupCaptureMapperTest`, probá las semillas 1 a 20 en orden y usá la primera que sí; avisalo.

## Fuera de alcance

- `IncidentReport`, su JSON y `TraceReplay` (WP-28B).
- Tomar la copia antes de cada decisión en el plugin y escribir incidentes (WP-29).
- Copiar los eventos pendientes: el WP-29 los publica antes de copiar, así la copia arranca sin eventos.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 23 pruebas nuevas con sus nombres exactos, en verde, y todas las existentes también.
- [ ] Las 4 roturas mordieron.
- [ ] `PlanLifecycle` 16, `ThreatLedger` 7 y `GroupRoster` 9 métodos públicos; ninguna clase pasa de 20.
- [ ] Sin ciclos de paquetes nuevos (`GroupCapture` en `application`).
- [ ] Build, cobertura y CI en verde.
