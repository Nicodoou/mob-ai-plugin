# WP-15 — Guardar, cargar, resetear y consultar

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia |
| Depende de | WP-13 y WP-14 (mergeados) |
| Modelo | Sonnet |
| Rama | `wp-15-guardar-cargar-consultar` |

## Objetivo

1. **Convertir** grupos vivos en datos guardados y al revés (`StoredMemoriesMapper`), con el número de plan restaurado (método nuevo del dominio).
2. **Guardar** (`SaveMemories`): copiar en el hilo principal y escribir en cualquier hilo.
3. **Cargar** (`LoadMemories`): reconstruir los grupos al arrancar.
4. **Candado de guardado** (`GuardedMemoryRepository`): no se puede guardar si la carga no terminó bien. Si no, un guardado después de una carga fallida borra del disco los grupos que no se pudieron leer (D16).
5. **Resetear** memorias (`ResetMemories`) y **consultar** grupos y la memoria de un jugador (`DescribeGroup`, `DescribePlayerMemory`), para los comandos de RF-10.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/application/` (todo)
- `src/main/java/io/github/nicodoou/mobai/domain/port/MemoryRepository.java`, `StoredState.java`, `StoredGroup.java`, `StoredAttackRecord.java`, `StoredStrategyRecord.java`, `StoredMemories.java`, `MemoryLoad.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `GroupRoster.java`, `PlanLifecycle.java`, `Plan.java`, `Member.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java`, `AttackRecord.java`, `SuccessEstimate.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/InMemoryMemoryRepository.java`, `TestSettings.java`

## Reglas de negocio

1. **Qué guarda un grupo** (D14): id, política, `planSequence` actual (como `lastPlanSequence`), miembros en orden de ingreso y todos sus registros de memoria. No guarda plan, estado, amenaza ni objetivo.
2. **Orden determinista** de los registros guardados: por jugador (orden natural de su `UUID`) y, dentro de cada jugador, ataques por orden del enum `Attack` y estrategias por `StrategyId.value()` alfabético. Así el mismo estado produce siempre el mismo archivo.
3. **Restaurar un grupo:** política y registros guardados, memoria y amenaza que leen su sección de `SettingsHolder` (como `RecruitMob`), miembros con `restoreMember` en orden, y número de plan con `restorePlanSequence`. El grupo arranca en `OBSERVING` con la amenaza vacía.
4. **El estado global** (tick del reloj y ventana de reagrupamiento) **no** lo restauran estos casos de uso: `LoadMemories` lo devuelve y `SaveMemories` lo recibe. Lo aplica el arranque (WP-20), que es dueño del reloj y de `RegroupWindow`.
5. **Cargar** solo con `ActiveGroups` vacío: es lo primero que pasa al habilitar el plugin. Si ya hay grupos, `IllegalStateException`. Un grupo guardado que no se puede restaurar (por ejemplo, un mob que ya está en otro grupo cargado) se saltea, se informa su id y la carga sigue.
6. **Candado:** `GuardedMemoryRepository` envuelve al repositorio real. Arranca cerrado; un `load()` que termina sin excepción lo abre; `save()` con el candado cerrado lanza `IllegalStateException`. Una carga que falla lo deja cerrado.
7. **Guardar en dos pasos:** `capture(StoredState)` copia los grupos activos a datos guardados (rápido, hilo principal); `write(StoredMemories)` llama al repositorio (cualquier hilo).
8. **Resetear:** un jugador (`clearPlayer` en la memoria de cada grupo) o todo (`clear` en cada grupo). Devuelve cuántos grupos cambiaron. La amenaza no se toca: no es memoria aprendida.
9. **Consultas:** `DescribeGroup` lista los grupos activos o busca uno por los 8 caracteres de su id (D18). `DescribePlayerMemory` devuelve, por cada grupo que tiene registros de ese jugador, la estimación de cada ataque y de cada estrategia **registrados**, con el olvido aplicado al tick dado.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` (agregar `restorePlanSequence`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/StoredMemoriesMapper.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/GuardedMemoryRepository.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/SaveMemories.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/LoadReport.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/LoadMemories.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/ResetMemories.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/GroupStatusView.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/DescribeGroup.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/PlayerMemoryView.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/DescribePlayerMemory.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/PlanSequenceRestoreTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/StoredMemoriesMapperTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/GuardedMemoryRepositoryTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/SaveAndLoadMemoriesTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/ResetMemoriesTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/DescribeGroupTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/DescribePlayerMemoryTest.java` |

