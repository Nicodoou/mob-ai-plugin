# WP-31C — Agrandar lo aprendido sin perderlo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-31B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-31c-agrandar-lo-aprendido` |

## Objetivo

Segunda parte del CT-33 (recomendación A del CT-31). Con el WP-31B, el modelo de recetas pasó de 60 a 76 parámetros. Los modelos ya guardados (las memorias de cada jugador y la base del server) tienen 60 y hoy no se pueden usar.

Este WP los **agranda al leerlos, sin perder nada de lo aprendido**:
- cada bloque de 15 rasgos pasa a 19;
- lo viejo queda en su lugar;
- los 4 parámetros nuevos de cada bloque arrancan como en un modelo vacío, incluido el arranque en el medio.

Además, hace una copia de seguridad antes del primer guardado con el formato nuevo, y muestra las perillas nuevas en el log y en `/mobai memory`.

## Por qué es exacto

El modelo se guarda en forma de información: la precisión `P` y la información `b = P × media`.

- **Un modelo vacío** tiene `P = I ÷ varianza` y `b = media ÷ varianza`. Es diagonal: ningún parámetro sabe nada de otro.
- **Lo aprendido antes** se aprendió con las perillas nuevas fuera del modelo, que es lo mismo que si hubieran valido 0. Un rasgo que siempre vale 0 no suma nada a la precisión ni a la información de su parámetro, ni a los cruces.
- **Por eso el modelo agrandado:**
  - copia `P` y `b` viejos en sus lugares nuevos;
  - pone en los lugares nuevos la diagonal y la información del punto de partida actual;
  - deja en 0 los cruces entre viejos y nuevos.
- **La predicción no cambia** para una receta con las perillas nuevas en 0, porque los dos grupos de parámetros quedan independientes.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` (`prior`, `of`, `precision`, `information`, `mean`, `predict`, `dimension`, `observations`)
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/`: `RecipePlanner` (`prior`, `anchor`, `current`, `teachBase`), `ContextualFeatures`, `RecipeFeatures`, `RecipePrior`
- `src/main/java/io/github/nicodoou/mobai/persistence/SchemaMigrator.java`, `MemoryFiles.java`, `JsonMemoryRepository.java` (solo la carga y la copia de seguridad)
- `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java` (`recipePart`), `TrainingDataLog.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/command/MemoryReport.java` (`recipeLine`), `src/main/resources/messages.yml` (`memory-recipe` y `memory-recipe-volley`)
- Pruebas: `RecipePlannerTest`, `SchemaMigratorTest`, `JsonMemoryRepositoryTest`, `IncidentJsonTest`, `DebugLogTest`, `TrainingDataLogTest`, `MemoryReportTest`

## Reglas de negocio

1. **`RecipeModelUpgrade.toCurrent(model, prior)`:**
   - si `model.dimension()` es `ContextualFeatures.DIMENSION` (76), devuelve `model` tal cual;
   - si es `LEGACY_DIMENSION` (60 = 15 × `ContextualFeatures.BLOCKS`), devuelve el modelo agrandado;
   - si es otra, lanza `IllegalArgumentException("RecipeModelUpgrade: a recipe model of <n> parameters is neither the current 76 nor the legacy 60")`;
   - `prior` tiene que ser de 76: si no, `IllegalArgumentException("RecipeModelUpgrade.prior must have 76 parameters, got <n>")`.
2. **Agrandar:**
   - el índice viejo `bloque × 15 + i` (con `i` < 15) va al nuevo `bloque × 19 + i`;
   - `P'[nuevo(a)][nuevo(c)] = P[a][c]` para todo par de índices viejos;
   - `b'[nuevo(a)] = b[a]`;
   - en cada índice nuevo `n` (`bloque × 19 + 15` a `bloque × 19 + 18`): `P'[n][n] = prior.precision()[n][n]` y `b'[n] = prior.information()[n]`;
   - el resto de `P'`, en 0;
   - las observaciones no cambian;
   - se arma con `LinearPosterior.of(P', b', observaciones)`.
