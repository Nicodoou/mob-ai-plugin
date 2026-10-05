# WP-11 — Simulación de aprendizaje y calibración

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro (cierra la puerta E3) |
| Depende de | WP-10B |
| Modelo | Sonnet |
| Rama | `wp-11-simulacion` |

## Objetivo

Comprobar con números, sin server, que el aprendizaje cumple los criterios del MVP (catálogo, «Definición de terminado»):

1. Contra un jugador que mantiene el escudo levantado de frente, en los últimos 10 de 20 planes el grupo elige `FLANK` en el 50 % o más.
2. En esos mismos 10 planes, `zombie.front_strike` es menos del 30 % de los ataques de zombie.
3. Contra un jugador que no usa escudo, en 20 planes el golpe frontal no cae por debajo del 30 %.

Y medir cómo cambian esos números con la velocidad de aprendizaje y la vida media, para elegir los valores por defecto (decisión abierta del tablero).

Además, un cambio chico en el cerebro, **CT-08**: un zombie con rol `FLANK` siempre usa `zombie.flank_strike`.

## Por qué CT-08

Hoy el ataque de cada zombie lo elige la política entre los tres, sin mirar el rol. Un zombie que flanquea y llega al costado conecta aunque use el «golpe frontal», porque está fuera del arco del escudo. Ese acierto se anota en la memoria como un éxito del golpe frontal contra ese jugador. Así, el grupo aprendería que el golpe frontal funciona contra el que bloquea, que es justo lo contrario de lo que pasa.

