# WP-22A — Sin planes con el grupo en retirada y formación de flanqueo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | Puerta E5 (pasada) |
| Modelo | Sonnet |
| Rama | `wp-22a-retirada-y-formacion` |

## Objetivo

Dos cambios del dominio que necesita el WP-22B (goals de flanqueo y retirada):

1. **CT-13: no abrir planes con el grupo todavía en retirada.** En la puerta E5, una araña sola con poca vida abrió y cerró cinco planes seguidos con `GROUP_RETREATED` y éxito 0, sin atacar: la memoria aprendía fracasos de planes que nunca se jugaron. Ahora:
   - un grupo que observa y está «en retirada» (más de la mitad de los mobs presentes con 30 % de vida o menos) **no planifica**: pasa a reagrupar;
   - si la ventana de reagrupamiento vence y el grupo sigue en retirada, **sigue reagrupando** con la ventana reiniciada, y la ventana adaptativa no aprende nada de eso.
2. **CT-14: formación de flanqueo.** `CombatGeometry.flankPoint` les da el mismo punto a todos los flanqueadores de un mismo lado, y chocan. `FlankFormation` reparte un punto distinto a cada uno: el primero de cada lado a 135° del frente del jugador, el segundo a 165° y los demás a 180° (justo atrás).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `RetreatRule.java`, `RegroupRule.java`, `TraceDraft.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java`, `LifecycleCapture.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/DecisionTrace.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`, `PlayerPose.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Vec3.java`, `MobId.java`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainRegroupingTest.java`, `BrainObservingTest.java`, `RetreatRuleTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/group/GroupLifecycleTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java`, `MobSnapshotBuilder.java`, `GroupSnapshotBuilder.java`

## Reglas de negocio

1. **Grupo en retirada:** más de la mitad de los mobs **presentes en la foto** tienen 30 % de vida o menos (`RetreatRule.shouldRetreat`). La mitad justa no alcanza. Un grupo sin mobs en la foto no está en retirada.
2. **Al observar** (CT-13): si el grupo encontró objetivo y está en retirada, no planifica. Pasa a `REGROUPING` con la ventana arrancando en el tick de la foto, sin abrir plan (el número de plan no avanza y no se publica ningún evento). Las órdenes son las del reagrupamiento: todos en `RETREAT`, alejándose del objetivo comprometido (`committedTarget`, que puede estar vacío si el grupo nunca cerró un plan: en ese caso las órdenes van sin objetivo).
3. **Al reagrupar** (CT-13): las salidas de CT-07 no cambian (`RECOVERED` y `WINDOW_EXPIRED` terminan el reagrupamiento y la ventana sube 50), **salvo** un caso: si la ventana vence y el grupo sigue en retirada, el grupo sigue reagrupando con la ventana reiniciada en el tick de la foto, y la ventana adaptativa **no** cambia (ni sube ni baja: el reagrupamiento no terminó).
4. **La traza lo dice:** `DecisionTrace.stillRetreated` es `true` en las decisiones de las reglas 2 y 3, y `false` en todas las demás. En la regla 3, `regroupEnd` sigue diciendo `WINDOW_EXPIRED` (la ventana venció) y `stillRetreated` explica por qué el grupo no salió.
5. **Formación de flanqueo** (CT-14), para un objetivo y la lista de sus flanqueadores con sus posiciones:
   - el **lado** de cada flanqueador es el de `CombatGeometry` (signo del determinante entre la mirada del jugador y la posición del mob respecto del jugador, en horizontal);
   - en cada lado, los flanqueadores se ordenan del **más rodeado al menos rodeado** (ángulo respecto de la mirada del jugador, de mayor a menor); a igual ángulo, por `MobId` ascendente;
   - el que queda en el puesto `n` (desde 0) va a `min(135° + 30° · n, 180°)` de la mirada, de su lado, a `distanceBlocks` del jugador y a su misma altura;
   - el primero de cada lado da exactamente el mismo punto que `CombatGeometry.flankPoint`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/RetreatRule.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/TraceDraft.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/DecisionTrace.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/FlankQuery.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/FlankFormation.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/RetreatRuleTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainRegroupingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/group/GroupLifecycleTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/geometry/FlankFormationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/debug/DebugLogTest.java` y `TraceLevelsTest.java` (solo el argumento nuevo de `DecisionTrace`) |

