# WP-13 — Casos de uso de combate

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia |
| Depende de | WP-12 (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-13-casos-de-uso-de-combate` |

## Objetivo

Los casos de uso que mueven el combate y alimentan la memoria:

1. `TickGroups`: una decisión del cerebro para un grupo, y la publicación de sus eventos.
2. `RecordOutcome`: el resultado de un intento de ataque va a la memoria del grupo y suma el daño al plan.
3. `RecordDamageTaken`: el daño que un jugador le hace a un miembro sube su amenaza.
4. `RecordPlayerDeath`: la muerte del objetivo cierra el plan con `TARGET_DIED`.
5. `ClosePlan`: suscriptor de `PlanClosed`; registra el resultado de la estrategia en la memoria del grupo.
6. `GroupEvents`: drena los eventos pendientes de un grupo y los publica.

Y cierra los dos riesgos del WP-12:

- **Eventos perdidos al disolver:** `DisbandGroup` publica los eventos pendientes del grupo antes de soltarlo.
- **Ventana de reagrupamiento:** `RemoveMember` recibe la causa de la salida; si el último miembro **muere** mientras el grupo reagrupa, llama a `RegroupWindow.recordWiped()` (CT-07).

**Alcance MVP:** `ClosePlan` escribe solo en la memoria del grupo, con peso 1. Observadores (peso 0,5) y memoria global por equipo son de la fase 2 (RF-07).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/application/` (todo: es del WP-12)
- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` (solo la firma de `decide` y cómo cierra planes) y `RegroupWindow.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Group.java`, `PlanLifecycle.java`
- `src/main/java/io/github/nicodoou/mobai/domain/event/DomainEventPublisher.java`, `PlanClosed.java`, `LeaderDied.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java`, `AttackObservation.java`, `StrategyObservation.java`, `RecordChange.java`, `AttackRecord.java`
- `src/main/java/io/github/nicodoou/mobai/domain/attack/AttackOutcome.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`, `BrainResult.java`
- `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java`, `TestSettings.java`
- `src/test/java/io/github/nicodoou/mobai/application/RemoveMemberTest.java`, `DisbandGroupTest.java`

## Reglas de negocio

1. **Un solo camino para el resultado de un plan.** Todo plan cerrado, lo cierre el cerebro o la muerte del objetivo, deja un `PlanClosed` en los eventos pendientes del grupo. `GroupEvents` lo publica y `ClosePlan`, suscripto a `PlanClosed` en el arranque, lo registra en la memoria. El adaptador **no** llama a `ClosePlan` con `BrainResult.closedPlan()`: eso es para las trazas. Así un plan nunca se registra dos veces.
2. **`TickGroups` va de a un grupo.** Recibe una foto y decide para ese grupo. El adaptador recorre los grupos y atrapa la excepción de cada uno por separado: un grupo que falla no deja sin decisión a los demás. Si el grupo de la foto ya no existe (se disolvió entre la foto y la decisión), devuelve vacío sin llamar al cerebro.
3. **Después de cada decisión** se publican los eventos pendientes del grupo, en el orden en que se generaron.
4. **`RecordOutcome`:**
   - Si el mob no está en un grupo (murió o salió antes de resolverse el intento), no hace nada.
   - Si el daño causado es mayor que 0, se suma al plan con `recordPlanDamage` (el dominio lo ignora si no es el objetivo del plan o el grupo no está ejecutando).
   - El crédito sale de `AttackOutcome.credit(partialHitWeight)` con la configuración **vigente**. Si es vacío (neutral), la memoria no cambia.
   - Si no, registra `AttackObservation(objetivo, ataque, crédito, tick)` y devuelve el `RecordChange`.
5. **`RecordDamageTaken`:** si el mob es miembro, `threat().recordDamage(atacante, daño, tick)` de su grupo. Si no, nada.
6. **`RecordPlayerDeath`:** cada grupo activo que está `EXECUTING` con ese jugador como objetivo cierra el plan con `TARGET_DIED` (éxito 1, lo calcula el dominio) en el tick de la muerte, y se publican sus eventos enseguida. El grupo queda en `EVALUATING`; el cerebro termina la evaluación en la próxima decisión. Los grupos se recorren en el orden de `ActiveGroups`.
7. **`ClosePlan`:** registra `StrategyObservation(objetivo, estrategia, éxito, 1.0, tick de cierre)` en la memoria del grupo del plan. Si el grupo ya no está activo, no hace nada.
8. **`RemoveMember` con causa.** `RemovalCause` es `DIED` o `DESPAWNED`. Si el grupo queda vacío, la causa es `DIED` y el grupo estaba `REGROUPING`, llama a `recordWiped()` antes de disolverlo. Un despawn no dice nada sobre la ventana: no la toca.
9. **`DisbandGroup`** saca el grupo de `ActiveGroups` y después publica sus eventos pendientes (por ejemplo, el `LeaderDied` del último miembro). Los suscriptores ya no lo encuentran activo, así que `ClosePlan` no escribe en una memoria que se descarta.
10. Un miembro que sale de un grupo que sigue vivo deja su `LeaderDied` pendiente, que se publica en la próxima decisión del grupo (como mucho 10 ticks).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/GroupEvents.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/TickGroups.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/AttackResolution.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecordOutcome.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/DamageTaken.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecordDamageTaken.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RecordPlayerDeath.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/ClosePlan.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/RemovalCause.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/RemoveMember.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/DisbandGroup.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java` (agregar `brain()`) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/RemoveMemberTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/DisbandGroupTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/GroupEventsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/TickGroupsTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecordOutcomeTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecordDamageTakenTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/RecordPlayerDeathTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/ClosePlanTest.java` |

El dominio no cambia.

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `GroupEvents.java`

```java
public final class GroupEvents {
  private final DomainEventPublisher publisher;

  public GroupEvents(DomainEventPublisher publisher) {
    this.publisher = Objects.requireNonNull(publisher, "GroupEvents.publisher");
  }

  public void publishPending(Group group) {
    group.drainEvents().forEach(publisher::publish);
  }
}
```

### `TickGroups.java`

```java
public final class TickGroups {
  private final ActiveGroups activeGroups;
  private final Brain brain;
  private final GroupEvents groupEvents;

  public TickGroups(ActiveGroups activeGroups, Brain brain, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "TickGroups.activeGroups");
    this.brain = Objects.requireNonNull(brain, "TickGroups.brain");
    this.groupEvents = Objects.requireNonNull(groupEvents, "TickGroups.groupEvents");
  }

  public Optional<BrainResult> execute(GroupSnapshot snapshot) {
    return activeGroups.group(snapshot.groupId()).map(group -> decide(group, snapshot));
  }

  private BrainResult decide(Group group, GroupSnapshot snapshot) {
    BrainResult result = brain.decide(group, snapshot);
    groupEvents.publishPending(group);
    return result;
  }
}
```

### `AttackResolution.java`

```java
public record AttackResolution(
    MobId mob, PlayerId target, Attack attack, AttackOutcome outcome, double damageDealt, long tick) {
  public AttackResolution {
    Objects.requireNonNull(mob, "AttackResolution.mob");
    Objects.requireNonNull(target, "AttackResolution.target");
    Objects.requireNonNull(attack, "AttackResolution.attack");
    Objects.requireNonNull(outcome, "AttackResolution.outcome");
    if (!(damageDealt >= 0) || !Double.isFinite(damageDealt)) {
      throw new IllegalArgumentException(
          "AttackResolution.damageDealt must be zero or positive, got " + damageDealt);
    }
    if (tick < 0) {
      throw new IllegalArgumentException(
          "AttackResolution.tick must be zero or positive, got " + tick);
    }
  }
}
```

### `RecordOutcome.java`

```java
public final class RecordOutcome {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;