3. **Dónde se aplica:** en `RecipePlanner`, en los tres lugares donde lee un modelo guardado:
   - el de la memoria del jugador, en `current`;
   - el de la base, en `anchor` y en `teachBase`.

   Siempre con `prior()` como punto de partida. Así vale igual para las memorias cargadas del disco, la base y lo que muestra `/mobai memory`.
4. **El disco se pone al día solo:** un modelo agrandado se guarda con 76 la próxima vez que el jugador juega un plan, y la base, la próxima vez que aprende. Los que no se tocan siguen con 60 en el disco y se agrandan al leerlos.
5. **Copia de seguridad:**
   - el esquema de memorias pasa a **4**: el paso de 3 a 4 solo cambia el número;
   - así, la primera carga con este código copia los archivos de versión 3 a `backups/`, como en las migraciones anteriores;
   - **la copia ahora incluye `base.json`** si existe (hoy copia solo el estado y los grupos);
   - `base.json` **no** cambia de versión: su formato es el mismo y solo crece el tamaño de sus matrices. (Desvío del CT-33, que decía versión 2: no hace falta.)
6. **Mostrar las perillas:**
   - **línea `PLAN` del log:** después de `retreat=%.2f`, agrega ` group=%.2f chase=%.2f`;
   - **`/mobai memory`:** los mensajes de receta suman ` · grupo <group>% · persecución <chase>%` antes de `: <rate>%`. Los valores son `groupRetreatStrength × 100` y `chaseCommitment × 100`, redondeados como la retirada;
   - **`training-data.jsonl`:** la versión del formato (`FORMAT_VERSION`) pasa de 1 a 2. La receta ya trae las perillas nuevas y los rasgos traen 76 valores; las líneas viejas quedan con 60.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeModelUpgrade.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlanner.java` (`current`, `anchor`, `teachBase` y una función privada) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/persistence/SchemaMigrator.java`, `MemoryFiles.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/DebugLog.java`, `TrainingDataLog.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/command/MemoryReport.java`, `src/main/resources/messages.yml` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/RecipeModelUpgradeTest.java` |
| Modificar | `RecipePlannerTest`, `SchemaMigratorTest`, `JsonMemoryRepositoryTest`, `DebugLogTest`, `TrainingDataLogTest`, `MemoryReportTest` |
| Modificar | `IncidentJsonTest`: solo si falla porque espera la versión 3 como la actual |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código»: `RecipeModelUpgrade`) |

Antes de empezar, buscá con grep:
- `CURRENT_VERSION`, `schemaVersion` y `"version"` en `src/test`, para encontrar las pruebas que fijan la versión actual;
- `memory-recipe` en `src/`, para los mensajes.

## Especificación

### `RecipeModelUpgrade`

```java
package io.github.nicodoou.mobai.domain.strategy;

/**
 * Brings a recipe model stored before CT-33 (15 features per block) up to the current features,
 * keeping everything it learned: the new parameters start as in an empty model.
 */
public final class RecipeModelUpgrade {
  public static final int LEGACY_FEATURES = 15;
  public static final int LEGACY_DIMENSION = LEGACY_FEATURES * ContextualFeatures.BLOCKS;

  private RecipeModelUpgrade() {}