**Métodos públicos nuevos en clases existentes:** `RetreatRule.isGroupRetreated` (4 en total: es la regla de retirada aplicada al grupo) y `PlanLifecycle.regroupWithoutPlan` y `restartRegroupWindow` (18 en total: son transiciones del ciclo, que viven ahí).

## Especificación

### `RetreatRule.java`: un método nuevo

```java
  // CT-13: a group in this state would close any new plan at once with GROUP_RETREATED.
  public boolean isGroupRetreated(GroupSnapshot snapshot) {
    long retreating = snapshot.mobs().stream().filter(this::shouldRetreat).count();
    return 2 * retreating > snapshot.mobs().size();
  }
```

### `PlanLifecycle.java`: dos métodos nuevos y una validación

```java
  // CT-13: a group too hurt to fight regroups without opening a plan.
  public void regroupWithoutPlan(long tick) {
    requireState(GroupState.OBSERVING, "regroup without a plan");
    requireTick(tick);
    state = GroupState.REGROUPING;
    regroupStartTick = tick;
  }

  public void restartRegroupWindow(long tick) {
    requireState(GroupState.REGROUPING, "restart the regroup window");
    requireTick(tick);
    regroupStartTick = tick;
  }

  private static void requireTick(long tick) {
    if (tick < 0) {
      throw new IllegalArgumentException("PlanLifecycle.tick must be zero or positive, got " + tick);
    }
  }
```

`committedTarget`, `lastEndReason`, `lastEndTick` y `planSequence` no se tocan. El estado que queda es válido para `LifecycleCapture` (`REGROUPING` sin plan y con `regroupStartTick`), así que la reproducción de incidentes lo cubre sin cambios.

### `DecisionTrace.java` y `TraceDraft.java`

- `DecisionTrace` suma el componente `boolean stillRetreated` **entre `regroupEnd` y `attackChoices`**. Sin validación (es un `boolean`).
- `TraceDraft` suma el campo `private boolean stillRetreated;`, el método `void stillRetreated() { stillRetreated = true; }` y lo pasa en `build`, en esa misma posición.
- En `DebugLogTest` y `TraceLevelsTest`, cada `new DecisionTrace(` recibe `false` en esa posición. Nada más cambia en esos archivos.

### `Brain.java`

`observe`: después de elegir objetivo y **antes** de `beginPlanning`:

```java
    Optional<PlayerId> target = chooseTarget(turn);
    if (target.isEmpty()) {
      return Outcome.idle();
    }
    if (parts.retreatRule().isGroupRetreated(turn.snapshot())) {
      return holdBack(turn);
    }
    turn.lifecycle().beginPlanning();
    …
```

```java
  // CT-13: a plan opened now would close at once with GROUP_RETREATED and teach the memory a
  // failure that was never fought.
  private Outcome holdBack(Turn turn) {
    turn.lifecycle().regroupWithoutPlan(turn.snapshot().tick());
    turn.draft().stillRetreated();
    return Outcome.withOrders(regroupOrders(turn));
  }
```

`regroup` queda así:

```java
  // The group observes again and plans in the next decision.
  private Outcome regroup(Turn turn) {
    Optional<RegroupEndReason> regroupEnd = detectRegroupEnd(turn);
    if (regroupEnd.isEmpty()) {
      return Outcome.withOrders(regroupOrders(turn));
    }
    if (regroupEnd.get() == RegroupEndReason.WINDOW_EXPIRED
        && parts.retreatRule().isGroupRetreated(turn.snapshot())) {
      return keepRegrouping(turn);
    }
    endRegrouping(turn);
    return Outcome.idle();
  }

  // CT-13: the window ran out but the group still cannot fight; the adaptive window only learns
  // from regroups that end.
  private Outcome keepRegrouping(Turn turn) {
    turn.lifecycle().restartRegroupWindow(turn.snapshot().tick());
    turn.draft().stillRetreated();
    return Outcome.withOrders(regroupOrders(turn));
  }
```

`regroupOrders`, `retreatOrder`, `endRegrouping` y `groupDecision` no cambian (en `REGROUPING`, el objetivo de la decisión sigue siendo `committedTarget`).

### `CombatGeometry.java`

