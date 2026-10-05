# WP-08 — Grupo, plan y eventos

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-06 |
| Modelo | Sonnet |
| Rama | `wp-08-grupo-plan-eventos` |

## Objetivo

Crear el estado de un grupo y las reglas que lo mantienen válido:

- los miembros y el líder;
- el ciclo de cuatro estados (`OBSERVING` → `PLANNING` → `EXECUTING` → `EVALUATING`);
- el plan en curso, con su objetivo, roles y daño;
- el cierre del plan con su éxito fraccional;
- los eventos del dominio (`PlanClosed` y `LeaderDied`) y quien los reparte.

El cerebro (WP-10) va a decidir **cuándo** pasa cada cosa; este WP define **qué** está permitido y garantiza que el grupo nunca quede en un estado inválido.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/GroupId.java`, `MobId.java`, `PlayerId.java`, `StrategyId.java`, `MobKind.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/selection/SelectionPolicyType.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (solo el constructor)
   - `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatLedger.java` (solo el constructor)
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Miembros.** Cada miembro tiene un número de ingreso (`joinOrder`) que nunca se repite dentro del grupo: el primero es 1. Se guarda en disco (D14), así el orden sobrevive a un reinicio. La lista de miembros siempre está ordenada por número de ingreso.
2. **Líder** (RF-01.6). Es el miembro más antiguo (menor número de ingreso). Si se va el líder, el siguiente más antiguo pasa a serlo en el mismo momento y se emite `LeaderDied`. Si no queda nadie, no hay líder.
3. **Estados** (D10). Solo estas transiciones son válidas; cualquier otra lanza `IllegalStateException`:

   | Método | Desde | Hacia |
   | --- | --- | --- |
   | `beginPlanning` | `OBSERVING` | `PLANNING` |
   | `startPlan` | `PLANNING` | `EXECUTING` |
   | `closePlan` | `EXECUTING` | `EVALUATING` |
   | `finishEvaluation` | `EVALUATING` | `OBSERVING` |

4. **Plan.** Cada plan tiene un `PlanId` (grupo + número de plan dentro del grupo, empezando en 1): es el ID de correlación de todas las trazas del plan.
   - El objetivo y los roles se fijan al empezar el plan (D5 y D8).
   - Durante el plan solo cambia el rol de un mob puntual (por ejemplo, a `RETREAT` por vida baja). El cerebro decide cuándo; el grupo solo lo guarda.
   - El plan acumula el daño hecho al objetivo (D12) y el último tick en que algún miembro vio al objetivo (para `TARGET_LOST`).
   - Guarda la vida máxima del objetivo al empezar: el éxito se mide contra ella.
5. **Éxito de un plan** (catálogo):
   - si el objetivo murió, 1;
   - si no, `min(1, daño hecho ÷ (fullSuccessDamageFraction × vida máxima del objetivo))`.

   Con fracción 0,5 y vida máxima 20: 5 de daño → 0,5; 10 o más → 1; 0 → 0.
6. **Compromiso** (D5). Al cerrar un plan, su objetivo pasa a ser el «objetivo comprometido» del grupo: el selector le da el bonus en el plan siguiente.
7. **Arañas.** Cada araña guarda su propio objetivo (RF-02); se borra cuando la araña sale del grupo.
8. **Eventos.** El grupo no conoce a quién avisar: junta sus eventos en una lista y la aplicación (WP-13) los retira con `drainEvents()` y los publica con `DomainEventPublisher`. Así el dominio queda sin dependencias y los eventos se pueden probar como datos.
9. **Publicador.** Reparte cada evento a los suscriptores de su tipo, en el orden en que se suscribieron. Un suscriptor puede publicar otro evento dentro de la entrega; para que una cadena mal armada no cuelgue el server, más de 8 publicaciones anidadas lanzan `IllegalStateException`.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/shared/PlanId.java` |
| Crear | `domain/group/Role.java`, `GroupState.java`, `PlanEndReason.java` |
| Crear | `domain/group/Member.java`, `PlanStart.java`, `Plan.java`, `GroupKnowledge.java`, `Group.java` |
| Crear | `domain/decision/ClosedPlan.java` |
| Crear | `domain/event/DomainEvent.java`, `PlanClosed.java`, `LeaderDied.java`, `DomainEventPublisher.java` |
| Crear (prueba) | `domain/shared/PlanIdTest.java` |
| Crear (prueba) | `domain/group/PlanTest.java`, `GroupMembershipTest.java`, `GroupLifecycleTest.java` |
| Crear (prueba) | `domain/decision/ClosedPlanTest.java` |
| Crear (prueba) | `domain/event/DomainEventPublisherTest.java` |

## Especificación

### 1. `domain.shared.PlanId`

```java
public record PlanId(GroupId group, long sequence) {
  public PlanId {
    Objects.requireNonNull(group, "PlanId.group");
    if (sequence < 1) {
      throw new IllegalArgumentException("PlanId.sequence must be at least 1, got " + sequence);
    }
  }

