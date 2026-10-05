# WP-04 — Sorteo Beta y políticas de selección

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E2 Dominio: aprendizaje |
| Depende de | WP-03 |
| Modelo | Sonnet |
| Rama | `wp-04-politicas` |

## Objetivo

Implementar el sorteo desde la distribución Beta y las cuatro políticas que eligen entre opciones (ataques o estrategias): Thompson Sampling, explorar primero, epsilon-greedy y azar puro, más la fábrica que crea la política según la configuración (RF-12).

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/port/RandomSource.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/SuccessEstimate.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/selection/SelectionPolicyType.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/SelectionSettings.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/SeededRandomSource.java`, `ScriptedRandomSource.java`, `TestSettings.java`

## Reglas de negocio (RF-06 y RF-12, resumidas)

1. Cada opción llega como **candidata**: la opción, su **puntaje base** y su **estimación de éxito** (los parámetros de la Beta, que ya calculó la memoria).
2. **Multiplicador de memoria** = `mínimo + tasa × (máximo − mínimo)`. Con la configuración por defecto (0,5 y 1,5) queda en 0,5 + tasa (RF-06.3 y RF-06.4).
3. **Puntaje final** = puntaje base × multiplicador.
4. Las políticas:

| Política | Tasa que usa | Cómo elige |
| --- | --- | --- |
| Thompson Sampling | Un sorteo de la Beta de cada candidata | La de mayor puntaje final |
| Epsilon-greedy | La media de la Beta | Con probabilidad épsilon, una al azar; si no, la de mayor puntaje final |
| Explorar primero | La media de la Beta | Si la suma de intentos observados de todas las candidatas es menor que el umbral, una al azar; si no, la de mayor puntaje final |
| Azar puro | La media de la Beta (solo para la traza) | Siempre una al azar, sin mirar puntajes |

5. **Empates:** gana la primera candidata de la lista con el puntaje máximo.
6. **Trazas:** toda elección devuelve, además de la elegida, la tasa y el puntaje final de cada candidata, en el orden en que llegaron, y si la elección fue al azar.
7. **Orden de los sorteos** (importa para reproducir incidentes): está fijado para cada política en la especificación. No se puede sortear de más ni en otro orden.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Crear | `domain/selection/BetaSampler.java` |
| Crear | `domain/selection/MemoryMultiplier.java` |
| Crear | `domain/selection/SelectionCandidate.java`, `CandidateScore.java`, `SelectionResult.java` |
| Crear | `domain/selection/SelectionPolicy.java` |
| Crear | `domain/selection/CandidateScoring.java` (package-private) |
| Crear | `domain/selection/ThompsonSamplingPolicy.java`, `EpsilonGreedyPolicy.java`, `ExploreFirstPolicy.java`, `RandomPolicy.java` |
| Crear | `domain/selection/SelectionPolicyFactory.java` |
| Crear (prueba) | `domain/selection/BetaSamplerTest.java`, `MemoryMultiplierTest.java`, `SelectionPolicyTest.java`, `SelectionPolicyFactoryTest.java` |

## Especificación

Paquete `io.github.nicodoou.mobai.domain.selection`.

### 1. `BetaSampler`

Sortea un valor de una Beta usando dos sorteos Gamma: si `X ~ Gamma(alfa)` e `Y ~ Gamma(beta)`, entonces `X / (X + Y) ~ Beta(alfa, beta)`.

```java
public final class BetaSampler {
  private final RandomSource random;

  public BetaSampler(RandomSource random) { ... }   // requireNonNull(random, "BetaSampler.random")

  public double sample(SuccessEstimate estimate) { ... }
}
```

**`sample(estimate)`:**
1. `x = sampleGamma(estimate.alpha())`
2. `y = sampleGamma(estimate.beta())`
3. Devuelve `x / (x + y)`. Si `x + y == 0` (solo puede pasar con formas menores a 1 y un sorteo uniforme exactamente 0), devuelve `0.5`.

**`private double sampleGamma(double shape)`**: algoritmo de Marsaglia y Tsang (2000). Pseudocódigo exacto, respetando el orden de los sorteos:

```
si shape < 1:
    gamma = sampleGamma(shape + 1)
    u = random.nextUnit()
    devolver gamma * Math.pow(u, 1 / shape)
