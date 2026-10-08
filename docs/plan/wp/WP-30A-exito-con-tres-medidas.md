# WP-30A — Éxito del plan con tres medidas

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24G (mergeado) |
| Modelo | Opus |
| Rama | `wp-30a-exito-con-tres-medidas` |

## Objetivo

Primera parte del CT-27, pedido de Nico. Hoy el éxito de un plan mide solo el daño: `min(1, daño ÷ (0,5 × vida del objetivo))`, y vale 1 si el objetivo muere. Lo que pierde el grupo no cuenta.

Con este WP, el éxito combina **tres medidas**, cada una de 0 a 1:

| Medida | Cálculo |
| --- | --- |
| **Daño** | La de hoy |
| **Rapidez** | Qué parte de la vida del objetivo se le sacó, en proporción al tiempo que duró el plan, comparada con matarlo en `success.reference-kill-ticks` (600, lo que dura un plan) |
| **Supervivencia** | ½ × (aliados del inicio que siguen vivos ÷ aliados al inicio) + ½ × (1 − vida neta perdida ÷ vida del grupo al inicio). La vida neta perdida descuenta lo que se curaron |

`éxito = damage-weight × daño + speed-weight × rapidez + survival-weight × supervivencia`, con pesos **fijos** de la configuración: 0,4, 0,4 y 0,2.

**Lo que no entra en este WP:**
- que la supervivencia pese más contra jugadores muy buenos (WP-30B);
- la retirada aprendida (WP-30C, CT-28).

La memoria y Thompson no cambian: ya aprenden de un éxito fraccionario.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/group/`: `Plan`, `PlanStart`, `PlanLifecycle`, `PlanEndReason`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java`
- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` (`startPlan`, `execute`, `closeAndEvaluate`)
- `src/main/java/io/github/nicodoou/mobai/application/RecordPlayerDeath.java`, `ClosePlan.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/`: `MobAiSettings`, `PlanSettings`, `SettingsChecks`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java`
- Las pruebas que se nombran en «Pruebas existentes que cambian»

## Reglas de negocio

1. **La vida del grupo en el plan.**
   - El plan guarda, para cada mob **del inicio** (`startingRoles`), su vida la primera vez que la ve (`startingHealth`) y la última que vio (`lastSeenHealth`).
   - El cerebro se la pasa en cada decisión mientras ejecuta, **y también justo después de abrir el plan**.
   - Los mobs que se suman después del inicio no cuentan.
   - Un mob del inicio que **deja el grupo** (muere o desaparece) cuenta como perdido: vida 0 y no vivo. Hoy `withoutMember` lo saca de `roles`; eso sirve para contar los vivos.
2. **Daño** (`damageScore`): `TARGET_DIED` → 1. Si no, `min(1, damageDealt ÷ (fullSuccessDamageFraction × targetMaxHealth))`. Es lo de hoy.
3. **Rapidez** (`speedScore`):
   - `parte = TARGET_DIED ? 1 : min(1, damageDealt ÷ targetMaxHealth)`;
   - `duración = max(1, endTick − startTick)`;
   - `rapidez = min(1, parte × referenceKillTicks ÷ duración)`.
   - Matarlo en 300 ticks con referencia 600 da 1; sacarle la mitad en 600 da 0,5.
4. **Supervivencia** (`survivalScore`):
   - `vivos = mobs de startingRoles que siguen en roles ÷ startingMembers()`;
   - `perdida = Σ sobre los mobs con startingHealth de (startingHealth − (sigue en roles ? lastSeenHealth : 0))`, con piso en 0;
   - `inicial = Σ startingHealth`;
   - `parteVida = inicial > 0 ? 1 − min(1, perdida ÷ inicial) : 1` (sin datos de vida, no se penaliza);
   - `supervivencia = ½ × vivos + ½ × parteVida`.
5. **Éxito:** `min(1, wDaño × daño + wRapidez × rapidez + wSupervivencia × supervivencia)`. El `min` cubre el redondeo de punto flotante.
6. **Configuración** (sección nueva `success`):
   - `damage-weight: 0.4`, `speed-weight: 0.4`, `survival-weight: 0.2`: cada uno entre 0 y 1, y **tienen que sumar 1** (tolerancia 1e-9);
   - `reference-kill-ticks: 600`: al menos 1.