  public String shortId() {
    return group.shortId() + "#" + sequence;
  }
}
```

### 2. Enums (`domain.group`)

- `Role`: `PRESS`, `FLANK`, `SHOOT`, `RETREAT`, en ese orden.
- `GroupState`: `OBSERVING`, `PLANNING`, `EXECUTING`, `EVALUATING`.
- `PlanEndReason`: `TARGET_DIED`, `TARGET_LOST`, `TIMED_OUT`, `GROUP_RETREATED`.

### 3. `domain.group.Member`

```java
public record Member(MobId id, MobKind kind, long joinOrder) { ... }
```

`requireNonNull` de `id` y `kind` (`"Member.<componente>"`); `joinOrder < 1`: `IllegalArgumentException("Member.joinOrder must be at least 1, got " + joinOrder)`.

### 4. `domain.group.PlanStart`

Lo que el cerebro decide al empezar un plan. Record de valor (exento del límite de parámetros).

```java
public record PlanStart(
    StrategyId strategy, PlayerId target, Map<MobId, Role> roles, double targetMaxHealth, long tick) { ... }
```

Validación, en orden:
1. `requireNonNull` de `strategy`, `target` y `roles` (`"PlanStart.<componente>"`).
2. `!(targetMaxHealth > 0) || !Double.isFinite(targetMaxHealth)`: `IllegalArgumentException("PlanStart.targetMaxHealth must be a positive number, got " + targetMaxHealth)`.
3. `tick < 0`: `IllegalArgumentException("PlanStart.tick must be zero or positive, got " + tick)`.
4. `roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));` (conserva el orden en que el cerebro repartió los roles).

### 5. `domain.group.Plan`

Inmutable: cada cambio devuelve un plan nuevo.

```java
public record Plan(
    PlanId id,
    StrategyId strategy,
    PlayerId target,
    Map<MobId, Role> roles,
    double targetMaxHealth,
    long startTick,
    long lastTargetSeenTick,
    double damageDealt) {

  public static Plan start(PlanId id, PlanStart start) { ... }

  public Plan withDamageDealt(double damage) { ... }
  public Plan withTargetSeenAt(long tick) { ... }
  public Plan withRole(MobId mob, Role role) { ... }
  public Plan withoutMember(MobId mob) { ... }
  public Optional<Role> roleOf(MobId mob) { ... }
  public long ageTicks(long tick) { ... }
  public long ticksSinceTargetSeen(long tick) { ... }
  public double successFraction(double fullSuccessDamageFraction) { ... }
}
```

Constructor compacto: `requireNonNull` de `id`, `strategy`, `target` y `roles` (`"Plan.<componente>"`), y `roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));`. Sin más validaciones: los valores llegan desde `start` y los `with…`, que validan.

| Método | Comportamiento |
| --- | --- |
| `start` | `new Plan(id, start.strategy(), start.target(), start.roles(), start.targetMaxHealth(), start.tick(), start.tick(), 0)` |
| `withDamageDealt(damage)` | Si `!(damage > 0) \|\| !Double.isFinite(damage)`: `IllegalArgumentException("Plan.damage must be a positive number, got " + damage)`. Devuelve una copia con `damageDealt + damage` |
| `withTargetSeenAt(tick)` | Si `tick < lastTargetSeenTick`: `IllegalArgumentException("Plan.lastTargetSeenTick must not go back, got " + tick + " after " + lastTargetSeenTick)`. Devuelve una copia con `lastTargetSeenTick = tick` |
| `withRole(mob, role)` | `requireNonNull` de los dos (`"Plan.mob"`, `"Plan.role"`). Copia de `roles` en un `LinkedHashMap`, `put(mob, role)` (si el mob ya estaba, conserva su posición) y devuelve la copia del plan |
| `withoutMember(mob)` | Copia de `roles` sin `mob`; si no estaba, devuelve `this` |
| `roleOf(mob)` | `Optional.ofNullable(roles.get(mob))` |
| `ageTicks(tick)` | `tick - startTick` |
| `ticksSinceTargetSeen(tick)` | `tick - lastTargetSeenTick` |
| `successFraction(fraction)` | `Math.min(1, damageDealt / (fraction * targetMaxHealth))` |

Para no repetir el constructor con ocho argumentos en cada `with…`, usá un método privado por componente que cambia: `private Plan copyWithRoles(Map<MobId, Role> newRoles)`, `copyWithDamage(double newDamage)` y `copyWithLastSeen(long newTick)`.

### 6. `domain.decision.ClosedPlan`

```java
public record ClosedPlan(
    PlanId id,
    StrategyId strategy,
    PlayerId target,
    PlanEndReason reason,
    double success,
    double damageDealt,
    long startTick,
    long endTick) { ... }