**Método público nuevo en una clase existente:** `PlanLifecycle.restorePlanSequence`. Va ahí porque `planSequence` es estado privado del ciclo de planes y no hay otra forma de que los `PlanId` sigan después de un reinicio. `PlanLifecycle` pasa de 13 a 14 métodos públicos.

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `PlanLifecycle.restorePlanSequence` (dominio)

Agregar después de `planSequence()`:

```java
  public void restorePlanSequence(long lastSequence) {
    requireState(GroupState.OBSERVING, "restore the plan sequence");
    if (lastSequence < planSequence) {
      throw new IllegalArgumentException(
          "Group "
              + groupId.shortId()
              + " cannot restore plan sequence "
              + lastSequence
              + " below "
              + planSequence);
    }
    planSequence = lastSequence;
  }
```

### `StoredMemoriesMapper.java`

```java
public final class StoredMemoriesMapper {
  private static final Comparator<PlayerId> BY_PLAYER = Comparator.comparing(PlayerId::value);

  public StoredGroup toStored(Group group) {
    return new StoredGroup(
        group.id(),
        group.policy(),
        group.lifecycle().planSequence(),
        group.roster().members(),
        storedAttackRecords(group.memory()),
        storedStrategyRecords(group.memory()));
  }

  public Group toGroup(StoredGroup stored, SettingsHolder settings) {
    Group group =
        new Group(stored.id(), stored.policy(), restoredKnowledge(stored, settings));
    stored.members().forEach(group.roster()::restoreMember);
    group.lifecycle().restorePlanSequence(stored.lastPlanSequence());
    return group;
  }

  private static List<StoredAttackRecord> storedAttackRecords(GroupMemory memory) { … }

  private static List<StoredStrategyRecord> storedStrategyRecords(GroupMemory memory) { … }

  private static GroupKnowledge restoredKnowledge(StoredGroup stored, SettingsHolder settings) { … }

  private static Map<PlayerId, Map<Attack, AttackRecord>> attackMaps(StoredGroup stored) { … }

  private static Map<PlayerId, Map<StrategyId, AttackRecord>> strategyMaps(StoredGroup stored) { … }
}
```

- `storedAttackRecords`: recorre `memory.attackRecords()` con los jugadores ordenados por `BY_PLAYER` y, por jugador, los ataques en orden del enum (`Comparator.naturalOrder()` sobre `Attack`).
- `storedStrategyRecords`: igual, con las estrategias ordenadas por `StrategyId::value`.
- `restoredKnowledge`: `new GroupKnowledge(GroupMemory.restore(settings.section(MobAiSettings::memory), attackMaps(stored), strategyMaps(stored)), new ThreatLedger(settings.section(MobAiSettings::target)))`.
- `attackMaps` y `strategyMaps`: agrupan las listas por jugador (`LinkedHashMap` por jugador, conservando el orden de la lista).

### `GuardedMemoryRepository.java`

```java
public final class GuardedMemoryRepository implements MemoryRepository {
  private final MemoryRepository delegate;
  private boolean loaded;

  public GuardedMemoryRepository(MemoryRepository delegate) {
    this.delegate = Objects.requireNonNull(delegate, "GuardedMemoryRepository.delegate");
  }

  @Override
  public MemoryLoad load() {
    MemoryLoad load = delegate.load();
    loaded = true;
    return load;
  }

  // A save after a failed load would delete every group file that could not be read.
  @Override
  public void save(StoredMemories memories) {
    if (!loaded) {
      throw new IllegalStateException(
          "Memories were never loaded successfully; saving now would delete stored groups");
    }
    delegate.save(memories);
  }
}
```

