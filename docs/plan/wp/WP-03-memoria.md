# WP-03 — Memoria con olvido

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E2 Dominio: aprendizaje |
| Depende de | WP-02 |
| Modelo | Sonnet |
| Rama | `wp-03-memoria` |

## Objetivo

Implementar la memoria de un grupo: cuántos éxitos e intentos tuvo cada ataque y cada estrategia contra cada jugador, con olvido por vida media, y la estimación de éxito (los parámetros de la Beta) que combina esos datos con los intentos virtuales de la velocidad de aprendizaje.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente que vas a usar (solo leer, no modificar):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/PlayerId.java`, `Attack.java`, `MobKind.java`, `StrategyId.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/MemorySettings.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Las reglas de negocio (de RF-06, resumidas)

1. **Registro.** Por cada (jugador, ataque) y (jugador, estrategia) se guardan éxitos e intentos **reales**, como números decimales.
2. **Olvido.** Éxitos e intentos se multiplican por `0,5 ^ (ticks transcurridos / vida media)`. El olvido se aplica de forma perezosa: al leer o al registrar, se lleva el registro al tick actual.
3. **Observación.** Una observación suma `crédito × peso` a los éxitos y `peso` a los intentos. Crédito: acierto 1, parcial 0,5 (configurable), fallo 0, o la fracción de éxito de un plan. Peso: 1 normalmente; 0,5 para observadores (fase 2).
4. **Intentos virtuales.** `V = 50 − 48 × velocidad de aprendizaje`. No se guardan ni se olvidan: se suman al leer, mitad como éxitos y mitad como fallos. Con un registro vacío, la Beta queda en (V/2, V/2), o sea 50 %. El olvido devuelve cada registro hacia ese 50 %.
5. **Estimación.** `alfa = éxitos + V/2`, `beta = fallos + V/2`, con `fallos = intentos − éxitos`. Media = `alfa / (alfa + beta)`.

Valores de referencia que las pruebas verifican (vienen de `requerimientos.md`):

| Caso | Resultado |
| --- | --- |
| Velocidad 1 (V = 2), 1 acierto de 1 | Media 2/3 ≈ 0,667 |
| Velocidad 1 (V = 2), 10 aciertos de 10 | Media 11/12 ≈ 0,917 |
| Velocidad 0 (V = 50), 10 aciertos de 10 | Media 35/60 ≈ 0,583 |
| Velocidad 0,7 | V = 16,4 |
| Una vida media sin pelear | Éxitos e intentos reales a la mitad |

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/memory/AttackRecord.java` |
| Crear | `domain/memory/LearningPrior.java` |
| Crear | `domain/memory/SuccessEstimate.java` |
| Crear | `domain/memory/AttackObservation.java`, `StrategyObservation.java`, `RecordChange.java` |
| Crear | `domain/memory/GroupMemory.java` |
| Crear (prueba) | `domain/memory/AttackRecordTest.java`, `LearningPriorTest.java`, `GroupMemoryTest.java` |

## Especificación

Paquete `io.github.nicodoou.mobai.domain.memory`. Los mensajes de las excepciones tienen que ser exactamente los indicados; los `double` se imprimen por concatenación (`"" + valor`).

### 1. `AttackRecord`

Valor inmutable: los éxitos e intentos reales de un ataque o estrategia contra un jugador, al tick `lastUpdateTick`.

```java
public record AttackRecord(double successes, double attempts, long lastUpdateTick) { ... }
```

**Validación en el constructor compacto**, en este orden, con `IllegalArgumentException`:

| Condición que falla | Mensaje |
| --- | --- |
| `!(successes >= 0) \|\| !Double.isFinite(successes)` | `"AttackRecord.successes must be zero or positive, got " + successes` |
| `!(attempts >= 0) \|\| !Double.isFinite(attempts)` | `"AttackRecord.attempts must be zero or positive, got " + attempts` |
| `successes > attempts + TOLERANCE` | `"AttackRecord.successes must not exceed attempts, got " + successes + " > " + attempts` |
| `lastUpdateTick < 0` | `"AttackRecord.lastUpdateTick must be zero or positive, got " + lastUpdateTick` |

`TOLERANCE` es una constante privada `1e-9`, con el comentario `// Absorbs floating-point rounding after repeated decay.`

