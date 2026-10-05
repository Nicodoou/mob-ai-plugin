# WP-10A — Piezas del cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-07, WP-08 y WP-09 |
| Modelo | Sonnet |
| Rama | `wp-10a-piezas-del-cerebro` |

El WP-10 original pasaba las 400 líneas de producción, así que se dividió: este WP arma las piezas sueltas y los datos de salida; el WP-10B arma `Brain`, que las coordina.

## Objetivo

- **Fin de plan:** `PlanEndDetector` decide si el plan en curso terminó por objetivo perdido, por tiempo o por retirada del grupo, y si el objetivo está a la vista.
- **Retirada:** `RetreatRule` decide si un mob tiene poca vida y tiene que pasar a `RETREAT`.
- **Ataque sugerido:** `AttackSuggester` elige qué ataque le sugiere el cerebro a cada mob, con la política de selección y la memoria.
- **Datos de salida del cerebro:** la decisión por grupo (`GroupDecision`, con un `RoleAssignment` por mob) y su explicación (`DecisionTrace`).
- **`Plan` recuerda con cuántos miembros empezó,** para poder contar a los muertos en la regla de retirada.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer, salvo `Plan.java`, que se modifica):
   - `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java`, `PlanStart.java`, `Role.java`, `GroupState.java`, `PlanEndReason.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java`, `MobKind.java`, `MobId.java`, `PlayerId.java`, `GroupId.java`, `PlanId.java`, `StrategyId.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `MobSnapshot.java`, `PlayerSnapshot.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/selection/SelectionPolicy.java`, `SelectionCandidate.java`, `SelectionResult.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (solo las firmas públicas)
   - `src/main/java/io/github/nicodoou/mobai/domain/target/TargetSelection.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/PlanSettings.java`
   - `src/test/java/io/github/nicodoou/mobai/domain/group/PlanTest.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `GroupSnapshotBuilder.java`, `MobSnapshotBuilder.java`, `PlayerSnapshotBuilder.java`

## Reglas de negocio

**Fin de un plan** (catálogo, «Cuándo termina un plan»). Se evalúa en este orden y gana la primera que se cumpla:

| Orden | Condición | Motivo |
| --- | --- | --- |
| — | El objetivo murió | `TARGET_DIED`. **No lo decide este detector:** la muerte llega por evento (D12) y la cierra el caso de uso del WP-13 |
| 1 | Pasaron `targetLostTicks` (200) o más desde la última vez que se vio al objetivo | `TARGET_LOST` |
| 2 | El plan duró `maxPlanDurationTicks` (600) o más | `TIMED_OUT` |
| 3 | Más de la mitad de los mobs con los que empezó el plan murieron o están en `RETREAT`: `2 × (empezaron − siguen + en retirada) > empezaron` | `GROUP_RETREATED` |

- **«Empezaron»** = cantidad de roles al empezar el plan (los mobs de la foto). **«Siguen»** = roles que quedan en el plan (un miembro que se va sale de los roles, WP-08). Un plan que empezó sin mobs nunca termina por retirada.
- **Objetivo a la vista:** está en la foto, con vida mayor a 0, y algún mob de la foto está a `targetLostDistanceBlocks` (32) o menos. El cerebro llama a `markTargetSeen` solo cuando se cumple.

**Retirada** (D8). Un mob pasa a `RETREAT` si `vida ≤ retreatHealthFraction × vida máxima` (30 %).

**Ataque sugerido** (D9).
- Araña: siempre `SPIDER_BITE`, sin pasar por la política: el catálogo dice que la araña no elige su ataque, y así no consume números al azar.
- Zombie y esqueleto: los 3 ataques de su tipo (`Attack.forKind`, en ese orden), cada uno con puntaje base 1 (todos empiezan iguales; la memoria decide) y la estimación `memory.attackEstimate(objetivo, ataque, tick)`. Elige la política del grupo.
- A un mob en `RETREAT` no se le sugiere ataque; eso lo resuelve el cerebro, no el sugeridor.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Modificar | `domain/group/Plan.java` (agregar `startingMembers`) |
| Modificar (prueba) | `domain/group/PlanTest.java` (una prueba nueva) |
| Crear | `domain/decision/RoleAssignment.java`, `GroupDecision.java`, `StrategyCheck.java`, `AttackChoice.java`, `DecisionTrace.java`, `BrainResult.java` |
| Crear | `domain/brain/PlanEndDetector.java`, `RetreatRule.java`, `AttackContext.java`, `AttackSuggester.java` |
| Crear (prueba) | `domain/decision/GroupDecisionTest.java` |
| Crear (prueba) | `domain/brain/PlanEndDetectorTest.java`, `RetreatRuleTest.java`, `AttackSuggesterTest.java` |

## Especificación

### 1. `Plan`: con cuántos miembros empezó

- Agregar el componente `int startingMembers` **al final** del record (después de `damageDealt`).
- `Plan.start` lo llena con `start.roles().size()`.
- Los tres métodos `copyWith…` lo copian sin cambios.
- Ningún otro cambio. Ningún archivo fuera de `Plan.java` construye un `Plan` con `new`.

### 2. Datos de salida (`domain.decision`)

Todos son records. Cada componente de referencia lleva `requireNonNull` con mensaje `"<Record>.<componente>"`; las listas se copian con `List.copyOf`.

```java
/** What one mob does until the next decision. */
public record RoleAssignment(
    MobId mob, Role role, Optional<PlayerId> target, Optional<Attack> suggestedAttack) {}