Con CT-08:
- el rol `FLANK` ejecuta siempre `zombie.flank_strike`, sin consultar la política y sin consumir azar, igual que la mordida de la araña;
- la elección entre los tres golpes queda para los zombies con rol `PRESS`, y la estadística de cada golpe mide lo que mide.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer, salvo lo que la tabla «Archivos» marca para modificar):
   - `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `BrainParts.java`, `AttackSuggester.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/decision/AttackChoice.java`, `RoleAssignment.java`, `BrainResult.java`, `ClosedPlan.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (firmas públicas), `AttackObservation.java`, `StrategyObservation.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/attack/AttackOutcome.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `MemorySettings.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/BrainFixture.java`, `TestSettings.java`, `SeededRandomSource.java`
   - `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` (la prueba `plansAndExecutesInTheSameDecision`)
   - `build.gradle.kts`

## Reglas de negocio: el modelo de la simulación

La simulación usa el `Brain` real con un `Group` real y su `GroupMemory`. Lo único simulado es **el mundo**: qué pasa cuando un mob ataca y cuánto éxito tuvo un plan. Hace lo que harán después los casos de uso (WP-13): anota cada ataque en la memoria y, al cerrar un plan, anota el éxito de la estrategia.

**Escena.**
- El grupo de prueba del catálogo (4 zombies, 3 esqueletos y 2 arañas), con las posiciones de `GroupSnapshotBuilder`, contra un jugador `ALICE` en `(0, 64, 0)` mirando hacia +z.
- Nadie se mueve ni pierde vida: la simulación aísla el aprendizaje.
- Cada decisión avanza 10 ticks.
- Los planes cierran siempre por tiempo: 600 ticks, 60 decisiones.
- Una corrida son 20 planes.

**Arquetipo del jugador.**
- `BLOCKER`: siempre con el escudo levantado de frente.
- `OPEN`: nunca bloquea.

**Ataques.** En cada decisión, cada orden con ataque sugerido intenta atacar con probabilidad 0,15 (cuerpo a cuerpo) o 0,10 (esqueleto). El resultado sale de esta tabla, sorteando con `nextUnit()` en el orden de las columnas. «Sin intento» significa que el ataque con espera venció su espera sin atacar, así que no se anota nada.

| Arquetipo | Mob y rol | Ataque | Acierto | Parcial | Fallo | Sin intento |
| --- | --- | --- | --- | --- | --- | --- |
| `BLOCKER` | Zombie `PRESS` | `ZOMBIE_FRONT_STRIKE` | 0 | 0,85 | 0,15 | 0 |
| `BLOCKER` | Zombie `PRESS` | `ZOMBIE_FLANK_STRIKE` | 0,45 | 0 | 0,55 | 0 |
| `BLOCKER` | Zombie `PRESS` | `ZOMBIE_PATIENT_STRIKE` | 0,20 | 0 | 0 | 0,80 |
| `BLOCKER` | Zombie `FLANK` | `ZOMBIE_FLANK_STRIKE` | 0,70 | 0 | 0,30 | 0 |
| `BLOCKER` | Esqueleto | `SKELETON_DIRECT_SHOT` | 0,15 | 0,60 | 0,25 | 0 |
| `BLOCKER` | Esqueleto | `SKELETON_LEAD_SHOT` | 0,20 | 0,55 | 0,25 | 0 |
| `BLOCKER` | Esqueleto | `SKELETON_OPPORTUNISTIC_SHOT` | 0,40 | 0 | 0,20 | 0,40 |
| `BLOCKER` | Araña `PRESS` | `SPIDER_BITE` | 0 | 0,70 | 0,30 | 0 |
| `BLOCKER` | Araña `FLANK` | `SPIDER_BITE` | 0,60 | 0 | 0,40 | 0 |
| `OPEN` | Zombie `PRESS` | `ZOMBIE_FRONT_STRIKE` | 0,70 | 0 | 0,30 | 0 |
| `OPEN` | Zombie `PRESS` | `ZOMBIE_FLANK_STRIKE` | 0,50 | 0 | 0,50 | 0 |
| `OPEN` | Zombie `PRESS` | `ZOMBIE_PATIENT_STRIKE` | 0,55 | 0 | 0,45 | 0 |
| `OPEN` | Zombie `FLANK` | `ZOMBIE_FLANK_STRIKE` | 0,65 | 0 | 0,35 | 0 |
| `OPEN` | Esqueleto | `SKELETON_DIRECT_SHOT` | 0,35 | 0 | 0,65 | 0 |
| `OPEN` | Esqueleto | `SKELETON_LEAD_SHOT` | 0,60 | 0 | 0,40 | 0 |
| `OPEN` | Esqueleto | `SKELETON_OPPORTUNISTIC_SHOT` | 0,45 | 0 | 0,25 | 0,30 |
| `OPEN` | Araña (cualquier rol) | `SPIDER_BITE` | 0,60 | 0 | 0,40 | 0 |

- Cada intento con resultado se anota con `memory.recordAttack(new AttackObservation(ALICE, ataque, crédito, tick))`.
- El crédito es `outcome.credit(memorySettings.partialHitWeight()).getAsDouble()`, con `Hit`, `Partial` o `Miss` de `AttackOutcome`.

**Éxito de un plan.** No sale del daño: se sortea con `media + 0,15 × nextGaussian()`, acotado a [0, 1], según esta tabla. Se anota con `memory.recordStrategy(new StrategyObservation(ALICE, estrategia, éxito, 1.0, tick de cierre))`.

| Arquetipo | `DIRECT_ASSAULT` | `FLANK` | `PIN_AND_SHOOT` |
| --- | --- | --- | --- |
| `BLOCKER` | 0,25 | 0,70 | 0,45 |
| `OPEN` | 0,65 | 0,60 | 0,55 |

**Azar:** dos fuentes con semillas distintas.
- El cerebro: `SeededRandomSource(semilla)`.
- El mundo: `SeededRandomSource(semilla + 1_000_003)`.

Así, cambiar el modelo del mundo no cambia los números del cerebro.

**Métricas de una corrida:**
- `flankShareLast10`: planes 11 a 20 con estrategia `FLANK` ÷ 10.
- `frontShareLast10`: intentos con resultado de `ZOMBIE_FRONT_STRIKE` ÷ intentos con resultado de cualquier ataque de zombie, en los planes 11 a 20. Si no hubo ataques de zombie, es 0.
- `frontShareAll`: lo mismo sobre los 20 planes.

**Criterios de una corrida:**
- `BLOCKER`: pasa si `flankShareLast10 ≥ 0,5` **y** `frontShareLast10 < 0,3`.
- `OPEN`: pasa si `frontShareAll ≥ 0,3`.

**Criterio de una configuración:** con 30 semillas (de 1 a 30), pasa si **al menos el 80 %** de las corridas `BLOCKER` y al menos el 80 % de las `OPEN` pasan. Una sola sesión de prueba con Nico en el server es una corrida: el 80 % pide que la mayoría de las sesiones salgan bien, no que alguna salga bien por suerte.

**Grilla de calibración:** velocidad de aprendizaje {0,5; 0,7; 0,9; 1,0} × vida media {6.000; 12.000; 24.000} ticks. La configuración del catálogo es 0,7 y 12.000.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Modificar | `domain/brain/Brain.java` (CT-08) |
| Modificar (prueba) | `domain/brain/BrainObservingTest.java` (`plansAndExecutesInTheSameDecision`) |
| Modificar (prueba) | `domain/brain/BrainExecutingTest.java` (una prueba nueva) |
| Crear (prueba) | `simulation/PlayerArchetype.java`, `OutcomeModel.java`, `PlanSuccessModel.java`, `SimulationConfig.java`, `RunMetrics.java`, `LearningSimulation.java` |
| Crear (prueba) | `simulation/OutcomeModelTest.java`, `LearningSimulationTest.java`, `CalibrationReport.java` |
| Modificar | `build.gradle.kts` (excluir la calibración del `test` y agregar la tarea `simulationReport`) |

Todo lo de `simulation` vive en `src/test`: es una herramienta de prueba, no código del plugin.

## Especificación

### 1. CT-08 en `Brain`

En `fighterOrder`, si `mob.kind() == MobKind.ZOMBIE && role == Role.FLANK`:
- el ataque es `Attack.ZOMBIE_FLANK_STRIKE` **sin** llamar al sugeridor ni a la política;
- igual se anota en la traza `new AttackChoice(mob.id(), Attack.ZOMBIE_FLANK_STRIKE, Optional.empty())`, con `addAttackChoice`.

Hacelo con un método privado nuevo (`private AttackChoice flankStrike(Turn turn, MobSnapshot mob)` o similar) con el comentario `// A flanker already stands outside the shield arc; letting the policy pick its strike would credit the front strike for hits the flank earned.`