**Métodos públicos:**

| Firma | Comportamiento |
| --- | --- |
| `static AttackRecord empty(long tick)` | `new AttackRecord(0, 0, tick)` |
| `double failures()` | `Math.max(0, attempts - successes)` |
| `AttackRecord decayedTo(long tick, long halfLifeTicks)` | Si `tick < lastUpdateTick`: `IllegalArgumentException("cannot decay backwards: record at tick " + lastUpdateTick + ", requested " + tick)`. Si no: `factor = Math.pow(0.5, (tick - lastUpdateTick) / (double) halfLifeTicks)` y devuelve `new AttackRecord(successes * factor, attempts * factor, tick)` |
| `AttackRecord withObservation(double credit, double weight)` | Devuelve `new AttackRecord(successes + credit * weight, attempts + weight, lastUpdateTick)`. No valida `credit` ni `weight`: eso lo hacen las observaciones |

### 2. `SuccessEstimate`

Los parámetros de la Beta para una opción.

```java
public record SuccessEstimate(double alpha, double beta, double observedAttempts) { ... }
```

| Condición que falla (en orden) | Mensaje |
| --- | --- |
| `!(alpha > 0) \|\| !Double.isFinite(alpha)` | `"SuccessEstimate.alpha must be positive, got " + alpha` |
| `!(beta > 0) \|\| !Double.isFinite(beta)` | `"SuccessEstimate.beta must be positive, got " + beta` |
| `!(observedAttempts >= 0)` | `"SuccessEstimate.observedAttempts must be zero or positive, got " + observedAttempts` |

Método público: `double mean()` → `alpha / (alpha + beta)`.

### 3. `LearningPrior`

```java
public final class LearningPrior {
  // RF-06: 50 virtual attempts at learning speed 0 and 2 (a Beta(1, 1)) at learning speed 1.
  private static final double MAX_VIRTUAL_ATTEMPTS = 50;
  private static final double MIN_VIRTUAL_ATTEMPTS = 2;

  private final double virtualAttempts;

  private LearningPrior(double virtualAttempts) { ... }

  public static LearningPrior fromLearningSpeed(double learningSpeed) { ... }

  public double virtualAttempts() { ... }

  public SuccessEstimate estimate(AttackRecord record) { ... }
}
```

| Método | Comportamiento |
| --- | --- |
| `fromLearningSpeed(learningSpeed)` | Si `!(learningSpeed >= 0 && learningSpeed <= 1)`: `IllegalArgumentException("learningSpeed must be between 0.0 and 1.0, got " + learningSpeed)`. Si no: `virtualAttempts = MAX − (MAX − MIN) × learningSpeed` |
| `virtualAttempts()` | El valor calculado |
| `estimate(record)` | `half = virtualAttempts / 2`; devuelve `new SuccessEstimate(record.successes() + half, record.failures() + half, record.attempts())`. El registro tiene que llegar ya llevado al tick actual: este método no aplica olvido |

### 4. Observaciones y cambios

Tres records sin lógica, salvo la validación:

```java
public record AttackObservation(PlayerId player, Attack attack, double credit, long tick) { ... }

public record StrategyObservation(
    PlayerId player, StrategyId strategy, double credit, double weight, long tick) { ... }

/** A memory change, so every update can be traced. */
public record RecordChange(AttackRecord before, AttackRecord after) {}
```

**Validación de `AttackObservation`** (en orden):
1. `Objects.requireNonNull(player, "AttackObservation.player")` y `Objects.requireNonNull(attack, "AttackObservation.attack")`.
2. Si `!(credit >= 0 && credit <= 1)`: `IllegalArgumentException("AttackObservation.credit must be between 0.0 and 1.0, got " + credit)`.

**Validación de `StrategyObservation`** (en orden):
1. `requireNonNull` de `player` y `strategy`, con mensajes `"StrategyObservation.player"` y `"StrategyObservation.strategy"`.
2. Si `!(credit >= 0 && credit <= 1)`: `IllegalArgumentException("StrategyObservation.credit must be between 0.0 and 1.0, got " + credit)`.
3. Si `!(weight > 0 && weight <= 1)`: `IllegalArgumentException("StrategyObservation.weight must be greater than 0.0 and at most 1.0, got " + weight)`.

