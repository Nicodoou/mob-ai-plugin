# WP-23B — Golpe esquivo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24D (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-23b-golpe-esquivo` |

## Objetivo

CT-19, pedido de Nico (opción 2): un ataque nuevo de zombie, **`zombie.evasive_strike`**, que la memoria aprende como los otros. El zombie al que el cerebro se lo sugiere lee la carga del arma del jugador:

- si el jugador **lo está mirando**, lo tiene **a su alcance** y su golpe está **cargado**, el zombie **retrocede** lo justo para salir del alcance;
- cuando el arma del jugador **no está cargada** (acaba de pegar, o pega rápido sin cargar), o el jugador no lo mira, el zombie **entra y pega**. Ese golpe se registra como `ZOMBIE_EVASIVE_STRIKE`.

Contra un hacha (1 s de carga) rinde; contra quien pega rápido sin cargar, el esquive casi no se activa. Esa diferencia es lo que la memoria tiene que aprender.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (método `attack`)
- `src/main/resources/config.yml` (sección `attack`)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java`, `PatientWait.java`, `PatientMove.java`, `PlayerStance.java`, `Waypoints.java`, `MeleeRhythm.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/shared/AttackTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/brain/AttackSuggesterTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `src/test/java/io/github/nicodoou/mobai/simulation/OutcomeModel.java`, `src/test/java/io/github/nicodoou/mobai/adapter/goal/PatientWaitTest.java`, `WaypointsTest.java`

## Reglas de negocio

1. **El ataque:** `Attack.ZOMBIE_EVASIVE_STRIKE("zombie.evasive_strike", MobKind.ZOMBIE)`, **después** de `ZOMBIE_PATIENT_STRIKE` en el enum. El cerebro lo ofrece solo, como un candidato más del zombie: no hay que tocar el cerebro. Las memorias guardadas siguen sirviendo, porque el id es nuevo.
2. **Configuración** (claves nuevas en `attack`):
   - `evasive-charge-threshold: 0.8`: carga del arma desde la que el golpe del jugador se considera cargado (de 0 a 1);
   - `evasive-distance-blocks: 3.5`: hasta dónde retrocede el zombie. El alcance del jugador es 3, más medio bloque.
3. **Amenaza** (`PlayerThreat`), leída cada vez que el zombie esquivo decide:
   - `SAFE`: el jugador **no** lo mira (el zombie está fuera de su vista de 120°, `isOutOfSight`, CT-16) **o** su `getAttackCooldown()` es menor que el umbral;
   - `IN_DANGER`: lo mira, está cargado y el zombie está a `evasive-distance-blocks` o menos, en horizontal;
   - `AT_THE_EDGE`: lo mira, está cargado y el zombie ya está más lejos.
4. **Qué hace** (`EvasiveWait`, `EvasiveMove`):
   - `SAFE` → `STRIKE_EVASIVE`: se acerca y, en alcance y con el intervalo cumplido, pega y registra `ZOMBIE_EVASIVE_STRIKE`. La espera se reinicia.
   - `IN_DANGER` → `BACK_OFF`: retrocede hasta `evasive-distance-blocks` del jugador, en la dirección opuesta. No pega.
   - `AT_THE_EDGE` → `HOLD`: se queda quieto mirando, fuera del alcance.
   - **Se cansa:** si lleva `attack.patient-strike-max-wait-ticks` (60, 3 s) esquivando o esperando sin que el jugador se descuide, pasa a `CHARGE`: entra y pega de frente, y lo registra como `ZOMBIE_FRONT_STRIKE`, que es lo que ejecutó. Es lo mismo que el abandono del golpe paciente (CT-17). Sigue cargando hasta que pega; después vuelve a esquivar.
   - En `CHARGE`, si el jugador se descuida (`SAFE`), vuelve a `STRIKE_EVASIVE`.
5. **Cuándo se reacciona:** la decisión se toma en cada tick del goal, no cada 10. El esquive tiene que ser rápido: la carga de una espada dura 0,6 s. La orden de retroceder (`moveTo`) se da solo cuando el movimiento cambia a `BACK_OFF`, o cada 10 ticks mientras sigue en `BACK_OFF`, para no recalcular el camino en cada tick.
6. **Todo con el reloj del plugin** (regla B-01).

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PlayerThreat.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveMove.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/EvasiveWait.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Modificar | `docs/catalogo-mvp.md` (fila nueva en la tabla de ataques del zombie) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/shared/AttackTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/AttackSuggesterTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/simulation/OutcomeModel.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/EvasiveWaitTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |

Antes de empezar:
- buscá con grep `new AttackSettings(` en `src/`: si aparece fuera de los archivos de la tabla, frená y reportá;
- buscá que los nombres de las pruebas nuevas no existan ya en sus archivos (regla del WP-24A).

**Métodos públicos nuevos:** `Waypoints.evadePoint` (11 en total).

## Especificación

API de Paper: `HumanEntity.getAttackCooldown()` (ya usada en `PressGoal` y `ShootGoal`). Nada nuevo de Paper.

### `Attack.java`

```java
  ZOMBIE_PATIENT_STRIKE("zombie.patient_strike", MobKind.ZOMBIE),
  ZOMBIE_EVASIVE_STRIKE("zombie.evasive_strike", MobKind.ZOMBIE),
```

### `AttackSettings.java`

Dos componentes nuevos **al final**: `double evasiveChargeThreshold, double evasiveDistanceBlocks`, con:

```java
    SettingsChecks.requireBetween(
        "AttackSettings.evasiveChargeThreshold", evasiveChargeThreshold, 0, 1);
    SettingsChecks.requirePositive("AttackSettings.evasiveDistanceBlocks", evasiveDistanceBlocks);
```

### `ConfigLoader.java` y `config.yml`

`attack(…)` suma `number(root, "attack.evasive-charge-threshold")` y `number(root, "attack.evasive-distance-blocks")` al final. En `config.yml`, sección `attack`, al final:

```yaml
  # Golpe esquivo: carga del arma del jugador (0 a 1) desde la que el zombie se aparta, y hasta dónde.
  evasive-charge-threshold: 0.8
  evasive-distance-blocks: 3.5
```

### Pruebas existentes que cambian por el ataque o la configuración nueva

| Archivo | Cambio |
| --- | --- |
| `TestSettings` | `new AttackSettings(60, 60, 60, 8.0, 15.0, 4.0, 16.0, 0.8, 3.5)` |
| `ConfigLoaderTest` | el `AttackSettings` esperado suma `0.8, 3.5` al final |
| `SettingsValidationTest` | `boundaryValuesAreAccepted`: `new AttackSettings(1, 1, 1, 8.0, 8.0, 1.0, 1.0, 0.0, 0.1)` y otra con `1.0` de umbral; el caso de `shootMinDistanceBlocks` suma `0.8, 3.5`. Casos nuevos en `outOfRangeValuesProvider`: umbral `1.5` → `AttackSettings.evasiveChargeThreshold must be between 0.0 and 1.0, got 1.5`; distancia `0` → el mensaje de `requirePositive` para `AttackSettings.evasiveDistanceBlocks` (copialo del texto que arma `SettingsChecks`) |
| `AttackTest` | `forKindListsAttacksInCatalogOrder`: los ataques de zombie son `FRONT`, `FLANK`, `PATIENT`, `EVASIVE` |
| `AttackSuggesterTest` | `zombieChoosesAmongItsThreeAttacks` pasa a llamarse `zombieChoosesAmongItsFourAttacks` y espera los cuatro |
| `OutcomeModel` | Filas nuevas para `ZOMBIE_EVASIVE_STRIKE` con rol `PRESS`: `BLOCKER` `new OutcomeOdds(0.05, 0.60, 0.15, 0.20)` (contra el escudo casi siempre se cansa y pega al escudo); `OPEN` `new OutcomeOdds(0.65, 0, 0.35, 0)` |

Si con el candidato nuevo alguna prueba de simulación o del cerebro deja de pasar (`LearningSimulationTest`, `BrainInvariantsTest`, `BrainObservingTest`…), **frená y reportá** cuál y el mensaje. No la cambies.

### `PlayerThreat.java`, `EvasiveMove.java` y `EvasiveWait.java` (Java puro, `adapter.goal`)

```java
/** How dangerous the player is to a zombie trying to dodge its charged hits. */
public enum PlayerThreat {
  SAFE,
  IN_DANGER,
  AT_THE_EDGE
}
```

```java
/** What an evasive zombie does this tick. */
public enum EvasiveMove {
  STRIKE_EVASIVE,
  BACK_OFF,
  HOLD,
  CHARGE
}
```