```

Validación, en orden:
1. `requireNonNull` de `id`, `strategy`, `target` y `reason` (`"ClosedPlan.<componente>"`).
2. `!(success >= 0 && success <= 1)`: `IllegalArgumentException("ClosedPlan.success must be between 0.0 and 1.0, got " + success)`.
3. `endTick < startTick`: `IllegalArgumentException("ClosedPlan.endTick must not be before startTick, got " + endTick + " < " + startTick)`.

### 7. Eventos (`domain.event`)

```java
public sealed interface DomainEvent permits PlanClosed, LeaderDied {
  GroupId groupId();
  long tick();
}

public record PlanClosed(ClosedPlan plan) implements DomainEvent {
  // requireNonNull(plan, "PlanClosed.plan")
  // groupId() devuelve plan.id().group(); tick() devuelve plan.endTick()
}

public record LeaderDied(GroupId groupId, MobId formerLeader, Optional<MobId> newLeader, long tick)
    implements DomainEvent {
  // requireNonNull de los tres objetos: "LeaderDied.<componente>"
}
```

`LeaderDied` se emite cada vez que el líder deja el grupo (muere, se descarga sin volver, o lo saca la aplicación): para el grupo es lo mismo.

### 8. `domain.event.DomainEventPublisher`

```java
public final class DomainEventPublisher {
  public <E extends DomainEvent> void subscribe(Class<E> type, Consumer<? super E> subscriber) { ... }
  public void publish(DomainEvent event) { ... }
}
```

Estado y constantes:

```java
  // A subscriber may publish while handling an event; a deeper chain means a subscriber loop.
  private static final int MAX_NESTED_PUBLISH_DEPTH = 8;

  private final List<Subscription<?>> subscriptions = new ArrayList<>();
  private int depth = 0;

  private record Subscription<E extends DomainEvent>(Class<E> type, Consumer<? super E> subscriber) {
    void deliver(DomainEvent event) {
      if (type.isInstance(event)) {
        subscriber.accept(type.cast(event));
      }
    }
  }