  public RecordOutcome(ActiveGroups activeGroups, SettingsHolder settings) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordOutcome.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecordOutcome.settings");
  }

  public Optional<RecordChange> execute(AttackResolution resolution) {
    return activeGroups.groupOf(resolution.mob()).flatMap(group -> record(group, resolution));
  }

  private Optional<RecordChange> record(Group group, AttackResolution resolution) {
    addPlanDamage(group, resolution);
    OptionalDouble credit =
        resolution.outcome().credit(settings.current().memory().partialHitWeight());
    if (credit.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        group
            .memory()
            .recordAttack(
                new AttackObservation(
                    resolution.target(), resolution.attack(), credit.getAsDouble(), resolution.tick())));
  }

  // The plan rejects zero damage, and a blocked or missed attempt deals none.
  private static void addPlanDamage(Group group, AttackResolution resolution) {
    if (resolution.damageDealt() > 0) {
      group.lifecycle().recordPlanDamage(resolution.target(), resolution.damageDealt());
    }
  }
}
```

### `DamageTaken.java`

```java
public record DamageTaken(MobId mob, PlayerId attacker, double damage, long tick) {
  public DamageTaken {
    Objects.requireNonNull(mob, "DamageTaken.mob");
    Objects.requireNonNull(attacker, "DamageTaken.attacker");
    if (!(damage > 0) || !Double.isFinite(damage)) {
      throw new IllegalArgumentException("DamageTaken.damage must be a positive number, got " + damage);
    }
    if (tick < 0) {
      throw new IllegalArgumentException("DamageTaken.tick must be zero or positive, got " + tick);
    }
  }
}
```

### `RecordDamageTaken.java`

```java
public final class RecordDamageTaken {
  private final ActiveGroups activeGroups;

