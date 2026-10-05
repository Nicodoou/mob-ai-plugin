# WP-05 — Clasificador de ataques

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E2 Dominio: aprendizaje |
| Depende de | WP-02 |
| Modelo | Sonnet |
| Rama | `wp-05-clasificador` |

## Objetivo

Implementar el clasificador que decide si un intento de ataque fue acierto, parcial, fallo o neutral a partir de los hechos que junta el rastreador, aplicando las 8 reglas de `arquitectura.md` con las correcciones que salieron del spike en el server.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/MobId.java`, `PlayerId.java`, `Attack.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

El rastreador (un adaptador, WP-18 y WP-24) junta **hechos** sobre un intento; el clasificador (dominio) los convierte en un **resultado**. Las reglas se evalúan **en este orden** y gana la primera que aplica:

| Regla | Condición | Resultado |
| --- | --- | --- |
| 1 | El objetivo ya no es válido al resolver (murió, se desconectó o cambió de mundo) | Neutral, `TARGET_INVALID` |
| 2 | Otro plugin o una protección de región canceló el daño | Neutral, `DAMAGE_CANCELLED` |
| 3 | El objetivo era invulnerable al abrir el intento, el daño real fue 0 y no hubo bloqueo | Neutral, `TARGET_INVULNERABLE` |
| 4 | La flecha impactó a un aliado | Neutral, `ALLY_HIT` |
| 5 | Un tercero o el ambiente dañó al mob antes de resolver | Neutral, `INTERRUPTED` |
| 6 | Daño real mayor a 0 | Acierto |
| 7 | El escudo bloqueó | Parcial; acierto si el golpe deshabilitó el escudo |
| 8 | Cualquier otro caso (la flecha tocó un bloque u otra entidad, venció el plazo, o no hubo daño) | Fallo |

Por qué las reglas son así (hallazgos del spike, `docs/plan/hallazgos-api.md`):
- **Daño real** = daño final + lo que absorbió la absorción. Con absorción el daño final es 0: la regla «daño final > 0» habría contado como fallo cada golpe contra alguien que comió una manzana dorada. El adaptador calcula el daño real; el clasificador solo lo compara con 0.
- **Bloqueo:** se detecta por el modificador `BLOCKING` del evento, no por `isBlocking()`. El adaptador lo traduce a un booleano.
- **Invulnerabilidad:** un golpe que cae en la ventana de invulnerabilidad no dispara ningún evento. Por eso se mira si el objetivo era invulnerable **al abrir** el intento. Si igual hubo daño real (el golpe superó al anterior), es un acierto por la regla 6.

**Crédito para la memoria:** acierto 1, parcial el peso configurable (`MemorySettings.partialHitWeight`, 0,5 por defecto), fallo 0, neutral no se registra.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/shared/AttemptId.java` |
| Crear | `domain/attack/ProjectileContact.java`, `NeutralCause.java` |
| Crear | `domain/attack/AttackFacts.java` |
| Crear | `domain/attack/AttackOutcome.java` |
| Crear | `domain/attack/ClassificationTrace.java`, `Classification.java` |
| Crear | `domain/attack/AttackClassifier.java` |
| Crear (prueba) | `testsupport/AttackFactsBuilder.java` |
| Crear (prueba) | `domain/attack/AttackFactsTest.java`, `AttackOutcomeTest.java`, `AttackClassifierTest.java` |

## Especificación

### 1. `domain.shared.AttemptId`

Número de un intento en el rastreador; viaja en todas las trazas del intento.

```java
public record AttemptId(long value) {
  public AttemptId {
    if (value < 1) {
      throw new IllegalArgumentException("AttemptId must be at least 1, got " + value);
    }
  }
}
```

### 2. Enums (`domain.attack`)

- `ProjectileContact`: `NONE`, `TARGET`, `ALLY`, `OTHER_ENTITY`, `BLOCK`, en ese orden. `NONE` es el valor para cuerpo a cuerpo y para una flecha que no tocó nada antes del plazo.
- `NeutralCause`: `TARGET_INVALID`, `DAMAGE_CANCELLED`, `TARGET_INVULNERABLE`, `ALLY_HIT`, `INTERRUPTED`, en ese orden.

### 3. `AttackFacts`

Hechos crudos de un intento. Es un record de valor: está exento del límite de 3 parámetros.

```java
public record AttackFacts(
    AttemptId attemptId,
    MobId mob,
    PlayerId target,
    Attack attack,
    long openTick,
    boolean targetValid,
    boolean targetInvulnerableAtOpen,
    boolean damageEventReceived,
    boolean damageCancelledByOtherPlugin,
    double realDamage,
    boolean blockedByShield,
    boolean shieldDisabled,
    ProjectileContact projectileContact,
    boolean interruptedBeforeResolution,
    boolean timedOut) { ... }
