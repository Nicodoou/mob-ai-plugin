# WP-08B — División de `Group` y estado de reagrupamiento

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-08 y WP-09 (mergeados) |
| Modelo | Sonnet |
| Rama | `wp-08b-division-de-group` |

## Objetivo

1. **Regla automática:** ninguna clase de producción puede tener más de 20 métodos públicos «de comportamiento». Hoy `Group` tiene 25.
2. **Dividir `Group`** en tres piezas con una responsabilidad cada una:
   - `GroupRoster`: miembros, líder y objetivos de araña;
   - `PlanLifecycle`: estados, plan en curso, compromiso y reagrupamiento;
   - `PendingEvents`: los eventos que esperan a la aplicación.

   `Group` queda como fachada chica que las arma y coordina lo que toca a las dos, que es sacar un miembro.
3. **Estado nuevo `REGROUPING`** (decisión D26): un plan cerrado por `GROUP_RETREATED` lleva, al terminar la evaluación, a reagrupar en vez de a observar.
4. **El plan recuerda sus roles iniciales** (D25): un mob que se recupera de la retirada vuelve al rol con el que empezó el plan, y la cantidad de mobs con los que empezó sale de ese mapa.

Es una refactorización: **el comportamiento existente no cambia**. Las pruebas existentes se conservan con sus nombres y sus verificaciones; solo cambia el camino de las llamadas (por ejemplo, `group.addMember(...)` pasa a ser `group.roster().addMember(...)`).

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente:
   - `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `Plan.java`, `GroupState.java`, `PlanStart.java`, `Member.java`, `GroupKnowledge.java`
   - `src/test/java/io/github/nicodoou/mobai/domain/group/GroupMembershipTest.java`, `GroupLifecycleTest.java`, `PlanTest.java`
   - `src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java`

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Modificar (prueba) | `ArchitectureTest.java` (una regla nueva) |
| Modificar | `domain/group/GroupState.java` (un valor nuevo) |
| Modificar | `domain/group/Plan.java` (un componente y dos métodos) |
| Crear | `domain/group/PendingEvents.java`, `GroupRoster.java`, `PlanLifecycle.java` |
| Modificar | `domain/group/Group.java` (pasa a ser la fachada) |
| Modificar (prueba) | `domain/group/GroupMembershipTest.java`, `GroupLifecycleTest.java`, `PlanTest.java` |

## Especificación

### 1. Regla de ArchUnit

En `ArchitectureTest`, agregá exactamente esto. Imports nuevos: `com.tngtech.archunit.core.domain.JavaClass`, `com.tngtech.archunit.lang.ArchCondition`, `com.tngtech.archunit.lang.ConditionEvents`, `com.tngtech.archunit.lang.SimpleConditionEvent`, `java.lang.reflect.Modifier`, `java.lang.reflect.RecordComponent`, `java.util.Arrays`, `java.util.HashSet`, `java.util.Set`.

```java
  private static final int MAX_PUBLIC_METHODS = 20;

  @Test
  void classesHaveAtMostTwentyPublicMethods() {
    classes()
        .should(haveAtMostPublicMethods(MAX_PUBLIC_METHODS))
        .because("a class with a large public surface is doing too much")
        .allowEmptyShould(true)
        .check(MAIN_CLASSES);
  }

  private static ArchCondition<JavaClass> haveAtMostPublicMethods(int maximum) {
    return new ArchCondition<>("have at most " + maximum + " public methods") {
      @Override
      public void check(JavaClass javaClass, ConditionEvents events) {
        long count = countPublicBehavior(javaClass.reflect());
        if (count > maximum) {
          events.add(
              SimpleConditionEvent.violated(
                  javaClass,
                  javaClass.getName()
                      + " has "
                      + count
                      + " public methods (maximum "
                      + maximum
                      + ")"));
        }
      }
    };
  }

  // Record accessors and the methods the compiler writes for records and enums are not behavior.
  private static long countPublicBehavior(Class<?> type) {
    Set<String> generated = generatedMethodNames(type);
    return Arrays.stream(type.getDeclaredMethods())
        .filter(method -> Modifier.isPublic(method.getModifiers()))
        .filter(method -> !method.isSynthetic() && !method.isBridge())
        .filter(method -> !generated.contains(method.getName()))
        .count();
  }

  private static Set<String> generatedMethodNames(Class<?> type) {
    Set<String> names =
        new HashSet<>(Set.of("equals", "hashCode", "toString", "values", "valueOf"));
    if (type.isRecord()) {
      Arrays.stream(type.getRecordComponents())
          .map(RecordComponent::getName)
          .forEach(names::add);
    }
    return names;
  }