Sin cambios de comportamiento: `FLANK_ANGLE_DEGREES`, `sideOf` y `rotateAroundVertical` pasan de `private` a **package-private** (sin modificador de acceso), para que `FlankFormation` use exactamente la misma geometría. Nada más.

### `FlankQuery.java`

```java
/** One flanker asking for its point, given every flanker of the same target. */
public record FlankQuery(
    PlayerPose pose, MobId self, Map<MobId, Vec3> flankers, double distanceBlocks) {
  public FlankQuery {
    Objects.requireNonNull(pose, "FlankQuery.pose");
    Objects.requireNonNull(self, "FlankQuery.self");
    Objects.requireNonNull(flankers, "FlankQuery.flankers");
    flankers = Map.copyOf(flankers);
    if (!flankers.containsKey(self)) {
      throw new IllegalArgumentException(
          "FlankQuery.self must be one of the flankers, got " + self.value());
    }
    if (!(distanceBlocks > 0) || !Double.isFinite(distanceBlocks)) {
      throw new IllegalArgumentException(
          "FlankQuery.distanceBlocks must be a positive number, got " + distanceBlocks);
    }
  }
}
```

### `FlankFormation.java`

```java
/** Gives each flanker of a target its own point, so flankers on the same side do not collide. */
public final class FlankFormation {
  // Each flanker further down a side stands this much further round the player.
  private static final double SLOT_STEP_DEGREES = 30.0;
  // Past straight behind, a flanker would end up on the other side.
  private static final double BEHIND_DEGREES = 180.0;

  private final CombatGeometry geometry;

  public FlankFormation(CombatGeometry geometry) {
    this.geometry = Objects.requireNonNull(geometry, "FlankFormation.geometry");
  }

  public Vec3 pointFor(FlankQuery query) {
    PlayerPose pose = query.pose();
    int side = sideOf(pose, query.flankers().get(query.self()));
    double angle =
        Math.min(
            CombatGeometry.FLANK_ANGLE_DEGREES + slotOf(query, side) * SLOT_STEP_DEGREES,
            BEHIND_DEGREES);
    Vec3 direction = CombatGeometry.rotateAroundVertical(pose.facing(), side * angle);
    return pose.position().plus(direction.times(query.distanceBlocks()));
  }

  private static int sideOf(PlayerPose pose, Vec3 position) {
    return CombatGeometry.sideOf(pose.facing(), position.minus(pose.position()).horizontal());
  }

  // The flanker furthest round takes the first slot, so nobody crosses another on the way.
  private int slotOf(FlankQuery query, int side) {
    List<MobId> sameSide =
        query.flankers().entrySet().stream()
            .filter(flanker -> sideOf(query.pose(), flanker.getValue()) == side)
            .sorted(furthestRoundFirst(query.pose()))
            .map(Map.Entry::getKey)
            .toList();
    return sameSide.indexOf(query.self());
  }

  private Comparator<Map.Entry<MobId, Vec3>> furthestRoundFirst(PlayerPose pose) {
    Comparator<Map.Entry<MobId, Vec3>> byAngle =
        Comparator.comparingDouble(
            flanker -> geometry.angleFromFacingDegrees(pose, flanker.getValue()));
    return byAngle.reversed().thenComparing(flanker -> flanker.getKey().value());
  }
}
```

## Pruebas obligatorias

Valores de referencia calculados a mano: jugador en el origen mirando a +Z, `distanceBlocks` 3. Rotar la mirada `θ` grados da `(−sen θ, 0, cos θ)`. Un mob en `(2, 0, z)` queda del lado `−1`; uno en `(−2, 0, z)`, del lado `+1`. Ángulos: `(2,0,1)` → 63,435°; `(3,0,0)` → 90°; `(2,0,−1)` → 116,565°.

### `RetreatRuleTest` (+3)

Snapshots con `new GroupSnapshotBuilder().withMob(…)…build()`, y cada mob con id propio (`new MobSnapshotBuilder().withId(new MobId(new UUID(2, n))).withKind(MobKind.ZOMBIE).withHealth(h).withMaxHealth(20)`).

| Prueba | Verifica |
| --- | --- |
| `groupIsRetreatedWhenMoreThanHalfAreLow` | 3 zombies con vida 6, 6 y 20: `isGroupRetreated` `true` |
| `halfIsNotEnoughForAGroupRetreat` | 4 zombies con vida 6, 6, 20 y 20: `false` |
| `emptyGroupIsNotRetreated` | `new GroupSnapshotBuilder().build()`: `false` |