d = shape - ONE_THIRD
c = 1 / Math.sqrt(NINE * d)
repetir:
    repetir:
        z = random.nextGaussian()
        v = 1 + c * z
    mientras v <= 0
    v = v * v * v
    u = random.nextUnit()
    si u < 1 - SQUEEZE * z * z * z * z: devolver d * v
    si Math.log(u) < 0.5 * z * z + d * (1 - v + Math.log(v)): devolver d * v
```

Constantes privadas: `ONE_THIRD = 1.0 / 3`, `NINE = 9`, `SQUEEZE = 0.0331`, con un comentario arriba de las tres: `// Constants of the Marsaglia-Tsang (2000) gamma sampler.` La recursión del caso `shape < 1` es de un solo nivel (`shape + 1 ≥ 1`), así que no hay riesgo de recursión profunda.

### 2. `MemoryMultiplier`

```java
public final class MemoryMultiplier {
  private MemoryMultiplier() {}

  public static double of(double rate, SelectionSettings settings) { ... }
}
```

`of` devuelve `settings.memoryMultiplierMin() + rate * (settings.memoryMultiplierMax() - settings.memoryMultiplierMin())`. Si `!(rate >= 0 && rate <= 1)`: `IllegalArgumentException("rate must be between 0.0 and 1.0, got " + rate)`.

### 3. Candidatas y resultados

```java
public record SelectionCandidate<T>(T option, double baseScore, SuccessEstimate estimate) { ... }

public record CandidateScore<T>(T option, double rate, double finalScore) {}

public record SelectionResult<T>(T chosen, List<CandidateScore<T>> scores, boolean randomPick) { ... }
```

| Record | Validación (en orden) |
| --- | --- |
| `SelectionCandidate` | `requireNonNull(option, "SelectionCandidate.option")`; si `!(baseScore >= 0) \|\| !Double.isFinite(baseScore)`: `IllegalArgumentException("SelectionCandidate.baseScore must be zero or positive, got " + baseScore)`; `requireNonNull(estimate, "SelectionCandidate.estimate")` |
| `CandidateScore` | Ninguna |
| `SelectionResult` | `requireNonNull(chosen, "SelectionResult.chosen")`; `scores = List.copyOf(scores)` |

### 4. `SelectionPolicy`

```java
/** Chooses one option; the result explains every score so the decision can be traced. */
public interface SelectionPolicy {
  <T> SelectionResult<T> choose(List<SelectionCandidate<T>> candidates);
}
```

Toda implementación: si `candidates` está vacía, lanza `IllegalArgumentException("candidates must not be empty")` **antes** de sortear nada.

### 5. `CandidateScoring` (package-private, `final class`, constructor privado, métodos `static`)

| Firma | Comportamiento |
| --- | --- |
| `static <T> List<CandidateScore<T>> scoreByMean(List<SelectionCandidate<T>> candidates, SelectionSettings settings)` | Para cada candidata, en orden: `rate = estimate.mean()`, `finalScore = baseScore × MemoryMultiplier.of(rate, settings)` |
| `static <T> int indexOfBest(List<CandidateScore<T>> scores)` | Índice del mayor `finalScore`; ante empate, el primero (comparación estricta `>`) |
| `static <T> void requireCandidates(List<SelectionCandidate<T>> candidates)` | Lanza la excepción de la sección 4 si está vacía |

### 6. Las cuatro políticas

Todas son `public final class ... implements SelectionPolicy`. El límite de 3 parámetros de la política de código aplica también a los métodos privados. La configuración llega como `Supplier<SelectionSettings>` y se lee una vez por llamada a `choose`.

**`ThompsonSamplingPolicy(Supplier<SelectionSettings> settings, BetaSampler sampler)`**
1. Para cada candidata, en orden: `rate = sampler.sample(estimate)`; `finalScore = baseScore × MemoryMultiplier.of(rate, settings)`.
2. Elegida: `indexOfBest`. `randomPick = false`.

**`EpsilonGreedyPolicy(Supplier<SelectionSettings> settings, RandomSource random)`**
1. `scores = scoreByMean(...)`.
2. `roll = random.nextUnit()`. **Siempre** se sortea, una vez.
3. Si `roll < epsilon`: `index = random.nextIndex(candidates.size())` y `randomPick = true`.
4. Si no: `index = indexOfBest(scores)` y `randomPick = false`.