```

| Método | Comportamiento |
| --- | --- |
| `subscribe` | `requireNonNull` (`"DomainEventPublisher.type"`, `"DomainEventPublisher.subscriber"`) y agrega `new Subscription<>(type, subscriber)` al final |
| `publish` | `requireNonNull(event, "DomainEventPublisher.event")`. Si `depth >= MAX_NESTED_PUBLISH_DEPTH`: `IllegalStateException("DomainEventPublisher: nested publish deeper than " + MAX_NESTED_PUBLISH_DEPTH + " levels for " + event)`. Si no: `depth++`, después `deliverToAll(event)` dentro de un `try`, y `depth--` en el `finally` |
| `private void deliverToAll(DomainEvent event)` | `for (Subscription<?> subscription : List.copyOf(subscriptions)) subscription.deliver(event);`. Se recorre una copia: un suscriptor que se suscribe durante la entrega no recibe el evento en curso y no alarga el recorrido |

Las excepciones de un suscriptor no se atrapan: llegan a quien publicó.

### 9. `domain.group.GroupKnowledge`

Lo que el grupo recuerda: la memoria de largo plazo y la amenaza de los últimos 30 s.

```java
public record GroupKnowledge(GroupMemory memory, ThreatLedger threat) { ... }
// requireNonNull de los dos: "GroupKnowledge.<componente>"
```

Existe para que el constructor de `Group` tenga 3 parámetros.

### 10. `domain.group.Group`

```java
public final class Group {
  public Group(GroupId id, SelectionPolicyType policy, GroupKnowledge knowledge) { ... }

  // identidad
  public GroupId id()
  public SelectionPolicyType policy()
  public GroupMemory memory()
  public ThreatLedger threat()

  // miembros
  public Member addMember(MobId id, MobKind kind)
  public void restoreMember(Member member)
  public void removeMember(MobId id, long tick)
  public List<Member> members()
  public Optional<Member> member(MobId id)
  public boolean isEmpty()
  public Optional<MobId> leader()

  // ciclo y plan
  public GroupState state()
  public Optional<Plan> plan()
  public Optional<PlayerId> committedTarget()
  public void beginPlanning()
  public Plan startPlan(PlanStart start)
  public void recordPlanDamage(PlayerId player, double damage)
  public void markTargetSeen(long tick)
  public void assignRole(MobId mob, Role role)
  public ClosedPlan closePlan(PlanEndReason reason, long tick, double fullSuccessDamageFraction)
  public void finishEvaluation()
  public long planSequence()

  // arañas
  public Optional<PlayerId> spiderTarget(MobId spider)
  public void assignSpiderTarget(MobId spider, PlayerId target)

  // eventos
  public List<DomainEvent> drainEvents()
}
```

**Estado:**

```java
  private final GroupId id;
  private final SelectionPolicyType policy;
  private final GroupKnowledge knowledge;
  private final List<Member> members = new ArrayList<>();
  private final Map<MobId, PlayerId> spiderTargets = new LinkedHashMap<>();
  private final List<DomainEvent> pendingEvents = new ArrayList<>();
  private GroupState state = GroupState.OBSERVING;
  private Plan plan;                     // null fuera de EXECUTING y EVALUATING; nunca sale como null
  private PlayerId committedTarget;      // null hasta el primer plan cerrado; sale como Optional
  private long nextJoinOrder = 1;
  private long planSequence = 0;
