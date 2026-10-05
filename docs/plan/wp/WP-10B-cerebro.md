# WP-10B — Cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-08B y WP-10A |
| Modelo | Opus |
| Rama | `wp-10b-cerebro` |

## Objetivo

Implementar `Brain`: cada 10 ticks recibe un grupo y su foto, avanza el ciclo del grupo y devuelve qué hace cada mob, junto con la explicación completa de por qué.

Es la pieza más sensible del plugin: un error de lógica acá se ve en todas las peleas. Por eso:
- el algoritmo está fijado paso por paso en este WP;
- `Brain` solo coordina: cada regla vive en una pieza ya probada (WP-07 a WP-10A);
- además de las pruebas de cada camino, hay pruebas de **invariantes** sobre cientos de decisiones seguidas y una prueba de **reproducibilidad**.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. `docs/plan/cambios-tecnicos.md`, solo CT-07 (retirada táctica y reagrupamiento).
3. Código existente (solo leer): todo `domain/group`, `domain/decision`, `domain/brain`, `domain/strategy` y `domain/target`; de `domain/selection`, `SelectionPolicy`, `SelectionPolicyFactory`, `SelectionCandidate`, `SelectionResult` y `RandomPolicy`; `domain/snapshot`; `domain/settings/MobAiSettings.java`; y de `testsupport`, todo.

## Reglas de negocio: el algoritmo

`decide(group, snapshot)` hace, en este orden:

**0. Validar la entrada.**
- La foto tiene que ser del grupo: `snapshot.groupId()` igual a `group.id()`.
- Cada mob de la foto tiene que ser miembro del grupo.
- Al empezar una decisión el grupo nunca puede estar en `PLANNING`: el cerebro siempre lo deja en otro estado al terminar. Si pasa, es un bug y se lanza una excepción.

**1. Según el estado del grupo al empezar:**

| Estado | Qué hace |
| --- | --- |
| `OBSERVING` | Paso 2 (observar) |
| `EXECUTING` | Paso 3 (ejecutar) |
| `EVALUATING` | El plan se cerró entre decisiones (la muerte del objetivo la cierra el WP-13). `finishEvaluation()`. Si quedó en `REGROUPING`, devuelve órdenes de reagrupamiento (paso 5); si quedó en `OBSERVING`, una decisión sin órdenes. El plan siguiente arranca en la decisión siguiente (D10) |
| `REGROUPING` | Paso 4 (reagrupar) |
| `PLANNING` | `IllegalStateException` |

**2. Observar.**
1. Sin mobs en la foto (todos en chunks descargados): decisión sin órdenes.
2. Elegir el objetivo con `TargetSelector`, con el objetivo comprometido del grupo. Sin objetivo: decisión sin órdenes (el grupo se queda quieto, D2).
3. `beginPlanning()`.
4. **Estrategia:**
   - Se anota en la traza un `StrategyCheck` por **cada** estrategia del catálogo, en su orden.
   - Candidatas: las viables, cada una con puntaje base 1 y la estimación `memory.strategyEstimate(objetivo, id, tick)`.
   - Elige la política del grupo (`SelectionPolicyFactory.forType(group.policy())`). El resultado va a la traza.
5. **Roles:**
   - La estrategia reparte los roles (`assignRoles`).
   - `startPlan(new PlanStart(estrategia, objetivo, roles, vida máxima del objetivo, tick))`. Los roles iniciales del plan son **los de la estrategia**: un mob que después se recupera vuelve a ese rol.
   - Después, cada mob de la foto con `shouldRetreat` pasa a `RETREAT` con `assignRole`, y se anota en `newlyRetreating`.
6. Órdenes del plan (paso 6).

**3. Ejecutar.**
1. Si `isTargetVisible(foto, objetivo)`: `markTargetSeen(tick)`.
2. **Roles, mob por mob de la foto, en orden:**

   | Situación | Acción | Traza |
   | --- | --- | --- |
   | Sin rol en el plan (se sumó a mitad de plan) | Rol básico (`SKELETON` → `SHOOT`; los demás → `PRESS`). Si además `shouldRetreat`, `RETREAT` | `newlyRetreating` si quedó en `RETREAT` |
   | En `RETREAT` y `shouldReturn` | Vuelve a `startingRoleOf(mob)` o, si no tiene, al rol básico | `returningFromRetreat` |
   | Fuera de `RETREAT` y `shouldRetreat` | `RETREAT` | `newlyRetreating` |
   | Cualquier otro caso | Nada | — |