`RecordChange`: `requireNonNull` de ambos componentes, con mensajes `"RecordChange.before"` y `"RecordChange.after"`.

### 5. `GroupMemory`

Todo lo que un grupo aprendió. Es el único objeto mutable de este WP: su estado solo cambia con los métodos con nombre de abajo.

**Campos privados:**
- `final Supplier<MemorySettings> settings`: se lee en cada operación, así `/mobai reload` cambia la vida media y la velocidad sin reconstruir nada.
- `final Map<PlayerId, Map<Attack, AttackRecord>> attackRecords = new HashMap<>()`: los mapas internos son `EnumMap<Attack, AttackRecord>`.
- `final Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords = new HashMap<>()`: los mapas internos son `HashMap`.

**Constructores:**

| Firma | Comportamiento |
| --- | --- |
| `GroupMemory(Supplier<MemorySettings> settings)` | Memoria vacía. `requireNonNull(settings, "GroupMemory.settings")` |
| `static GroupMemory restore(Supplier<MemorySettings> settings, Map<PlayerId, Map<Attack, AttackRecord>> attackRecords, Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords)` | Memoria con esos registros, copiados (no conserva referencias a los mapas recibidos). La usa la persistencia al cargar |

**Métodos públicos:**

| Firma | Comportamiento |
| --- | --- |
| `RecordChange recordAttack(AttackObservation observation)` | 1. Toma el registro actual de (jugador, ataque), o `AttackRecord.empty(observation.tick())` si no hay. 2. `before = actual.decayedTo(observation.tick(), halfLifeTicks)`. 3. `after = before.withObservation(observation.credit(), 1.0)`. 4. Guarda `after`. 5. Devuelve `new RecordChange(before, after)` |
| `RecordChange recordStrategy(StrategyObservation observation)` | Igual, con el registro de (jugador, estrategia) y `withObservation(observation.credit(), observation.weight())` |
| `SuccessEstimate attackEstimate(PlayerId player, Attack attack, long tick)` | Registro de (jugador, ataque) o vacío en `tick`, llevado a `tick` con `decayedTo`, pasado por `LearningPrior.fromLearningSpeed(learningSpeed).estimate(...)`. **No modifica nada** |
| `SuccessEstimate strategyEstimate(PlayerId player, StrategyId strategy, long tick)` | Igual, con la estrategia |
| `SuccessEstimate kindEstimate(PlayerId player, MobKind kind, long tick)` | Suma los registros (ya llevados a `tick`) de todos los ataques de `Attack.forKind(kind)` contra ese jugador: éxitos con éxitos e intentos con intentos, en un `AttackRecord` combinado en `tick`. Después aplica el prior **una sola vez**. Lo usa la selección de objetivo para el daño esperado. **No modifica nada** |
| `void clearPlayer(PlayerId player)` | Borra los registros de ataques y de estrategias de ese jugador |
| `void clear()` | Borra todo |
| `Map<PlayerId, Map<Attack, AttackRecord>> attackRecords()` | Copia profunda **inmutable** (`Map.copyOf` en ambos niveles) de los registros **tal como están guardados**, sin olvido. La usan la persistencia y los comandos de estado |
| `Map<PlayerId, Map<StrategyId, AttackRecord>> strategyRecords()` | Ídem para estrategias |

Detalles de implementación obligatorios:
- Un registro que todavía no existe se considera `AttackRecord.empty(tick)`, con el tick de la operación.
- Las lecturas (`attackEstimate`, `strategyEstimate`, `kindEstimate`) no crean entradas en los mapas.
- Si una observación o lectura pide un tick anterior al `lastUpdateTick` guardado, la excepción de `decayedTo` se propaga sin atraparla: el tiempo del server nunca retrocede, y si pasa es un bug que tiene que verse.
- Ningún método público recibe más de 3 parámetros (`restore` está exento: es un constructor alternativo, como un record).

## Pruebas obligatorias

Tolerancia `within(1e-9)`. Para las pruebas de `GroupMemory`, la configuración sale de `TestSettings.defaults().memory()` (vida media 12 000, velocidad 0,7 → V = 16,4), salvo que se diga otra cosa. Los jugadores de prueba se crean con UUID fijos, por ejemplo `new PlayerId(new UUID(0, 1))` y `new PlayerId(new UUID(0, 2))`.

