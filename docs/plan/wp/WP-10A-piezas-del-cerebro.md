# WP-10A — Piezas del cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-07, WP-08, WP-08B y WP-09 |
| Modelo | Sonnet |
| Rama | `wp-10a-piezas-del-cerebro` |

El WP-10 original pasaba las 400 líneas de producción, así que se dividió: este WP arma las piezas sueltas y los datos de salida; el WP-10B arma `Brain`, que las coordina.

## Objetivo

- **Fin de plan:** `PlanEndDetector` decide si el plan en curso terminó por objetivo perdido, por tiempo o por retirada del grupo, y si el objetivo está a la vista.
- **Retirada táctica** (CT-07 en `docs/plan/cambios-tecnicos.md`):
  - `RetreatRule` decide si un mob pasa a `RETREAT`, si ya se recuperó para volver, y si puede curarse (lejos de los jugadores).
  - `RegroupRule` decide cuándo termina el reagrupamiento del grupo.
  - `RegroupWindow` es la ventana de reagrupamiento global y adaptativa.
  - `RetreatSettings` es su configuración.
- **Ataque sugerido:** `AttackSuggester` elige qué ataque le sugiere el cerebro a cada mob, con la política de selección y la memoria.
- **Datos de salida del cerebro:** la decisión por grupo (`GroupDecision`, con un `RoleAssignment` por mob) y su explicación (`DecisionTrace`).

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer, salvo los archivos que la tabla «Archivos» marca para modificar):
   - `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java` (tiene `startingMembers()` desde el WP-08B), `PlanStart.java`, `Role.java`, `GroupState.java`, `PlanEndReason.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java`, `MobKind.java`, `MobId.java`, `PlayerId.java`, `GroupId.java`, `PlanId.java`, `StrategyId.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `MobSnapshot.java`, `PlayerSnapshot.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/selection/SelectionPolicy.java`, `SelectionCandidate.java`, `SelectionResult.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (solo las firmas públicas)
   - `src/main/java/io/github/nicodoou/mobai/domain/target/TargetSelection.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/PlanSettings.java`, `MobAiSettings.java`, `SettingsChecks.java`
   - `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java`
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

**Retirada táctica** (D8 y CT-07):
- Un mob pasa a `RETREAT` si `vida ≤ PlanSettings.retreatHealthFraction × vida máxima` (30 %).
- Vuelve si `vida ≥ RetreatSettings.recoveryHealthFraction × vida máxima` (60 %). Entre los dos umbrales mantiene lo que tenga.
- Puede curarse si ningún jugador vivo de la foto está a menos de `healSafeDistanceBlocks` (12). Un jugador justo a 12 bloques no lo impide.

**Reagrupamiento** (CT-07). En `REGROUPING`, se evalúa en este orden:
1. `RECOVERED`: más de la mitad de los mobs de la foto tiene 60 % o más (`2 × recuperados > mobs`).
2. `WINDOW_EXPIRED`: pasaron `RegroupWindow.currentTicks()` ticks o más desde que empezó.

Sin mobs en la foto solo puede vencer la ventana.

**Ventana de reagrupamiento** (CT-07). Global: una sola instancia para todo el server, inyectada.
- Empieza en `regroupInitialTicks` (600).
- `recordWiped()` (un grupo murió entero reagrupándose) le resta `regroupStepTicks` (50).
- `recordSurvived()` (un grupo terminó de reagruparse vivo) le suma 50.
- Siempre se lee acotada entre `regroupMinTicks` (200) y `regroupMaxTicks` (1.200), con los valores de configuración vigentes: un `/mobai reload` que achica el rango la acota en la próxima lectura.