3. `PlanEndDetector.detect(plan actualizado, tick)`. El motivo va a la traza.
4. Si terminó:
   - `closePlan(motivo, tick, fullSuccessDamageFraction)` y `finishEvaluation()`, en la misma decisión (D10).
   - Si quedó en `REGROUPING` (el motivo fue `GROUP_RETREATED`): órdenes de reagrupamiento (paso 5). Si no: decisión sin órdenes.
   - El `ClosedPlan` va en el resultado.
5. Si no terminó: órdenes del plan (paso 6).

**4. Reagrupar.**
1. `RegroupRule.detect(foto, regroupStartTick)`. El resultado va a la traza (`regroupEnd`).
2. Si terminó (por recuperación o porque venció la ventana): `finishRegrouping()`, `RegroupWindow.recordSurvived()` y decisión sin órdenes. El grupo vuelve a observar y planifica en la decisión siguiente.
3. Si no terminó: órdenes de reagrupamiento (paso 5).

**5. Órdenes de reagrupamiento.** Una por cada mob de la foto, en orden:
- rol `RETREAT`;
- objetivo: el comprometido del grupo (del que se alejan);
- sin ataque sugerido;
- `recovering = canRecover(mob, foto)`.

**6. Órdenes del plan.** Una por cada mob de la foto, en orden, según su rol en el plan:

| Rol / tipo | Objetivo | Ataque sugerido | `recovering` |
| --- | --- | --- | --- |
| `RETREAT` (cualquier tipo) | El del plan (del que se aleja) | Ninguno | `canRecover(mob, foto)` |
| Araña (fuera de `RETREAT`) | `SpiderTargetRule.choose(araña, jugadores de la foto, objetivo actual de la araña)`; si hay, se guarda con `assignSpiderTarget` | Si tiene objetivo, `AttackSuggester.suggest` (siempre mordida); si no, ninguno | `false` |
| Zombie o esqueleto (fuera de `RETREAT`) | El del plan | `AttackSuggester.suggest(mob, objetivo, contexto)` | `false` |

Cada `AttackChoice` va a la traza, en el mismo orden.

**Orden del azar** (para que un incidente se pueda reproducir): primero la elección de estrategia (si se planifica) y después los ataques, mob por mob en el orden de la foto. Ninguna otra cosa consume números al azar.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/brain/BrainParts.java`, `TraceDraft.java`, `Brain.java` |
| Crear (prueba) | `domain/brain/BrainObservingTest.java`, `BrainExecutingTest.java`, `BrainRegroupingTest.java`, `BrainInvariantsTest.java` |
| Crear (prueba, si la necesitás) | `testsupport/BrainFixture.java` (arma `Brain`, `Group` y fotos para las cuatro pruebas) |

## Especificación

### 1. `BrainParts`

```java
/** The rule objects the brain coordinates; built once at startup. */
public record BrainParts(
    TargetSelector targetSelector,
    SpiderTargetRule spiderTargetRule,
    StrategyCatalog strategies,
    SelectionPolicyFactory policies,
    AttackSuggester attackSuggester,
    PlanEndDetector planEndDetector,
    RetreatRule retreatRule,
    RegroupRule regroupRule,
    RegroupWindow regroupWindow) { ... }   // requireNonNull de cada uno: "BrainParts.<componente>"
```

### 2. `TraceDraft` (package-private)

Borrador mutable de la `DecisionTrace`, que los pasos van llenando.

- Se arma con `new TraceDraft(GroupId group, long tick, GroupState stateBefore)`.
- Tiene un setter por cada componente opcional o de lista de `DecisionTrace`:
  - `plan`, `targetSelection`, `strategyChecks`, `strategySelection` y `endReason`, `regroupEnd`;
  - `addNewlyRetreating(MobId)`, `addReturningFromRetreat(MobId)` y `addAttackChoice(AttackChoice)`, para las listas.
- `DecisionTrace build(GroupState stateAfter)` arma el record.

Sin lógica: solo guarda.

### 3. `Brain`

```java
public final class Brain {
  public Brain(Supplier<MobAiSettings> settings, BrainParts parts) { ... }   // "Brain.settings", "Brain.parts"