**`AttackRecordTest`**

| Prueba | Verificación |
| --- | --- |
| `emptyRecordHasNoData` | `AttackRecord.empty(5)` es `(0, 0, 5)` y `failures()` es 0 |
| `oneHalfLifeHalvesSuccessesAndAttempts` | `new AttackRecord(8, 10, 0).decayedTo(12_000, 12_000)` es `(4, 5, 12_000)` |
| `twoHalfLivesQuarterTheCounts` | Mismo registro a 24 000: `(2, 2.5, 24_000)` |
| `decayingToTheSameTickChangesNothing` | `(8, 10, 100).decayedTo(100, 12_000)` es igual al original |
| `decayingBackwardsFails` | `(8, 10, 100).decayedTo(99, 12_000)` lanza `IllegalArgumentException` con mensaje `cannot decay backwards: record at tick 100, requested 99` |
| `observationAddsWeightedCredit` | `(1, 2, 0).withObservation(0.5, 1.0)` es `(1.5, 3, 0)`; `(0, 0, 0).withObservation(0.73, 0.5)` tiene éxitos 0,365 e intentos 0,5 |
| `failuresAreAttemptsMinusSuccesses` | `(3, 10, 0).failures()` es 7 |
| `rejectsMoreSuccessesThanAttempts` | `new AttackRecord(3, 2, 0)` lanza `IllegalArgumentException` con mensaje `AttackRecord.successes must not exceed attempts, got 3.0 > 2.0` |
| `rejectsNegativeOrNonFiniteCounts` | `new AttackRecord(-1, 2, 0)` lanza con mensaje `AttackRecord.successes must be zero or positive, got -1.0`; `new AttackRecord(0, Double.NaN, 0)` lanza `IllegalArgumentException` |

**`LearningPriorTest`**

| Prueba | Verificación |
| --- | --- |
| `virtualAttemptsFollowTheLearningSpeedFormula` | Velocidad 1 → 2; velocidad 0 → 50; velocidad 0,7 → 16,4 |
| `oneHitOfOneGivesTwoThirdsAtFullLearningSpeed` | Velocidad 1, registro `(1, 1, 0)`: media 2/3 |
| `tenHitsOfTenAtFullLearningSpeed` | Velocidad 1, `(10, 10, 0)`: media 11/12 |
| `tenHitsOfTenAtSlowestLearningSpeed` | Velocidad 0, `(10, 10, 0)`: media 35/60 |
| `emptyRecordGivesFiftyPercent` | Velocidad 0,7, registro vacío: `alpha` = `beta` = 8,2, media 0,5 y `observedAttempts` 0 |
| `rejectsLearningSpeedOutOfRange` | `fromLearningSpeed(1.5)` lanza `IllegalArgumentException` con mensaje `learningSpeed must be between 0.0 and 1.0, got 1.5` |

**`GroupMemoryTest`**

