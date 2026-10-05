# WP-12 — Grupos activos y membresía

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia |
| Depende de | Puerta E3 (pasada) |
| Modelo | Sonnet |
| Rama | `wp-12-grupos-activos` |

## Objetivo

Abrir la capa de aplicación con lo que sabe **qué grupos existen y a qué grupo pertenece cada mob**:

1. `SettingsHolder`: la configuración vigente y los `Supplier` de cada sección (D17).
2. `ActiveGroups`: los grupos vivos y el **único índice mob → grupo** (D15). Toda alta y baja de miembros pasa por acá, así el índice y las listas de miembros nunca se desincronizan.
3. Puerto nuevo `GroupIdSource` (CT-10): de dónde salen los ids de grupo nuevos, para que las pruebas y las reproducciones sean deterministas sin gastar tiradas de `RandomSource`.
4. Casos de uso `RecruitMob`, `RemoveMember` y `DisbandGroup`.

No hay ticks, decisiones ni aprendizaje: eso es el WP-13.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos de código (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `GroupRoster.java`, `GroupKnowledge.java`, `Member.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `GroupSettings.java`
- `src/main/java/io/github/nicodoou/mobai/domain/port/RandomSource.java` (como modelo de puerto)
- `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Reclutar** (`RecruitMob`), en este orden:
   1. Si el mob ya está en un grupo: se rechaza con `ALREADY_IN_GROUP`. No se consume ningún id.
   2. Si no hay grupo cercano: el mob **funda un grupo de uno**.
   3. Si el grupo cercano no existe en `ActiveGroups`: se rechaza con `UNKNOWN_GROUP`.
   4. Si el grupo cercano está lleno (miembros ≥ `GroupSettings.maxGroupSize`, leído en el momento, así una recarga vale enseguida): el mob **funda un grupo nuevo**.
   5. Si no: el mob **se suma** al grupo cercano, con el siguiente orden de llegada.
2. **Grupo nuevo:** id de `GroupIdSource`, política `SelectionSettings.defaultPolicy` vigente, memoria y ledger de amenaza nuevos que leen su sección con `SettingsHolder.section(...)`.
3. **Sumarse con un plan en curso:** el mob entra al grupo, pero el plan no cambia: no tiene rol ni cuenta entre los miembros iniciales. Recibe rol en la próxima decisión (WP-13). El grupo no cambia de estado.
4. **Quién es «cercano»** lo decide el adaptador (radio de reclutamiento, WP de adaptadores). La aplicación no mide distancias.
5. **Sacar un miembro** (`RemoveMember`): si el mob no está indexado, `NOT_A_MEMBER`. Si no, sale del grupo (el dominio publica `LeaderDied` si era el líder) y del índice. Si el grupo queda vacío se disuelve en el momento: `GROUP_DISBANDED`; si no, `REMOVED`.
6. **Disolver** (`DisbandGroup`): saca el grupo y desindexa a todos sus miembros. Devuelve el grupo sacado, para que el WP-13 decida qué hace con su memoria y sus eventos pendientes. Disolver un grupo desconocido devuelve vacío.
7. **Invariante de `ActiveGroups`:** un mob está en el índice si y solo si es miembro de exactamente un grupo activo, y apunta a ese grupo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/GroupIdSource.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/SettingsHolder.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecruitRequest.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecruitResult.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecruitMob.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RemovalOutcome.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RemoveMember.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/DisbandGroup.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/testsupport/SequentialGroupIdSource.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/SettingsHolderTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/ActiveGroupsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecruitMobTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RemoveMemberTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/DisbandGroupTest.java` |

No se modifica ningún archivo existente. El dominio de grupos no cambia: no se agregan métodos públicos a clases existentes.

## Especificación

El código de producción va así (los imports los resolvés vos; Spotless decide el formato).

### `domain/port/GroupIdSource.java`

```java
package io.github.nicodoou.mobai.domain.port;

/** New group ids come from here, so tests and replays get the same ids without random draws. */
public interface GroupIdSource {
  GroupId nextGroupId();
}
```

### `application/SettingsHolder.java`

```java
public final class SettingsHolder {
  private MobAiSettings current;

  public SettingsHolder(MobAiSettings initial) {
    this.current = Objects.requireNonNull(initial, "SettingsHolder.initial");
  }

  public MobAiSettings current() {
    return current;
  }

  public void replace(MobAiSettings settings) {
    current = Objects.requireNonNull(settings, "SettingsHolder.settings");
  }

  // Reads on every call, so a reload reaches everyone who holds the supplier.
  public <T> Supplier<T> section(Function<MobAiSettings, T> selector) {
    Objects.requireNonNull(selector, "SettingsHolder.selector");
    return () -> selector.apply(current);
  }
}
```

### `application/ActiveGroups.java`

```java
public final class ActiveGroups {
  private final Map<GroupId, Group> groups = new LinkedHashMap<>();
  private final Map<MobId, GroupId> groupByMob = new HashMap<>();

  public void add(Group group) {
    requireAddable(group);
    register(group);
  }

  public Optional<Group> remove(GroupId groupId) {
    Group removed = groups.remove(groupId);
    if (removed == null) {
      return Optional.empty();
    }
    unindexMembers(removed);
    return Optional.of(removed);
  }

  public Optional<Group> group(GroupId groupId) {
    return Optional.ofNullable(groups.get(groupId));
  }

  public Optional<Group> groupOf(MobId mobId) {
    return Optional.ofNullable(groupByMob.get(mobId)).map(groups::get);
  }

  public List<Group> groups() {
    return List.copyOf(groups.values());
  }

  public int size() {
    return groups.size();
  }

  public Member join(GroupId groupId, MobId mobId, MobKind kind) {
    Group group = requireGroup(groupId);
    requireLoose(mobId);
    Member member = group.roster().addMember(mobId, kind);
    groupByMob.put(mobId, groupId);
    return member;
  }

  public Optional<Group> leave(MobId mobId, long tick) {
    Optional<Group> group = groupOf(mobId);
    group.ifPresent(found -> detach(found, mobId, tick));
    return group;
  }

  private void requireAddable(Group group) {
    if (groups.containsKey(group.id())) {
      throw new IllegalArgumentException("ActiveGroups already has group " + group.id().shortId());
    }
    group.roster().members().forEach(member -> requireLoose(member.id()));
  }

  private void register(Group group) {
    groups.put(group.id(), group);
    group.roster().members().forEach(member -> groupByMob.put(member.id(), group.id()));
  }

  private void unindexMembers(Group group) {
    group.roster().members().forEach(member -> groupByMob.remove(member.id()));
  }

  private void detach(Group group, MobId mobId, long tick) {
    group.removeMember(mobId, tick);
    groupByMob.remove(mobId);
  }

  private Group requireGroup(GroupId groupId) {
    return group(groupId)
        .orElseThrow(
            () -> new IllegalArgumentException("ActiveGroups has no group " + groupId.shortId()));
  }

  private void requireLoose(MobId mobId) {
    GroupId current = groupByMob.get(mobId);
    if (current != null) {
      throw new IllegalArgumentException(
          "Mob " + mobId.shortId() + " is already in group " + current.shortId());
    }
  }
}
```

`add` sirve para los grupos nuevos (vacíos) y para los que restaura la persistencia (WP-15), que llegan con miembros: por eso indexa a todos. Valida todo **antes** de guardar nada: si falla, `ActiveGroups` queda como estaba.

### `application/RecruitRequest.java`

```java
public record RecruitRequest(MobId mob, MobKind kind, Optional<GroupId> nearbyGroup) {
  public RecruitRequest {
    Objects.requireNonNull(mob, "RecruitRequest.mob");
    Objects.requireNonNull(kind, "RecruitRequest.kind");
    Objects.requireNonNull(nearbyGroup, "RecruitRequest.nearbyGroup");
  }

  public static RecruitRequest loose(MobId mob, MobKind kind) {
    return new RecruitRequest(mob, kind, Optional.empty());
  }

  public static RecruitRequest near(MobId mob, MobKind kind, GroupId nearbyGroup) {
    return new RecruitRequest(mob, kind, Optional.of(nearbyGroup));
  }
}
```

### `application/RecruitResult.java`

```java
public sealed interface RecruitResult {
  enum Rejection {
    ALREADY_IN_GROUP,
    UNKNOWN_GROUP
  }

  record Joined(GroupId groupId, Member member) implements RecruitResult {
    public Joined {
      Objects.requireNonNull(groupId, "Joined.groupId");
      Objects.requireNonNull(member, "Joined.member");
    }
  }

  record Founded(GroupId groupId, Member member) implements RecruitResult {
    public Founded {
      Objects.requireNonNull(groupId, "Founded.groupId");
      Objects.requireNonNull(member, "Founded.member");
    }
  }

  record Rejected(Rejection reason) implements RecruitResult {
    public Rejected {
      Objects.requireNonNull(reason, "Rejected.reason");
    }
  }
}
```

### `application/RecruitMob.java`

```java
public final class RecruitMob {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final GroupIdSource groupIds;

  public RecruitMob(ActiveGroups activeGroups, SettingsHolder settings, GroupIdSource groupIds) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecruitMob.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecruitMob.settings");
    this.groupIds = Objects.requireNonNull(groupIds, "RecruitMob.groupIds");
  }

  public RecruitResult execute(RecruitRequest request) {
    if (activeGroups.groupOf(request.mob()).isPresent()) {
      return new RecruitResult.Rejected(RecruitResult.Rejection.ALREADY_IN_GROUP);
    }
    return request
        .nearbyGroup()
        .map(groupId -> recruitNear(request, groupId))
        .orElseGet(() -> found(request));
  }

  private RecruitResult recruitNear(RecruitRequest request, GroupId groupId) {
    Optional<Group> group = activeGroups.group(groupId);
    if (group.isEmpty()) {
      return new RecruitResult.Rejected(RecruitResult.Rejection.UNKNOWN_GROUP);
    }
    if (isFull(group.get())) {
      return found(request);
    }
    return new RecruitResult.Joined(
        groupId, activeGroups.join(groupId, request.mob(), request.kind()));
  }

  private boolean isFull(Group group) {
    return group.roster().members().size() >= settings.current().group().maxGroupSize();
  }

  private RecruitResult found(RecruitRequest request) {
    Group group = newGroup();
    activeGroups.add(group);
    return new RecruitResult.Founded(
        group.id(), activeGroups.join(group.id(), request.mob(), request.kind()));
  }

  private Group newGroup() {
    GroupKnowledge knowledge =
        new GroupKnowledge(
            new GroupMemory(settings.section(MobAiSettings::memory)),
            new ThreatLedger(settings.section(MobAiSettings::target)));
    return new Group(
        groupIds.nextGroupId(), settings.current().selection().defaultPolicy(), knowledge);
  }
}
```

### `application/RemovalOutcome.java`

```java
public enum RemovalOutcome {
  NOT_A_MEMBER,
  REMOVED,
  GROUP_DISBANDED
}
```

### `application/RemoveMember.java`

```java
public final class RemoveMember {
  private final ActiveGroups activeGroups;
  private final DisbandGroup disbandGroup;