```

Constructor: `requireNonNull` con `"Group.id"`, `"Group.policy"` y `"Group.knowledge"`.

**Métodos:**

| Método | Comportamiento |
| --- | --- |
| `memory()`, `threat()` | `knowledge.memory()`, `knowledge.threat()` |
| `addMember(id, kind)` | `requireNotMember(id)`; `Member member = new Member(id, kind, nextJoinOrder++)`; lo agrega al final de `members`; lo devuelve |
| `restoreMember(member)` | Para cargar desde disco. `requireNotMember(member.id())`; si algún miembro ya tiene ese `joinOrder`: `IllegalArgumentException("Group " + id.shortId() + " already has join order " + member.joinOrder())`. Lo agrega, ordena `members` con `Comparator.comparingLong(Member::joinOrder)` y deja `nextJoinOrder = Math.max(nextJoinOrder, member.joinOrder() + 1)` |
| `removeMember(id, tick)` | Si no es miembro, no hace nada. Si es: `boolean wasLeader = leader().filter(id::equals).isPresent();` lo quita de `members`, borra su objetivo de araña, `plan = plan == null ? null : plan.withoutMember(id)`; si `wasLeader`, agrega `new LeaderDied(this.id, id, leader(), tick)` a `pendingEvents` |
| `members()` | `List.copyOf(members)` |
| `member(id)` | Búsqueda por stream |
| `isEmpty()` | `members.isEmpty()` |
| `leader()` | El primero de `members` (la lista siempre está ordenada por ingreso): `members.isEmpty() ? Optional.empty() : Optional.of(members.getFirst().id())` |
| `plan()` | `Optional.ofNullable(plan)` |
| `committedTarget()` | `Optional.ofNullable(committedTarget)` |
| `beginPlanning()` | `requireState(OBSERVING, "begin planning")`; `state = PLANNING` |
| `startPlan(start)` | `requireState(PLANNING, "start a plan")`; `requireMembers(start.roles().keySet())`; `planSequence++`; `plan = Plan.start(new PlanId(id, planSequence), start)`; `state = EXECUTING`; devuelve `plan` |
| `recordPlanDamage(player, damage)` | Si `state != EXECUTING` o `player` no es `plan.target()`, no hace nada (el daño a otro jugador no es del plan). Si no: `plan = plan.withDamageDealt(damage)` |
| `markTargetSeen(tick)` | `requireState(EXECUTING, "mark the target seen")`; `plan = plan.withTargetSeenAt(tick)` |
| `assignRole(mob, role)` | `requireState(EXECUTING, "assign a role")`; `requireMembers(Set.of(mob))`; `plan = plan.withRole(mob, role)` |
| `closePlan(reason, tick, fraction)` | `requireState(EXECUTING, "close a plan")`; `ClosedPlan closed = closedPlan(reason, tick, fraction)`; `committedTarget = plan.target()`; `state = EVALUATING`; agrega `new PlanClosed(closed)` a `pendingEvents`; devuelve `closed`. El plan se conserva hasta `finishEvaluation`, para que la evaluación lo pueda leer |
| `finishEvaluation()` | `requireState(EVALUATING, "finish evaluation")`; `plan = null`; `state = OBSERVING` |
| `planSequence()` | El último número de plan usado (0 si no hubo ninguno) |
| `spiderTarget(spider)` | `Optional.ofNullable(spiderTargets.get(spider))` |
| `assignSpiderTarget(spider, target)` | `requireNonNull(target, "Group.spiderTarget")`; `requireMembers(Set.of(spider))`; si el miembro no es `MobKind.SPIDER`: `IllegalArgumentException("Group " + id.shortId() + ": member " + spider.value() + " is not a spider")`; `spiderTargets.put(spider, target)` |
| `drainEvents()` | `List<DomainEvent> drained = List.copyOf(pendingEvents); pendingEvents.clear(); return drained;` |

**Privados:**

| Método | Cuerpo |
| --- | --- |
| `private void requireState(GroupState expected, String action)` | Si `state != expected`: `IllegalStateException("Group " + id.shortId() + " cannot " + action + " while " + state)` |
| `private void requireNotMember(MobId mob)` | Si ya es miembro: `IllegalArgumentException("Group " + id.shortId() + " already has member " + mob.value())` |
| `private void requireMembers(Set<MobId> mobs)` | Para el primero que no sea miembro (recorré con `for`): `IllegalArgumentException("Group " + id.shortId() + " has no member " + mob.value())` |
| `private ClosedPlan closedPlan(PlanEndReason reason, long tick, double fraction)` | `double success = reason == PlanEndReason.TARGET_DIED ? 1 : plan.successFraction(fraction);` devuelve `new ClosedPlan(plan.id(), plan.strategy(), plan.target(), reason, success, plan.damageDealt(), plan.startTick(), tick)` |

`requireMembers` recibe un `Set` y no una lista, porque en `startPlan` se valida el `keySet()` de los roles.

## Pruebas obligatorias

Datos comunes: `GROUP = new GroupId(new UUID(0, 3))` (`shortId` = `00000000`), mobs `new MobId(new UUID(1, n))`, jugador `ALICE = new PlayerId(new UUID(0, 10))`, `FLANK_STRATEGY = new StrategyId("FLANK")`. El grupo se arma con:

```java
new Group(GROUP, SelectionPolicyType.THOMPSON_SAMPLING,
    new GroupKnowledge(
        new GroupMemory(() -> TestSettings.defaults().memory()),
        new ThreatLedger(() -> TestSettings.defaults().target())));