| Prueba | Verificación |
| --- | --- |
| `unknownPlayerHasThePriorEstimate` | Memoria vacía: `attackEstimate(jugador, ZOMBIE_FRONT_STRIKE, 0)` tiene `alpha` = `beta` = 8,2 y `observedAttempts` 0 |
| `recordedHitRaisesTheEstimate` | `recordAttack(jugador, ZOMBIE_FRONT_STRIKE, crédito 1, tick 0)`: la estimación en tick 0 tiene `alpha` 9,2, `beta` 8,2 y media 9,2 / 17,4 |
| `recordAttackReturnsTheChangeForTracing` | Un acierto en tick 0 y otro en tick 12 000: el segundo `RecordChange` tiene `before` = `(0.5, 0.5, 12_000)` y `after` = `(1.5, 1.5, 12_000)` |
| `oneHalfLifeWithoutFightingHalvesEffectiveCounts` | 10 aciertos en tick 0; `attackEstimate` en tick 12 000: `observedAttempts` 5 y `alpha` = 5 + 8,2 |
| `readingDoesNotStoreDecay` | 4 aciertos en tick 0; `attackEstimate` en tick 12 000; `attackRecords()` todavía tiene el registro `(4, 4, 0)` |
| `partialCreditCountsAsHalfASuccess` | `recordAttack(..., crédito 0.5, ...)` deja el registro en `(0.5, 1, tick)` |
| `strategyObservationUsesItsWeight` | `recordStrategy(jugador, FLANK, crédito 0.73, peso 0.5, tick 0)` deja el registro en éxitos 0,365 e intentos 0,5 |
| `playersAreIndependent` | Un acierto contra el jugador 1 no cambia la estimación del jugador 2 |
| `kindEstimateCombinesAllAttacksOfTheKindAndAppliesThePriorOnce` | Contra el jugador 1, en tick 0: `ZOMBIE_FRONT_STRIKE` 1 acierto y 1 fallo; `ZOMBIE_FLANK_STRIKE` 2 aciertos; `SKELETON_DIRECT_SHOT` 1 fallo. `kindEstimate(jugador, ZOMBIE, 0)`: `alpha` = 3 + 8,2, `beta` = 1 + 8,2 y `observedAttempts` 4 (el esqueleto no cuenta) |
| `learningSpeedIsReadOnEveryOperation` | Configuración en un `AtomicReference<MemorySettings>` usado como `Supplier`. Un acierto en tick 0; con velocidad 0,7, media 9,2 / 17,4; se cambia la referencia a velocidad 1; la misma lectura da media 2/3 |
| `clearPlayerForgetsOnlyThatPlayer` | Datos de dos jugadores; `clearPlayer(jugador 1)`: el jugador 1 vuelve al prior y el 2 conserva sus datos |
| `clearForgetsEverything` | `clear()`: `attackRecords()` y `strategyRecords()` vacíos |
| `snapshotsAreUnmodifiable` | `attackRecords().put(...)` y `attackRecords().get(jugador).put(...)` lanzan `UnsupportedOperationException` |
| `restoreKeepsTheRecords` | `GroupMemory.restore(settings, memoria.attackRecords(), memoria.strategyRecords())` devuelve una memoria con `attackRecords()` y `strategyRecords()` iguales a los originales |
| `observationsRejectInvalidCredit` | `new AttackObservation(jugador, ZOMBIE_FRONT_STRIKE, 1.5, 0)` lanza con mensaje `AttackObservation.credit must be between 0.0 and 1.0, got 1.5`; `new StrategyObservation(jugador, FLANK, 0.5, 0.0, 0)` lanza con mensaje `StrategyObservation.weight must be greater than 0.0 and at most 1.0, got 0.0` |

`FLANK` en las pruebas es `new StrategyId("FLANK")`.

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `AttackRecord.decayedTo`, usar `factor = 1` | `oneHalfLifeHalvesSuccessesAndAttempts` y `oneHalfLifeWithoutFightingHalvesEffectiveCounts` |
| En `LearningPrior.estimate`, sumar `virtualAttempts` entero en vez de la mitad | `oneHitOfOneGivesTwoThirdsAtFullLearningSpeed` |
| En `GroupMemory.attackEstimate`, guardar el registro llevado al tick en el mapa | `readingDoesNotStoreDecay` |
| En `GroupMemory.kindEstimate`, aplicar el prior a cada ataque y sumar las estimaciones | `kindEstimateCombinesAllAttacksOfTheKindAndAppliesThePriorOnce` |

## Procedimiento

1. Rama `wp-03-memoria` desde `main`.
2. `AttackRecord`, `SuccessEstimate`, `LearningPrior` y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add attack records with decay and learning prior`.
3. Observaciones, `RecordChange`, `GroupMemory` y `GroupMemoryTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add group memory`.
4. Pruebas que muerden.
5. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
6. Push, PR `WP-03: group memory with decay`, y esperar el check `build` en verde **antes** de escribir el informe; incluí el resultado del CI.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Sorteo Beta y políticas de selección (WP-04).
- Unir memorias de dos grupos y memoria global por equipo (fase 2).
- Decidir el crédito de un acierto, parcial o fallo: lo hace el clasificador (WP-05) y el caso de uso (WP-13). Acá el crédito ya llega como número.
- Persistencia en disco (WP-14).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres y mensajes idénticos a los del WP.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 4 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 2 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