**`ExploreFirstPolicy(Supplier<SelectionSettings> settings, RandomSource random)`**
1. `scores = scoreByMean(...)`.
2. `observed` = suma de `estimate.observedAttempts()` de todas las candidatas.
3. Si `observed < exploreFirstAttempts`: `index = random.nextIndex(candidates.size())` y `randomPick = true`.
4. Si no: `index = indexOfBest(scores)`, `randomPick = false` y **no** se sortea nada.

**`RandomPolicy(Supplier<SelectionSettings> settings, RandomSource random)`**
1. `scores = scoreByMean(...)` (solo para la traza).
2. `index = random.nextIndex(candidates.size())`; `randomPick = true`.

### 7. `SelectionPolicyFactory`

```java
public final class SelectionPolicyFactory {
  public SelectionPolicyFactory(Supplier<SelectionSettings> settings, RandomSource random) { ... }

  public SelectionPolicy forType(SelectionPolicyType type) { ... }
}
```

El constructor crea **una instancia de cada política** (Thompson recibe `new BetaSampler(random)`) y las guarda en un `EnumMap<SelectionPolicyType, SelectionPolicy>`. `forType` devuelve siempre la misma instancia para el mismo tipo. `requireNonNull` en ambos parámetros del constructor y en `type`.

## Pruebas obligatorias

La configuración sale de `TestSettings.defaults().selection()` (mínimo 0,5, máximo 1,5, épsilon 0,1, explorar primero 10), salvo que se diga otra cosa. Las candidatas de prueba usan opciones `String` (`"A"`, `"B"`, `"C"`) y estimaciones construidas con `new SuccessEstimate(alfa, beta, intentosObservados)`.

Las pruebas estadísticas usan `SeededRandomSource` con la semilla indicada y verifican con la tolerancia indicada. Son deterministas: con la misma semilla dan siempre lo mismo.

**`BetaSamplerTest`**

| Prueba | Verificación |
| --- | --- |
| `sampleMeanMatchesTheBetaMean` | Semilla 1, 20 000 muestras: Beta(9,2; 8,2) da media 0,5287 ± 0,01; Beta(2; 1) da 0,6667 ± 0,01 |
| `sampleVarianceMatchesTheBetaFormula` | Semilla 2, 20 000 muestras de Beta(2; 5): varianza 10 / (49 × 8) = 0,02551 ± 0,002 |
| `samplesStayBetweenZeroAndOne` | Semilla 3, 10 000 muestras de Beta(1; 1): todas en [0, 1] |
| `shapesBelowOneUseTheBoost` | Semilla 4, 20 000 muestras de Beta(0,5; 0,5): media 0,5 ± 0,02 |
| `scarceDataIsDispersedAndAbundantDataIsStable` | Semilla 5, 5 000 muestras: el desvío estándar de Beta(1; 1) es mayor que 0,25 y el de Beta(500; 500) es menor que 0,02 (RF-06.2) |
| `sameSeedGivesTheSameSamples` | Dos `BetaSampler` con `SeededRandomSource(9)`: las primeras 10 muestras de Beta(3; 4) son iguales |

**`MemoryMultiplierTest`**

| Prueba | Verificación |
| --- | --- |
| `defaultRangeIsHalfToOneAndAHalf` | Tasa 0 → 0,5; 0,5 → 1,0; 1 → 1,5 |
| `customRangeIsInterpolated` | Con mínimo 0,8 y máximo 1,2: tasa 0,25 → 0,9 |
| `rejectsRateOutOfRange` | Tasa 1,1 lanza `IllegalArgumentException` con mensaje `rate must be between 0.0 and 1.0, got 1.1` |

**`SelectionPolicyTest`**