```

**`PlanIdTest`**

| Prueba | Verificación |
| --- | --- |
| `shortIdJoinsGroupAndSequence` | `new PlanId(GROUP, 3).shortId()` es `00000000#3` |
| `sequenceStartsAtOne` | `new PlanId(GROUP, 0)`: mensaje `PlanId.sequence must be at least 1, got 0` |

**`PlanTest`.** `start = new PlanStart(FLANK_STRATEGY, ALICE, roles, 20, 100)` con `roles` = mob1 `PRESS`, mob2 `FLANK` (en ese orden).

| Prueba | Verificación |
| --- | --- |
| `startsWithNoDamageAndTheTargetJustSeen` | `Plan.start(new PlanId(GROUP, 1), start)`: daño 0, `startTick` 100, `lastTargetSeenTick` 100 |
| `damageAccumulates` | `withDamageDealt(3).withDamageDealt(2.5)`: daño 5,5; el plan original sigue en 0 |
| `rejectsNonPositiveDamage` | `withDamageDealt(0)`: mensaje `Plan.damage must be a positive number, got 0.0` |
| `successIsDamageOverHalfTheTargetMaxHealth` | Con fracción 0,5: daño 5 → 0,5; daño 12 → 1; sin daño → 0 |
| `roleChangeKeepsTheOrderOfTheRoles` | `withRole(mob1, RETREAT)`: `roles` es mob1 `RETREAT`, mob2 `FLANK`, en ese orden; el plan original sigue con mob1 `PRESS` |
| `withoutMemberDropsItsRole` | `withoutMember(mob2)`: `roleOf(mob2)` vacío y `roles` tiene solo mob1 |
| `targetSeenTickCannotGoBack` | `withTargetSeenAt(150)` y después `withTargetSeenAt(120)`: mensaje `Plan.lastTargetSeenTick must not go back, got 120 after 150` |
| `ageAndTimeSinceSeenAreMeasuredFromTheirTicks` | `withTargetSeenAt(150)`: en el tick 400, `ageTicks` 300 y `ticksSinceTargetSeen` 250 |
| `rolesCannotBeChangedFromOutside` | Cambiar el mapa original después de armar el `PlanStart` no cambia el plan; `plan.roles().put(...)` lanza `UnsupportedOperationException` |

**`ClosedPlanTest`**

| Prueba | Verificación |
| --- | --- |
| `rejectsSuccessAboveOne` | `success = 1.5`: mensaje `ClosedPlan.success must be between 0.0 and 1.0, got 1.5` |
| `rejectsEndBeforeStart` | `startTick = 100`, `endTick = 99`: mensaje `ClosedPlan.endTick must not be before startTick, got 99 < 100` |

**`GroupMembershipTest`**

| Prueba | Verificación |
| --- | --- |
| `membersGetIncreasingJoinOrders` | Agregar mob1 (zombie), mob2 (araña), mob3 (esqueleto): `joinOrder` 1, 2 y 3; `members()` en ese orden |
| `firstMemberIsTheLeader` | Con mob1 y mob2: `leader()` es mob1. Sin miembros: vacío |
| `rejectsDuplicateMembers` | Agregar mob1 dos veces: mensaje `Group 00000000 already has member 00000000-0000-0001-0000-000000000001` |
| `leaderLeavingPromotesTheOldestMemberAndEmitsLeaderDied` | mob1, mob2 y mob3; `removeMember(mob1, 500)`: `leader()` es mob2; `drainEvents()` es exactamente `[new LeaderDied(GROUP, mob1, Optional.of(mob2), 500)]` |
| `lastLeaderLeavingEmitsLeaderDiedWithoutSuccessor` | Solo mob1; `removeMember(mob1, 500)`: evento con `newLeader` vacío; `isEmpty()` es `true` |
| `nonLeaderLeavingEmitsNothing` | mob1 y mob2; `removeMember(mob2, 500)`: `drainEvents()` vacío |
| `removingAnUnknownMobDoesNothing` | `removeMember(new MobId(new UUID(9, 9)), 500)` no lanza nada y no cambia los miembros |
| `restoredMembersKeepTheirOrderAndTheNextJoinOrderContinues` | `restoreMember(new Member(mob3, ZOMBIE, 7))` y después `restoreMember(new Member(mob1, ZOMBIE, 2))`: `members()` es mob1, mob3; `leader()` es mob1; `addMember(mob2, ZOMBIE).joinOrder()` es 8 |
| `rejectsRestoringARepeatedJoinOrder` | Dos miembros distintos con `joinOrder` 2: mensaje `Group 00000000 already has join order 2` |
| `spiderTargetsBelongToSpidersAndLeaveWithThem` | mob2 es araña: `assignSpiderTarget(mob2, ALICE)`, `spiderTarget(mob2)` es ALICE; después de `removeMember(mob2, 500)`, vacío |
| `onlySpidersHaveSpiderTargets` | mob1 es zombie: `assignSpiderTarget(mob1, ALICE)`: mensaje `Group 00000000: member 00000000-0000-0001-0000-000000000001 is not a spider` |
| `drainingEventsEmptiesThePendingList` | Después de un `LeaderDied`, el segundo `drainEvents()` está vacío |

