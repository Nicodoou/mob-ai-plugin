# WP-33B — Recetas, rasgos y búsqueda

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33A (no lo usa todavía; misma serie) |
| Modelo | Opus |
| Rama | `wp-33b-recetas-y-busqueda` |

## Objetivo

CT-30, segunda pieza, **pura y sin usar todavía**:

1. **La receta jugada** (`PlanRecipe`): cuántos zombies presionan, flanquean o esperan en reserva; cuántas arañas presionan o flanquean; si hay andanada; cuándo entra la reserva; y el umbral de retirada.
2. **Sus rasgos** (`RecipeFeatures`): los 15 números que el modelo del WP-33A pesa, cuadráticos en cada perilla.
3. **La búsqueda** (`RecipeSearch`): con los pesos sorteados, la receta jugable de mayor puntaje.

## Decisiones tomadas en este WP (precisan el CT-30)

1. **La receta guarda cantidades, no fracciones.** Con mobs enteros, lo que se juega son cantidades; los rasgos se calculan con las fracciones **realizadas** (decisión 4 de Nico). Ya no hace falta pasar de fracciones pedidas a cantidades.
2. **Fracciones: se prueban todas las jugables.** Con n zombies hay (n+1)(n+2)/2 repartos (15 con 4 y 91 con 12) y n+1 para las arañas. Probarlos todos da el óptimo exacto y cuesta unas 2.400 evaluaciones como máximo, nada para el server.
3. **Demora y umbral: óptimo exacto, sin búsqueda iterativa.** En el modelo, cada una de esas perillas aparece solo con su valor y su cuadrado (a·x + b·x²), así que el mejor x en [0, 1] se calcula directo: los extremos y, si la curva tiene pico adentro (b < 0), el vértice −a/(2b). Es lo que buscaba la búsqueda binaria de la decisión 2 de Nico, pero exacto. Si un WP futuro suma interacciones con esas perillas, ahí sí hará falta la búsqueda gruesa-a-fina.
4. **Demora en escala logarítmica:** x = ln(demora/mín)/ln(máx/mín). Con la búsqueda, demora = round(mín·(máx/mín)^x).
5. **La demora solo cuenta si hay reserva:** sin zombies en reserva, sus dos rasgos valen 0 y la demora es la mínima. Si no, el modelo aprendería ruido de una perilla que no hizo nada.
6. **Andanada:** solo es candidata si es viable (2 o más esqueletos y 2 o más cuerpo a cuerpo, la regla de `VolleyStrategy`, que se reusa).
7. **Las arañas no tienen reserva** (CT-30).
8. **Empates:** gana el primer candidato en el orden de la enumeración (reemplaza solo un puntaje estrictamente mayor). Es determinista.
9. **Paquete `domain.strategy`:** las recetas reemplazan a las estrategias en el WP-33D; un paquete aparte haría un ciclo con `strategy`.
10. **Límites en un record** (`RecipeBounds`: demora mínima y máxima, umbral máximo). Llegan a la configuración en el WP-33D.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/GroupComposition.java`, `VolleyStrategy.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/SettingsChecks.java` (estilo de mensajes)
- Pruebas de referencia: `src/test/java/io/github/nicodoou/mobai/domain/strategy/` (cualquiera, para el estilo)
- `docs/arquitectura.md` (tabla «Nombres en el código»), para actualizarla

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RoleSplit.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/PlanRecipe.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeBounds.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeFeatures.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/UnitIntervalPeak.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeSearch.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/VolleyStrategy.java` (solo `isViableFor`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/PlanRecipeTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipeFeaturesTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/UnitIntervalPeakTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipeSearchTest.java` |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

## Especificación

### `RoleSplit.java`

```java
/** How many mobs of one kind take each role in a recipe. */
public record RoleSplit(int press, int flank, int reserve) {
  public RoleSplit { … }

  public int total() { return press + flank + reserve; }
}
```

Cualquier cantidad negativa → `IllegalArgumentException("RoleSplit counts must be zero or positive, got " + press + "/" + flank + "/" + reserve)`.

### `PlanRecipe.java`

```java
/** A plan as played (CT-30): roles by kind, volley, when the reserve joins and when mobs retreat. */
public record PlanRecipe(
    RoleSplit zombies,
    RoleSplit spiders,
    boolean volley,
    long reserveDelayTicks,
    double retreatHealthFraction) {
  public PlanRecipe { … }
}
```

Validaciones, en este orden:
- `requireNonNull` con `"PlanRecipe.zombies"` y `"PlanRecipe.spiders"`;
- `spiders.reserve() != 0` → `IllegalArgumentException("PlanRecipe.spiders cannot keep a reserve, got " + spiders.reserve())`;
- `reserveDelayTicks < 1` → `IllegalArgumentException("PlanRecipe.reserveDelayTicks must be at least 1, got " + reserveDelayTicks)`;
- `!(retreatHealthFraction >= 0 && retreatHealthFraction < 1)` → `IllegalArgumentException("PlanRecipe.retreatHealthFraction must be in [0, 1), got " + retreatHealthFraction)`.

### `RecipeBounds.java`

```java
/** The ranges the recipe search explores (CT-30). */
public record RecipeBounds(
    long minReserveDelayTicks, long maxReserveDelayTicks, double maxRetreatHealthFraction) {
  public RecipeBounds { … }
}
```

- `minReserveDelayTicks < 1` → `"RecipeBounds.minReserveDelayTicks must be at least 1, got " + v`;
- `maxReserveDelayTicks <= minReserveDelayTicks` → `"RecipeBounds.maxReserveDelayTicks must exceed the minimum, got " + max + " <= " + min`;
- `!(maxRetreatHealthFraction > 0 && maxRetreatHealthFraction < 1)` → `"RecipeBounds.maxRetreatHealthFraction must be in (0, 1), got " + v`.

### `RecipeFeatures.java`

```java
/** The numbers the recipe model weighs: each knob with its square, plus chosen interactions. */
public final class RecipeFeatures {
  public static final int DIMENSION = 15;

  private RecipeFeatures() {}

  public static double[] of(PlanRecipe recipe, int skeletons, RecipeBounds bounds) { … }
}
```

**Validaciones:** `skeletons < 0` → `"RecipeFeatures.skeletons must be zero or positive, got " + n`; sin mobs (zombies + arañas + esqueletos = 0) → `"RecipeFeatures needs at least one mob"`; si hay reserva y la demora está fuera de [mín, máx] → `"RecipeFeatures.reserveDelayTicks must be between " + min + " and " + max + ", got " + d`; `retreatHealthFraction > maxRetreatHealthFraction` → `"RecipeFeatures.retreatHealthFraction must not exceed " + max + ", got " + r`.

**Valores** (cada fracción vale 0 si su denominador es 0):
- `zf` = zombies.flank / zombies.total; `zr` = zombies.reserve / zombies.total;
- `sf` = spiders.flank / spiders.total;
- `u` = ln(demora/mín) / ln(máx/mín) si zombies.reserve > 0; si no, 0;
- `t` = retreatHealthFraction / maxRetreatHealthFraction;
- `v` = 1 con andanada, 0 sin;
- `k` = skeletons / (zombies.total + spiders.total + skeletons);
- `fs` = (zombies.flank + spiders.flank) / (zombies.total + spiders.total).

**Vector, en este orden exacto** (los índices los usan las pruebas y el WP-33C):

| Índice | Rasgo |
| --- | --- |
| 0 | 1 |
| 1, 2 | zf, zf² |
| 3, 4 | zr, zr² |
| 5, 6 | sf, sf² |
| 7, 8 | u, u² |
| 9, 10 | t, t² |
| 11 | v |
| 12 | zf·zr |
| 13 | v·k |
| 14 | fs·k |

Constantes con nombre para los índices que se usan fuera de la clase: `RETREAT_LINEAR = 9`, `RETREAT_SQUARE = 10`, `DELAY_LINEAR = 7`, `DELAY_SQUARE = 8` (públicas).

### `UnitIntervalPeak.java`

```java
/** Where a·x + b·x² is highest for x in [0, 1]; the smallest such x on a tie. */
public final class UnitIntervalPeak {
  private UnitIntervalPeak() {}

  public static double of(double linear, double quadratic) { … }
}
```

Candidatos en este orden: 0, 1 y, si `quadratic < 0` y el vértice `−linear/(2·quadratic)` está estrictamente entre 0 y 1, el vértice. Gana el de mayor valor; ante un empate, el primero de la lista. Valores no finitos → `IllegalArgumentException("UnitIntervalPeak coefficients must be finite, got " + linear + ", " + quadratic)`.

### `VolleyStrategy.java`

Método nuevo `public static boolean isViableFor(GroupComposition composition)`, con la regla de hoy (`skeletons >= MIN_SKELETONS && melee >= MIN_MELEE`). `isViable(snapshot)` pasa a ser `return isViableFor(GroupComposition.of(snapshot));`. Nada más cambia.

### `RecipeSearch.java`

```java
/** The playable recipe that scores best under one set of sampled weights (CT-30). */
public final class RecipeSearch {
  private final Supplier<RecipeBounds> bounds;

  public RecipeSearch(Supplier<RecipeBounds> bounds) { … requireNonNull(bounds, "RecipeSearch.bounds") … }

  public PlanRecipe best(double[] weights, GroupComposition composition) { … }
}
```

`best`, coordinando funciones privadas de una tarea:
1. `weights.length != RecipeFeatures.DIMENSION` → `IllegalArgumentException("RecipeSearch.weights must have 15 values, got " + n)` (con `DIMENSION`, no el 15 escrito); la composición sin mobs → `"RecipeSearch needs at least one mob"`.
2. Lee `bounds.get()` **una vez** por llamada.
3. `t* = UnitIntervalPeak.of(weights[RETREAT_LINEAR], weights[RETREAT_SQUARE])`; umbral = `t* × maxRetreatHealthFraction`.
4. `u* = UnitIntervalPeak.of(weights[DELAY_LINEAR], weights[DELAY_SQUARE])`; demora con reserva = `Math.round(min × Math.pow((double) max / min, u*))`; sin reserva, `min`.
5. Enumera, en este orden de bucles anidados:
   - flanqueadores zombie de 0 a n_z;
   - reserva zombie de 0 a n_z − flanqueadores (el resto presiona);
   - flanqueadoras araña de 0 a n_s (el resto presiona);
   - andanada `false` y, si `VolleyStrategy.isViableFor(composition)`, `true`.
6. Puntaje de cada candidato = Σ pesos[i] × `RecipeFeatures.of(candidato, composition.skeletons(), bounds)`[i]. Reemplaza al mejor solo con un puntaje **estrictamente mayor**.
7. Devuelve el mejor.

Todos los bucles están acotados por las cantidades del grupo.

### `docs/arquitectura.md`

Fila nueva en la tabla «Nombres en el código», al final:

| Receta jugada, reparto por tipo, límites, rasgos, pico en [0, 1], búsqueda de la receta (CT-30) | `PlanRecipe`, `RoleSplit`, `RecipeBounds`, `RecipeFeatures`, `UnitIntervalPeak`, `RecipeSearch` | Dominio |

## Pruebas obligatorias

Límites de las pruebas: `new RecipeBounds(25, 400, 0.6)`. Con esos límites, una demora de 100 da u = 0,5 (ln 4 / ln 16). Tolerancia `1e-9`. Los pesos se arman con un arreglo de 15 ceros y se cambian los índices de cada fila.

### `PlanRecipeTest` (4)

| Prueba | Verifica |
| --- | --- |
| `negativeCountsAreRejected` | `new RoleSplit(-1, 0, 0)` con su mensaje |
| `spidersCannotKeepAReserve` | arañas `(1, 0, 1)` con su mensaje |
| `reserveDelayAndRetreatAreValidated` | demora 0 y umbral 1,0, cada uno con su mensaje |
| `boundsAreValidated` | `RecipeBounds(0, 400, 0.6)`, `(25, 25, 0.6)` y `(25, 400, 1.0)`, cada uno con su mensaje |

### `RecipeFeaturesTest` (4)

| Prueba | Verifica |
| --- | --- |
| `knownRecipeGivesKnownFeatures` | zombies `(2, 1, 1)`, arañas `(1, 1, 0)`, andanada, demora 100, umbral 0,3, 3 esqueletos → `[1, 0.25, 0.0625, 0.25, 0.0625, 0.5, 0.25, 0.5, 0.25, 0.5, 0.25, 1, 0.0625, 1/3, 1/9]` |
| `withoutReserveTheDelayIsIgnored` | zombies `(4, 0, 0)`, demora 400 → índices 7 y 8 en 0 |
| `missingKindsGiveZeroFractions` | sin zombies ni arañas, 3 esqueletos, sin andanada → índices 1 a 8, 12 y 14 en 0; índice 13 en 0 |
| `invalidInputsAreRejected` | sin mobs, esqueletos −1, demora 401 con reserva, umbral 0,7: cada uno con su mensaje |

### `UnitIntervalPeakTest` (2)

| Prueba | Verifica |
| --- | --- |
| `findsThePeakOrTheBetterEnd` | `(1, −1)` → 0,5; `(3, −1)` → 1 (vértice en 1,5); `(1, 1)` → 1; `(−1, 0)` → 0; `(0, 0)` → 0 |
| `nonFiniteCoefficientsAreRejected` | `(NaN, 0)` con su mensaje |

### `RecipeSearchTest` (8)

Composición `new GroupComposition(zombies, skeletons, spiders)`.

| Prueba | Pesos (índice = valor) | Composición | Receta esperada |
| --- | --- | --- | --- |
| `allZeroWeightsKeepTheFirstCandidate` | todos 0 | (4, 3, 2) | zombies `(4,0,0)`, arañas `(2,0,0)`, sin andanada, demora 25, umbral 0 |
| `flankFractionFollowsThePeak` | 1 = 2, 2 = −2, 3 = −1 | (4, 0, 0) | zombies `(2,2,0)` |
| `anInteriorPeakPicksTheBestPlayableSplit` | 1 = 0,8, 2 = −1, 3 = −1 | (4, 0, 0) y (5, 0, 0) | con 4: `(2,2,0)`; con 5: `(3,2,0)` |
| `reserveDelayFollowsItsPeakInLogScale` | 3 = 1, 7 = 1, 8 = −1 | (4, 0, 0) | zombies `(0,0,4)`, demora 100 |
| `withoutReserveTheDelayIsTheMinimum` | 3 = −1, 7 = 1, 8 = −1 | (4, 0, 0) | zombies `(4,0,0)`, demora 25 |
| `retreatThresholdFollowsItsPeak` | 9 = 0,6, 10 = −1 | (4, 0, 0) | umbral 0,18 |
| `volleyOnlyWhenViable` | 11 = 1 | (2, 0, 0) y (2, 2, 0) | sin esqueletos: sin andanada; con 2: con andanada |
| `spiderFlankersFollowTheirPeak` | 5 = 0,8, 6 = −1 | (0, 0, 3) | arañas `(2,1,0)` |

Y en `RecipeSearchTest`, una prueba más de validación: `wrongWeightCountIsRejected` (14 pesos, con su mensaje).

Total: **19 pruebas** (4 + 4 + 2 + 9).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `UnitIntervalPeak`, aceptar el vértice fuera de (0, 1) | `(3, −1)` da 1,5 | `findsThePeakOrTheBetterEnd` |
| 2 | En `RecipeFeatures`, calcular `u` aunque no haya reserva | índices 7 y 8 en 1 | `withoutReserveTheDelayIsIgnored` |
| 3 | En `RecipeSearch`, ofrecer la andanada sin mirar la viabilidad | con (2, 0, 0) sale con andanada | `volleyOnlyWhenViable` |
| 4 | En `RecipeSearch`, reemplazar con puntaje mayor **o igual** | gana el último: zombies `(0,4,0)`, arañas `(0,2,0)`, con andanada | `allZeroWeightsKeepTheFirstCandidate` |
| 5 | En `RecipeFeatures`, `k` sobre el cuerpo a cuerpo en vez del total | índice 13 = 0,5 | `knownRecipeGivesKnownFeatures` |

## Procedimiento

1. Rama `wp-33b-recetas-y-busqueda` desde `origin/main` actualizado.
2. `RoleSplit`, `PlanRecipe`, `RecipeBounds` y su prueba. Commit: `feat: plan recipes as played (CT-30)`.
3. `RecipeFeatures`, `UnitIntervalPeak` y sus pruebas. Commit: `feat: quadratic recipe features and unit-interval peak`.
4. `VolleyStrategy.isViableFor`, `RecipeSearch` y su prueba. Commit: `feat: exhaustive recipe search under sampled weights`.
5. `docs/arquitectura.md`. Commit: `docs: names for recipes and their search`.
6. Pruebas que muerden, de a una y sin commit.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-33B: recipes, features and search`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Todo el WP es Java puro: compila y se prueba con el JDK 21 del contenedor. Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10 de Maven Central (si da 429, de `https://repo.maven.apache.org/maven2/`) a una carpeta fuera del repo. Compilá con `javac --release 21` `src/main/java/io/github/nicodoou/mobai/domain`, lo que haga falta de `testsupport` y las pruebas nuevas (y las de `VolleyStrategy` si existen), y corré el launcher con los jars **explícitos** en `-cp`.
- Formateá con google-java-format 1.36.1 (la versión de Spotless; bajala a una carpeta fuera del repo) o a mano con su estilo. El CI del PR (check `build`) verifica Spotless, ArchUnit y la suite completa: esperá a que termine.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Usar las recetas en el cerebro, asignar mobs concretos a cada rol y la configuración de los límites (WP-33D).
- La simulación (WP-33C).
- El rol de reserva (WP-33F).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 19 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