### `SaveMemories.java`

```java
public final class SaveMemories {
  private final ActiveGroups activeGroups;
  private final MemoryRepository repository;
  private final StoredMemoriesMapper mapper = new StoredMemoriesMapper();

  public SaveMemories(ActiveGroups activeGroups, MemoryRepository repository) { … requireNonNull … }

  public StoredMemories capture(StoredState state) {
    return new StoredMemories(
        state, activeGroups.groups().stream().map(mapper::toStored).toList());
  }

  public void write(StoredMemories memories) {
    repository.save(memories);
  }
}
```

### `LoadReport.java`

```java
public record LoadReport(
    Optional<StoredState> state,
    List<GroupId> loadedGroups,
    List<GroupId> skippedGroups,
    List<String> quarantinedFiles) {
  public LoadReport {
    Objects.requireNonNull(state, "LoadReport.state");
    loadedGroups = List.copyOf(loadedGroups);
    skippedGroups = List.copyOf(skippedGroups);
    quarantinedFiles = List.copyOf(quarantinedFiles);
  }
}
```

### `LoadMemories.java`

```java
public final class LoadMemories {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final MemoryRepository repository;
  private final StoredMemoriesMapper mapper = new StoredMemoriesMapper();

  public LoadMemories(ActiveGroups activeGroups, SettingsHolder settings, MemoryRepository repository) { … }

  public LoadReport execute() {
    requireNoActiveGroups();
    MemoryLoad load = repository.load();
    List<GroupId> loaded = new ArrayList<>();
    List<GroupId> skipped = new ArrayList<>();
    for (StoredGroup stored : load.groups()) {
      (restore(stored) ? loaded : skipped).add(stored.id());
    }
    return new LoadReport(load.state(), loaded, skipped, load.quarantinedFiles());
  }

  private void requireNoActiveGroups() {
    if (activeGroups.size() > 0) {
      throw new IllegalStateException(
          "LoadMemories must run before any group exists, found " + activeGroups.size());
    }
  }

  // A group that cannot be rebuilt is skipped so the rest of the memories still load.
  private boolean restore(StoredGroup stored) {
    try {
      activeGroups.add(mapper.toGroup(stored, settings));
      return true;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }
}
```

### `ResetMemories.java`

```java
public final class ResetMemories {
  private final ActiveGroups activeGroups;

  public ResetMemories(ActiveGroups activeGroups) { … }

  public int resetPlayer(PlayerId player) {
    List<Group> touched =
        activeGroups.groups().stream().filter(group -> remembers(group, player)).toList();
    touched.forEach(group -> group.memory().clearPlayer(player));
    return touched.size();
  }

  public int resetAll() {
    List<Group> groups = activeGroups.groups();
    groups.forEach(group -> group.memory().clear());
    return groups.size();
  }

  private static boolean remembers(Group group, PlayerId player) {
    return group.memory().attackRecords().containsKey(player)
        || group.memory().strategyRecords().containsKey(player);
  }
}
```

### `GroupStatusView.java`

```java
public record GroupStatusView(
    GroupId id,
    GroupState state,
    SelectionPolicyType policy,
    List<Member> members,
    Optional<StrategyId> strategy,
    Optional<PlayerId> target,
    long planSequence) {
  public GroupStatusView {
    Objects.requireNonNull(id, "GroupStatusView.id");
    Objects.requireNonNull(state, "GroupStatusView.state");
    Objects.requireNonNull(policy, "GroupStatusView.policy");
    members = List.copyOf(members);
    Objects.requireNonNull(strategy, "GroupStatusView.strategy");
    Objects.requireNonNull(target, "GroupStatusView.target");
  }
}
```

### `DescribeGroup.java`