### `GroupLifecycleTest` (+4)

| Prueba | Verifica |
| --- | --- |
| `tooHurtGroupRegroupsWithoutAPlan` | `regroupWithoutPlan(500)`: estado `REGROUPING`, `regroupStartTick()` 500, `plan()` vacío, `planSequence()` 0, `committedTarget()` vacío. Después `finishRegrouping()`: `OBSERVING` |
| `regroupWindowRestartsAtTheGivenTick` | `regroupWithoutPlan(500)`, `restartRegroupWindow(1100)`: `REGROUPING` con `regroupStartTick()` 1100 |
| `newRegroupTransitionsAreGuarded` | desde `OBSERVING`, `restartRegroupWindow(10)` lanza `IllegalStateException` con mensaje exacto `Group 00000000 cannot restart the regroup window while OBSERVING`; después de `enterExecuting()`, `regroupWithoutPlan(10)` lanza `Group 00000000 cannot regroup without a plan while EXECUTING` |
| `regroupTicksMustNotBeNegative` | `regroupWithoutPlan(-1)` lanza `IllegalArgumentException` con `PlanLifecycle.tick must be zero or positive, got -1`, y el estado sigue `OBSERVING` |

### `BrainObservingTest` (+2)

Con `BrainFixture.choosingStrategy(0)` y `catalogGroup()` (4 zombies de 20, 3 esqueletos de 20, 2 arañas de 16).

| Prueba | Verifica |
| --- | --- |
| `badlyHurtGroupRegroupsInsteadOfPlanning` | vida 5 (`LOW_HEALTH`) a los mobs 0 a 4 (5 de 9), `decide(START_TICK, mobs, alice())`: `decision().state()` `REGROUPING`, `decision().plan()` vacío, `trace().stillRetreated()` `true`, `trace().strategySelection()` vacío, `closedPlan()` vacío, 9 órdenes todas `RETREAT` con `target()` vacío y sin ataque; `group().lifecycle().planSequence()` 0 y `regroupStartTick()` = `START_TICK`; `regroupWindow().currentTicks()` 600 |
| `halfHurtGroupStillPlans` | vida 5 a los mobs 0 a 3 (4 de 9): `decision().state()` `EXECUTING`, `trace().stillRetreated()` `false` |

### `BrainRegroupingTest` (1 reemplazada, +1)

`expiredWindowEndsRegrouping` se **reemplaza** por estas dos (con el `@BeforeEach` actual, en el que los mobs 0 a 4 tienen vida 5):

| Prueba | Verifica |
| --- | --- |
| `expiredWindowWithTheGroupStillRetreatedKeepsRegrouping` | `decide(REGROUP_START_TICK + INITIAL_WINDOW_TICKS, wounded, alice())`: `trace().regroupEnd()` contiene `WINDOW_EXPIRED`, `trace().stillRetreated()` `true`, `decision().state()` `REGROUPING`, 9 órdenes `RETREAT`, `regroupStartTick()` = `REGROUP_START_TICK + 600`, ventana 600 (no cambia). Después, `decide(REGROUP_START_TICK + 600 + 590, wounded, alice())`: sigue `REGROUPING` con `regroupEnd()` vacío (la ventana se reinició) |
| `expiredWindowEndsRegroupingOnceTheGroupCanFight` | `wounded` con el mob 4 en vida 8 (quedan 4 de 9 con 30 % o menos y 4 recuperados, ninguno es mayoría): `decide(REGROUP_START_TICK + 600, …)`: `regroupEnd()` contiene `WINDOW_EXPIRED`, `stillRetreated()` `false`, estado `OBSERVING`, sin órdenes, ventana 650 |

### `FlankFormationTest` (8)

`pose = new PlayerPose(new Vec3(0, 0, 0), new Vec3(0, 0, 1))`, distancia 3, tolerancia `1e-9`. `mob(n)` = `new MobId(new UUID(0, n))`.

