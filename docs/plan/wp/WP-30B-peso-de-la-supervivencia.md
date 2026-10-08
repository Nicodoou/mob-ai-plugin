# WP-30B — Peso de la supervivencia según el jugador

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-30A (mergeado) |
| Modelo | Opus |
| Rama | `wp-30b-peso-de-la-supervivencia` |

## Objetivo

Segunda parte del CT-27, pedido de Nico. Contra un jugador muy bueno, el grupo tiene que valorar más **seguir vivo**, y el jugador se detecta solo.

- **Qué guarda el grupo:** por jugador, un **registro de peligro** con la vida que perdió el grupo y el daño que le hizo, sumados plan a plan y con el olvido de la memoria (vida media).
- **Nivel de peligro, de 0 a 1:** sale de cuánta vida pierde el grupo por cada punto de daño que le hace al jugador.
  - Por debajo de `danger-ratio-low` (2), el jugador es normal.
  - Desde `danger-ratio-high` (8), es muy bueno.
  - Con poca pelea, un daño de referencia (`danger-prior-damage`, 10) hace que el jugador cuente como normal hasta tener datos.
- **Pesos del éxito:** el peso de la supervivencia sube de `survival-weight` (0,2) a `survival-weight-max` (0,6) según el nivel de peligro. El resto se reparte entre daño y rapidez en la proporción configurada.
- **Persistencia:** el registro se guarda con la memoria del grupo, en la versión 2 del esquema, con migración desde la 1.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/memory/`: `GroupMemory`, `AttackRecord`, `StrategyObservation`
- `src/main/java/io/github/nicodoou/mobai/domain/group/`: `PlanScoring`, `PlanLifecycle`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`, `PlanScores.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/SuccessSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` (`scoring`, `closeAndEvaluate`)
- `src/main/java/io/github/nicodoou/mobai/application/`: `ClosePlan`, `RecordPlayerDeath`, `StoredMemoriesMapper`
- `src/main/java/io/github/nicodoou/mobai/domain/port/StoredGroup.java`, `StoredStrategyRecord.java`
- `src/main/java/io/github/nicodoou/mobai/persistence/`: `GroupFile`, `GroupFileMapper`, `RecordEntry`, `SchemaMigrator`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java`, `IncidentJson.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- Pruebas: `GroupMemoryTest`, `PlanScoringTest`, `ClosePlanTest`, `SchemaMigratorTest`, `JsonMemoryRepositoryTest`, `SettingsValidationTest`, `ConfigLoaderTest`, `testsupport/TestSettings`

## Reglas de negocio

1. **Registro de peligro** (`DangerRecord(healthLost, damageDealt, lastUpdateTick)`):
   - con cada plan cerrado contra un jugador, el registro de ese jugador primero **decae** al tick del cierre (como `AttackRecord`) y después suma la vida neta perdida por el grupo en el plan y el daño que le hizo;
   - lo hace `ClosePlan`, después de registrar la estrategia.
2. **Nivel de peligro** (`DangerLevel.of`), con el registro decaído al tick actual:
   - `previoDaño = danger-prior-damage`, `previoVida = danger-ratio-low × previoDaño`;
   - `razón = (healthLost + previoVida) ÷ (damageDealt + previoDaño)`;
   - `nivel = clamp((razón − low) ÷ (high − low), 0, 1)`;
   - sin registro, el nivel es 0.
3. **Pesos** (`SuccessWeights.forDanger`):
   - `supervivencia = survivalWeight + (survivalWeightMax − survivalWeight) × nivel`;
   - `resto = 1 − supervivencia`;
   - `daño = resto × damageWeight ÷ (damageWeight + speedWeight)` y `rapidez = resto − daño`;
   - si `damageWeight + speedWeight == 0`, daño y rapidez son 0.
4. **Al cerrar un plan,** el nivel se calcula con el registro **anterior** al plan: el cierre usa lo que el grupo sabía antes. `ClosedPlan` guarda el nivel usado y la vida neta perdida.
5. **El log de debug** suma `danger=%.2f` a la línea `PLAN`.
6. **Persistencia:**
   - `StoredGroup` y el archivo del grupo llevan los registros de peligro;
   - el esquema pasa a la **versión 2**: un archivo de grupo en versión 1 se migra agregando `"dangerRecords": []`, y el de estado solo cambia el número;
   - los incidentes pasan a la versión 2, y uno viejo se rechaza con el mensaje de versión de siempre.