public record GroupDecision(
    GroupId group,
    long tick,
    GroupState state,
    Optional<PlanId> plan,
    Optional<StrategyId> strategy,
    Optional<PlayerId> target,
    List<RoleAssignment> assignments) {}

/** Whether a strategy could run with this composition, and its requirement in words. */
public record StrategyCheck(StrategyId strategy, boolean viable, String requirement) {}

/** The attack suggested to a mob; the selection is empty when no policy was asked (spiders). */
public record AttackChoice(MobId mob, Attack attack, Optional<SelectionResult<Attack>> selection) {}

/** Why the brain decided what it decided; returned as data, never logged by the domain. */
public record DecisionTrace(
    GroupId group,
    long tick,
    GroupState stateBefore,
    GroupState stateAfter,
    Optional<PlanId> plan,
    Optional<TargetSelection> targetSelection,
    List<StrategyCheck> strategyChecks,
    Optional<SelectionResult<StrategyId>> strategySelection,
    List<MobId> newlyRetreating,
    Optional<PlanEndReason> endReason,
    List<AttackChoice> attackChoices) {}

public record BrainResult(
    GroupDecision decision, DecisionTrace trace, Optional<ClosedPlan> closedPlan) {}
```

Validaciones extra:
- `GroupDecision`: `tick < 0` → `IllegalArgumentException("GroupDecision.tick must be zero or positive, got " + tick)`. Mob repetido en `assignments` (recorré con un `for` y un `HashSet`) → `IllegalArgumentException("GroupDecision.assignments has a duplicate mob " + mob.value())`.
- `DecisionTrace`: `tick < 0` → `IllegalArgumentException("DecisionTrace.tick must be zero or positive, got " + tick)`.

### 3. `domain.brain.PlanEndDetector`

```java
public final class PlanEndDetector {
  public PlanEndDetector(Supplier<PlanSettings> settings) { ... }   // "PlanEndDetector.settings"

  public Optional<PlanEndReason> detect(Plan plan, long tick) { ... }
  public boolean isTargetVisible(GroupSnapshot snapshot, PlayerId target) { ... }
}
```

| Método | Cuerpo |
| --- | --- |
| `detect` | `PlanSettings current = settings.get();` en orden: si `isTargetLost(plan, tick, current)` → `TARGET_LOST`; si `isTimedOut(plan, tick, current)` → `TIMED_OUT`; si `hasRetreated(plan)` → `GROUP_RETREATED`; si no, vacío |
| `private static boolean isTargetLost(Plan plan, long tick, PlanSettings settings)` | `plan.ticksSinceTargetSeen(tick) >= settings.targetLostTicks()` |
| `private static boolean isTimedOut(Plan plan, long tick, PlanSettings settings)` | `plan.ageTicks(tick) >= settings.maxPlanDurationTicks()` |
| `private static boolean hasRetreated(Plan plan)` | `int gone = plan.startingMembers() - plan.roles().size();` `return 2L * (gone + retreatingCount(plan)) > plan.startingMembers();` |
| `private static long retreatingCount(Plan plan)` | Cantidad de valores `Role.RETREAT` en `plan.roles()` |
| `isTargetVisible` | `snapshot.player(target).filter(player -> player.health() > 0).filter(player -> isNearAnyMob(snapshot, player)).isPresent()` |
| `private boolean isNearAnyMob(GroupSnapshot snapshot, PlayerSnapshot player)` | `double limit = settings.get().targetLostDistanceBlocks();` devuelve `snapshot.mobs().stream().anyMatch(mob -> mob.position().distanceTo(player.pose().position()) <= limit)` |

### 4. `domain.brain.RetreatRule`

```java
public final class RetreatRule {
  public RetreatRule(Supplier<PlanSettings> settings) { ... }   // "RetreatRule.settings"