**Ataque sugerido** (D9).
- Araña: siempre `SPIDER_BITE`, sin pasar por la política: el catálogo dice que la araña no elige su ataque, y así no consume números al azar.
- Zombie y esqueleto: los 3 ataques de su tipo (`Attack.forKind`, en ese orden), cada uno con puntaje base 1 (todos empiezan iguales; la memoria decide) y la estimación `memory.attackEstimate(objetivo, ataque, tick)`. Elige la política del grupo.
- A un mob en `RETREAT` no se le sugiere ataque; eso lo resuelve el cerebro, no el sugeridor.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/settings/RetreatSettings.java` |
| Modificar | `domain/settings/MobAiSettings.java` (sección nueva y una validación cruzada) |
| Modificar (prueba) | `testsupport/TestSettings.java`, `domain/settings/SettingsValidationTest.java` |
| Crear | `domain/decision/RoleAssignment.java`, `GroupDecision.java`, `StrategyCheck.java`, `AttackChoice.java`, `DecisionTrace.java`, `BrainResult.java` |
| Crear | `domain/brain/PlanEndDetector.java`, `RetreatRule.java`, `RegroupEndReason.java`, `RegroupWindow.java`, `RegroupRule.java`, `AttackContext.java`, `AttackSuggester.java` |
| Crear (prueba) | `domain/decision/GroupDecisionTest.java` |
| Crear (prueba) | `domain/brain/PlanEndDetectorTest.java`, `RetreatRuleTest.java`, `RegroupWindowTest.java`, `RegroupRuleTest.java`, `AttackSuggesterTest.java` |

## Especificación

### 1. `RetreatSettings` y `MobAiSettings`

```java
public record RetreatSettings(
    double recoveryHealthFraction,
    double healSafeDistanceBlocks,
    long regroupInitialTicks,
    long regroupMinTicks,
    long regroupMaxTicks,
    long regroupStepTicks) { ... }
```

Validación con `SettingsChecks`, en este orden:
1. `requireBetween("RetreatSettings.recoveryHealthFraction", recoveryHealthFraction, 0, 1)`
2. `requirePositive("RetreatSettings.healSafeDistanceBlocks", healSafeDistanceBlocks)`
3. `requireAtLeast("RetreatSettings.regroupMinTicks", regroupMinTicks, 1)`
4. `requireNotAbove("RetreatSettings.regroupMinTicks", regroupMinTicks, "RetreatSettings.regroupMaxTicks", regroupMaxTicks)`
5. `requireBetween("RetreatSettings.regroupInitialTicks", regroupInitialTicks, regroupMinTicks, regroupMaxTicks)`
6. `requireAtLeast("RetreatSettings.regroupStepTicks", regroupStepTicks, 1)`

`MobAiSettings`:
- Agregá el componente `RetreatSettings retreat` **al final** (después de `debug`), con `requireNonNull(retreat, "MobAiSettings.retreat")`.
- Después de los `requireNonNull`, la validación cruzada: si `retreat.recoveryHealthFraction() <= plan.retreatHealthFraction()`, lanzá `IllegalArgumentException("RetreatSettings.recoveryHealthFraction must exceed PlanSettings.retreatHealthFraction, got " + retreat.recoveryHealthFraction() + " <= " + plan.retreatHealthFraction())`. Sin ese margen, un mob cambiaría de rol en cada decisión.

`TestSettings.defaults()` suma `new RetreatSettings(0.6, 12.0, 600, 200, 1200, 50)` al final. En `SettingsValidationTest`, agregá el argumento nuevo (`defaults.retreat()`) a cada `new MobAiSettings(...)` que ya existe.

### 2. Datos de salida (`domain.decision`)

Todos son records. Cada componente de referencia lleva `requireNonNull` con mensaje `"<Record>.<componente>"`; las listas se copian con `List.copyOf`.

```java
/** What one mob does until the next decision. */
public record RoleAssignment(
    MobId mob,
    Role role,
    Optional<PlayerId> target,
    Optional<Attack> suggestedAttack,
    boolean recovering) {}   // recovering: the plugin heals this mob until the next decision

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
    List<MobId> returningFromRetreat,
    Optional<PlanEndReason> endReason,
    Optional<RegroupEndReason> regroupEnd,
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

### 4. `RetreatRule`, `RegroupEndReason`, `RegroupWindow` y `RegroupRule` (`domain.brain`)

```java
public final class RetreatRule {
  public RetreatRule(Supplier<PlanSettings> plan, Supplier<RetreatSettings> retreat) { ... }
  // requireNonNull: "RetreatRule.plan", "RetreatRule.retreat"

  public boolean shouldRetreat(MobSnapshot mob) { ... }
  public boolean shouldReturn(MobSnapshot mob) { ... }
  public boolean canRecover(MobSnapshot mob, GroupSnapshot snapshot) { ... }
}
```