```java
public final class DescribeGroup {
  private final ActiveGroups activeGroups;

  public DescribeGroup(ActiveGroups activeGroups) { … }

  public List<GroupStatusView> all() {
    return activeGroups.groups().stream().map(DescribeGroup::view).toList();
  }

  public Optional<GroupStatusView> find(String shortId) {
    return activeGroups.groups().stream()
        .filter(group -> group.id().shortId().equals(shortId))
        .findFirst()
        .map(DescribeGroup::view);
  }

  // While regrouping there is no plan, but the group still keeps away from its last target.
  private static GroupStatusView view(Group group) {
    PlanLifecycle lifecycle = group.lifecycle();
    return new GroupStatusView(
        group.id(),
        lifecycle.state(),
        group.policy(),
        group.roster().members(),
        lifecycle.plan().map(Plan::strategy),
        lifecycle.plan().map(Plan::target).or(lifecycle::committedTarget),
        lifecycle.planSequence());
  }
}
```

### `PlayerMemoryView.java`

```java
public record PlayerMemoryView(
    GroupId group,
    PlayerId player,
    Map<Attack, SuccessEstimate> attacks,
    Map<StrategyId, SuccessEstimate> strategies) {
  public PlayerMemoryView {
    Objects.requireNonNull(group, "PlayerMemoryView.group");
    Objects.requireNonNull(player, "PlayerMemoryView.player");
    attacks = Collections.unmodifiableMap(new LinkedHashMap<>(attacks));
    strategies = Collections.unmodifiableMap(new LinkedHashMap<>(strategies));
  }
}
```

### `DescribePlayerMemory.java`

```java
public final class DescribePlayerMemory {
  private final ActiveGroups activeGroups;

  public DescribePlayerMemory(ActiveGroups activeGroups) { … }

  public List<PlayerMemoryView> execute(PlayerId player, long tick) {
    return activeGroups.groups().stream()
        .filter(group -> remembers(group, player))
        .map(group -> view(group, player, tick))
        .toList();
  }
  …
}
```

- `remembers`: igual que en `ResetMemories` (cada clase con su función privada; no se comparte).
- `view`: los ataques registrados del jugador en orden del enum, cada uno con `memory.attackEstimate(player, attack, tick)`; las estrategias registradas ordenadas por `value()`, con `memory.strategyEstimate(player, strategy, tick)`. Mapas `LinkedHashMap` en ese orden.

## Pruebas obligatorias

Convenciones de los WPs 12 a 14: `mob(n)`, `player` (`new PlayerId(new UUID(2, 1))`), `otherPlayer` (`new UUID(2, 2)`), `groupId(n)`, `newGroup(n)`. `settings = new SettingsHolder(TestSettings.defaults())`. Un grupo «con memoria»: `mob(1)` zombie y `mob(2)` esqueleto unidos con `activeGroups.join`, y en su memoria `recordAttack(new AttackObservation(player, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 100))`, `recordAttack(new AttackObservation(player, Attack.SKELETON_DIRECT_SHOT, 0.0, 100))` y `recordStrategy(new StrategyObservation(player, new StrategyId("FLANK"), 0.4, 1.0, 100))`.

### `PlanSequenceRestoreTest` (3, dominio)

| Prueba | Verifica |
| --- | --- |
| `restoredSequenceContinuesWithTheNextPlan` | grupo nuevo con `mob(1)`; `restorePlanSequence(3)`; `beginPlanning()` y `startPlan(PlanStart con mob(1) PRESS)` devuelve un plan con `id().sequence()` = 4 |
| `restoreCannotGoBack` | grupo que cerró un plan (`startPlan`, `closePlan(TIMED_OUT, 200, 0.5)`, `finishEvaluation()`: secuencia 1, estado `OBSERVING`); `restorePlanSequence(0)` lanza con `"Group " + shortId + " cannot restore plan sequence 0 below 1"` |
| `restoreRequiresObserving` | grupo ejecutando: `IllegalStateException` con `"Group " + shortId + " cannot restore the plan sequence while EXECUTING"` |

### `StoredMemoriesMapperTest` (4)