| Prueba | Flanqueadores | Verifica |
| --- | --- | --- |
| `loneFlankerGetsTheSingleFlankPoint` | `mob(1)` en `(2,0,0)` | `(2.1213203435596424, 0, -2.1213203435596424)` e igual a `new CombatGeometry().flankPoint(pose, new Vec3(2,0,0), 3)` |
| `furthestRoundFlankerTakesTheFirstSlot` | `mob(1)` en `(2,0,1)`, `mob(2)` en `(2,0,-1)` | `mob(2)` → `(2.1213203435596424, 0, -2.1213203435596424)`; `mob(1)` → `(0.7764571353075622, 0, -2.897777478867205)` |
| `thirdFlankerOnASideStandsBehind` | los dos anteriores y `mob(3)` en `(3,0,0)` | `mob(1)` (puesto 2) → `(0, 0, -3)` |
| `flankersOnOppositeSidesKeepTheirSide` | `mob(1)` en `(2,0,0)`, `mob(4)` en `(-2,0,0)` | `mob(1)` → `(2.1213…, 0, -2.1213…)`; `mob(4)` → `(-2.1213203435596424, 0, -2.1213203435596424)` |
| `tiesGoToTheLowerMobIdFirst` | `mob(1)` y `mob(2)` los dos en `(2,0,0)` | `mob(1)` → `(2.1213…, 0, -2.1213…)`; `mob(2)` → `(0.7764…, 0, -2.8977…)` |
| `pointUsesThePlayerPositionAndHeight` | pose en `(10,70,-5)` mirando a +Z; `mob(1)` en `(12,70,-5)` | `(12.121320343559642, 70, -7.121320343559642)` |
| `queryRejectsASelfThatIsNotAFlanker` | `self` `mob(9)`, flanqueadores `{mob(1)}` | `IllegalArgumentException`: `FlankQuery.self must be one of the flankers, got 00000000-0000-0000-0000-000000000009` |
| `queryRejectsNonPositiveDistance` | distancia 0 | `IllegalArgumentException`: `FlankQuery.distanceBlocks must be a positive number, got 0.0` |

Total: **19 pruebas nuevas** (y una reemplazada). Toda la suite tiene que seguir en verde, incluida `BrainInvariantsTest`.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `isGroupRetreated`, `>=` en vez de `>` | 2 de 4: `4 >= 4` da `true` | `halfIsNotEnoughForAGroupRetreat` |
| 2 | En `observe`, sacar el `if` de `holdBack` | 5 de 9 heridos: abre el plan 1, estado `EXECUTING` | `badlyHurtGroupRegroupsInsteadOfPlanning` |
| 3 | En `keepRegrouping`, llamar a `parts.regroupWindow().recordSurvived()` | ventana 650 | `expiredWindowWithTheGroupStillRetreatedKeepsRegrouping` |
| 4 | En `furthestRoundFirst`, sin `.reversed()` (ascendente) | `mob(1)` (63°) toma el puesto 0: `(2.1213…, 0, -2.1213…)` | `furthestRoundFlankerTakesTheFirstSlot` |

## Procedimiento

1. Rama `wp-22a-retirada-y-formacion` desde `origin/main` actualizado.
2. `RetreatRule`, `PlanLifecycle`, `DecisionTrace`, `TraceDraft`, `Brain` y sus pruebas (incluido el argumento nuevo en `DebugLogTest` y `TraceLevelsTest`). Commit: `feat: regroup instead of planning while the group is still retreated (CT-13)`.
3. `CombatGeometry` (visibilidad), `FlankQuery`, `FlankFormation` y `FlankFormationTest`. Commit: `feat: flank formation that spreads flankers on the same side (CT-14)`.
4. Pruebas que muerden (de a una, en secuencia; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-22A: no plans while still retreated, and flank formation`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `BrainInvariantsTest.theScenarioCoversEveryPath` u otra prueba existente deja de pasar por el cambio de comportamiento del CT-13, **frená y reportá** qué prueba, el mensaje y la cobertura que imprime. No cambies esas pruebas.
3. Si una prueba existente construye `DecisionTrace` en un archivo que no está en la tabla, agregale el `false` y avisalo en el informe.

## Fuera de alcance

- Los goals (`FlankGoal`, `RetreatGoal`), la curación y el uso de `FlankFormation` desde Paper: WP-22B.
- Cambiar `CombatGeometry.flankPoint` o cualquier regla de CT-07 que no sea la de la regla 3.
- Una línea nueva en `mobai-debug.log` para el reagrupamiento.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 19 pruebas nuevas y la reemplazada, con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