  public RecordDamageTaken(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordDamageTaken.activeGroups");
  }

  public Optional<GroupId> execute(DamageTaken damageTaken) {
    Optional<Group> group = activeGroups.groupOf(damageTaken.mob());
    group.ifPresent(
        found ->
            found.threat().recordDamage(damageTaken.attacker(), damageTaken.damage(), damageTaken.tick()));
    return group.map(Group::id);
  }
}
```

### `RecordPlayerDeath.java`

```java
public final class RecordPlayerDeath {
  private final ActiveGroups activeGroups;
  private final SettingsHolder settings;
  private final GroupEvents groupEvents;

  public RecordPlayerDeath(
      ActiveGroups activeGroups, SettingsHolder settings, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RecordPlayerDeath.activeGroups");
    this.settings = Objects.requireNonNull(settings, "RecordPlayerDeath.settings");
    this.groupEvents = Objects.requireNonNull(groupEvents, "RecordPlayerDeath.groupEvents");
  }

  public List<ClosedPlan> execute(PlayerId player, long tick) {
    return activeGroups.groups().stream()
        .filter(group -> isExecutingAgainst(group, player))
        .map(group -> closeTargetDied(group, tick))
        .toList();
  }

  private static boolean isExecutingAgainst(Group group, PlayerId player) {
    return group.lifecycle().state() == GroupState.EXECUTING
        && group.lifecycle().plan().map(Plan::target).filter(player::equals).isPresent();
  }

  private ClosedPlan closeTargetDied(Group group, long tick) {
    ClosedPlan closed =
        group
            .lifecycle()
            .closePlan(
                PlanEndReason.TARGET_DIED,
                tick,
                settings.current().plan().fullSuccessDamageFraction());
    groupEvents.publishPending(group);
    return closed;
  }
}
```

### `ClosePlan.java`

```java
public final class ClosePlan {
  // The group's own plan counts in full; observers (phase 2) will count less.
  private static final double OWN_PLAN_WEIGHT = 1.0;

  private final ActiveGroups activeGroups;