| Prueba | Verifica |
| --- | --- |
| `toStoredKeepsMembersPolicyAndPlanSequence` | grupo con memoria al que se le hizo `restorePlanSequence(5)`: `lastPlanSequence()` 5, política `THOMPSON_SAMPLING`, miembros `[Member(mob(1), ZOMBIE, 1), Member(mob(2), SKELETON, 2)]` |
| `toStoredOrdersRecordsByPlayerThenAttack` | registros de `otherPlayer` y de `player` (cargados en ese orden), con `SKELETON_DIRECT_SHOT` antes que `ZOMBIE_FRONT_STRIKE` para `player`: la lista sale `player/ZOMBIE_FRONT_STRIKE`, `player/SKELETON_DIRECT_SHOT`, `otherPlayer/...` (`new UUID(2, 1)` < `new UUID(2, 2)`) |
| `storedGroupRoundTripsThroughARestoredGroup` | `toStored(toGroup(toStored(g)))` es igual a `toStored(g)` |
| `restoredGroupStartsObservingWithEmptyThreat` | grupo de origen con memoria y `restorePlanSequence(5)`; el grupo restaurado de su `toStored`: estado `OBSERVING`, `threat().trackedPlayers()` vacío, y el próximo plan (`beginPlanning` + `startPlan` con `mob(1)` `PRESS`) tiene secuencia 6 |

### `GuardedMemoryRepositoryTest` (3)

| Prueba | Verifica |
| --- | --- |
| `saveBeforeLoadIsRefused` | `save` sin `load`: `IllegalStateException` con el mensaje exacto, y el repositorio de adentro (`InMemoryMemoryRepository`) tiene `saveCount()` 0 |
| `saveAfterASuccessfulLoadIsAllowed` | `load()` y `save`: `saveCount()` 1 |
| `saveAfterAFailedLoadIsRefused` | un `MemoryRepository` de prueba (lambda o clase anónima) cuyo `load()` lanza `IllegalStateException("newer")`: `load()` propaga, y después `save` lanza con el mensaje del candado |

### `SaveAndLoadMemoriesTest` (6)

Usan `InMemoryMemoryRepository`, envuelto en `GuardedMemoryRepository` cuando la prueba lo dice.

| Prueba | Verifica |
| --- | --- |
| `captureCopiesEveryActiveGroupAndTheState` | dos grupos activos; `capture(new StoredState(500, 650))`: `state()` igual al dado y 2 grupos, en el orden de `ActiveGroups` |
| `writeHandsTheMemoriesToTheRepository` | `write(capture(...))`: el repositorio carga lo mismo que se capturó (`groups()` iguales) |
| `loadRebuildsTheSavedGroups` | se guardan 2 grupos con memoria desde unos `ActiveGroups`; con **otros** `ActiveGroups` vacíos, `LoadMemories.execute()`: `loadedGroups` = los 2 ids, `groupOf(mob(1))` es el grupo 1 y su memoria tiene `attackRecords()` igual a la del original |
| `loadReturnsTheStoredStateWithoutApplyingIt` | se guarda con `StoredState(500, 650)`: `report.state()` = `Optional.of(new StoredState(500, 650))` |
| `loadSkipsAGroupThatCannotBeRestored` | un `StoredMemories` armado a mano con el grupo 1 (`mob(1)`) y el grupo 2 que también tiene `mob(1)`: `loadedGroups` = `[groupId(1)]`, `skippedGroups` = `[groupId(2)]` |
| `loadRefusesToRunWithActiveGroups` | con un grupo activo: `IllegalStateException` con `"LoadMemories must run before any group exists, found 1"` |

### `ResetMemoriesTest` (3)

| Prueba | Verifica |
| --- | --- |
| `resetPlayerClearsOnlyThatPlayer` | grupo con memoria de `player` y de `otherPlayer`: `resetPlayer(player)` devuelve 1; `attackRecords()` solo tiene a `otherPlayer` |
| `resetPlayerCountsOnlyGroupsThatRememberThem` | 2 grupos, solo uno con memoria de `player`: devuelve 1 |
| `resetAllClearsEveryGroup` | 2 grupos con memoria: devuelve 2 y los dos quedan con `attackRecords()` y `strategyRecords()` vacíos |

### `DescribeGroupTest` (4)