**`GroupLifecycleTest`.** Grupo con mob1 (zombie) y mob2 (araña); `start = new PlanStart(FLANK_STRATEGY, ALICE, Map.of(mob1, Role.PRESS), 20, 100)`.

| Prueba | Verificación |
| --- | --- |
| `newGroupIsObservingWithoutPlan` | `state()` es `OBSERVING`; `plan()` y `committedTarget()` vacíos; `planSequence()` 0 |
| `fullCycleWalksTheFourStates` | `beginPlanning` → `PLANNING`; `startPlan` → `EXECUTING` y devuelve un plan con `PlanId(GROUP, 1)`; `closePlan(TIMED_OUT, 700, 0.5)` → `EVALUATING`; `finishEvaluation` → `OBSERVING` con `plan()` vacío |
| `illegalTransitionsAreRejected` | En `OBSERVING`, `startPlan(start)`: `IllegalStateException` con mensaje `Group 00000000 cannot start a plan while OBSERVING`; en `OBSERVING`, `closePlan(...)`: mensaje `Group 00000000 cannot close a plan while OBSERVING`; en `PLANNING`, `beginPlanning()`: mensaje `Group 00000000 cannot begin planning while PLANNING` |
| `planIdsCountUpPerGroup` | Dos ciclos completos: el segundo plan tiene `PlanId(GROUP, 2)` y `planSequence()` es 2 |
| `startPlanRejectsRolesForNonMembers` | Roles con `new MobId(new UUID(9, 9))`: mensaje `Group 00000000 has no member 00000000-0000-0009-0000-000000000009`, y el estado sigue en `PLANNING` |
| `onlyDamageToThePlanTargetCounts` | En `EXECUTING`: `recordPlanDamage(ALICE, 3)`, `recordPlanDamage(BOB, 4)` (`BOB = new PlayerId(new UUID(0, 11))`): daño del plan 3 |
| `damageOutsideAPlanIsIgnored` | En `OBSERVING`, `recordPlanDamage(ALICE, 3)` no lanza nada |
| `closingComputesSuccessAndCommitsToTheTarget` | Daño 5, `closePlan(TIMED_OUT, 700, 0.5)`: devuelve `ClosedPlan(PlanId(GROUP, 1), FLANK, ALICE, TIMED_OUT, 0.5, 5.0, 100, 700)`; `committedTarget()` es ALICE; `drainEvents()` es `[new PlanClosed(eseClosedPlan)]` |
| `targetDeathIsAFullSuccess` | Sin daño, `closePlan(TARGET_DIED, 700, 0.5)`: `success` 1 |
| `planIsReadableDuringEvaluation` | Después de `closePlan`, `plan()` sigue presente y `state()` es `EVALUATING` |
| `assignRoleChangesOnlyThatMob` | En `EXECUTING`, `assignRole(mob1, RETREAT)`: `plan().get().roleOf(mob1)` es `RETREAT` |
| `removingAMemberDropsItsRoleFromThePlan` | En `EXECUTING`, `removeMember(mob1, 300)`: `plan().get().roleOf(mob1)` vacío |
| `markTargetSeenUpdatesThePlan` | En `EXECUTING`, `markTargetSeen(300)`: `ticksSinceTargetSeen(350)` es 50 |