  public BrainResult decide(Group group, GroupSnapshot snapshot) { ... }
}
```

Reglas de implementación:
- **Cada paso del algoritmo es un método privado**, y cada método hace una sola tarea, de unas 20 líneas como máximo. Nombres sugeridos: `requireMatchingSnapshot`, `observe`, `execute`, `evaluate`, `regroup`, `chooseTarget`, `chooseStrategy`, `strategyChecks`, `startPlan`, `retreatLowHealth`, `updateRoles`, `updatedRole`, `closeAndEvaluate`, `planOrders`, `orderFor`, `spiderOrder`, `retreatOrder`, `regroupOrders`, `idle`.
- Para que los métodos no pasen de 3 parámetros, armá **un record privado** `Turn(Group group, GroupSnapshot snapshot, TraceDraft draft)` con lo de una decisión, y pasalo entre pasos.
- `Brain` no guarda estado entre decisiones: todo vive en `Group` y en `RegroupWindow`.
- Puntaje base de estrategias: constante privada `BASE_SCORE = 1.0`, con el comentario `// Every strategy starts equal; the memory multiplier is what tells them apart.`
- La `GroupDecision` lleva el estado **después** de decidir. Plan, estrategia y objetivo van presentes solo si el grupo quedó en `EXECUTING`. Si quedó en `REGROUPING`, va solo el objetivo comprometido.
- `BrainResult.closedPlan()` está presente solo en la decisión que cerró el plan.
- Se escribe `newlyRetreating` también cuando el mob entra en retirada al empezar el plan.

Mensajes de error (exactos):

| Situación | Excepción y mensaje |
| --- | --- |
| La foto es de otro grupo | `IllegalArgumentException("Brain: snapshot of group " + snapshot.groupId().shortId() + " given to group " + group.id().shortId())` |
| Un mob de la foto no es miembro | `IllegalArgumentException("Brain: mob " + mob.id().value() + " is not a member of group " + group.id().shortId())` |
| El grupo está en `PLANNING` | `IllegalStateException("Brain: group " + group.id().shortId() + " was left in PLANNING")` |

## Pruebas obligatorias

**Recursos:**
- Las pruebas de caminos usan la política `RANDOM` con `ScriptedRandomSource.withIndexes(...)`. Así cada elección se fija a mano: la primera posición elige la estrategia entre las viables, y las siguientes los ataques, en el orden de la foto.
- Las pruebas de invariantes usan `THOMPSON_SAMPLING` con `SeededRandomSource`.
- El jugador `ALICE = new PlayerId(new UUID(0, 10))` empieza en `(0, 64, 0)`, mirando hacia +z.
- El grupo de prueba del catálogo: 4 zombies, 3 esqueletos y 2 arañas, con los IDs y posiciones de `GroupSnapshotBuilder`, y `addMember` de cada uno en el mismo orden.

Para cada prueba, verificá también la traza: lo que la columna menciona tiene que estar ahí.

**`BrainObservingTest`**