  public static LinearPosterior toCurrent(LinearPosterior model, LinearPosterior prior)
}
```

- Un comentario corto explica por qué es exacto: lo aprendido antes vio las perillas nuevas en 0, así que no sabe nada de ellas ni de sus cruces.
- Funciones privadas de una tarea:
  - `newIndexOf(int legacyIndex)`;
  - `enlargedPrecision(...)`;
  - `enlargedInformation(...)`.

### `RecipePlanner`

Función privada:

```java
private LinearPosterior upgraded(LinearPosterior model) {
  return RecipeModelUpgrade.toCurrent(model, prior());
}
```

- **`current`:** `upgraded(record.model()).shrunkToward(anchor(), …)`.
- **`anchor`:** trabaja con `upgraded(base.model().get())`.
- **`teachBase`:** `base.model().map(this::upgraded).orElseGet(this::prior)`.

### `SchemaMigrator`

- `CURRENT_VERSION = 4`, con la constante `VERSION_FOUR = 4`.
- Paso nuevo `fromVersionThree(file)`: solo `file.addProperty(VERSION_FIELD, VERSION_FOUR)`, con el comentario:

  ```java
  // Nothing in the files changes: recipe models grow from 60 to 76 numbers when the domain reads
  // them (CT-33). The new number only makes the first load back the version 3 files up.
  ```

### `MemoryFiles.backupOnce`

Si existe `baseFile()`, lo copia a la carpeta de la copia con el mismo nombre (`base.json`).

### `DebugLog.recipePart`

El formato queda:

```
" recipe=z%d/%d/%d s%d/%d volley=%s delay=%d retreat=%.2f group=%.2f chase=%.2f traits=%.2f/%.2f/%.2f"
```

### `MemoryReport.recipeLine` y `messages.yml`

**Valores nuevos** del `Map`, con el mismo redondeo que `retreat`:

```java
"group", String.valueOf(Math.round(recipe.groupRetreatStrength() * PERCENT)),
"chase", String.valueOf(Math.round(recipe.chaseCommitment() * PERCENT)),
```

Si `Map.of` pasa de 10 pares, usá `Map.ofEntries` (corrección 2).

**`messages.yml`:**

```yaml
memory-recipe: "<gray>    <rank>. zombies <white><zombies></white> · arañas <white><spiders></white> · reserva <delay> ticks · retirada <retreat>% · grupo <group>% · persecución <chase>%: <white><rate>%</white>"
memory-recipe-volley: "<gray>    <rank>. zombies <white><zombies></white> · arañas <white><spiders></white> · andanada · reserva <delay> ticks · retirada <retreat>% · grupo <group>% · persecución <chase>%: <white><rate>%</white>"
```

### `TrainingDataLog`

`FORMAT_VERSION = 2`. Nada más cambia.

## Pruebas obligatorias

### `RecipeModelUpgradeTest` (6)

**Escenario de las pruebas 2 a 4:**
- `legacy` = `LinearPosterior.prior(media de 60 con 0,5 en el índice 0, 1.0)`, con dos observaciones:
  - `x1[i] = (i + 1) / 60.0` con éxito 0,8;
  - `x2[i] = 1 − i / 60.0` con éxito 0,3;
  - varianza de ruido 0,01.
- `prior` = `LinearPosterior.prior(RecipePrior.contextualMean(0.5, 2.0), 1.0)`.
- `upgraded = RecipeModelUpgrade.toCurrent(legacy, prior)`.

Tolerancia 1e-9.

| Prueba | Verifica |
| --- | --- |
| `currentModelsPassUnchanged` | `toCurrent(prior, prior)` es igual a `prior` |
| `legacyLearningKeepsItsPlace` | para todo `a`, `c` < 60: `upgraded.precision()[nuevo(a)][nuevo(c)] == legacy.precision()[a][c]` y `upgraded.information()[nuevo(a)] == legacy.information()[a]`, con `nuevo(a) = (a / 15) × 19 + a % 15`. Además, `upgraded.observations() == 2` y `upgraded.dimension() == 76` |
| `newParametersStartAsAnEmptyModel` | en los índices 15, 16, 17, 18, 34 y 75: la diagonal es 1,0, la información es la del `prior` (`[15] = 2`, `[16] = −2`, `[17] = 2`, `[18] = −2`, `[34] = 0`, `[75] = 0`) y el cruce con el índice 0 es 0 |
| `predictionsWithTheNewKnobsAtZeroDoNotChange` | `upgraded.predict(x1')` es igual a `legacy.predict(x1)`, con `x1'` = `x1` puesto en sus índices nuevos y 0 en los nuevos |
| `unknownSizesAreRejected` | un modelo de 10 parámetros: el mensaje exacto de la regla 1 |
| `aPriorOfTheWrongSizeIsRejected` | `toCurrent(prior, legacy)`: `"RecipeModelUpgrade.prior must have 76 parameters, got 60"` |

### `RecipePlannerTest` (2 nuevas)

| Prueba | Verifica |
| --- | --- |
| `aLegacyPlayerModelIsUpgradedWhenRead` | `current(Optional.of(new RecipeModelRecord(legacy, tick)), tick)` tiene dimensión 76 |
| `aLegacyBaseIsUpgradedAsTheAnchor` | con la base reemplazada por `legacy`, `anchor()` tiene dimensión 76 y sus observaciones son las de `legacy` (sin pasar el tope de la base) |

`legacy` es el mismo modelo de 60 de `RecipeModelUpgradeTest`. Armalo con un helper de la prueba.

### Persistencia

- **`SchemaMigratorTest`:**
  - `versionThreeOnlyGetsTheNewNumber`: un archivo de grupo de versión 3 sale igual, con `schemaVersion` 4;
  - `currentVersionPassesUnchanged` usa 4;
  - `versionOneGoesThroughEveryStep` termina en 4.
- **`JsonMemoryRepositoryTest`:** `backupIncludesTheBase`: con archivos de versión 3 y un `base.json`, la carga deja `backups/<carpeta de la versión 3>/base.json` igual al original. Usá el helper de la carpeta de copia que ya existe.
- **Pruebas que escriben archivos de versión 3 como actuales:** pasan a 4. Las que prueban la migración desde 1 o 2 esperan 4 al final.

### Visualización

- **`DebugLogTest`:** la línea esperada suma `group=0.30 chase=0.50` (los valores que el WP-31B puso en las recetas de las pruebas).
- **`MemoryReportTest`:** los valores de las líneas de receta suman `group` y `chase`.
- **`TrainingDataLogTest`:** la versión 2.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | `newIndexOf` sin el salto de bloque (`a` en lugar de `(a / 15) × 19 + a % 15`) | `legacyLearningKeepsItsPlace` |
| 2 | Diagonal nueva en 0, sin copiar la del prior (no se puede invertir) | `newParametersStartAsAnEmptyModel` (y la construcción lanza) |
| 3 | `current` sin `upgraded` | `aLegacyPlayerModelIsUpgradedWhenRead` |
| 4 | `backupOnce` sin `base.json` | `backupIncludesTheBase` |

## Verificación en el server (Nico, después del merge)

Opus actualiza `messages.yml` y `config.yml` de prueba (claves de los WP-31B y 31C) antes de que Nico reinicie.

- Al arrancar, aparece `plugins/MobAI/backups/` con la copia de la versión 3 (incluido `base.json`).
- `/mobai memory papu123` muestra las recetas con `grupo` y `persecución`.
- Las líneas `PLAN` del log muestran `group=` y `chase=`.
- **Atención:** recién con el WP-34C las perillas cambian cómo pelean los mobs.

## Procedimiento

1. Rama `wp-31c-agrandar-lo-aprendido` desde `origin/main` actualizado.
2. `RecipeModelUpgrade`, su prueba, `RecipePlanner` y sus pruebas. Commit: `feat: stored recipe models grow to the new features without losing what they learned (CT-33)`.
3. `SchemaMigrator`, `MemoryFiles` y sus pruebas. Commit: `feat: schema 4 backs up the memories and the base before the first save (CT-33)`.
4. `DebugLog`, `MemoryReport`, `messages.yml`, `TrainingDataLog` y sus pruebas. Commit: `feat: the new knobs in the log, the memory report and the training data (CT-33)`.
5. `arquitectura.md`. Commit: `docs: recipe model upgrade in the names table`.
6. Pruebas que muerden (de a una; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`.
8. Push, PR `WP-31C: grow what was learned without losing it (CT-33)`, CI en verde e informe con la prueba exacta que falló en cada rotura y cada prueba existente que cambiaste.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala y avisalo. `Map.ofEntries` en `MemoryReport` si hace falta.
3. Actualizar el número de versión esperado en pruebas existentes (3 → 4), informando cada una.

## Fuera de alcance

- Que el cerebro use las perillas (WP-34C).
- Reescribir en el disco los modelos que no se tocan.
- Reproducir incidentes grabados antes del CT-33.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las pruebas nuevas, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