`plansAndExecutesInTheSameDecision` pasa a usar `withIndexes(1, 0, 1, 2, 0, 1, 2)`: el cuarto zombie flanquea y ya no consume azar. Los ataques esperados pasan a ser, en orden:

`ZOMBIE_FRONT_STRIKE, ZOMBIE_FLANK_STRIKE, ZOMBIE_PATIENT_STRIKE, ZOMBIE_FLANK_STRIKE, SKELETON_DIRECT_SHOT, SKELETON_LEAD_SHOT, SKELETON_OPPORTUNISTIC_SHOT, SPIDER_BITE, SPIDER_BITE`

La traza sigue teniendo 9 `AttackChoice`, y el azar tiene que quedar agotado. Ninguna otra aserción de esa prueba cambia.

Prueba nueva en `BrainExecutingTest`:
- `flankingZombiesAlwaysUseTheFlankStrike`: en un plan `FLANK`, en tres decisiones seguidas, el zombie con rol `FLANK` tiene `ZOMBIE_FLANK_STRIKE` y su `AttackChoice` tiene `selection()` vacío.

### 2. El modelo (`simulation`)

```java
enum PlayerArchetype { BLOCKER, OPEN }

/** What happens when a mob attacks; empty means it waited and never attacked. */
final class OutcomeModel {
  OutcomeModel(RandomSource world) { ... }
  Optional<AttackOutcome> resolve(PlayerArchetype archetype, RoleAssignment order, MobKind kind) { ... }
}

final class PlanSuccessModel {
  PlanSuccessModel(RandomSource world) { ... }
  double sample(PlayerArchetype archetype, StrategyId strategy) { ... }
}
```