| Método | Cuerpo |
| --- | --- |
| `shouldRetreat` | `mob.health() <= plan.get().retreatHealthFraction() * mob.maxHealth()` |
| `shouldReturn` | `mob.health() >= retreat.get().recoveryHealthFraction() * mob.maxHealth()` |
| `canRecover` | `double safe = retreat.get().healSafeDistanceBlocks();` devuelve `snapshot.players().stream().filter(player -> player.health() > 0).noneMatch(player -> player.pose().position().distanceTo(mob.position()) < safe)` |

```java
public enum RegroupEndReason { RECOVERED, WINDOW_EXPIRED }

/** The regroup time shared by every group; it learns from how regrouping ends. */
public final class RegroupWindow {
  public RegroupWindow(Supplier<RetreatSettings> settings) { ... }   // "RegroupWindow.settings"

  public long currentTicks() { ... }
  public void recordWiped() { ... }
  public void recordSurvived() { ... }
  public void restore(long savedTicks) { ... }
}
```

| Elemento | Comportamiento |
| --- | --- |
| Campo | `private long ticks;`, que el constructor inicializa con `settings.get().regroupInitialTicks()` |
| `currentTicks()` | `bounded(ticks)` |
| `recordWiped()` | `ticks = bounded(currentTicks() - settings.get().regroupStepTicks());` |
| `recordSurvived()` | `ticks = bounded(currentTicks() + settings.get().regroupStepTicks());` |
| `restore(savedTicks)` | `ticks = savedTicks;` (se acota al leer; lo usa la carga desde disco del WP-15) |
| `private long bounded(long value)` | `RetreatSettings current = settings.get(); return Math.clamp(value, current.regroupMinTicks(), current.regroupMaxTicks());` |

```java
public final class RegroupRule {
  public RegroupRule(Supplier<RetreatSettings> settings, RegroupWindow window) { ... }
  // requireNonNull: "RegroupRule.settings", "RegroupRule.window"

  public Optional<RegroupEndReason> detect(GroupSnapshot snapshot, long regroupStartTick) { ... }
}
```

| Método | Cuerpo |
| --- | --- |
| `detect` | Si `hasRecoveredMajority(snapshot)` → `RECOVERED`; si `snapshot.tick() - regroupStartTick >= window.currentTicks()` → `WINDOW_EXPIRED`; si no, vacío |
| `private boolean hasRecoveredMajority(GroupSnapshot snapshot)` | `long recovered = snapshot.mobs().stream().filter(this::isRecovered).count();` devuelve `2 * recovered > snapshot.mobs().size()` |
| `private boolean isRecovered(MobSnapshot mob)` | `mob.health() >= settings.get().recoveryHealthFraction() * mob.maxHealth()` |

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

Settings: `TestSettings.defaults()`. Plan: duración máxima 600, distancia de objetivo perdido 32, tiempo de objetivo perdido 200 y retirada 0,3. Retirada: vuelta 0,6, distancia segura 12 y ventana 600 (de 200 a 1.200, paso 50). Plan de prueba: `Plan.start(new PlanId(new GroupId(new UUID(0, 3)), 1), new PlanStart(new StrategyId("FLANK"), ALICE, roles, 20, 100))`, donde `roles` tiene 4 mobs `m1` a `m4` (`new MobId(new UUID(1, n))`), todos `PRESS`, y `ALICE = new PlayerId(new UUID(0, 10))`.

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
| `returnsAtSixtyPercentOrMore` | `shouldReturn`: vida 12 de 20 `true`; 11,9 de 20 `false`; araña con 9,6 de 16 `true` |
| `betweenThresholdsNothingChanges` | Vida 8 de 20: `shouldRetreat` y `shouldReturn` son `false` |
| `recoversOnlyAwayFromLivingPlayers` | Mob en `(0, 64, 0)`. Jugador vivo a 12 bloques: `canRecover` `true`. A 11,9: `false`. Jugador muerto (vida 0) a 1 bloque: `true`. Sin jugadores: `true` |