```

### 2. `GroupState` y `Plan`

- `GroupState`: agregar `REGROUPING` **al final** (después de `EVALUATING`).
- `Plan`: agregar el componente `Map<MobId, Role> startingRoles` **al final** del record.
  - Constructor compacto: `requireNonNull(startingRoles, "Plan.startingRoles")` y `startingRoles = Collections.unmodifiableMap(new LinkedHashMap<>(startingRoles));`.
  - `Plan.start` lo llena con `start.roles()`.
  - Los tres `copyWith…` lo copian sin cambios.
  - Métodos nuevos:
    - `public int startingMembers()`: `startingRoles.size()`;
    - `public Optional<Role> startingRoleOf(MobId mob)`: `Optional.ofNullable(startingRoles.get(mob))`.

### 3. `PendingEvents` (package-private)

```java
final class PendingEvents {
  private final List<DomainEvent> events = new ArrayList<>();

  void add(DomainEvent event) { ... }          // requireNonNull(event, "PendingEvents.event")
  List<DomainEvent> drain() { ... }            // copia, vacía y devuelve la copia
}
```

### 4. `GroupRoster`

```java
public final class GroupRoster {
  GroupRoster(GroupId groupId) { ... }                       // package-private

  public Member addMember(MobId mobId, MobKind kind)
  public void restoreMember(Member member)
  public List<Member> members()
  public Optional<Member> member(MobId mobId)
  public boolean isEmpty()
  public Optional<MobId> leader()
  public Optional<PlayerId> spiderTarget(MobId spider)
  public void assignSpiderTarget(MobId spider, PlayerId target)

  void remove(MobId mobId)                                   // package-private
  void requireMembers(Set<MobId> mobs)                       // package-private
}
```

**Movés** el código de `Group` sin cambiarlo: los campos `members`, `spiderTargets` y `nextJoinOrder`, y los métodos `addMember`, `restoreMember`, `members`, `member`, `isEmpty`, `leader`, `spiderTarget`, `assignSpiderTarget`, `requireNotMember` y `requireMembers`. Los mensajes siguen empezando con `"Group " + groupId.shortId()`, con el `groupId` que recibe el constructor.

- `remove(mobId)`: `members.removeIf(member -> member.id().equals(mobId));` y `spiderTargets.remove(mobId);`.
- **Separá la validación de `restoreMember`** en `private void requireFreeJoinOrder(long joinOrder)` (el chequeo del número de ingreso repetido). Así `restoreMember` coordina: `requireNotMember`, `requireFreeJoinOrder`, agregar, ordenar y actualizar el contador.

### 5. `PlanLifecycle`

```java
public final class PlanLifecycle {
  PlanLifecycle(GroupId groupId, GroupRoster roster, PendingEvents events) { ... }   // package-private

  public GroupState state()
  public Optional<Plan> plan()
  public Optional<PlayerId> committedTarget()
  public long planSequence()
  public OptionalLong regroupStartTick()
  public void beginPlanning()
  public Plan startPlan(PlanStart start)
  public void recordPlanDamage(PlayerId player, double damage)
  public void markTargetSeen(long tick)
  public void assignRole(MobId mobId, Role role)
  public ClosedPlan closePlan(PlanEndReason reason, long tick, double fullSuccessDamageFraction)
  public void finishEvaluation()
  public void finishRegrouping()