- Las probabilidades de las dos tablas van como datos, no como `if` encadenados. Usá un `record OutcomeOdds(double hit, double partial, double miss, double noAttempt)` privado y un mapa armado una vez en el constructor.
- La clave del mapa es un `record OddsKey(PlayerArchetype archetype, MobKind kind, Role role, Attack attack)`. Las filas «Esqueleto» valen para el rol `SHOOT`. La fila «Araña (cualquier rol)» se carga para `PRESS` y para `FLANK`.
- Un ataque sin fila (combinación imposible) lanza `IllegalStateException("No odds for " + key)`.
- `resolve` sortea un `nextUnit()` y recorre acierto, parcial, fallo y sin intento acumulando probabilidades.
- Si las probabilidades de una fila no suman 1 con tolerancia 1e-9, lanzá `IllegalStateException` en el constructor: así un error de tipeo en la tabla no pasa callado.

```java
record SimulationConfig(double learningSpeed, long halfLifeTicks, PlayerArchetype archetype, long seed) {}

record RunMetrics(
    List<StrategyId> strategyPerPlan,       // 20 entradas, en orden
    double flankShareLast10,
    double frontShareLast10,
    double frontShareAll) {
  boolean passes(PlayerArchetype archetype) { ... }   // los criterios de una corrida
}

final class LearningSimulation {
  static final int PLANS = 20;
  RunMetrics run(SimulationConfig config) { ... }
}
```

`LearningSimulation.run`:
1. Configuración: `TestSettings.defaults()` con `MemorySettings` reemplazado por `new MemorySettings(config.halfLifeTicks(), config.learningSpeed(), 0.5)`.
2. Arma `Brain`, `Group` (política `THOMPSON_SAMPLING`) y el grupo del catálogo, como hace `BrainFixture`. Reusá `BrainFixture` si te alcanza; si no, armalo igual, sin modificar `BrainFixture`.
3. Repetí las decisiones hasta juntar 20 `closedPlan`, con un tope de `PLANS × 61` decisiones. Si se llega al tope, lanzá `IllegalStateException` (bucle acotado).
4. En cada decisión: resolvé los ataques de las órdenes (sección «Ataques» de las reglas). Si hay `closedPlan`, sorteá el éxito y anotá la estrategia.
5. Llevá la cuenta por plan, con el plan en curso según `result.decision().plan()`.

Cada método con una sola tarea; la cuenta de métricas, en métodos separados.

### 3. Gradle

```kotlin
tasks.test {
    useJUnitPlatform {
        excludeTags("calibration")
    }
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

val simulationReport by tasks.registering(Test::class) {
    description = "Runs the learning simulation grid and writes build/reports/simulation/calibration.md"
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnitPlatform {
        includeTags("calibration")
    }
    outputs.upToDateWhen { false }
}
```

Reemplazá el bloque `tasks.test` existente por el de arriba (solo agrega `excludeTags`). No cambies nada más de `build.gradle.kts`.

### 4. `CalibrationReport`

Clase de prueba con `@Tag("calibration")` y un solo `@Test`, `writesTheCalibrationGrid`:
- Corre la grilla completa (12 configuraciones × 2 arquetipos × 30 semillas).
- Escribe `build/reports/simulation/calibration.md`: una tabla con velocidad de aprendizaje, vida media, % de corridas `BLOCKER` que pasan, % de `OPEN` que pasan, y los promedios de `flankShareLast10`, `frontShareLast10` y `frontShareAll`. Al final, la lista de configuraciones que pasan el criterio del 80 %.
- No afirma nada sobre los resultados: su trabajo es medir.

Para escribir el archivo usá `java.nio.file.Files`, que está permitido en `src/test`.

## Pruebas obligatorias

Estas sí corren en `./gradlew build` y tienen que ser rápidas: menos de 10 segundos entre todas.

**`OutcomeModelTest`**