  public boolean shouldRetreat(MobSnapshot mob) {
    return mob.health() <= settings.get().retreatHealthFraction() * mob.maxHealth();
  }
}
```

### 5. `domain.brain.AttackContext` y `AttackSuggester`

```java
/** What the suggester needs from the current decision. */
public record AttackContext(GroupMemory memory, SelectionPolicy policy, long tick) {}
// requireNonNull de memory y policy: "AttackContext.<componente>"

public final class AttackSuggester {
  // Every attack starts equal; the memory multiplier is what tells them apart.
  private static final double BASE_SCORE = 1.0;

  public AttackChoice suggest(MobSnapshot mob, PlayerId target, AttackContext context) { ... }
}
```

| Método | Cuerpo |
| --- | --- |
| `suggest` | Si `mob.kind() == MobKind.SPIDER`, devuelve `new AttackChoice(mob.id(), Attack.SPIDER_BITE, Optional.empty())`. Si no: `SelectionResult<Attack> result = context.policy().choose(candidates(mob.kind(), target, context));` devuelve `new AttackChoice(mob.id(), result.chosen(), Optional.of(result))` |
| `private static List<SelectionCandidate<Attack>> candidates(MobKind kind, PlayerId target, AttackContext context)` | `Attack.forKind(kind).stream().map(attack -> new SelectionCandidate<>(attack, BASE_SCORE, context.memory().attackEstimate(target, attack, context.tick()))).toList()` |

## Pruebas obligatorias

Settings: `TestSettings.defaults().plan()` (duración máxima 600, distancia de objetivo perdido 32, tiempo de objetivo perdido 200, retirada 0,3). Plan de prueba: `Plan.start(new PlanId(new GroupId(new UUID(0, 3)), 1), new PlanStart(new StrategyId("FLANK"), ALICE, roles, 20, 100))`, donde `roles` tiene 4 mobs `m1` a `m4` (`new MobId(new UUID(1, n))`), todos `PRESS`, y `ALICE = new PlayerId(new UUID(0, 10))`.

**`PlanTest`** (prueba nueva)

| Prueba | Verificación |
| --- | --- |
| `remembersHowManyMembersItStartedWith` | El plan de 4 roles tiene `startingMembers()` 4; después de `withoutMember(m1)` sigue en 4 |

**`PlanEndDetectorTest`**

| Prueba | Verificación |
| --- | --- |
| `planInProgressHasNoEndReason` | `detect(plan, 299)` vacío |
| `targetUnseenForTargetLostTicksIsLost` | `detect(plan, 300)` es `TARGET_LOST` (200 ticks sin verlo) |
| `seeingTheTargetResetsTheLostCount` | `detect(plan.withTargetSeenAt(250), 300)` vacío |
| `planTimesOutAtMaxDuration` | Con `withTargetSeenAt(690)`: `detect(…, 699)` vacío y `detect(…, 700)` es `TIMED_OUT` |
| `lostWinsOverTimeout` | `detect(plan, 700)` es `TARGET_LOST` (las dos se cumplen) |
| `moreThanHalfRetreatingEndsThePlan` | Con `withTargetSeenAt(150)`, en el tick 150: con `m1` y `m2` en `RETREAT`, vacío (2 de 4 no es más de la mitad); con `m1`, `m2` y `m3`, `GROUP_RETREATED` |
| `deadMembersCountAsRetreated` | Con `withTargetSeenAt(150)`, en el tick 150: `withoutMember(m1)` y `m2` en `RETREAT` → vacío (2 de 4); además `withoutMember(m3)` → `GROUP_RETREATED` (3 de 4) |
| `planWithoutMembersNeverRetreats` | Plan con `roles` vacío, en el tick 100: vacío |
| `targetWithinLostDistanceOfAMobIsVisible` | ALICE en `(0, 64, 0)` y un mob en `(0, 64, 32)`: visible. Con el mob en `(0, 64, 32.5)`: no visible |
| `absentOrDeadTargetIsNotVisible` | Foto sin ALICE: no visible. ALICE con vida 0 y un mob al lado: no visible |
| `targetIsNotVisibleWithoutMobs` | ALICE en la foto, sin mobs: no visible |

**`RetreatRuleTest`**

| Prueba | Verificación |
| --- | --- |
| `retreatsAtThirtyPercentOrLess` | Vida 6 de 20: `true`. Vida 6,5 de 20: `false` |
| `usesEachMobsMaxHealth` | Araña con vida 4,8 de 16: `true`; con 5 de 16: `false` |

**`AttackSuggesterTest`.** La política es una clase de prueba, `RecordingPolicy implements SelectionPolicy`, que guarda los candidatos recibidos y la cantidad de llamadas, y elige el candidato de mayor `estimate().mean()` (el primero ante un empate). `memory = new GroupMemory(() -> TestSettings.defaults().memory())`, tick 1000.

| Prueba | Verificación |
| --- | --- |
| `spiderAlwaysBitesWithoutAskingThePolicy` | Araña: `attack()` es `SPIDER_BITE`, `selection()` vacío y la política tuvo 0 llamadas |
| `zombieChoosesAmongItsThreeAttacks` | Zombie: los candidatos son `ZOMBIE_FRONT_STRIKE`, `ZOMBIE_FLANK_STRIKE`, `ZOMBIE_PATIENT_STRIKE` en ese orden, cada uno con `baseScore` 1 |
| `skeletonChoosesAmongItsThreeShots` | Esqueleto: `SKELETON_DIRECT_SHOT`, `SKELETON_LEAD_SHOT`, `SKELETON_OPPORTUNISTIC_SHOT` |
| `estimatesComeFromTheMemoryForThatTarget` | Antes: 5 veces `memory.recordAttack(new AttackObservation(ALICE, ZOMBIE_FLANK_STRIKE, 1.0, 900))`. Sugerencia a un zombie contra ALICE: el candidato `ZOMBIE_FLANK_STRIKE` tiene una estimación igual (`isEqualTo`) a `memory.attackEstimate(ALICE, ZOMBIE_FLANK_STRIKE, 1000)` y la política lo eligió; `attack()` es `ZOMBIE_FLANK_STRIKE` y `selection()` trae el mismo resultado de la política (`isSameAs`). Contra BOB (`new PlayerId(new UUID(0, 11))`), sin datos, elige `ZOMBIE_FRONT_STRIKE` (empate, el primero) |

**`GroupDecisionTest`**

| Prueba | Verificación |
| --- | --- |
| `rejectsDuplicateAssignments` | Dos `RoleAssignment` para `m1`: mensaje `GroupDecision.assignments has a duplicate mob 00000000-0000-0001-0000-000000000001` |
| `assignmentsCannotBeChangedFromOutside` | Cambiar la lista original después de armar la decisión no la cambia; `assignments().add(...)` lanza `UnsupportedOperationException` |

### Pruebas que muerden (obligatorio, va en el informe)

Calculadas antes de escribirlas:

| Cambio temporal | Tiene que fallar | Por qué cambia |
| --- | --- | --- |
| En `isTargetLost`, `>` en vez de `>=` | `targetUnseenForTargetLostTicksIsLost` | 200 > 200 es falso: no se pierde en el tick 300 |
| En `hasRetreated`, `>=` en vez de `>` | `moreThanHalfRetreatingEndsThePlan` | Con 2 de 4, 4 ≥ 4 termina el plan |
| En `hasRetreated`, no contar a los que se fueron (`gone = 0`) | `deadMembersCountAsRetreated` | Con 2 que se fueron y 1 en retirada da 1 de 4: no termina |
| En `detect`, evaluar `isTimedOut` antes que `isTargetLost` | `lostWinsOverTimeout` | En el tick 700 daría `TIMED_OUT` |
| En `isNearAnyMob`, `<` en vez de `<=` | `targetWithinLostDistanceOfAMobIsVisible` | A 32 bloques justos dejaría de estar a la vista |
| En `suggest`, no tratar aparte a la araña | `spiderAlwaysBitesWithoutAskingThePolicy` | La política recibiría una llamada |

## Procedimiento

1. Rama `wp-10a-piezas-del-cerebro` desde `main`.
2. `Plan` con `startingMembers` y la prueba nueva de `PlanTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): remember how many members a plan started with`.
3. Los 6 records de `domain.decision` y `GroupDecisionTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add brain decision and trace records`.
4. `PlanEndDetector`, `RetreatRule` y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add plan end detector and retreat rule`.
5. `AttackContext`, `AttackSuggester` y `AttackSuggesterTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add attack suggester`.
6. Pruebas que muerden.
7. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
8. Push, PR `WP-10A: brain building blocks`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- `Brain` y la coordinación de todas estas piezas (WP-10B).
- Cerrar el plan por la muerte del objetivo (WP-13).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, orden de las reglas y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite; ninguna clase con más de 20 métodos públicos.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 6 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 4 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