**`RegroupWindowTest`.** `settings` es un `AtomicReference<RetreatSettings>` con los valores por defecto.

| Prueba | Verificación |
| --- | --- |
| `startsAtTheInitialValue` | `currentTicks()` es 600 |
| `wipesShortenAndSurvivalsLengthen` | `recordWiped()`: 550. Después dos `recordSurvived()`: 650 |
| `staysWithinItsBounds` | 9 `recordWiped()` seguidos: 200 (el noveno no baja de 200). Después 25 `recordSurvived()`: 1.200 |
| `restoredValueIsBoundedOnRead` | `restore(900)`: 900. `restore(5000)`: 1.200 |
| `boundsFollowTheCurrentSettings` | `restore(900)` y después `settings.set(new RetreatSettings(0.6, 12.0, 600, 200, 800, 50))`: `currentTicks()` es 800 |
| `rejectsInconsistentSettings` | `new RetreatSettings(0.6, 12.0, 600, 1300, 1200, 50)`: mensaje `RetreatSettings.regroupMinTicks must not exceed RetreatSettings.regroupMaxTicks, got 1300.0 > 1200.0` |

**`RegroupRuleTest`.** Ventana nueva (600). El reagrupamiento empezó en el tick 1000.

| Prueba | Verificación |
| --- | --- |
| `recoveredMajorityEndsRegrouping` | Foto en el tick 1100 con mobs de vida 12, 12 y 5 (de 20): `RECOVERED` |
| `halfRecoveredIsNotEnough` | Tick 1100, vida 12 y 5: vacío |
| `windowExpiresAfterItsTicks` | Mobs con vida 5: tick 1599 vacío; tick 1600 `WINDOW_EXPIRED` |
| `recoveryWinsOverTheWindow` | Tick 1600, mobs con vida 12 y 12: `RECOVERED` |
| `withoutMobsOnlyTheWindowCanEndIt` | Sin mobs: tick 1100 vacío; tick 1600 `WINDOW_EXPIRED` |

**`SettingsValidationTest`** (prueba nueva)

| Prueba | Verificación |
| --- | --- |
| `recoveryMustExceedTheRetreatThreshold` | Defaults con `new RetreatSettings(0.3, 12.0, 600, 200, 1200, 50)`: mensaje `RetreatSettings.recoveryHealthFraction must exceed PlanSettings.retreatHealthFraction, got 0.3 <= 0.3` |

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
| En `shouldReturn`, `>` en vez de `>=` | `returnsAtSixtyPercentOrMore` | 12 > 12 es falso |
| En `canRecover`, `<=` en vez de `<` | `recoversOnlyAwayFromLivingPlayers` | El jugador a 12 justos impediría curarse |
| En `hasRecoveredMajority`, `>=` en vez de `>` | `halfRecoveredIsNotEnough` | 1 de 2 alcanzaría |
| En `RegroupWindow`, quitar el acotado (`bounded` devuelve `value` sin cambios) | `staysWithinItsBounds` | Nueve restas darían 150 |

## Procedimiento

1. Rama `wp-10a-piezas-del-cerebro` desde `main`.
2. `RetreatSettings`, `MobAiSettings`, `TestSettings` y `SettingsValidationTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add retreat settings`.
3. Los 6 records de `domain.decision` y `GroupDecisionTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add brain decision and trace records`.
4. `PlanEndDetector`, `RetreatRule`, `RegroupEndReason`, `RegroupWindow`, `RegroupRule` y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add plan end, retreat and regroup rules with an adaptive regroup window`.
5. `AttackContext`, `AttackSuggester` y `AttackSuggesterTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add attack suggester`.
6. Pruebas que muerden.
7. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
8. Push, PR `WP-10A: brain building blocks`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- `Brain` y la coordinación de todas estas piezas (WP-10B).
- Cerrar el plan por la muerte del objetivo y llamar a `recordWiped` cuando un grupo muere reagrupándose (WP-13).
- Guardar y cargar la ventana (WP-14 y WP-15).
- Mover al mob al margen y curarlo en el server (WP-22).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, orden de las reglas y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite; ninguna clase con más de 20 métodos públicos.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 10 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 4 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