7. **El log de debug** suma las tres medidas a la línea `PLAN`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/settings/SuccessSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/SettingsChecks.java` (solo si hace falta el chequeo de suma) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/decision/PlanScores.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanScoring.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanLifecycle.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/ClosedPlan.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/RecordPlayerDeath.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java` |
| Modificar | `docs/catalogo-mvp.md`, `docs/requerimientos.md` (ver «Documentos») |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/group/PlanScoringTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/group/PlanTest.java` |
| Modificar | las pruebas de «Pruebas existentes que cambian» |

Antes de empezar, buscá con grep `closePlan(`, `new ClosedPlan(`, `new MobAiSettings(` y `fullSuccessDamageFraction` en `src/`. La lista de pruebas de abajo tiene que cubrir todo; si aparece un archivo de `src/main` que no está en la tabla, frená y reportá.

**Métodos públicos nuevos:** `Plan.withHealthSeen`, `startingHealth`, `lastSeenHealth`, `PlanLifecycle.recordGroupHealth` y `PlanScoring.scoresOf`/`successOf`. Si alguna clase pasa de 20, frená y reportá.

## Especificación

### `SuccessSettings.java`

```java
/** How a closed plan is scored (CT-27): three measures, weighted. */
public record SuccessSettings(
    double damageWeight, double speedWeight, double survivalWeight, long referenceKillTicks) {
  // Floating point: 0.4 + 0.4 + 0.2 is not exactly 1.
  private static final double WEIGHT_SUM_TOLERANCE = 1e-9;
  …
}
```

**Validaciones:**
- cada peso con `requireBetween(…, 0, 1)`;
- `referenceKillTicks` con `requireAtLeast(…, 1)`;
- la suma: si `Math.abs(damage + speed + survival − 1) > WEIGHT_SUM_TOLERANCE`, `IllegalArgumentException` con el mensaje `SuccessSettings weights must add up to 1.0, got <suma>`.

**Configuración:**
- `MobAiSettings` suma `SuccessSettings success` **al final**, con su `requireNonNull`;
- `ConfigLoader` lee la sección `success` en ese orden;
- `config.yml`, al final:

```yaml
success:
  # Éxito de un plan (CT-27): daño hecho, rapidez y supervivencia del grupo, con estos pesos (suman 1).
  damage-weight: 0.4
  speed-weight: 0.4
  survival-weight: 0.2
  # Rapidez: comparada con matar al objetivo en estos ticks.
  reference-kill-ticks: 600
```

### `PlanScores.java` (`domain.decision`)

```java
/** The three measures of a closed plan, each between 0 and 1 (CT-27). */
public record PlanScores(double damage, double speed, double survival) { … }
```

Valida cada medida entre 0 y 1 (mensaje `PlanScores.<campo> must be between 0.0 and 1.0, got …`).

### `Plan.java`

**Componentes nuevos, al final:** `Map<MobId, Double> startingHealth` y `Map<MobId, Double> lastSeenHealth`. Se copian sin modificar, como `roles`; `Plan.start` los crea vacíos.

```java
  /** Health seen this decision; only mobs the plan started with count (CT-27). */
  public Plan withHealthSeen(Map<MobId, Double> health) { … }
```

- Para cada mob de `startingRoles` que esté en `health`:
  - `startingHealth.putIfAbsent(mob, vida)`;
  - `lastSeenHealth.put(mob, vida)`.
- Los demás se ignoran.
- Devuelve una copia nueva.
- Todos los `copyWith…` existentes llevan los dos mapas nuevos.

### `PlanScoring.java` (`domain.group`, pura)

```java
/** Scores a plan at its close (CT-27). */
public final class PlanScoring {
  private final PlanSettings plan;
  private final SuccessSettings success;

  public PlanScoring(PlanSettings plan, SuccessSettings success) { … }

  public PlanScores scoresOf(Plan plan, PlanEndReason reason, long endTick) { … }

  public double successOf(PlanScores scores) { … }
}
```

Las reglas 2 a 5, cada medida en su propia función privada. El constructor recibe los records ya resueltos: quien la arma (el cerebro y `RecordPlayerDeath`) la arma en cada cierre con `settings.get()`, así que un `/mobai reload` aplica en el próximo cierre.

### `PlanLifecycle.java`

- `public void recordGroupHealth(Map<MobId, Double> health)`: si `state != EXECUTING`, no hace nada; si no, `plan = plan.withHealthSeen(health)`.
- `closePlan(PlanEndReason reason, long tick, PlanScoring scoring)` reemplaza al parámetro `double fullSuccessDamageFraction`. `closedPlan` arma `PlanScores scores = scoring.scoresOf(plan, reason, tick)` y `new ClosedPlan(…, scoring.successOf(scores), scores, …)`.
- Se borra `Plan.successFraction`: su cálculo pasa a `PlanScoring`.

### `ClosedPlan.java`

`PlanScores scores` va como componente nuevo **después de `success`**, con su `requireNonNull`.

### `Brain.java`

- **`startPlan`:** después de `startPlan(...)`, `turn.lifecycle().recordGroupHealth(healthOf(turn.snapshot()))`.
- **`execute`:** después de `markTargetSeenIfVisible(turn)`, `turn.lifecycle().recordGroupHealth(healthOf(turn.snapshot()))`.
- `private static Map<MobId, Double> healthOf(GroupSnapshot snapshot)`: vida de cada mob del snapshot.
- **`closeAndEvaluate`:** `closePlan(endReason, tick, scoring())`, con `private PlanScoring scoring() { return new PlanScoring(settings.get().plan(), settings.get().success()); }`.

### `RecordPlayerDeath.java`

`closePlan(TARGET_DIED, tick, new PlanScoring(settings.current().plan(), settings.current().success()))`.

### `DebugLog.java`

La línea `PLAN` suma, después de `success=%.2f`, `scores=%.2f/%.2f/%.2f` (daño, rapidez y supervivencia).

### Documentos

- **`docs/requerimientos.md`:** después del párrafo de la velocidad de aprendizaje de RF-06, agregá:

  > **Éxito de un plan (CT-27).** Combina tres medidas de 0 a 1: daño hecho (`daño ÷ (0,5 × vida del objetivo)`, 1 si muere), rapidez (la parte de la vida sacada en proporción al tiempo, contra matarlo en 600 ticks) y supervivencia del grupo (½ aliados vivos, ½ vida neta conservada). Pesos configurables: 0,4, 0,4 y 0,2.

- **`docs/catalogo-mvp.md`:** si menciona cómo se mide el éxito del plan (buscá `full-success-damage-fraction` o "éxito"), sumá una línea que remita al CT-27. Si no lo menciona, no lo toques y avisalo.

## Pruebas obligatorias

### `PlanScoringTest` (9)

Con `TestSettings` (pesos 0,4/0,4/0,2, referencia 600, `fullSuccessDamageFraction` 0,5), objetivo de 20 de vida y plan iniciado en el tick 1.000 con dos mobs (`MOB_1` y `MOB_2`).

| Prueba | Situación | Esperado |
| --- | --- | --- |
| `targetDeathScoresFullDamage` | `TARGET_DIED` en 1.300, sin daño registrado | daño 1, rapidez 1 |
| `damageScoreIsTheShareOfTheFullSuccessDamage` | 5 de daño, `TIMED_OUT` en 1.600 | daño 0,5 |
| `speedComparesThePaceWithTheReferenceKill` | 10 de daño, `TIMED_OUT` en 1.600 | rapidez 0,5. Con 10 de daño en 1.300: 1 |
| `survivalIsFullWhenNobodyIsHurt` | vida vista 20 y 20 al inicio y al cierre | supervivencia 1 |
| `lostHealthLowersSurvival` | inicio 20 y 20; último 10 y 20 | supervivencia ½ × 1 + ½ × (1 − 10/40) = 0,875 |
| `deadAlliesCountAsLost` | inicio 20 y 20; `MOB_2` deja el plan (`withoutMember`); último de `MOB_1` 20 | vivos ½; vida 1 − 20/40 = ½; supervivencia 0,5 |
| `healingCountsAgainstTheLoss` | inicio 20 y 20; visto 6 y 20; después 16 y 20 | supervivencia ½ + ½ × (1 − 4/40) = 0,95 |
| `newcomersDoNotCount` | `withHealthSeen` con un `MOB_3` que no estaba al inicio | `startingHealth` sin `MOB_3` |
| `successWeighsTheThreeMeasures` | `PlanScores(1, 0.5, 0.25)` | 0,4 + 0,2 + 0,05 = 0,65 |

Tolerancia 1e-9 en todas.

### `PlanTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `firstHealthSeenIsKeptAsStartingHealth` | `withHealthSeen` dos veces (20 y después 12): `startingHealth` 20 y `lastSeenHealth` 12 |

### `SettingsValidationTest` (+2 casos)

- `success` con pesos 0,5/0,4/0,2 → `SuccessSettings weights must add up to 1.0, got 1.1` (copiá el número exacto que da el `+` en Java si no es `1.1`, y avisalo);
- `referenceKillTicks` 0 → el mensaje de `requireAtLeast`.

Total: **10 pruebas nuevas y 2 casos**.

### Pruebas existentes que cambian

| Archivo | Cambio |
| --- | --- |
| `TestSettings` | `new SuccessSettings(0.4, 0.4, 0.2, 600)` al final de `MobAiSettings`; un helper `static PlanScoring scoring()` con su `plan()` y `success()` |
| `ConfigLoaderTest` | el `MobAiSettings` esperado suma `new SuccessSettings(0.4, 0.4, 0.2, 600)` |
| `RecordOutcomeTest`, `RecruitMobTest`, `SettingsHolderTest`, `SettingsValidationTest`, `LearningSimulation` | sus `new MobAiSettings(…)` suman el mismo `SuccessSettings` al final |
| Cada `closePlan(…, <fracción>)` en las pruebas | pasa a `closePlan(…, TestSettings.scoring())` |
| Cada `new ClosedPlan(…)` en las pruebas | suma `PlanScores` después de `success`. Usá `new PlanScores(1, 1, 1)` salvo que la prueba compare con un cierre real; en ese caso, el valor que da la fórmula |
| `GroupLifecycleTest`, `RecordPlayerDeathTest`, `BrainExecutingTest`, `DebugLogTest` | donde se compara `success` o la línea `PLAN`, recalculá con la fórmula nueva y **anotá en el informe cada valor que cambiaste, con el cálculo** |

Si una prueba del cerebro, de la simulación o de las trazas cambia de resultado por algo que no sea el número del éxito (otra estrategia elegida, otro estado), **frená y reportá**. Una traza grabada que ya no se reproduce cuenta como ese caso.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En la rapidez, `parte × duración ÷ referenceKillTicks` | 10 de daño en 1.600 da 0,5; en 1.300, 0,25 | `speedComparesThePaceWithTheReferenceKill` |
| 2 | En la supervivencia, ignorar que el mob dejó el plan (usar `lastSeenHealth` aunque no siga en `roles`) | vivos ½, vida 1 → 0,75 | `deadAlliesCountAsLost` |
| 3 | En `withHealthSeen`, `put` en lugar de `putIfAbsent` para `startingHealth` | inicio 12 | `firstHealthSeenIsKeptAsStartingHealth` |
| 4 | En `successOf`, cambiar los pesos de rapidez y supervivencia entre sí | 0,4 + 0,1 + 0,1 = 0,6 | `successWeighsTheThreeMeasures` |

## Verificación en el server (Nico, después del merge)

Opus suma la sección `success` al `config.yml` del server de prueba. Con `/mobai debug all full`, en el `mobai-debug.log` cada `PLAN` muestra `scores=daño/rapidez/supervivencia`:

- si el grupo te mata rápido sin perder a nadie, las tres cerca de 1;
- si te pega poco y pierde mobs, supervivencia baja.

## Procedimiento

1. Rama `wp-30a-exito-con-tres-medidas` desde `origin/main` actualizado.
2. `SuccessSettings`, configuración y sus pruebas. Commit: `feat: success settings for plan scoring`.
3. `PlanScores`, `Plan` (vida), `PlanScoring` y sus pruebas. Commit: `feat: score plans by damage, speed and group survival (CT-27)`.
4. `PlanLifecycle`, `ClosedPlan`, `Brain`, `RecordPlayerDeath`, `DebugLog`, las pruebas existentes y los documentos. Commit: `feat: the brain scores each closed plan with three measures (CT-27)`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-30A: plan success with three measures (CT-27)`, CI en verde e informe con la prueba exacta que falló en cada rotura y la lista de valores esperados que cambiaste.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Ajustar los valores esperados del éxito en pruebas existentes según la fórmula, informándolos.

## Fuera de alcance

- El peso de supervivencia según el jugador (WP-30B).
- La retirada aprendida (WP-30C).
- La simulación del WP-11: sortea el éxito desde su propio modelo y no usa `PlanScoring`.

## Aceptación

- [ ] Exactamente los archivos de la tabla y las pruebas listadas, con las firmas especificadas.
- [ ] Las 10 pruebas nuevas y los 2 casos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