```

**Validación en el constructor compacto**, en este orden:

| Condición que falla | Excepción y mensaje |
| --- | --- |
| Algún componente de referencia en `null` | `NullPointerException` con el mensaje `"AttackFacts.<componente>"` (`attemptId`, `mob`, `target`, `attack`, `projectileContact`) |
| `openTick < 0` | `IllegalArgumentException("AttackFacts.openTick must be zero or positive, got " + openTick)` |
| `!(realDamage >= 0) \|\| !Double.isFinite(realDamage)` | `IllegalArgumentException("AttackFacts.realDamage must be zero or positive, got " + realDamage)` |
| `!damageEventReceived && (realDamage > 0 \|\| blockedByShield \|\| damageCancelledByOtherPlugin)` | `IllegalArgumentException("AttackFacts: damage, block or cancellation require a damage event")` |
| `shieldDisabled && !blockedByShield` | `IllegalArgumentException("AttackFacts: shieldDisabled requires blockedByShield")` |

### 4. `AttackOutcome`

```java
public sealed interface AttackOutcome
    permits AttackOutcome.Hit, AttackOutcome.Partial, AttackOutcome.Miss, AttackOutcome.Neutral {

  /** Credit for the memory: empty when the attempt must not be recorded. */
  OptionalDouble credit(double partialHitWeight);

  record Hit() implements AttackOutcome { ... }       // credit: OptionalDouble.of(1)
  record Partial() implements AttackOutcome { ... }   // credit: OptionalDouble.of(partialHitWeight)
  record Miss() implements AttackOutcome { ... }      // credit: OptionalDouble.of(0)
  record Neutral(NeutralCause cause) implements AttackOutcome { ... }  // credit: OptionalDouble.empty()
}
```

`Neutral` valida `requireNonNull(cause, "AttackOutcome.Neutral.cause")`.

### 5. Trazas y resultado

```java
/** Which tracker rule (1 to 8) decided the outcome, with the facts it used. */
public record ClassificationTrace(int rule, AttackFacts facts) { ... }