```java
/** Dodges the player's charged hits and strikes when the player is off guard (CT-19). */
final class EvasiveWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;
  private boolean charging;

  EvasiveMove next(PlayerThreat threat, long now, long maxWaitTicks) {
    if (threat == PlayerThreat.SAFE) {
      charging = false;
      waitStartTick = NOT_WAITING;
      return EvasiveMove.STRIKE_EVASIVE;
    }
    if (charging) {
      return EvasiveMove.CHARGE;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
    } else if (now - waitStartTick >= maxWaitTicks) {
      charging = true;
      waitStartTick = NOT_WAITING;
      return EvasiveMove.CHARGE;
    }
    return threat == PlayerThreat.IN_DANGER ? EvasiveMove.BACK_OFF : EvasiveMove.HOLD;
  }

  // After any hit the zombie dodges again from scratch.
  void struck() {
    charging = false;
    waitStartTick = NOT_WAITING;
  }
}
```

### `Waypoints.java`: un método nuevo

```java
  // Empty once the zombie is already out of the player's reach.
  public Optional<Vec3> evadePoint(Vec3 mobPosition, Vec3 dangerPosition) {
    double missing =
        settings.get().evasiveDistanceBlocks() - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }
```

### `PressGoal.java`

Campos nuevos: `private final EvasiveWait evasion = new EvasiveWait();` y `private EvasiveMove lastEvasiveMove = EvasiveMove.HOLD;`.

`pressOn(order, target)` pasa a:

```java
  private void pressOn(RoleAssignment order, Player target) {
    mob.lookAt(target);
    if (isEvasive(order)) {
      evade(target);
      return;
    }
    followIfDue(target);
    strikeIfReady(order, target);
  }
```

Funciones nuevas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `boolean isEvasive(RoleAssignment order)` | `kind == MobKind.ZOMBIE && order.suggestedAttack().equals(Optional.of(Attack.ZOMBIE_EVASIVE_STRIKE))` |
| `void evade(Player target)` | `EvasiveMove move = evasion.next(threatOf(target), ahora, attack().patientStrikeMaxWaitTicks())`; `switch (move)`: `STRIKE_EVASIVE -> engage(target, Attack.ZOMBIE_EVASIVE_STRIKE)`; `CHARGE -> engage(target, Attack.ZOMBIE_FRONT_STRIKE)`; `BACK_OFF -> backOff(target)`; `HOLD -> mob.getPathfinder().stopPathfinding()`. Al final, `lastEvasiveMove = move` |
| `void engage(Player target, Attack attack)` | `followIfDue(target)`; si `rhythm.canStrike(distancia)`: `context.tools().weapons().melee().strike(mob, target, attack)`, `rhythm.markStrike()` y `evasion.struck()` |
| `PlayerThreat threatOf(Player target)` | Regla 3: `watching = !waypoints().isOutOfSight(PoseReader.poseOf(target), posición del mob)`; `charged = target.getAttackCooldown() >= attack().evasiveChargeThreshold()`; si `!watching \|\| !charged` → `SAFE`; si la distancia horizontal es `<= attack().evasiveDistanceBlocks()` → `IN_DANGER`; si no, `AT_THE_EDGE` |
| `void backOff(Player target)` | Si el movimiento anterior no era `BACK_OFF` **o** `rhythm.shouldRepath()`: `waypoints().evadePoint(posición del mob, posición del jugador)`; con punto, `moveTo(new Location(mob.getWorld(), …), WALK_SPEED)` y `rhythm.markRepath()`; sin punto, `stopPathfinding()` |
| `AttackSettings attack()` y `Waypoints waypoints()` | `context.tools().timing().attack().get()` y `context.tools().waypoints()` |

Nota: `backOff` no lleva un parámetro booleano (regla del código). Compara `lastEvasiveMove != EvasiveMove.BACK_OFF` adentro. En el `switch` queda `BACK_OFF -> backOff(target)`.

El Javadoc de la clase pasa a: `/** PRESS: walk to the target and strike it head-on, wait for an opening if patient, or dodge its charged hits if evasive. */`.

### `docs/catalogo-mvp.md`

Fila nueva en la tabla de ataques del zombie, después del golpe paciente:

`| Golpe esquivo (`zombie.evasive_strike`) | Si el jugador lo mira con el golpe cargado y lo tiene a su alcance, retrocede fuera del alcance; entra y pega cuando el arma del jugador no está cargada o deja de mirarlo; a los 3 s pega igual, de frente (CT-19) | Castiga al jugador que carga golpes fuertes y lentos |`

## Pruebas obligatorias

### `EvasiveWaitTest` (7)

Espera máxima 60.