7. **Configuración** (sección `success`, claves nuevas):
   - `survival-weight-max: 0.6`: entre `survival-weight` y 1;
   - `danger-ratio-low: 2.0`: positiva;
   - `danger-ratio-high: 8.0`: mayor que `danger-ratio-low`;
   - `danger-prior-damage: 10.0`: positiva.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/memory/DangerRecord.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/memory/DangerObservation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/memory/MemoryRecords.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/DangerLevel.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/SuccessWeights.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanScoring.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/SuccessSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/ClosePlan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/RecordPlayerDeath.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/StoredMemoriesMapper.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredDangerRecord.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredGroup.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/DangerEntry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/persistence/GroupFile.java`, `GroupFileMapper.java`, `SchemaMigrator.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java`, `IncidentJson.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `docs/requerimientos.md`, `docs/arquitectura.md` (ver «Documentos») |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/memory/DangerRecordTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/DangerLevelTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/SuccessWeightsTest.java` |
| Modificar | las pruebas de «Pruebas obligatorias» y «Pruebas existentes que cambian» |

Antes de empezar, buscá con grep `GroupMemory.restore(`, `new StoredGroup(`, `new GroupFile(`, `new ClosedPlan(`, `new PlanScoring(`, `new SuccessSettings(` y `CURRENT_VERSION` en `src/`. Si aparece un archivo de `src/main` fuera de la tabla, frená y reportá.

**Métodos públicos nuevos en `GroupMemory`:** `recordDanger`, `dangerRecord` y `dangerRecords`. Si alguna clase pasa de 20, frená y reportá.

## Especificación

### `DangerRecord.java`, `DangerObservation.java` y `MemoryRecords.java` (`domain.memory`)

```java
/** How much health a group loses to a player per point of damage it deals them (CT-27). */
public record DangerRecord(double healthLost, double damageDealt, long lastUpdateTick) {
  // validations: both zero or positive and finite; lastUpdateTick zero or positive (AttackRecord's messages)
  public static DangerRecord empty(long tick) { … }
  public DangerRecord decayedTo(long tick, long halfLifeTicks) { … }   // same rule and error as AttackRecord
  public DangerRecord withPlan(double planHealthLost, double planDamageDealt) { … }
}
```

```java
public record DangerObservation(PlayerId player, double healthLost, double damageDealt, long tick) { … }
```

Valida `player` no nulo y los dos números en cero o más, finitos, con mensajes `DangerObservation.<campo> must be zero or positive, got …`.

```java
/** Every kind of record a group memory restores from storage. */
public record MemoryRecords(
    Map<PlayerId, Map<Attack, AttackRecord>> attackRecords,
    Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords,
    Map<PlayerId, DangerRecord> dangerRecords) { … requireNonNull … }
```

### `GroupMemory.java`

- Campo `Map<PlayerId, DangerRecord> dangerRecords = new HashMap<>()`.
- `restore(Supplier<MemorySettings> settings, MemoryRecords records)` reemplaza a la versión de tres parámetros y restaura también el peligro.
- `public void recordDanger(DangerObservation observation)`: decae el registro del jugador (o uno vacío) al tick de la observación y le suma la del plan.
- `public DangerRecord dangerRecord(PlayerId player, long tick)`: el registro decaído a `tick` sin guardarlo, o `DangerRecord.empty(tick)` (como `strategyEstimate`, que lee sin guardar).
- `public Map<PlayerId, DangerRecord> dangerRecords()`: una copia inmodificable.
- `clearPlayer` y `clear` también borran el peligro.

### `DangerLevel.java` y `SuccessWeights.java` (`domain.group`, puras)

```java
/** How dangerous a player is to this group, from 0 (ordinary) to 1 (very good) (CT-27). */
public final class DangerLevel {
  private DangerLevel() {}

  public static double of(DangerRecord record, SuccessSettings settings) { … }   // regla 2
}
```

```java
/** The weights a closed plan is scored with: survival weighs more against dangerous players. */
public record SuccessWeights(double damage, double speed, double survival) {
  public static SuccessWeights forDanger(SuccessSettings settings, double danger) { … }   // regla 3
}
```

### `PlanScoring.java`

- El constructor pasa a ser `PlanScoring(PlanSettings plan, SuccessSettings success, double danger)`. `danger` tiene que estar entre 0 y 1; si no, `IllegalArgumentException` (`PlanScoring.danger must be between 0.0 and 1.0, got …`).
- `successOf` usa `SuccessWeights.forDanger(success, danger)`.
- `public double danger()` devuelve el nivel.
- `public double healthLostOf(Plan plan)` devuelve la vida neta perdida (la `netHealthLost` que hoy es privada).

### `ClosedPlan.java` y `PlanLifecycle.java`

- **`ClosedPlan`:** dos componentes nuevos después de `scores`, `double danger` (entre 0 y 1) y `double groupHealthLost` (cero o más).
- **`PlanLifecycle.closedPlan`:** los llena con `scoring.danger()` y `scoring.healthLostOf(plan)`.

### `Brain.java` y `RecordPlayerDeath.java`

`scoring()` pasa a recibir el plan que se cierra y su tick:

```java
  private PlanScoring scoring(Turn turn) {
    long tick = turn.snapshot().tick();
    DangerRecord record =
        turn.group().memory().dangerRecord(currentPlan(turn).target(), tick);
    SuccessSettings success = settings.get().success();
    return new PlanScoring(settings.get().plan(), success, DangerLevel.of(record, success));
  }
```

`RecordPlayerDeath` hace lo mismo con `group.memory().dangerRecord(player, tick)` y `settings.current()`.

### `ClosePlan.java`

Después de `recordStrategy`:

```java
group.memory().recordDanger(
    new DangerObservation(plan.target(), plan.groupHealthLost(), plan.damageDealt(), plan.endTick()));
```

Sigue devolviendo el `RecordChange` de la estrategia.

### Persistencia

**`domain.port`:**
- `StoredDangerRecord(PlayerId player, DangerRecord record)`.
- `StoredGroup` suma `List<StoredDangerRecord> dangerRecords` **al final**, copiado con `List.copyOf` y sin repetir jugador (`requireDistinct`, etiqueta `"dangerRecords"`).

**`StoredMemoriesMapper`:**
- `toStored` agrega los registros ordenados por jugador (`BY_PLAYER`);
- `restoredKnowledge` arma `MemoryRecords` con los tres mapas.

**`persistence`:**
- `DangerEntry(String playerId, double healthLost, double damageDealt, long lastUpdateTick)`.
- `GroupFile` suma `List<DangerEntry> dangerRecords` al final.
- `GroupFileMapper` lo mapea en los dos sentidos. Al leer, `required(file.dangerRecords(), "GroupFile.dangerRecords")`, porque la migración garantiza el campo.

**`SchemaMigrator`:**
- `CURRENT_VERSION = 2`;
- en `migrate`, un archivo en versión 1 pasa por `fromVersionOne(JsonObject)`. Si tiene `members` (es un archivo de grupo) y no tiene `dangerRecords`, le agrega `"dangerRecords": []`. En todos los casos pone `schemaVersion` en 2;
- borrá el comentario "Migrations … will go here" y dejá en su lugar `// One step per version, oldest first.`

**`IncidentJson`:** `CURRENT_VERSION = 2`.

### `SuccessSettings`, `ConfigLoader` y `config.yml`

**`SuccessSettings`:**
- Componentes nuevos, al final: `double survivalWeightMax, double dangerRatioLow, double dangerRatioHigh, double dangerPriorDamage`.
- Validaciones:
  - `requireBetween("SuccessSettings.survivalWeightMax", survivalWeightMax, survivalWeight, 1)`;
  - `requirePositive` para `dangerRatioLow` y `dangerPriorDamage`;
  - si `dangerRatioHigh <= dangerRatioLow`, `IllegalArgumentException` con el mensaje `SuccessSettings.dangerRatioHigh must be greater than dangerRatioLow, got <high> <= <low>`.

**`ConfigLoader`:** lee las cuatro claves nuevas en ese orden.

**`config.yml`, debajo de `reference-kill-ticks`:**

```yaml
  # Contra jugadores muy buenos, el peso de la supervivencia sube de survival-weight hasta este valor.
  survival-weight-max: 0.6
  # Vida que pierde el grupo por cada punto de daño que le hace al jugador: desde low el peso empieza
  # a subir; en high llega al máximo.
  danger-ratio-low: 2.0
  danger-ratio-high: 8.0
  # Daño de referencia: con poca pelea, el jugador cuenta como normal.
  danger-prior-damage: 10.0
```

### `DebugLog.java`

La línea `PLAN` suma `danger=%.2f` después de `scores=…`.

### Documentos

- **`docs/requerimientos.md`:** al párrafo «Éxito de un plan (CT-27)» de RF-06 sumale:

  > Contra un jugador muy bueno pesa más la supervivencia: el grupo guarda por jugador, con olvido, la vida que pierde por cada punto de daño que le hace; de 2 a 8, el peso de la supervivencia sube de 0,2 a 0,6.

- **`docs/arquitectura.md`:** si hay una tabla de la memoria o de los archivos guardados, sumá el registro de peligro y la versión 2 del esquema. Si no encontrás dónde, avisá en el informe.

## Pruebas obligatorias

### `DangerRecordTest` (3)

| Prueba | Verifica |
| --- | --- |
| `decayHalvesBothAfterAHalfLife` | `(40, 10, 1000)` decaído a 13.000 con vida media 12.000: `(20, 5, 13000)` |
| `plansAddUp` | `empty(0).withPlan(10, 4).withPlan(6, 2)`: `(16, 6)` |
| `rejectsNegativeValues` | `healthLost` −1 lanza, con su mensaje |

### `DangerLevelTest` (4), con `TestSettings` (low 2, high 8, previo 10)

| Prueba | Registro | Nivel |
| --- | --- | --- |
| `noFightingMeansOrdinary` | vacío | 0 |
| `halfwayRatioIsHalfDanger` | perdida 80, daño 10 → (80 + 20) ÷ (10 + 10) = 5 | 0,5 |
| `highRatioIsFullDanger` | perdida 140, daño 10 → 160 ÷ 20 = 8 | 1 |
| `dangerIsCapped` | perdida 1.000, daño 10 | 1 |

### `SuccessWeightsTest` (4), con `TestSettings` (0,4/0,4/0,2, máximo 0,6)

| Prueba | Nivel | Pesos |
| --- | --- | --- |
| `ordinaryPlayerKeepsTheConfiguredWeights` | 0 | 0,4 / 0,4 / 0,2 |
| `veryGoodPlayerRaisesSurvivalToItsMax` | 1 | 0,2 / 0,2 / 0,6 |
| `halfDangerIsHalfway` | 0,5 | 0,3 / 0,3 / 0,4 |
| `unevenWeightsKeepTheirProportion` | 1, con pesos 0,6/0,2/0,2 y máximo 0,6 (un `SuccessSettings` propio de la prueba) | 0,3 / 0,1 / 0,6 |

### Pruebas que se suman a clases existentes

| Prueba | Clase | Verifica |
| --- | --- | --- |
| `successUsesTheDangerWeights` | `PlanScoringTest` | `PlanScores(1, 0.5, 0.25)` con nivel 1: 0,2 + 0,1 + 0,15 = 0,45 |
| `dangerIsRecordedPerPlayerAndDecays` | `GroupMemoryTest` | `recordDanger` de 40/10 en el tick 1.000 para Alice; `dangerRecord(Alice, 13000)` es 20/5; Bob está vacío |
| `clearPlayerForgetsTheDanger` | `GroupMemoryTest` | después de `clearPlayer(Alice)`, el registro de Alice está vacío |
| `closingAPlanRecordsTheDanger` | `ClosePlanTest` | un `PlanClosed` con `groupHealthLost` 12 y daño 3 deja el registro del objetivo en 12/3 |
| `versionOneGroupFileGetsEmptyDangerRecords` | `SchemaMigratorTest` | un archivo de grupo v1 sin `dangerRecords` sale en v2 con la lista vacía; uno de estado v1 sale en v2 sin ese campo |
| `dangerRecordsSurviveSaveAndLoad` | `JsonMemoryRepositoryTest` | un `StoredGroup` con un registro de peligro vuelve igual después de guardar y cargar |

### `SettingsValidationTest` (+3 casos)

| Caso | Mensaje |
| --- | --- |
| `survivalWeightMax` 0,1 (con `survival-weight` 0,2) | `SuccessSettings.survivalWeightMax must be between 0.2 and 1.0, got 0.1` |
| `dangerRatioHigh` 2,0 (con low 2,0) | `SuccessSettings.dangerRatioHigh must be greater than dangerRatioLow, got 2.0 <= 2.0` |
| `dangerPriorDamage` 0 | el mensaje de `requirePositive` |

Total: **17 pruebas nuevas y 3 casos**.

### Pruebas existentes que cambian

| Archivo | Cambio |
| --- | --- |
| `TestSettings` | `new SuccessSettings(0.4, 0.4, 0.2, 600, 0.6, 2.0, 8.0, 10.0)`; `scoring()` pasa el nivel 0 |
| `ConfigLoaderTest`, `RecordOutcomeTest`, `RecruitMobTest`, `SettingsHolderTest`, `SettingsValidationTest`, `LearningSimulation` | el mismo `SuccessSettings` |
| Cada `new ClosedPlan(…)` | suma `0, 0` (nivel y vida perdida) después de `scores`, salvo que la prueba los compare |
| `GroupMemoryTest.restoreKeepsTheRecords` | `restore` con `MemoryRecords`, incluido el peligro |
| `new StoredGroup(…)` y `new GroupFile(…)` en pruebas | suman la lista de peligro (vacía, salvo la prueba nueva) |
| `SchemaMigratorTest`, `JsonMemoryRepositoryTest`, incidentes | donde se espera la versión 1, pasa a 2. **Anotá en el informe cada cambio de este tipo** |
| `DebugLogTest` | la línea `PLAN` con `danger=0.00` |

Si un archivo de memoria o un incidente grabado en `src/test/resources` deja de cargar por la versión, actualizalo **con la migración** (no a mano) y avisá. Si una prueba del cerebro o de la simulación cambia por algo que no sea el número del éxito, **frená y reportá**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `DangerLevel`, sin el previo (`healthLost ÷ damageDealt`) | vacío → división 0/0; 80/10 = 8 → 1 | `noFightingMeansOrdinary` o `halfwayRatioIsHalfDanger` |
| 2 | En `SuccessWeights`, repartir el resto en partes iguales sin mirar los pesos | 0,2 / 0,2 / 0,6 | `unevenWeightsKeepTheirProportion` |
| 3 | En `GroupMemory.recordDanger`, no decaer antes de sumar | 40/10 + 40/10 un período después da 80/20 en vez de 60/15 | sumá ese paso a `dangerIsRecordedPerPlayerAndDecays` |
| 4 | En `SchemaMigrator`, no agregar `dangerRecords` | la carga lanza `GroupFile.dangerRecords` | `versionOneGroupFileGetsEmptyDangerRecords` |
| 5 | En `ClosePlan`, no registrar el peligro | el registro queda vacío | `closingAPlanRecordsTheDanger` |

## Verificación en el server (Nico, después del merge)

Opus suma las claves nuevas al `config.yml` del server de prueba.

- Peleá contra el **mismo grupo** varias veces. En el `mobai-debug.log`, el `danger=` de cada `PLAN` sube si el grupo pierde mucho y te hace poco daño.
- Al reiniciar el server, `danger` sigue donde estaba, porque se guarda con la memoria.
- Los archivos de memoria viejos cargan bien, con la migración a la versión 2.

## Procedimiento

1. Rama `wp-30b-peso-de-la-supervivencia` desde `origin/main` actualizado.
2. `DangerRecord`, `DangerObservation`, `MemoryRecords`, `GroupMemory` y sus pruebas. Commit: `feat: per-player danger records in the group memory`.
3. `SuccessSettings`, configuración, `DangerLevel`, `SuccessWeights`, `PlanScoring`, `ClosedPlan`, `PlanLifecycle`, `Brain`, `RecordPlayerDeath`, `ClosePlan`, `DebugLog` y sus pruebas. Commit: `feat: survival weighs more against dangerous players (CT-27)`.
4. Persistencia (`StoredDangerRecord`, `StoredGroup`, `StoredMemoriesMapper`, `DangerEntry`, `GroupFile`, `GroupFileMapper`, `SchemaMigrator`, `IncidentJson`), sus pruebas y los documentos. Commit: `feat: save danger records, memory schema version 2`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-30B: survival weight by player danger (CT-27)`, CI en verde e informe con la prueba exacta que falló en cada rotura y la lista de valores esperados o versiones que cambiaste.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Ajustar valores esperados del éxito y números de versión en pruebas existentes, informándolos.

## Fuera de alcance

- Mostrar el peligro en `/mobai memory` (WP-26).
- La retirada aprendida (WP-30C).
- Que el peligro afecte la elección de objetivo.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 17 pruebas nuevas y los 3 casos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