**`DomainEventPublisherTest`.** Eventos: `leaderDied = new LeaderDied(GROUP, mob1, Optional.empty(), 10)` y un `PlanClosed` armado con un `ClosedPlan` válido.

| Prueba | Verificación |
| --- | --- |
| `deliversOnlyToSubscribersOfTheEventType` | Un suscriptor de `PlanClosed` y otro de `LeaderDied`; publicar `leaderDied`: solo el segundo lo recibe |
| `subscribersOfTheInterfaceReceiveEveryEvent` | Suscriptor de `DomainEvent.class`: recibe los dos eventos |
| `deliversInSubscriptionOrder` | Tres suscriptores que agregan `"a"`, `"b"` y `"c"` a una lista: queda `[a, b, c]` |
| `subscriberAddedDuringDeliveryWaitsForTheNextEvent` | Un suscriptor que, al recibir, suscribe a otro: el nuevo no recibe el evento en curso y sí el siguiente |
| `nestedPublishingHasALimit` | Un suscriptor de `LeaderDied` que vuelve a publicar el mismo evento: `IllegalStateException` cuyo mensaje empieza con `DomainEventPublisher: nested publish deeper than 8 levels for` |
| `publisherRecoversAfterAFailedDelivery` | Publicador nuevo con un suscriptor de `LeaderDied` que lanza `IllegalArgumentException` en sus primeras 8 llamadas y después suma 1 a un contador. Publicar 8 veces (cada una lanza `IllegalArgumentException`) y una novena: no lanza nada y el contador es 1. Prueba que `depth` vuelve a 0 aunque un suscriptor falle |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `leader()`, devolver el último de `members` | `leaderLeavingPromotesTheOldestMemberAndEmitsLeaderDied` |
| En `restoreMember`, no ordenar `members` | `restoredMembersKeepTheirOrderAndTheNextJoinOrderContinues` |
| En `closePlan`, no tratar `TARGET_DIED` como éxito completo | `targetDeathIsAFullSuccess` |
| En `publish`, sacar el `finally` (dejar `depth--` después de `deliverToAll`) | `publisherRecoversAfterAFailedDelivery` |
| En `startPlan`, quitar `requireMembers` | `startPlanRejectsRolesForNonMembers` |

## Procedimiento

1. Rama `wp-08-grupo-plan-eventos` desde `main`.
2. `PlanId`, los enums, `Member`, `PlanStart`, `Plan`, `ClosedPlan` y sus pruebas (`PlanIdTest`, `PlanTest`, `ClosedPlanTest`). `./gradlew spotlessApply build`. Commit: `feat(domain): add plan, plan id and closed plan`.
3. `DomainEvent`, `PlanClosed`, `LeaderDied`, `DomainEventPublisher` y `DomainEventPublisherTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add domain events and a bounded publisher`.
4. `GroupKnowledge`, `Group`, `GroupMembershipTest` y `GroupLifecycleTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add group aggregate with members, leader and plan lifecycle`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-08: group, plan and domain events`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si `-Xlint` marca una advertencia de tipos genéricos en `Subscription` (`unchecked` o `rawtypes`), podés agregar `@SuppressWarnings("unchecked")` **solo** sobre `deliver`, con un comentario de por qué es seguro (`type.cast` ya verifica el tipo). Avisalo en «Desvíos».

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Decidir cuándo empezar o cerrar un plan, repartir roles y elegir objetivos (cerebro, WP-10; estrategias, WP-09).
- `RoleAssignment` y `GroupDecision` (las arma el cerebro, WP-10).
- Escribir el resultado del plan en la memoria y publicar los eventos (`ClosePlan` y `TickGroups`, WP-13).
- El tamaño máximo del grupo y el reclutamiento (WP-12).
- Guardar y cargar grupos (WP-14 y WP-15).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, transiciones y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite (el publicador corta a los 8 niveles).
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 5 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