  public RemoveMember(ActiveGroups activeGroups, DisbandGroup disbandGroup) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RemoveMember.activeGroups");
    this.disbandGroup = Objects.requireNonNull(disbandGroup, "RemoveMember.disbandGroup");
  }

  public RemovalOutcome execute(MobId mobId, long tick) {
    Optional<Group> group = activeGroups.leave(mobId, tick);
    if (group.isEmpty()) {
      return RemovalOutcome.NOT_A_MEMBER;
    }
    if (!group.get().roster().isEmpty()) {
      return RemovalOutcome.REMOVED;
    }
    disbandGroup.execute(group.get().id());
    return RemovalOutcome.GROUP_DISBANDED;
  }
}
```

### `application/DisbandGroup.java`

```java
public final class DisbandGroup {
  private final ActiveGroups activeGroups;

  public DisbandGroup(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DisbandGroup.activeGroups");
  }

  public Optional<Group> execute(GroupId groupId) {
    return activeGroups.remove(groupId);
  }
}
```

### `testsupport/SequentialGroupIdSource.java`

```java
public final class SequentialGroupIdSource implements GroupIdSource {
  private long issued;

  @Override
  public GroupId nextGroupId() {
    issued++;
    return new GroupId(new UUID(0, issued));
  }

  public long issued() {
    return issued;
  }
}
```

## Pruebas obligatorias

Convenciones de todas las pruebas de este WP:

- `mob(n)` = `new MobId(new UUID(1, n))`; `player` = `new PlayerId(new UUID(2, 1))`; `groupId(n)` = `new GroupId(new UUID(0, n))` (coincide con el n-ésimo id de `SequentialGroupIdSource`).
- Un grupo armado a mano: método privado `newGroup(long n)` en cada clase de prueba que lo necesite, que devuelve `new Group(groupId(n), SelectionPolicyType.THOMPSON_SAMPLING, new GroupKnowledge(new GroupMemory(() -> TestSettings.defaults().memory()), new ThreatLedger(() -> TestSettings.defaults().target())))`.
- Configuración con otro tamaño máximo: `withMaxGroupSize(int size)`, método privado en `RecruitMobTest` que copia `TestSettings.defaults()` reemplazando solo `group` por `new GroupSettings(size, 10, 24.0)`.

### `SettingsHolderTest` (3)

| Prueba | Verifica |
| --- | --- |
| `currentReturnsTheInitialSettings` | `current()` es el mismo objeto que se pasó al constructor (`isSameAs`) |
| `currentReturnsTheLastReplacedSettings` | después de `replace(otro)`, `current()` es `otro` |
| `sectionReadsTheCurrentValueAfterReplace` | `Supplier<GroupSettings> s = holder.section(MobAiSettings::group)` tomado **antes** de `replace(withMaxGroupSize(3))` devuelve `maxGroupSize` 3 después |

### `ActiveGroupsTest` (11)

| Prueba | Verifica |
| --- | --- |
| `addIndexesEveryExistingMember` | grupo 1 con `mob(1)` y `mob(2)` cargados en su roster antes de `add`; `groupOf` de los dos devuelve el grupo 1 |
| `addRejectsADuplicateGroupId` | segundo `add` de otro grupo con `groupId(1)`: `IllegalArgumentException` con mensaje exacto `"ActiveGroups already has group " + groupId(1).shortId()` |
| `addRejectsAMobAlreadyInAnotherGroupAndAddsNothing` | grupo 1 con `mob(1)` agregado; grupo 2 con `mob(1)` y `mob(2)` en su roster; `add(grupo2)` lanza con mensaje `"Mob " + mob(1).shortId() + " is already in group " + groupId(1).shortId()`; después `group(groupId(2))` vacío y `groupOf(mob(2))` vacío |
| `joinAddsTheMemberAndIndexesIt` | `join(groupId(1), mob(1), ZOMBIE)` devuelve `Member(mob(1), ZOMBIE, 1)` y `groupOf(mob(1))` es el grupo 1 |
| `joinRejectsAnUnknownGroup` | `join(groupId(9), ...)` lanza con `"ActiveGroups has no group " + groupId(9).shortId()` |
| `joinRejectsAMobAlreadyInAGroup` | `mob(1)` en el grupo 1; `join(groupId(2), mob(1), ZOMBIE)` lanza con `"Mob " + mob(1).shortId() + " is already in group " + groupId(1).shortId()`; el grupo 2 sigue vacío |
| `leaveRemovesTheMemberAndUnindexesIt` | `mob(1)` y `mob(2)` en el grupo 1; `leave(mob(2), 50)` devuelve el grupo 1; `groupOf(mob(2))` vacío; el roster solo tiene `mob(1)` |
| `leaveOfALooseMobReturnsEmpty` | `leave(mob(7), 50)` vacío y no cambia nada (`size()` igual) |
| `leaveOfTheLeaderQueuesLeaderDied` | `mob(1)` (líder) y `mob(2)`; `leave(mob(1), 50)`; `drainEvents()` es exactamente `[new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50)]` |
| `removeUnindexesEveryMember` | `remove(groupId(1))` devuelve el grupo; `groupOf` de sus dos miembros vacío; `size()` 0 |
| `groupsKeepInsertionOrder` | se agregan los grupos 3, 1 y 2; `groups()` mapeado a ids es `[groupId(3), groupId(1), groupId(2)]` |

### `RecruitMobTest` (8)

Arranque común: `ActiveGroups`, `SettingsHolder(TestSettings.defaults())`, `SequentialGroupIdSource`.

| Prueba | Verifica |
| --- | --- |
| `recruitWithoutANearbyGroupFoundsAGroupOfOne` | `loose(mob(1), ZOMBIE)` da `Founded(groupId(1), Member(mob(1), ZOMBIE, 1))`; el grupo tiene política `THOMPSON_SAMPLING`, estado `OBSERVING` y un miembro |
| `recruitNearAGroupJoinsIt` | después del anterior, `near(mob(2), SKELETON, groupId(1))` da `Joined(groupId(1), Member(mob(2), SKELETON, 2))`; `issued()` sigue en 1 |
| `recruitNearAFullGroupFoundsANewGroup` | holder con `withMaxGroupSize(2)`; `mob(1)` funda, `mob(2)` se suma; `near(mob(3), ZOMBIE, groupId(1))` da `Founded(groupId(2), Member(mob(3), ZOMBIE, 1))`; el grupo 1 sigue con 2 |
| `recruitOfAMobAlreadyInAGroupIsRejected` | `mob(1)` fundó; `loose(mob(1), ZOMBIE)` da `Rejected(ALREADY_IN_GROUP)`; `issued()` sigue en 1 y `size()` en 1 |
| `recruitNearAnUnknownGroupIsRejected` | `near(mob(1), ZOMBIE, groupId(9))` da `Rejected(UNKNOWN_GROUP)`; `issued()` 0 y `size()` 0 |
| `recruitReadsTheMaximumSizeAfterAReload` | con defaults, `mob(1)` funda y `mob(2)` se suma; `replace(withMaxGroupSize(2))`; `near(mob(3), ZOMBIE, groupId(1))` da `Founded` con `groupId(2)` |
| `foundedGroupUsesTheDefaultPolicyInForce` | `replace` con `SelectionSettings(EPSILON_GREEDY, 0.5, 1.5, 0.1, 10)` (el resto, defaults); el grupo fundado tiene política `EPSILON_GREEDY` |
| `joinWhileExecutingKeepsThePlanAndGivesNoRole` | `mob(1)` funda; en el grupo: `lifecycle().beginPlanning()` y `startPlan(new PlanStart(new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20.0, 100))`; `near(mob(2), ZOMBIE, groupId(1))` da `Joined`; el estado sigue `EXECUTING`, `plan().orElseThrow().roleOf(mob(2))` vacío y `startingMembers()` 1 |

### `RemoveMemberTest` (4)

Arranque: `ActiveGroups`, `DisbandGroup`, `RemoveMember`, y los mobs se agregan con `activeGroups.add(newGroup(1))` y `join`.

| Prueba | Verifica |
| --- | --- |
| `removeOfAMemberKeepsTheGroupWhenOthersRemain` | `mob(1)` y `mob(2)`; `execute(mob(2), 50)` da `REMOVED`; el grupo sigue activo con `mob(1)` |
| `removeOfTheLastMemberDisbandsTheGroup` | solo `mob(1)`; `execute(mob(1), 50)` da `GROUP_DISBANDED`; `group(groupId(1))` vacío y `size()` 0 |
| `removeOfALooseMobReturnsNotAMember` | `execute(mob(7), 50)` da `NOT_A_MEMBER` |
| `removeOfTheLeaderQueuesLeaderDiedWithTheNextLeader` | `mob(1)` y `mob(2)`; `execute(mob(1), 50)`; el grupo drena `[new LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50)]` |

### `DisbandGroupTest` (2)

| Prueba | Verifica |
| --- | --- |
| `disbandRemovesTheGroupAndUnindexesItsMembers` | grupo 1 con `mob(1)` y `mob(2)`; `execute(groupId(1))` devuelve ese grupo (`isSameAs`); `groupOf` de los dos vacío |
| `disbandOfAnUnknownGroupReturnsEmpty` | `execute(groupId(9))` vacío |

Total: **28 pruebas nuevas**.

## Pruebas que muerden

Cada rotura es temporal; confirmá la falla y revertí con `git checkout`.

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `ActiveGroups.detach`, borrar `groupByMob.remove(mobId)` | `groupOf(mob(2))` sigue devolviendo el grupo 1 | `leaveRemovesTheMemberAndUnindexesIt` |
| 2 | En `RecruitMob.isFull`, `>=` por `>` | con máximo 2 y 2 miembros, `2 > 2` es falso: `mob(3)` se suma (`Joined`) en vez de fundar | `recruitNearAFullGroupFoundsANewGroup` |
| 3 | En `SettingsHolder.section`, capturar el valor: `T value = selector.apply(current); return () -> value;` | el supplier devuelve `maxGroupSize` 12 en vez de 3 | `sectionReadsTheCurrentValueAfterReplace` |
| 4 | En `ActiveGroups.add`, llamar a `register(group)` antes de `requireAddable(group)` | el grupo 2 queda guardado aunque se lance la excepción: `group(groupId(2))` no está vacío | `addRejectsAMobAlreadyInAnotherGroupAndAddsNothing` |

Pegá en el informe el nombre de la prueba que falló con cada rotura.

## Procedimiento

1. Rama `wp-12-grupos-activos` desde `main`.
2. `GroupIdSource`, `SettingsHolder` y `SequentialGroupIdSource`, con `SettingsHolderTest`. Commit: `feat: add settings holder and group id source port`.
3. `ActiveGroups` con `ActiveGroupsTest`. Commit: `feat: add active groups with a single mob-to-group index`.
4. `RecruitRequest`, `RecruitResult`, `RecruitMob` con `RecruitMobTest`. Commit: `feat: add recruit mob use case`.
5. `RemovalOutcome`, `DisbandGroup`, `RemoveMember` con sus pruebas. Commit: `feat: add remove member and disband group use cases`.
6. Pruebas que muerden (sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-12: active groups and membership use cases`, esperar el check `build` en verde, informe.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` se queja del método genérico `section` u otra advertencia sin cambiar comportamiento, ajustá solo la declaración (por ejemplo, tipos explícitos) y avisalo.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Ticks, decisiones, `RecordOutcome`, memoria al disolver y `RegroupWindow.recordWiped`: WP-13.
- Unir grupos (`MergeGroups`) y escape (`RecordEscape`).
- Medir distancias o radios: adaptadores.
- La implementación real de `GroupIdSource` (`UUID.randomUUID` en el adaptador): WP-16.
- Tocar `Group`, `GroupRoster` o cualquier clase existente.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 28 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ninguna clase nueva pasa de 20 métodos públicos (`ActiveGroups` tiene 8).
- [ ] Build, cobertura y CI en verde.
