# WP-33A — Modelo bayesiano lineal

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | Nada nuevo (CT-30 aprobado) |
| Modelo | Opus |
| Rama | `wp-33a-modelo-bayesiano` |

## Objetivo

CT-30, primera pieza: la matemática del aprendizaje de recetas, **pura y sin usar todavía**. Una regresión lineal bayesiana con ruido conocido, que:

1. arranca de un punto de partida (media y varianza);
2. aprende de cada plan cerrado (rasgos de la receta y éxito);
3. olvida, acercándose a un ancla (el punto de partida o la base), con un factor que calcula quien la usa;
4. sortea pesos para Thompson, con una escala de exploración;
5. se puede guardar y restaurar.

Ningún otro código la usa en este WP. Los rasgos de las recetas son el WP-33B; el cerebro, el WP-33D.

## Decisiones tomadas en este WP

1. **Forma de información:** se guardan la **precisión** Λ (la inversa de la covarianza) y el **vector de información** b = Λ·μ. Aprender es sumar: Λ += x·xᵀ/σ² y b += x·r/σ². La media se obtiene resolviendo Λ·μ = b. Así una observación es exacta y barata, y el olvido es lineal.
2. **Olvido y base, con una sola operación:** `shrunkToward(anchor, keep)` deja Λ = Λₐ + keep·(Λ − Λₐ) y lo mismo con b. Con `keep = 0,5^(ticks/vida media)` es el olvido; con `keep` = «N planes ÷ planes de la base», es el peso de la base (WP-33E).
3. **Sorteo:** w = μ + √escala · (Lᵀ)⁻¹·z, con Λ = L·Lᵀ (Cholesky de la precisión) y z normales estándar sacadas **en orden** de `RandomSource.nextGaussian()`. La covarianza de w es escala·Λ⁻¹, la del modelo.
4. **Cuenta de observaciones:** `observations` suma 1 por observación y se encoge con el mismo `keep`. Sirve para mostrar el respaldo y para el peso de la base.
5. **Inmutable:** cada operación devuelve un modelo nuevo. Los arreglos se copian al entrar y al salir.
6. **Paquete nuevo `domain.learning`.**

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/port/RandomSource.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/AttackRecord.java` (estilo de un registro con olvido)
- `src/test/java/io/github/nicodoou/mobai/testsupport/ScriptedRandomSource.java`, `SeededRandomSource.java`
- `src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java` (para ver que un paquete nuevo del dominio no rompe ninguna regla)
- `docs/arquitectura.md` (tabla «Nombres en el código»), para actualizarla

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/learning/Cholesky.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/learning/CholeskyTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/learning/LinearPosteriorTest.java` |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

## Especificación

### `Cholesky.java` (package-private)

```java
/** L·Lᵀ factor of a symmetric positive definite matrix, and the solves the posterior needs. */
final class Cholesky {
  static Cholesky of(double[][] matrix) { … }

  /** x with L·Lᵀ·x = b. */
  double[] solve(double[] b) { … }

  /** x with Lᵀ·x = z (back substitution). */
  double[] solveTransposed(double[] z) { … }

  double[][] lower() { … copia … }
}
```

- `of`:
  - requiere una matriz cuadrada no vacía;
  - recorre i de 0 a n−1 y j de 0 a i: `sum = a[i][j] − Σₖ<ⱼ L[i][k]·L[j][k]`;
  - si i == j: `sum <= 0` o no finito → `IllegalArgumentException("Cholesky.matrix must be symmetric positive definite")`; si no, `L[i][i] = √sum`;
  - si j < i: `L[i][j] = sum / L[j][j]`.
  - Además verifica la simetría: `|a[i][j] − a[j][i]| > SYMMETRY_TOLERANCE × max(1, |a[i][j]|)`, con `SYMMETRY_TOLERANCE = 1e-9`, lanza el mismo mensaje.
- `solve`: sustitución hacia adelante (L·y = b) y hacia atrás (Lᵀ·x = y).
- `solveTransposed`: solo la sustitución hacia atrás.
- Los bucles están acotados por la dimensión. Separá la verificación de simetría, la factorización y cada sustitución en métodos de una tarea.

### `LinearPosterior.java` (pública)

```java
/**
 * Bayesian linear regression with known noise, kept in information form: precision and
 * precision times mean (CT-30).
 */
public final class LinearPosterior {
  public static LinearPosterior prior(double[] mean, double variance) { … }
  public static LinearPosterior of(double[][] precision, double[] information, double observations) { … }

  public int dimension() { … }
  public double observations() { … }
  public double[] mean() { … }
  public double predict(double[] features) { … }
  public double[][] precision() { … copia … }
  public double[] information() { … copia … }

  public LinearPosterior withObservation(double[] features, double reward, double noiseVariance) { … }
  public LinearPosterior shrunkToward(LinearPosterior anchor, double keep) { … }
  public double[] sample(RandomSource random, double explorationScale) { … }
}
```