  void dropMember(MobId mobId)                               // package-private
}
```

**Movés** el código de `Group` sin cambiarlo: los campos `state`, `plan`, `committedTarget` y `planSequence`, y los métodos `state`, `plan`, `committedTarget`, `planSequence`, `beginPlanning`, `startPlan`, `recordPlanDamage`, `markTargetSeen`, `assignRole`, `closePlan`, `finishEvaluation`, `requireState` y `closedPlan`.
- `startPlan` y `assignRole` validan con `roster.requireMembers(...)`.
- `closePlan` agrega el `PlanClosed` con `events.add(...)`.

Cambios de comportamiento (los únicos de este WP):

| Elemento | Comportamiento |
| --- | --- |
| Campos nuevos | `private PlanEndReason lastEndReason;` y `private long lastEndTick;` (los llena `closePlan`), y `private long regroupStartTick = NO_REGROUP;` con `private static final long NO_REGROUP = -1;` |
| `closePlan` | Además de lo que hacía: `lastEndReason = reason; lastEndTick = tick;` |
| `finishEvaluation` | `requireState(EVALUATING, "finish evaluation")`; `plan = null`; si `lastEndReason == PlanEndReason.GROUP_RETREATED`: `state = REGROUPING` y `regroupStartTick = lastEndTick`; si no, `state = OBSERVING` |
| `finishRegrouping` | `requireState(REGROUPING, "finish regrouping")`; `state = OBSERVING`; `regroupStartTick = NO_REGROUP` |
| `regroupStartTick()` | `regroupStartTick == NO_REGROUP ? OptionalLong.empty() : OptionalLong.of(regroupStartTick)` |
| `dropMember(mobId)` | `plan = plan == null ? null : plan.withoutMember(mobId);` |

### 6. `Group` (fachada)

```java
public final class Group {
  public Group(GroupId id, SelectionPolicyType policy, GroupKnowledge knowledge)