| Prueba | Verificación |
| --- | --- |
| `withoutPlayersTheGroupStaysIdle` | Sin jugadores: estado `OBSERVING`, sin órdenes, la traza tiene la selección de objetivo vacía |
| `withoutMobsInTheSnapshotNothingIsPlanned` | Miembros sin mobs en la foto: `OBSERVING`, sin órdenes, sin selección de objetivo en la traza |
| `plansAndExecutesInTheSameDecision` | Grupo de prueba y ALICE, índices `[1, 0, 1, 2, 0, 1, 2, 0]`. Estado `EXECUTING`; estrategia `FLANK` (índice 1 de las tres viables); plan `#1`; objetivo ALICE; 9 órdenes en el orden de la foto. Los ataques de los 4 zombies y 3 esqueletos siguen los índices, y las arañas muerden. La traza tiene 3 `StrategyCheck` y 9 `AttackChoice`, y el azar quedó agotado (`isExhausted()`) |
| `onlyViableStrategiesAreCandidates` | Solo 2 zombies y ALICE: la traza tiene 3 `StrategyCheck` (solo `DIRECT_ASSAULT` viable) y la política recibió un solo candidato |
| `lowHealthMobsStartThePlanRetreating` | Un zombie con vida 5 de 20: al empezar, su orden es `RETREAT` sin ataque; `newlyRetreating` lo incluye; `startingRoleOf` sigue siendo el rol de la estrategia |
| `commitmentFavoursThePreviousTarget` | Después de cerrar un plan contra ALICE, con ALICE y BOB iguales: la traza muestra a ALICE con `committed = true` y la elige |

**`BrainExecutingTest`** (cada prueba arranca con un plan en curso)

| Prueba | Verificación |
| --- | --- |
| `visibleTargetResetsTheLostCount` | ALICE en la foto y cerca: `lastTargetSeenTick` pasa al tick de la decisión |
| `lostTargetClosesThePlanAndObservesAgain` | Sin ALICE durante 200 ticks: `TARGET_LOST`, `closedPlan` presente, estado `OBSERVING`, sin órdenes. En la decisión siguiente, con ALICE de vuelta, se planifica el plan `#2` |
| `planTimesOut` | ALICE visible hasta el tick 600 del plan: `TIMED_OUT` |
| `woundedMobRetreatsAndComesBackToItsStartingRole` | Un zombie que flanqueaba baja a 5 de 20: `RETREAT`, sin ataque, `recovering` según la distancia a ALICE. Cuando sube a 12: vuelve a `FLANK` y aparece en `returningFromRetreat` |
| `retreatingMobHealsOnlyAwayFromPlayers` | El mob en `RETREAT` a 11 bloques de ALICE: `recovering = false`; a 13: `true` |
| `memberJoiningMidPlanGetsTheBasicRole` | Un esqueleto agregado con `addMember` durante el plan: su orden es `SHOOT` |
| `spidersFollowTheNearestPlayerAndRememberIt` | Con ALICE y BOB, la araña más cerca de BOB tiene objetivo BOB, y `roster().spiderTarget` lo guarda |
| `groupRetreatStartsRegroupingWithRetreatOrders` | 5 de 9 mobs con vida baja: `GROUP_RETREATED`, estado `REGROUPING`, todas las órdenes `RETREAT` sin ataque, `closedPlan` presente |
| `planClosedBetweenDecisionsIsEvaluated` | El plan se cierra a mano con `closePlan(TARGET_DIED, …)` (como lo hará el WP-13): la decisión siguiente termina la evaluación, queda en `OBSERVING`, sin órdenes y sin `closedPlan` |

**`BrainRegroupingTest`**

| Prueba | Verificación |
| --- | --- |
| `regroupingKeepsEveryoneRetreating` | En `REGROUPING`, mobs con poca vida y la ventana sin vencer: todas las órdenes `RETREAT`, objetivo = comprometido |
| `recoveredMajorityEndsRegroupingAndLengthensTheWindow` | Más de la mitad con 60 %: `regroupEnd = RECOVERED`, estado `OBSERVING`, sin órdenes, ventana 650 |
| `expiredWindowEndsRegrouping` | Ventana de 600 vencida: `WINDOW_EXPIRED`, `OBSERVING`, ventana 650 |
| `nextDecisionAfterRegroupingPlansAgain` | Después de salir de reagrupar, con ALICE: plan nuevo |

**`BrainInvariantsTest`.** Simulación con semilla fija: un grupo de prueba contra ALICE y BOB durante **500 decisiones** (tick +10 por decisión). El guion lo arma un generador propio de la prueba, con un `SeededRandomSource` **distinto** del del cerebro:
- Los jugadores se mueven.
- Los mobs pierden vida mientras pelean; se curan si la orden dice `recovering`.
- Los que llegan a 0 se sacan con `removeMember` y dejan la foto.
- Hay ventanas en las que ALICE desaparece de la foto.
- De vez en cuando, cuando se repone el grupo, entra un miembro nuevo.