| Prueba | Secuencia (`next(amenaza, tick)`) | Verifica |
| --- | --- | --- |
| `offGuardPlayerGetsStruck` | `SAFE`, 1000 | `STRIKE_EVASIVE` |
| `chargedPlayerWatchingCloseMakesItBackOff` | `IN_DANGER`, 1000 | `BACK_OFF` |
| `chargedPlayerWatchingFromAfarMakesItHold` | `AT_THE_EDGE`, 1000 | `HOLD` |
| `waitingTooLongTurnsIntoACharge` | `IN_DANGER` en 1000 y 1059; `AT_THE_EDGE` en 1060; `IN_DANGER` en 1070 | `BACK_OFF`, `BACK_OFF`, `CHARGE`, `CHARGE` (sigue cargando) |
| `aHitEndsTheCharge` | lo anterior hasta 1060; `struck()`; `IN_DANGER` en 1065 | `BACK_OFF` (espera nueva) |
| `anOffGuardMomentEndsTheChargeAndTheWait` | `IN_DANGER`, 1000; `SAFE`, 1030; `IN_DANGER` en 1050, 1109 y 1110 | `BACK_OFF`; `STRIKE_EVASIVE`; `BACK_OFF`, `BACK_OFF`, `CHARGE` |
| `safeDuringAChargeStrikesEvasively` | cargando desde 1060 como en `waitingTooLongTurnsIntoACharge`; `SAFE` en 1070; `IN_DANGER` en 1075 | `STRIKE_EVASIVE`; `BACK_OFF` (la carga terminó) |

### `WaypointsTest` (+2)

Con `TestSettings` (`evasive-distance-blocks` 3,5).

| Prueba | Verifica |
| --- | --- |
| `evadePointStepsOutOfReach` | zombie `(0,64,2)`, jugador `(0,64,0)`: `(0, 64, 3.5)` |
| `zombieAlreadyOutOfReachDoesNotMove` | zombie `(0,64,3.5)`: vacío; `(0,64,5)`: vacío |

Total: **9 pruebas nuevas**, más las existentes de la tabla de arriba actualizadas (2 casos nuevos de validación y la prueba renombrada del sugeridor).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `next`, sin `charging = false` en la rama de `SAFE` | en 1075 da `CHARGE` | `safeDuringAChargeStrikesEvasively` |
| 2 | En `next`, `>` en vez de `>=` | en 1060 da `HOLD` | `waitingTooLongTurnsIntoACharge` |
| 3 | `struck()` sin `charging = false` | en 1065 da `CHARGE` | `aHitEndsTheCharge` |
| 4 | En `evadePoint`, `missing < 0` | con 3,5 justos `CombatGeometry` lanza por distancia 0 | `zombieAlreadyOutOfReachDoesNotMove` |

## Verificación en el server (Nico, después del merge)

Hay que sumar las dos claves nuevas al `config.yml` del server de prueba (lo hace Opus al mergear). Con `/mobai debug all full`, de noche, con un **hacha** (`/give @s iron_axe`):

1. Mirando a un zombie esquivo con el hacha cargada (esperá 1 s sin pegar): el zombie se aparta a unos 3,5 bloques y espera.
2. Pegás al aire: el zombie entra enseguida y te pega mientras el hacha recarga. En `mobai-debug.log`: `attack=zombie.evasive_strike`.
3. Si te quedás cargado y mirándolo más de 3 s, entra igual y pega de frente (`zombie.front_strike`).
4. Con espada y pegando rápido, el zombie esquivo casi no se aparta.

No todos los zombies son esquivos: el cerebro elige entre los cuatro golpes según lo que aprendió de vos.

## Procedimiento

1. Rama `wp-23b-golpe-esquivo` desde `origin/main` actualizado.
2. `Attack`, `AttackSettings`, `ConfigLoader`, `config.yml`, `catalogo-mvp.md` y las pruebas existentes de la tabla. Commit: `feat: evasive strike in the catalog and its settings (CT-19)`.
3. `PlayerThreat`, `EvasiveMove`, `EvasiveWait`, `Waypoints.evadePoint` y sus pruebas. Commit: `feat: evasive wait that dodges charged hits`.
4. `PressGoal`. Commit: `feat: evasive zombies in the press goal`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-23B: evasive strike (CT-19)`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función de `PressGoal` pasa las 20 líneas, separala en otra privada con una tarea y avisalo.
3. Si alguna prueba existente fuera de la tabla deja de pasar, frená y reportá.

## Fuera de alcance

- Cambiar el cerebro, el sugeridor o la política: el ataque entra como un candidato más.
- La andanada (CT-23).
- Migrar los `config.yml` existentes (el del server de prueba lo actualiza Opus).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 9 pruebas nuevas con sus nombres exactos y las existentes actualizadas, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] El ataque registrado es siempre el ejecutado: esquivo cuando el jugador está descuidado, frontal al cansarse.
- [ ] Build, cobertura y CI en verde.