  public GroupId id()
  public SelectionPolicyType policy()
  public GroupMemory memory()
  public ThreatLedger threat()
  public GroupRoster roster()
  public PlanLifecycle lifecycle()
  public void removeMember(MobId mobId, long tick)
  public List<DomainEvent> drainEvents()
}
```

- Campos: `id`, `policy`, `knowledge`, `private final PendingEvents events = new PendingEvents();`, `private final GroupRoster roster;` y `private final PlanLifecycle lifecycle;`. El constructor arma `roster = new GroupRoster(id)` y `lifecycle = new PlanLifecycle(id, roster, events)`.
- `removeMember(mobId, tick)` coordina: si `roster.member(mobId)` está vacío, no hace nada. Si no: `boolean wasLeader = roster.leader().filter(mobId::equals).isPresent();` `roster.remove(mobId);` `lifecycle.dropMember(mobId);` y si `wasLeader`, `events.add(new LeaderDied(id, mobId, roster.leader(), tick))`.
- `drainEvents()`: `events.drain()`.
- `GroupRoster` no tiene un `remove` público: sacar un miembro siempre pasa por `Group.removeMember`, que es quien también lo saca del plan y emite `LeaderDied`.

## Pruebas obligatorias

**Pruebas existentes:** `GroupMembershipTest`, `GroupLifecycleTest` y `PlanTest` conservan **todos** sus nombres y verificaciones. Solo cambiá las llamadas al camino nuevo:
- `group.addMember` → `group.roster().addMember`, y lo mismo para todo lo de miembros, líder y arañas;
- `group.beginPlanning` → `group.lifecycle().beginPlanning`, y lo mismo para todo lo del ciclo;
- `group.removeMember` y `group.drainEvents` quedan en `group`.

**Pruebas nuevas:**

| Clase | Prueba | Verificación |
| --- | --- | --- |
| `ArchitectureTest` | `classesHaveAtMostTwentyPublicMethods` | La regla de la sección 1 |
| `PlanTest` | `remembersTheRolesItStartedWith` | Plan con m1 `PRESS` y m2 `FLANK`: `startingMembers()` es 2. Después de `withRole(m1, RETREAT)`, `startingRoleOf(m1)` sigue siendo `PRESS`. Después de `withoutMember(m2)`, `startingMembers()` sigue en 2 y `startingRoleOf(m2)` es `FLANK` |
| `GroupLifecycleTest` | `groupRetreatLeadsToRegrouping` | En `EXECUTING`, `closePlan(GROUP_RETREATED, 700, 0.5)` y `finishEvaluation()`: estado `REGROUPING`, `regroupStartTick()` es 700 y `plan()` vacío. Después `finishRegrouping()`: estado `OBSERVING` y `regroupStartTick()` vacío |
| `GroupLifecycleTest` | `otherEndReasonsSkipRegrouping` | `closePlan(TARGET_LOST, 700, 0.5)` y `finishEvaluation()`: estado `OBSERVING`, `regroupStartTick()` vacío |
| `GroupLifecycleTest` | `regroupingTransitionsAreGuarded` | En `OBSERVING`, `finishRegrouping()`: `IllegalStateException` con mensaje `Group 00000000 cannot finish regrouping while OBSERVING`. En `REGROUPING`, `beginPlanning()`: mensaje `Group 00000000 cannot begin planning while REGROUPING` |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar | Por qué cambia |
| --- | --- | --- |
| Correr la regla nueva **antes** de dividir `Group` (paso 2 del procedimiento) | `classesHaveAtMostTwentyPublicMethods`, con el mensaje `io.github.nicodoou.mobai.domain.group.Group has 25 public methods (maximum 20)` | Es el estado actual. Copiá el mensaje en el informe |
| En `finishEvaluation`, ir siempre a `OBSERVING` | `groupRetreatLeadsToRegrouping` | No entraría nunca en `REGROUPING` |
| En `Plan.withRole`, cambiar también `startingRoles` | `remembersTheRolesItStartedWith` | El rol inicial de m1 pasaría a `RETREAT` |
| En `Group.removeMember`, no llamar a `lifecycle.dropMember` | `removingAMemberDropsItsRoleFromThePlan` (prueba existente) | El rol del mob quedaría en el plan |

## Procedimiento

1. Rama `wp-08b-division-de-group` desde `main`.
2. Agregá la regla a `ArchitectureTest` y corré `./gradlew test --tests "io.github.nicodoou.mobai.ArchitectureTest"`: **tiene que fallar** con el mensaje de `Group`. Si falla otra clase además de `Group`, frená y reportá con la lista. Todavía no commitees.
3. `GroupState`, `Plan` y la prueba nueva de `PlanTest`.
4. `PendingEvents`, `GroupRoster`, `PlanLifecycle` y la nueva `Group`. Actualizá las llamadas de las pruebas existentes y agregá las tres pruebas nuevas de `GroupLifecycleTest`.
5. `./gradlew spotlessApply build`: tiene que pasar entero, con la regla nueva incluida. Commit: `refactor(domain): split group into roster and plan lifecycle with a regrouping state`.
6. Pruebas que muerden 2, 3 y 4 (la 1 ya se vio en el paso 2).
7. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
8. Push, PR `WP-08B: split Group and add the regrouping state`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si `javaClass.reflect()` falla por una clase que no se puede cargar, frená y reportá el error exacto: no cambies la regla.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Cuándo entrar y salir de `REGROUPING`, la curación y la ventana adaptativa (WP-10A y WP-10B).
- Cualquier otro cambio de comportamiento en `Group`, `Plan` o sus pruebas.

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] La regla falló antes de la división con el mensaje de `Group` y pasa después.
- [ ] Las pruebas existentes conservan nombres y verificaciones; solo cambió el camino de las llamadas.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite.
- [ ] Todas las pruebas obligatorias pasan; las 4 pruebas que muerden fallaron y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 1 commit con el mensaje indicado.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