**Estado:** `double[][] precision`, `double[] information` y `double observations`, privados y finales. Constructor privado; los métodos de fábrica copian los arreglos.

- **`prior(mean, variance)`:**
  - `mean` no vacío y finito; `variance > 0` y finita;
  - Λ = I/variance;
  - b = Λ·mean, es decir, `mean[i] / variance`. **No** b = mean;
  - observations = 0.
- **`of(precision, information, observations)`:**
  - misma dimensión en los dos, `observations >= 0` y finito;
  - verifica que la precisión sea simétrica y definida positiva con `Cholesky.of` (deja pasar su excepción);
  - copia los arreglos.
- **`mean()`:** `Cholesky.of(precision).solve(information)`.
- **`predict(features)`:** producto escalar de `mean()` con `features` (largo verificado).
- **`withObservation(features, reward, noiseVariance)`:**
  - largo de `features` igual a la dimensión, todos finitos; `reward` finito; `noiseVariance > 0` y finita;
  - Λ' = Λ + x·xᵀ/σ²; b' = b + x·r/σ²; observations' = observations + 1.
- **`shrunkToward(anchor, keep)`:**
  - misma dimensión; `0 <= keep <= 1`;
  - Λ' = Λₐ + keep·(Λ − Λₐ); b' = bₐ + keep·(b − bₐ); observations' = observations·keep.
- **`sample(random, explorationScale)`:**
  - `explorationScale > 0` y finita;
  - z de largo d con `random.nextGaussian()` en orden (z[0] primero);
  - devuelve μ + √escala · `Cholesky.of(Λ).solveTransposed(z)`.

**Mensajes de error** (`IllegalArgumentException`), textuales:
- `"LinearPosterior.mean must not be empty"`
- `"LinearPosterior.variance must be a positive number, got " + variance`
- `"LinearPosterior.features must have " + d + " values, got " + n`
- `"LinearPosterior.features must be finite"`
- `"LinearPosterior.reward must be finite, got " + reward`
- `"LinearPosterior.noiseVariance must be a positive number, got " + noiseVariance`
- `"LinearPosterior.keep must be between 0 and 1, got " + keep`
- `"LinearPosterior.anchor must have " + d + " dimensions, got " + n`
- `"LinearPosterior.explorationScale must be a positive number, got " + explorationScale`
- `"LinearPosterior.observations must be zero or positive, got " + observations`
- `"LinearPosterior.information must have " + d + " values, got " + n`

Todas las validaciones van en métodos privados `require…`, y cada operación pública coordina.

### `docs/arquitectura.md`

Fila nueva en la tabla «Nombres en el código», al final:

| Modelo del aprendizaje de recetas (CT-30): precisión, información, sorteo | `LinearPosterior`, `Cholesky` | Dominio |

## Pruebas obligatorias

Tolerancia `1e-9` salvo que se diga otra. Los valores salen de cuentas a mano: están en la columna «Verifica».

### `CholeskyTest` (4)

| Prueba | Verifica |
| --- | --- |
| `factorsAKnownMatrix` | `of([[4,2],[2,3]]).lower()` = `[[2,0],[1,√2]]` |
| `solveInvertsTheMatrix` | `of([[3,4],[4,9]]).solve([2,4])` = `[2/11, 4/11]` |
| `solveTransposedIsTheBackSubstitution` | `of([[3,4],[4,9]]).solveTransposed([0,1])` = `[-0.6963106238227916, 0.5222329678670936]` |
| `aMatrixThatIsNotPositiveDefiniteIsRejected` | `of([[1,2],[2,1]])` lanza con `Cholesky.matrix must be symmetric positive definite`; `of([[2,1],[0,2]])` (no simétrica) también |

### `LinearPosteriorTest` (12)