  public ClosePlan(ActiveGroups activeGroups) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "ClosePlan.activeGroups");
  }

  public Optional<RecordChange> execute(PlanClosed event) {
    return activeGroups.group(event.groupId()).map(group -> record(group, event.plan()));
  }

  private static RecordChange record(Group group, ClosedPlan plan) {
    return group
        .memory()
        .recordStrategy(
            new StrategyObservation(
                plan.target(), plan.strategy(), plan.success(), OWN_PLAN_WEIGHT, plan.endTick()));
  }
}
```

En el arranque (WP de bootstrap) se suscribe con `publisher.subscribe(PlanClosed.class, closePlan::execute)`. Las pruebas de este WP lo suscriben igual.

### `RemovalCause.java`

```java
public enum RemovalCause {
  DIED,
  DESPAWNED
}
```

### `RemoveMember.java` (reemplaza al del WP-12)

```java
public final class RemoveMember {
  private final ActiveGroups activeGroups;
  private final DisbandGroup disbandGroup;
  private final RegroupWindow regroupWindow;

  public RemoveMember(
      ActiveGroups activeGroups, DisbandGroup disbandGroup, RegroupWindow regroupWindow) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "RemoveMember.activeGroups");
    this.disbandGroup = Objects.requireNonNull(disbandGroup, "RemoveMember.disbandGroup");
    this.regroupWindow = Objects.requireNonNull(regroupWindow, "RemoveMember.regroupWindow");
  }

  public RemovalOutcome execute(MobId mobId, RemovalCause cause, long tick) {
    Optional<Group> group = activeGroups.leave(mobId, tick);
    if (group.isEmpty()) {
      return RemovalOutcome.NOT_A_MEMBER;
    }
    if (!group.get().roster().isEmpty()) {
      return RemovalOutcome.REMOVED;
    }
    recordWipeIfRegrouping(group.get(), cause);
    disbandGroup.execute(group.get().id());
    return RemovalOutcome.GROUP_DISBANDED;
  }

  // Only deaths say the window was too long; a despawn says nothing about it.
  private void recordWipeIfRegrouping(Group group, RemovalCause cause) {
    if (cause == RemovalCause.DIED && group.lifecycle().state() == GroupState.REGROUPING) {
      regroupWindow.recordWiped();
    }
  }
}
```

### `DisbandGroup.java` (reemplaza al del WP-12)

```java
public final class DisbandGroup {
  private final ActiveGroups activeGroups;
  private final GroupEvents groupEvents;

  public DisbandGroup(ActiveGroups activeGroups, GroupEvents groupEvents) {
    this.activeGroups = Objects.requireNonNull(activeGroups, "DisbandGroup.activeGroups");
    this.groupEvents = Objects.requireNonNull(groupEvents, "DisbandGroup.groupEvents");
  }

  // Removed first, so subscribers never write into a group that is going away.
  public Optional<Group> execute(GroupId groupId) {
    Optional<Group> removed = activeGroups.remove(groupId);
    removed.ifPresent(groupEvents::publishPending);
    return removed;
  }
}
```

### `BrainFixture.java`

Agregar, junto a `regroupWindow()`:

```java
  public Brain brain() {
    return brain;
  }