public record Classification(AttackOutcome outcome, ClassificationTrace trace) { ... }
```

- `ClassificationTrace`: si `rule < 1 || rule > 8`, `IllegalArgumentException("ClassificationTrace.rule must be between 1 and 8, got " + rule)`; `requireNonNull(facts, "ClassificationTrace.facts")`.
- `Classification`: `requireNonNull` de ambos componentes, con mensajes `"Classification.outcome"` y `"Classification.trace"`.

### 6. `AttackClassifier`

```java
public final class AttackClassifier {
  public Classification classify(AttackFacts facts) { ... }
}
```

Sin estado y sin dependencias. Estructura obligatoria, para que cada método tenga menos de 20 líneas:

```java
public Classification classify(AttackFacts facts) {
  Optional<Classification> neutral = classifyNeutral(facts);   // reglas 1 a 5
  if (neutral.isPresent()) {
    return neutral.get();
  }
  return classifyContact(facts);                                // reglas 6 a 8
}
```

- `private Optional<Classification> classifyNeutral(AttackFacts facts)`: reglas 1 a 5 en orden, con retorno temprano. La condición de la regla 3 es exactamente `facts.targetInvulnerableAtOpen() && facts.realDamage() == 0 && !facts.blockedByShield()`. La regla 4 es `facts.projectileContact() == ProjectileContact.ALLY`.
- `private Classification classifyContact(AttackFacts facts)`: regla 6 (`realDamage > 0` → `Hit`), regla 7 (`blockedByShield` → `Hit` si `shieldDisabled`, si no `Partial`) y regla 8 (`Miss`).
- Cada resultado se arma con `new Classification(outcome, new ClassificationTrace(número de regla, facts))`. Para que el número de regla no sea un número mágico, usá constantes privadas con nombre: `RULE_TARGET_INVALID = 1`, `RULE_DAMAGE_CANCELLED = 2`, `RULE_TARGET_INVULNERABLE = 3`, `RULE_ALLY_HIT = 4`, `RULE_INTERRUPTED = 5`, `RULE_REAL_DAMAGE = 6`, `RULE_SHIELD = 7`, `RULE_MISS = 8`.

### 7. `testsupport.AttackFactsBuilder`

Builder solo de pruebas (`arquitectura.md` lo permite). Empieza con estos valores, que describen un golpe cuerpo a cuerpo que acertó:

| Campo | Valor inicial |
| --- | --- |
| `attemptId` | `new AttemptId(1)` |
| `mob` | `new MobId(new UUID(0, 1))` |
| `target` | `new PlayerId(new UUID(0, 2))` |
| `attack` | `Attack.ZOMBIE_FRONT_STRIKE` |
| `openTick` | `100` |
| `targetValid` | `true` |
| `targetInvulnerableAtOpen` | `false` |
| `damageEventReceived` | `true` |
| `damageCancelledByOtherPlugin` | `false` |
| `realDamage` | `3.0` |
| `blockedByShield` | `false` |
| `shieldDisabled` | `false` |
| `projectileContact` | `ProjectileContact.NONE` |
| `interruptedBeforeResolution` | `false` |
| `timedOut` | `false` |

Un método `withX(valor)` por campo (por ejemplo `withRealDamage(double)`, `withBlockedByShield(boolean)`), que devuelve `this`, y `AttackFacts build()`. Además, dos atajos:
- `noDamageEvent()`: `damageEventReceived = false` y `realDamage = 0`.
- `blockedHit()`: `realDamage = 0` y `blockedByShield = true`.

## Pruebas obligatorias

**`AttackFactsTest`**

| Prueba | Verificación |
| --- | --- |
| `defaultBuilderFactsAreValid` | `new AttackFactsBuilder().build()` no lanza nada |
| `rejectsNegativeRealDamage` | `withRealDamage(-1)` lanza `IllegalArgumentException` con mensaje `AttackFacts.realDamage must be zero or positive, got -1.0` |
| `damageRequiresADamageEvent` | `noDamageEvent()` y después `withRealDamage(2)`: lanza con mensaje `AttackFacts: damage, block or cancellation require a damage event` |
| `shieldDisabledRequiresABlock` | `withShieldDisabled(true)` sin bloqueo: lanza con mensaje `AttackFacts: shieldDisabled requires blockedByShield` |
| `attemptIdStartsAtOne` | `new AttemptId(0)` lanza con mensaje `AttemptId must be at least 1, got 0` |

**`AttackOutcomeTest`**

| Prueba | Verificación |
| --- | --- |
| `hitCreditIsOne` | `new Hit().credit(0.5)` es 1 |
| `partialCreditIsTheConfiguredWeight` | `new Partial().credit(0.5)` es 0,5 y `credit(0.25)` es 0,25 |
| `missCreditIsZero` | `new Miss().credit(0.5)` es 0 |
| `neutralIsNotRecorded` | `new Neutral(NeutralCause.INTERRUPTED).credit(0.5)` está vacío |

**`AttackClassifierTest`**. Cada prueba verifica el resultado **y** el número de regla de la traza, y que la traza trae los mismos hechos (`isSameAs`).

| Prueba | Hechos (sobre el builder por defecto) | Resultado | Regla |
| --- | --- | --- | --- |
| `invalidTargetIsNeutral` | `withTargetValid(false)` | Neutral `TARGET_INVALID` | 1 |
| `invalidTargetWinsOverEveryOtherRule` | `withTargetValid(false)`, `withDamageCancelledByOtherPlugin(true)`, `withInterruptedBeforeResolution(true)` | Neutral `TARGET_INVALID` | 1 |
| `cancelledDamageIsNeutral` | `withDamageCancelledByOtherPlugin(true)`, `withRealDamage(0)` | Neutral `DAMAGE_CANCELLED` | 2 |
| `invulnerableTargetWithoutDamageIsNeutral` | `noDamageEvent()`, `withTargetInvulnerableAtOpen(true)` | Neutral `TARGET_INVULNERABLE` | 3 |
| `invulnerableTargetThatStillTookDamageIsAHit` | `withTargetInvulnerableAtOpen(true)`, `withRealDamage(1)` (el golpe superó al anterior) | `Hit` | 6 |
| `allyHitByArrowIsNeutral` | `withAttack(SKELETON_DIRECT_SHOT)`, `noDamageEvent()`, `withProjectileContact(ALLY)` | Neutral `ALLY_HIT` | 4 |
| `interruptedAttackIsNeutral` | `noDamageEvent()`, `withInterruptedBeforeResolution(true)` | Neutral `INTERRUPTED` | 5 |
| `realDamageIsAHit` | builder por defecto (3 de daño) | `Hit` | 6 |
| `damageFullyAbsorbedIsStillAHit` | `withRealDamage(2.4)` (daño final 0 + 2,4 absorbidos, tal como lo calcula el adaptador) | `Hit` | 6 |
| `blockedHitIsPartial` | `blockedHit()` | `Partial` | 7 |
| `blockedHitThatDisablesTheShieldIsAHit` | `blockedHit()`, `withShieldDisabled(true)` | `Hit` | 7 |
| `arrowThatHitABlockIsAMiss` | `withAttack(SKELETON_LEAD_SHOT)`, `noDamageEvent()`, `withProjectileContact(BLOCK)` | `Miss` | 8 |
| `arrowThatTimedOutIsAMiss` | `withAttack(SKELETON_LEAD_SHOT)`, `noDamageEvent()`, `withTimedOut(true)` | `Miss` | 8 |
| `meleeWithoutDamageAgainstAVulnerableTargetIsAMiss` | `noDamageEvent()` | `Miss` | 8 |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En la regla 3, sacar `&& facts.realDamage() == 0` | `invulnerableTargetThatStillTookDamageIsAHit` |
| En la regla 7, devolver `Partial` aunque `shieldDisabled` sea `true` | `blockedHitThatDisablesTheShieldIsAHit` |
| Mover la regla 5 antes que la regla 1 | `invalidTargetWinsOverEveryOtherRule` |
| En `Partial.credit`, devolver 0,5 fijo | `partialCreditIsTheConfiguredWeight` |

## Procedimiento

1. Rama `wp-05-clasificador` desde `main`.
2. `AttemptId`, enums, `AttackFacts`, `AttackOutcome`, `AttackFactsBuilder`, `AttackFactsTest` y `AttackOutcomeTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add attack facts and outcomes`.
3. `ClassificationTrace`, `Classification`, `AttackClassifier` y `AttackClassifierTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add attack classifier with the eight tracker rules`.
4. Pruebas que muerden.
5. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
6. Push, PR `WP-05: attack classifier`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Juntar los hechos desde los eventos de Paper (rastreador, WP-18 y WP-24).
- Registrar el resultado en la memoria (caso de uso `RecordOutcome`, WP-13).
- Aplicar la lentitud de la araña (WP-25).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, mensajes y orden de las reglas idénticos a los del WP.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 4 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 2 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