| Prueba | Verifica |
| --- | --- |
| `allListsEveryGroupInOrder` | grupos 2 y 1 agregados en ese orden: ids `[groupId(2), groupId(1)]` |
| `findMatchesTheShortId` | `find(groupId(1).shortId())` presente con id `groupId(1)`; `find("zzzzzzzz")` vacío |
| `executingGroupShowsItsPlan` | grupo ejecutando contra `player` con `DIRECT_ASSAULT`: `strategy` = `Optional.of(DIRECT_ASSAULT)`, `target` = `Optional.of(player)`, `planSequence` 1, estado `EXECUTING` |
| `regroupingGroupShowsTheTargetItKeepsAwayFrom` | grupo reagrupando (plan cerrado con `GROUP_RETREATED` y `finishEvaluation`): `strategy` vacío, `target` = `Optional.of(player)`, estado `REGROUPING` |

### `DescribePlayerMemoryTest` (3)

| Prueba | Verifica |
| --- | --- |
| `viewShowsOnlyTheRecordedAttacksAndStrategies` | grupo con memoria; `execute(player, 100)`: una vista, `attacks().keySet()` = `[ZOMBIE_FRONT_STRIKE, SKELETON_DIRECT_SHOT]` en ese orden, `strategies().keySet()` = `[new StrategyId("FLANK")]` |
| `viewEstimatesMatchTheGroupMemory` | `attacks().get(ZOMBIE_FRONT_STRIKE)` es igual a `memory().attackEstimate(player, ZOMBIE_FRONT_STRIKE, 100)` |
| `groupsWithoutRecordsOfThePlayerAreLeftOut` | 2 grupos, solo uno con memoria de `player`: una vista, la de ese grupo |

Total: **26 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `StoredMemoriesMapper.toGroup`, sacar `restorePlanSequence` | el próximo plan tiene secuencia 1 en vez de 6 | `restoredGroupStartsObservingWithEmptyThreat` |
| 2 | En `GuardedMemoryRepository.load`, poner `loaded = true` **antes** de `delegate.load()` | la carga fallida abre el candado y el `save` pasa | `saveAfterAFailedLoadIsRefused` |
| 3 | En `LoadMemories.restore`, sacar el `try`/`catch` | el grupo 2 lanza `IllegalArgumentException` y la carga entera falla | `loadSkipsAGroupThatCannotBeRestored` |
| 4 | En `storedAttackRecords`, no ordenar los jugadores | el orden de los jugadores queda librado al mapa de `attackRecords()` (una copia inmutable, cuyo orden puede variar entre ejecuciones): verificá que la prueba falle al menos una vez en 3 corridas; si no, aplicá la corrección permitida 2 | `toStoredOrdersRecordsByPlayerThenAttack` |

## Procedimiento

1. Rama `wp-15-guardar-cargar-consultar` desde `origin/main` actualizado.
2. `restorePlanSequence` con `PlanSequenceRestoreTest`. Commit: `feat: restore the plan sequence of a group`.
3. `StoredMemoriesMapper`, `GuardedMemoryRepository`, `SaveMemories`, `LoadReport`, `LoadMemories` con sus pruebas. Commit: `feat: save and load group memories`.
4. `ResetMemories`, `GroupStatusView`, `DescribeGroup`, `PlayerMemoryView`, `DescribePlayerMemory` con sus pruebas. Commit: `feat: reset memories and describe groups and player memory`.
5. Pruebas que muerden (sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-15: save, load, reset and describe memories`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si la rotura 4 no falla en 3 corridas por el orden del `HashMap`, cambiá en la prueba los jugadores por otros UUID (por ejemplo `new UUID(2, 9)` para `otherPlayer`) hasta que la rotura falle, sin cambiar lo que verifica, y avisalo.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Aplicar el estado guardado al reloj y a `RegroupWindow`, el hilo de escritura y el guardado periódico (WP-20).
- Comandos y mensajes (WP-21 y WP-26).
- Memoria global y testigos (fase 2).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 26 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] `PlanLifecycle` tiene 14 métodos públicos; ninguna clase pasa de 20.
- [ ] Build, cobertura y CI en verde.