| Prueba | Verificación |
| --- | --- |
| `everyPolicyRejectsEmptyCandidates` | Las 4 políticas lanzan `IllegalArgumentException` con mensaje `candidates must not be empty` ante una lista vacía; con un `ScriptedRandomSource` vacío, que demuestra que no sortearon nada |
| `thompsonPicksAClearWinnerAlmostAlways` | Semilla 11. A = (900, 100, 1000), B = (100, 900, 1000), base 1. En 1 000 elecciones, A gana al menos 990 veces |
| `thompsonFrequencyMatchesTheProbabilityOfBeingBest` | Semilla 12. A = (3, 2, 3), B = (2, 3, 3), base 1. En 20 000 elecciones, A gana 0,7571 ± 0,015 de las veces (P(Beta(3,2) > Beta(2,3)) = 53/70) |
| `thompsonBaseScoreScalesTheFinalScore` | Semilla 13. A y B con (5000, 5000, 10000); A base 2 y B base 1. A gana las 100 elecciones |
| `thompsonReportsEveryCandidateScoreInOrder` | Semilla 14, A, B y C con base 1, 2 y 3: `scores` tiene 3 entradas en el orden A, B, C; cada una cumple `finalScore = base × (0.5 + rate)`; `randomPick` es `false` |
| `epsilonGreedyExploresWithProbabilityEpsilon` | Semilla 15. A = (90, 10, 100), B = (10, 90, 100). En 20 000 elecciones, `randomPick` es `true` en 0,10 ± 0,01 y B gana 0,05 ± 0,01 |
| `epsilonGreedyDrawsInAFixedOrder` | `ScriptedRandomSource` con `withUnits(0.05)` y `withIndexes(1)`: elige B (la peor) con `randomPick` `true`. Con `withUnits(0.5)` y sin índices: elige A, `randomPick` `false`, y después `isExhausted()` es `true` |
| `epsilonZeroAlwaysExploits` | Épsilon 0 (configuración propia), semilla 16, 1 000 elecciones: siempre A, nunca `randomPick` |
| `exploreFirstPicksAtRandomBelowTheThreshold` | A = (3, 1, 3), B = (1, 3, 4) (suma 7 < 10). `ScriptedRandomSource.withIndexes(1)`: elige B con `randomPick` `true` |
| `exploreFirstExploitsOnceTheThresholdIsReached` | A = (5, 2, 6), B = (2, 3, 4) (suma 10). `ScriptedRandomSource` vacío: elige A con `randomPick` `false`, sin sortear |
| `tiesGoToTheFirstCandidate` | Explorar primero con umbral alcanzado; A y B con (5, 5, 10) y base 1: elige A. Con el orden invertido, elige B |
| `randomPolicyIsUniformAndIgnoresMemory` | Semilla 17. A = (900, 100, 1000), B = (100, 900, 1000), C = (500, 500, 1000). En 30 000 elecciones, cada una gana 1/3 ± 0,02 |

**`SelectionPolicyFactoryTest`**

| Prueba | Verificación |
| --- | --- |
| `createsTheMatchingPolicyForEachType` | `THOMPSON_SAMPLING` → `ThompsonSamplingPolicy`; `EXPLORE_FIRST` → `ExploreFirstPolicy`; `EPSILON_GREEDY` → `EpsilonGreedyPolicy`; `RANDOM` → `RandomPolicy` |
| `returnsTheSameInstanceForTheSameType` | Dos llamadas a `forType(THOMPSON_SAMPLING)` devuelven el mismo objeto (`isSameAs`) |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `BetaSampler.sample`, devolver `y / (x + y)` | `sampleMeanMatchesTheBetaMean` |
| En `CandidateScoring.indexOfBest`, comparar con `>=` en vez de `>` | `tiesGoToTheFirstCandidate` |
| En `EpsilonGreedyPolicy`, sortear `nextIndex` antes que `nextUnit` | `epsilonGreedyDrawsInAFixedOrder` |
| En `MemoryMultiplier.of`, devolver `rate` | `defaultRangeIsHalfToOneAndAHalf` |

## Procedimiento

1. Rama `wp-04-politicas` desde `main`.
2. `BetaSampler`, `MemoryMultiplier` y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add beta sampler and memory multiplier`.
3. Candidatas, resultados, `SelectionPolicy`, `CandidateScoring`, las 4 políticas, la fábrica y sus pruebas. `./gradlew spotlessApply build`. Commit: `feat(domain): add selection policies and factory`.
4. Pruebas que muerden.
5. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
6. Push, PR `WP-04: beta sampler and selection policies`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si una prueba estadística falla por poco con la semilla indicada, **no** cambies la semilla ni la tolerancia: frená y reportá el valor obtenido (puede ser un error del sorteo).

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Armar las candidatas (qué ataques o estrategias se ofrecen y con qué puntaje base): lo hace el cerebro (WP-10).
- Elegir objetivo: no pasa por estas políticas (decisión D6).
- Asignar una política distinta por grupo: lo hace el grupo con la fábrica (WP-08 y WP-10).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, mensajes y orden de los sorteos idénticos a los del WP.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 4 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 2 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