```

## Pruebas obligatorias

Convenciones (las del WP-12): `mob(n)` = `new MobId(new UUID(1, n))`, `player` = `new PlayerId(new UUID(2, 1))`, `otherPlayer` = `new PlayerId(new UUID(2, 2))`, `groupId(n)` = `new GroupId(new UUID(0, n))`, y `newGroup(long n)` como en el WP-12. Un grupo «ejecutando» se arma así: `mob(1)` (y los que diga la prueba) unidos con `activeGroups.join`, después `lifecycle().beginPlanning()` y `lifecycle().startPlan(new PlanStart(new StrategyId("DIRECT_ASSAULT"), player, Map.of(mob(1), Role.PRESS), 20.0, 100))`. Para capturar eventos: `List<DomainEvent> published = new ArrayList<>()` y `publisher.subscribe(DomainEvent.class, published::add)`.

### `GroupEventsTest` (2)

| Prueba | Verifica |
| --- | --- |
| `publishPendingDeliversEventsInOrder` | grupo con `mob(1)`, `mob(2)`, `mob(3)`; `removeMember(mob(1), 50)` y `removeMember(mob(2), 60)`; `publishPending`; `published` es exactamente `[LeaderDied(groupId(1), mob(1), Optional.of(mob(2)), 50), LeaderDied(groupId(1), mob(2), Optional.of(mob(3)), 60)]` |
| `publishPendingDrainsTheGroup` | después de `publishPending`, `group.drainEvents()` está vacío y un segundo `publishPending` no publica nada más (`published` sigue con 2) |

### `TickGroupsTest` (3)

Arranque: `BrainFixture fixture = BrainFixture.seeded(7)`, `List<MobSnapshot> mobs = fixture.catalogGroup()`, `activeGroups.add(fixture.group())`, `TickGroups(activeGroups, fixture.brain(), new GroupEvents(publisher))`.

| Prueba | Verifica |
| --- | --- |
| `tickDecidesForTheGroupOfTheSnapshot` | `execute(BrainFixture.snapshot(START_TICK, mobs, BrainFixture.alice()))` presente, con `decision().group()` = `GROUP_ID` y `decision().state()` = `EXECUTING` |
| `tickOfAGroupThatIsNoLongerActiveReturnsEmpty` | sin el `add`: `execute(...)` vacío y el grupo sigue `OBSERVING` |
| `tickPublishesThePlanClosedOfTheDecision` | primer `execute` en `START_TICK`; segundo en `START_TICK + 700` (más que los 600 de duración máxima); `published` tiene exactamente un `PlanClosed`, con `plan().reason()` = `TIMED_OUT`, y `fixture.group().drainEvents()` queda vacío |

### `RecordOutcomeTest` (8)

Arranque: `ActiveGroups`, `SettingsHolder(TestSettings.defaults())`, grupo 1 con `mob(1)` zombie.

| Prueba | Verifica |
| --- | --- |
| `hitRecordsFullCreditForTheTarget` | `Hit`, ataque `ZOMBIE_FRONT_STRIKE`, daño 0, tick 100: `after()` = `AttackRecord(1.0, 1.0, 100)` |
| `partialRecordsThePartialWeightInForce` | `replace` con `MemorySettings(12_000, 0.7, 0.25)` (el resto, defaults); `Partial`: `after().successes()` = 0.25 y `attempts()` = 1.0 |
| `missRecordsAnAttemptWithoutSuccess` | `Miss`: `after()` = `AttackRecord(0.0, 1.0, 100)` |
| `neutralRecordsNothing` | `Neutral` con cualquier `NeutralCause`: resultado vacío y `memory().attackRecords()` vacío |
| `outcomeOfALooseMobRecordsNothing` | `mob(7)` (no es miembro): vacío |
| `damageAgainstThePlanTargetAddsToThePlan` | grupo ejecutando contra `player`; `Hit` con daño 6.0: `plan().orElseThrow().damageDealt()` = 6.0 |
| `damageAgainstAnotherPlayerDoesNotTouchThePlan` | mismo grupo; `Hit` contra `otherPlayer` con daño 6.0: `damageDealt()` = 0.0 |
| `zeroDamageDoesNotTouchThePlan` | grupo ejecutando; `Miss` contra `player` con daño 0: no lanza y `damageDealt()` = 0.0 |

### `RecordDamageTakenTest` (3)

| Prueba | Verifica |
| --- | --- |
| `damageToAMemberRaisesTheThreatOfTheAttacker` | `mob(1)` en el grupo 1; `DamageTaken(mob(1), player, 4.0, 100)`: devuelve `groupId(1)` y `threat().threatOf(player, 100)` = 4.0 |
| `damageToALooseMobRecordsNothing` | `mob(7)`: vacío |
| `damageTakenRejectsZeroDamage` | `new DamageTaken(mob(1), player, 0.0, 100)` lanza con el mensaje exacto `"DamageTaken.damage must be a positive number, got 0.0"` |

### `RecordPlayerDeathTest` (5)

Arranque: `ActiveGroups`, `SettingsHolder(TestSettings.defaults())`, `DomainEventPublisher` con `ClosePlan(activeGroups)` suscripto a `PlanClosed` y el capturador de eventos, `GroupEvents`.

| Prueba | Verifica |
| --- | --- |
| `deathOfThePlanTargetClosesThePlanAsTargetDied` | grupo 1 ejecutando; `execute(player, 160)` devuelve un `ClosedPlan` con `reason()` `TARGET_DIED`, `success()` 1.0 y `endTick()` 160; el grupo queda `EVALUATING` |
| `deathRecordsTheStrategySuccessThroughClosePlan` | mismo arranque; después de `execute(player, 160)`, `memory().strategyRecords().get(player).get(new StrategyId("DIRECT_ASSAULT"))` = `AttackRecord(1.0, 1.0, 160)` |
| `deathOfAnotherPlayerLeavesThePlanOpen` | `execute(otherPlayer, 160)` vacío y el grupo sigue `EXECUTING` |
| `deathClosesThePlanOfEveryGroupTargetingThePlayer` | grupos 1 (`mob(1)`) y 2 (`mob(2)`, plan con `Map.of(mob(2), Role.PRESS)`) ejecutando contra `player`; `execute(player, 160)` devuelve 2 planes cerrados, el del grupo 1 primero (`id().group()`) |
| `deathIgnoresAGroupThatIsNotExecuting` | grupo 1 con `mob(1)` en `OBSERVING`: `execute(player, 160)` vacío y `published` vacío |

### `ClosePlanTest` (2)

| Prueba | Verifica |
| --- | --- |
| `planClosedRecordsTheStrategyWithFullWeight` | grupo 1 activo; `execute(new PlanClosed(new ClosedPlan(new PlanId(groupId(1), 1), new StrategyId("FLANK"), player, PlanEndReason.TIMED_OUT, 0.4, 3.0, 100, 700)))`: `after()` = `AttackRecord(0.4, 1.0, 700)` |
| `planClosedOfAGroupThatIsNoLongerActiveRecordsNothing` | mismo evento sin el grupo en `ActiveGroups`: vacío |

### `RemoveMemberTest` (4 existentes + 5 nuevas)

Las 4 pruebas existentes conservan nombre y verificaciones; cambian el armado (`DisbandGroup(activeGroups, new GroupEvents(publisher))`, `RemoveMember(activeGroups, disbandGroup, regroupWindow)` con `regroupWindow = new RegroupWindow(() -> TestSettings.defaults().retreat())`) y la llamada pasa a `execute(mob, RemovalCause.DIED, 50)`.

Un grupo «reagrupando»: grupo ejecutando (con `mob(1)` y los que diga la prueba en el plan), `lifecycle().closePlan(PlanEndReason.GROUP_RETREATED, 200, 0.5)` y `lifecycle().finishEvaluation()`.

| Prueba nueva | Verifica |
| --- | --- |
| `lastMemberLeaderDiedReachesSubscribersWhenTheGroupDisbands` | solo `mob(1)`; `execute(mob(1), DIED, 50)`: `published` = `[LeaderDied(groupId(1), mob(1), Optional.empty(), 50)]` |
| `lastDeathWhileRegroupingShortensTheRegroupWindow` | grupo reagrupando con solo `mob(1)`; `execute(mob(1), DIED, 300)`: `regroupWindow.currentTicks()` = 550 |
| `lastDespawnWhileRegroupingKeepsTheRegroupWindow` | igual con `DESPAWNED`: 600 |
| `lastDeathOutsideRegroupingKeepsTheRegroupWindow` | solo `mob(1)` en `OBSERVING`, `DIED`: 600 |
| `deathWhileRegroupingWithSurvivorsKeepsTheRegroupWindow` | grupo reagrupando con `mob(1)` y `mob(2)` (los dos en el plan con `PRESS`); `execute(mob(2), DIED, 300)`: `REMOVED` y 600 |

### `DisbandGroupTest` (2 existentes + 1 nueva)

Las existentes cambian solo el armado. Nueva:

| Prueba | Verifica |
| --- | --- |
| `disbandPublishesThePendingEventsAfterRemovingTheGroup` | grupo con `mob(1)` y `mob(2)`; `removeMember(mob(1), 50)` directo sobre el grupo (queda un `LeaderDied` pendiente); suscriptor que, al recibir cada evento, anota `activeGroups.group(groupId(1)).isPresent()`; `execute(groupId(1))`: se publicó un `LeaderDied` y lo anotado es `false` |

Total: **37 pruebas nuevas** (2 + 3 + 8 + 3 + 5 + 2 + 5 + 1) y 6 existentes adaptadas.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `RecordOutcome.addPlanDamage`, sacar el `if` (llamar siempre) | `Plan.withDamageDealt(0.0)` lanza `IllegalArgumentException` | `zeroDamageDoesNotTouchThePlan` |
| 2 | En `RecordOutcome.record`, reemplazar `settings.current().memory().partialHitWeight()` por `0.5` | `successes()` 0.5 en vez de 0.25 | `partialRecordsThePartialWeightInForce` |
| 3 | En `RemoveMember.recordWipeIfRegrouping`, sacar `cause == RemovalCause.DIED &&` | ventana 550 en vez de 600 | `lastDespawnWhileRegroupingKeepsTheRegroupWindow` |
| 4 | En `DisbandGroup.execute`, publicar antes de `remove` (`group(groupId).ifPresent(groupEvents::publishPending)` primero) | lo anotado es `true` | `disbandPublishesThePendingEventsAfterRemovingTheGroup` |
| 5 | En `TickGroups.decide`, sacar `groupEvents.publishPending(group)` | `published` vacío | `tickPublishesThePlanClosedOfTheDecision` |

## Procedimiento

1. Rama `wp-13-casos-de-uso-de-combate` desde `main`.
2. `GroupEvents`, `RemovalCause`, los nuevos `DisbandGroup` y `RemoveMember`, con `GroupEventsTest` y las pruebas adaptadas y nuevas de `RemoveMemberTest` y `DisbandGroupTest`. Commit: `feat: publish group events on disband and record wipes while regrouping`.
3. `ClosePlan` y `RecordPlayerDeath` con sus pruebas. Commit: `feat: close plans on target death and record strategy results`.
4. `AttackResolution`, `RecordOutcome`, `DamageTaken`, `RecordDamageTaken` con sus pruebas. Commit: `feat: record attack outcomes and damage taken`.
5. `BrainFixture.brain()`, `TickGroups` con su prueba. Commit: `feat: add tick groups use case`.
6. Pruebas que muerden (sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-13: combat use cases`, esperar el check `build` en verde, informe.

## Correcciones permitidas

1. Formato de Spotless.
2. Si en `tickPublishesThePlanClosedOfTheDecision` la semilla 7 hace que el primer `execute` no deje el grupo en `EXECUTING` (por ejemplo, si el cerebro no elige objetivo), probá las semillas 1 a 20 en orden y usá la primera que sí; avisalo en «Desvíos» con la semilla elegida.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Observadores, memoria global y testigos (fase 2).
- Unir grupos y escape (`MergeGroups`, `RecordEscape`).
- Armar la suscripción de `ClosePlan` en el plugin (bootstrap) y el recorrido de grupos con su `catch` (adaptadores).
- Persistir `RegroupWindow` y el número de plan (WP-14).
- Tocar el dominio.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 37 pruebas nuevas y las 6 adaptadas, con sus nombres exactos, en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Ninguna clase pasa de 20 métodos públicos.
- [ ] Build, cobertura y CI en verde.