| Prueba | Verificación |
| --- | --- |
| `everyRowAddsUpToOne` | Construir el modelo no lanza nada |
| `frequenciesMatchTheTable` | 20.000 sorteos de `BLOCKER`, zombie `PRESS`, `ZOMBIE_FRONT_STRIKE`: parciales 0,85 ± 0,01 y ningún acierto. 20.000 de `BLOCKER`, zombie `PRESS`, `ZOMBIE_PATIENT_STRIKE`: sin intento 0,80 ± 0,01 |
| `flankRoleChangesTheOdds` | 20.000 sorteos de `BLOCKER`, zombie `FLANK`, `ZOMBIE_FLANK_STRIKE`: aciertos 0,70 ± 0,01 (contra 0,45 en `PRESS`) |
| `impossibleCombinationsFail` | Esqueleto con `ZOMBIE_FRONT_STRIKE`: `IllegalStateException` cuyo mensaje empieza con `No odds for` |

**`LearningSimulationTest`** (una semilla, la configuración del catálogo)

| Prueba | Verificación |
| --- | --- |
| `aRunHasTwentyPlans` | `strategyPerPlan` tiene 20 entradas |
| `sameSeedGivesTheSameRun` | Dos corridas con la misma configuración dan el mismo `RunMetrics` |
| `differentSeedsGiveDifferentRuns` | Semillas 1 y 2: `strategyPerPlan` distinto |
| `blockerRunsRecordNoFrontHits` | En una corrida `BLOCKER`, ningún golpe frontal tuvo crédito 1. Verificalo leyendo la memoria al final: la estimación de `ZOMBIE_FRONT_STRIKE` tiene una media menor que la de `ZOMBIE_FLANK_STRIKE` |
| `sharesAreBetweenZeroAndOne` | Las tres métricas están en [0, 1] para los dos arquetipos |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| Quitar CT-08 (el zombie `FLANK` vuelve a la política) | `flankingZombiesAlwaysUseTheFlankStrike` y `plansAndExecutesInTheSameDecision` |
| En la tabla, `BLOCKER` zombie `PRESS` `ZOMBIE_FRONT_STRIKE` con parcial 0,80 (la fila no suma 1) | `everyRowAddsUpToOne` |
| En `RunMetrics`, contar los planes 1 a 10 en vez de 11 a 20 | Agregá vos la prueba que la detecte, con nombre `lastTenMeansPlansElevenToTwenty`, sobre un `RunMetrics` armado a mano |

## Procedimiento

1. Rama `wp-11-simulacion` desde `main`.
2. CT-08 en `Brain`, las dos pruebas de brain. `./gradlew spotlessApply build`. Commit: `feat(domain): flanking zombies always use the flank strike`.
3. `build.gradle.kts`, el modelo, `LearningSimulation` y sus pruebas. `./gradlew spotlessApply build`. Commit: `test(simulation): add a learning simulation against blocking and open players`.
4. `CalibrationReport`. Corré `./gradlew simulationReport` y verificá que se generó `build/reports/simulation/calibration.md`. Commit: `test(simulation): add the calibration grid report`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-11: learning simulation and calibration`, esperar el check `build` en verde antes del informe.
8. **En el informe, pegá completo el contenido de `calibration.md`** y cuánto tardó `simulationReport`.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si `simulationReport` tarda más de 10 minutos, bajá a 20 semillas y avisalo.

**Si los resultados no cumplen los criterios, no cambies el modelo, el cerebro ni los umbrales para que pasen.** El objetivo es medir. La decisión de los valores la toman Nico y Opus con el informe.

## Fuera de alcance

- Modelar movimiento, vida de los mobs, retiradas o varios jugadores.
- Cambiar los valores por defecto de la configuración (se decide con el informe, en la puerta E3).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] CT-08 implementado, con sus dos pruebas.
- [ ] Las tablas del modelo son exactamente las del WP.
- [ ] `./gradlew build` no corre la calibración y sigue siendo rápido.
- [ ] `./gradlew simulationReport` genera `calibration.md`, y su contenido está en el informe.
- [ ] Ninguna función hace más de una tarea; todos los bucles acotados.
- [ ] Todas las pruebas obligatorias pasan; las pruebas que muerden fallaron y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