| Prueba | Verifica |
| --- | --- |
| `priorMeanIsTheGivenMean` | `prior([0.5, 0, -1], 4).mean()` = `[0.5, 0, -1]`; `observations()` = 0 |
| `oneObservationGivesTheKnownPosteriorMean` | `prior([0,0], 1).withObservation([1,2], 1, 0.5)`: `precision()` = `[[3,4],[4,9]]`, `information()` = `[2,4]`, `mean()` = `[2/11, 4/11]`, `observations()` = 1 |
| `aNonZeroPriorMeanIsWeighedByItsPrecision` | `prior([1,0], 2).withObservation([1,0], 3, 1).mean()` = `[7/3, 0]` |
| `manyObservationsRecoverTheTrueWeights` | prior `[0,0,0]` con varianza 10; 50 vueltas sobre los rasgos `[1,0,0]`, `[0,1,0]`, `[0,0,1]`, `[1,1,0]`, `[0,1,1]` y `[1,0.5,0.25]`, con recompensa = `[0.2,-0.5,0.8]`·x y ruido 0,01 → media dentro de `1e-3` de `[0.2,-0.5,0.8]` |
| `predictIsTheMeanTimesTheFeatures` | el modelo de `oneObservation…` predice `2/11·1 + 4/11·3` = `14/11` para `[1,3]` |
| `shrinkingHalfwayTowardThePrior` | el modelo de `oneObservation…`, `shrunkToward(prior([0,0],1), 0.5)`: `precision()` = `[[2,2],[2,5]]`, `mean()` = `[1/6, 1/3]`, `observations()` = 0,5 |
| `keepOneChangesNothingAndKeepZeroIsTheAnchor` | con `keep` 1, la media no cambia; con 0, es la media del ancla |
| `sampleWithZeroDrawsIsTheMean` | `ScriptedRandomSource` con gaussianas `0, 0`: el sorteo de `oneObservation…` es su media |
| `sampleUsesTheInverseTransposedCholeskyFactor` | gaussianas `0, 1`, escala 1: `[2/11 − 0.6963106238227916, 4/11 + 0.5222329678670936]`; con escala 4 y otra vez `0, 1`, el desvío se duplica |
| `sampleCovarianceIsTheInversePrecision` | `SeededRandomSource(33)`, 20.000 sorteos del modelo de `oneObservation…` con escala 1: covarianza empírica dentro de `0.02` de `[[9/11, −4/11], [−4/11, 3/11]]` |
| `restoredModelHasTheSameMean` | `of(m.precision(), m.information(), m.observations())` tiene la misma media y las mismas observaciones; cambiar un arreglo devuelto por `precision()` no cambia `m` |
| `invalidInputsAreRejected` | uno por mensaje: rasgos de largo 3 en un modelo de 2, un rasgo `NaN`, recompensa infinita, ruido 0, `keep` 1,5, ancla de otra dimensión, escala 0, varianza 0, media vacía, observaciones −1, información de otro largo. Cada uno con su mensaje exacto |

Total: **16 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `withObservation`, no dividir por σ² | Λ = `[[2,2],[2,5]]`, media `[1/6, 1/3]` | `oneObservationGivesTheKnownPosteriorMean` |
| 2 | En `prior`, b = media (sin dividir por la varianza) | `[8/3, 0]` | `aNonZeroPriorMeanIsWeighedByItsPrecision` |
| 3 | En `sample`, usar L·z en vez de (Lᵀ)⁻¹·z | desvío `[0, 1.9149]` | `sampleUsesTheInverseTransposedCholeskyFactor` y `sampleCovarianceIsTheInversePrecision` |
| 4 | En `shrunkToward`, aplicar `keep` al ancla en vez de a la diferencia | otra precisión | `shrinkingHalfwayTowardThePrior` |
| 5 | En `Cholesky.of`, no verificar la simetría | no lanza | `aMatrixThatIsNotPositiveDefiniteIsRejected` |

## Procedimiento

1. Rama `wp-33a-modelo-bayesiano` desde `origin/main` actualizado.
2. `Cholesky` y su prueba. Commit: `feat: Cholesky factor and solves`.
3. `LinearPosterior` y su prueba. Commit: `feat: Bayesian linear posterior with Thompson sampling (CT-30)`.
4. `docs/arquitectura.md`. Commit: `docs: names for the recipe learning model`.
5. Pruebas que muerden, de a una y sin commit.
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-33A: Bayesian linear posterior`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Todo el WP es Java puro: compila y se prueba con el JDK 21 del contenedor. Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central (si da 429, de `https://repo.maven.apache.org/maven2/`) a una carpeta fuera del repo. Compilá con `javac --release 21` `src/main/java/io/github/nicodoou/mobai/domain`, las dos clases de `testsupport` y las pruebas nuevas, y corré el launcher con los jars **explícitos** en `-cp`.
- Formateá a mano con el estilo de google-java-format: 2 espacios, 100 columnas y la misma forma de partir líneas que el código vecino. El CI del PR (check `build`) verifica Spotless, ArchUnit y la suite completa: esperá a que termine.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si ArchUnit pide algo para un paquete nuevo del dominio (por ejemplo, una lista de paquetes permitidos), sumá `domain.learning` donde corresponda y avisalo.

## Fuera de alcance

- Rasgos de las recetas, búsqueda y simulación (WP-33B y WP-33C).
- Guardar el modelo en disco (WP-33E).
- Usarlo en el cerebro (WP-33D).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 16 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