En **cada** decisión se verifica:

| Prueba | Invariante |
| --- | --- |
| `everySnapshotMobGetsExactlyOneOrderWhenThereAreOrders` | Si hay órdenes, sus mobs son exactamente los de la foto, cada uno una vez y en el mismo orden |
| `retreatingMobsNeverGetAnAttack` | Toda orden `RETREAT` tiene ataque vacío; toda orden con `recovering = true` es `RETREAT` |
| `theGroupNeverEndsADecisionInATransitState` | El estado después de decidir nunca es `PLANNING` ni `EVALUATING` |
| `everyPlanClosesAtMostOnce` | Cada `PlanId` aparece en un solo `closedPlan`, y los `PlanId` van subiendo de a 1 |
| `ordersAgreeWithTheGroupState` | En `EXECUTING` la decisión trae plan, estrategia y objetivo; en `OBSERVING` no trae órdenes; en `REGROUPING` todas las órdenes son `RETREAT` |
| `sameSeedsGiveTheSameDecisions` | Dos corridas completas con las mismas semillas dan la misma secuencia de `GroupDecision` (`isEqualTo`) y la misma secuencia de motivos de cierre |
| `theScenarioCoversEveryPath` | En las 500 decisiones aparecieron al menos una vez: las tres estrategias, `TARGET_LOST`, `TIMED_OUT`, `GROUP_RETREATED`, `RECOVERED` o `WINDOW_EXPIRED`, un mob que volvió de la retirada y un `LeaderDied`. Si alguno falta, ajustá el guion (no el cerebro) hasta que aparezca, y explicalo en el informe |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En el paso 2.5, empezar el plan con los roles ya cambiados a `RETREAT` | `woundedMobRetreatsAndComesBackToItsStartingRole` o `lowHealthMobsStartThePlanRetreating` (`startingRoleOf` sería `RETREAT`) |
| En las órdenes del plan, sugerir ataque también a los mobs en `RETREAT` | `retreatingMobsNeverGetAnAttack` |
| En el paso 3, no llamar a `finishEvaluation` después de `closePlan` | `theGroupNeverEndsADecisionInATransitState` |
| En el paso 4, no llamar a `recordSurvived` | `recoveredMajorityEndsRegroupingAndLengthensTheWindow` |
| Elegir los ataques antes que la estrategia (cambiar el orden del azar) | `plansAndExecutesInTheSameDecision` |
| En el paso 3.2, devolver al mob recuperado al rol básico en vez del inicial | `woundedMobRetreatsAndComesBackToItsStartingRole` |

## Procedimiento

1. Rama `wp-10b-cerebro` desde `main`.
2. `BrainParts`, `TraceDraft`, `Brain`, `BrainObservingTest` y `BrainExecutingTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add brain that plans, executes and closes group plans`.
3. Reagrupamiento y `BrainRegroupingTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): let the brain regroup retreating groups`.
4. `BrainInvariantsTest`. `./gradlew spotlessApply build`. Commit: `test(domain): check brain invariants over a seeded 500 decision fight`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-10B: brain`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Nombres de métodos privados de `Brain` distintos de los sugeridos, si se mantiene una tarea por método.
3. Detalles del guion de `BrainInvariantsTest` (velocidades, daños y ventanas de ausencia), siempre que cubra todos los caminos de `theScenarioCoversEveryPath`.

Si una pieza de los WP-07 a WP-10A no se comporta como dice su WP, o si el algoritmo de este WP deja un caso sin definir: frená y reportá. No cambies piezas ajenas.

## Fuera de alcance

- Publicar los eventos, registrar el resultado del plan en la memoria y cerrar el plan por la muerte del objetivo (WP-13).
- Llamar a `recordWiped` cuando un grupo muere entero reagrupándose (WP-13).
- Escribir las trazas (WP-28 y WP-29).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] El algoritmo sigue los pasos 0 a 6 y el orden del azar.
- [ ] Mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite; ninguna clase con más de 20 métodos públicos.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto, incluidas las invariantes en 500 decisiones.
- [ ] Las 6 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
