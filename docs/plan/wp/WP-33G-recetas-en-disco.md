# WP-33G — Recetas y rasgos en disco

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33F (mergeado: `BrainParts.traitLedger`) |
| Modelo | Sonnet |
| Rama | `wp-33g-recetas-en-disco` |

## Objetivo

CT-30, séptima pieza: que lo aprendido con recetas **sobreviva a un reinicio**.

1. **Archivos de grupo:** guardan el modelo de recetas de cada jugador.
2. **`state.json`:** guarda los rasgos de cada jugador (`TraitLedger`).
3. **Esquema 3**, con migración desde el 2. **No se borra nada:** los registros por estrategia quedan.
4. **Copia de seguridad antes de migrar:** si algún archivo tiene un esquema más viejo que el actual, la carpeta de memorias se copia una sola vez a `backups/schema-v<versión>/` antes de leer.

Como los incidentes copian el grupo con el mismo formato que el disco (`StoredGroup`), también llevan los modelos de recetas. Los rasgos en los incidentes quedan para el WP-33H.

## Decisiones tomadas en este WP

1. **El modelo se guarda tal cual:** precisión (matriz de 60 × 60), información (60), observaciones y último tick. Al leerlo, `LinearPosterior.of` verifica que la matriz sea válida. Un archivo con un modelo roto se pone en cuarentena como cualquier otro archivo dañado.
2. **Los rasgos van en `state.json`,** porque son del jugador y no de un grupo.
3. **Migración 2 → 3:** a los archivos de grupo les suma `recipeModels: []` y a `state.json`, `traits: []`. Se encadena con la de 1 → 2: un archivo versión 1 pasa por las dos.
4. **Copia de seguridad una vez por versión vieja:**
   - antes de leer, se busca el esquema más viejo entre `state.json` y los archivos de grupo; los que no se pueden leer se saltean;
   - si es menor que el actual y `backups/schema-v<ese número>/` no existe, se copian ahí `state.json` y `groups/*.json`;
   - si la carpeta ya existe, no se toca, así una segunda carga no pisa la copia original.
   - La carga no reescribe los archivos: el próximo guardado los escribe en versión 3.
5. **Las estrategias viejas no se tocan:** sus registros se guardan y se cargan como hoy (decisión 7 de Nico en CT-30).

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/persistence/`: `GroupFile`, `StateFile`, `RecordEntry`, `GroupFileMapper`, `SchemaMigrator`, `MemoryFiles`, `JsonMemoryRepository`
- `src/main/java/io/github/nicodoou/mobai/domain/port/`: `StoredGroup`, `StoredState`, `StoredAttackRecord`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/RecipeModelRecord.java`, `GroupMemory.java` (`recipeModels`, `storeRecipeModel`)
- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` (`of`, `precision`, `information`, `observations`)
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/TraitLedger.java`, `TraitSums.java`
- `src/main/java/io/github/nicodoou/mobai/application/StoredMemoriesMapper.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`storedState`, `restore`, `messaging`)
- Pruebas: `persistence/JsonMemoryRepositoryTest`, `persistence/SchemaMigratorTest`, `domain/port/StoredDataTest`, `application/SaveAndLoadMemoriesTest`, `bootstrap/CoreServicesTest`
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredRecipeModel.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredTraits.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredGroup.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredState.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/RecipeModelEntry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/TraitEntry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/persistence/GroupFile.java`, `StateFile.java`, `GroupFileMapper.java`, `SchemaMigrator.java`, `MemoryFiles.java`, `JsonMemoryRepository.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/StoredMemoriesMapper.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepositoryTest.java`, `SchemaMigratorTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/port/StoredDataTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/SaveAndLoadMemoriesTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/bootstrap/CoreServicesTest.java` |
| Modificar | las demás pruebas con `new StoredGroup(` o `new StoredState(` (cambio mecánico, lista abajo) |
| Modificar | `docs/arquitectura.md` |

**Cambio mecánico.** Antes de empezar corré `grep -rln "new StoredGroup(\|new StoredState(" src/`. Hoy, además de los de la tabla: `adapter/scheduler/PersistenceSchedulerTest`, `application/GuardedMemoryRepositoryTest` y `testsupport/InMemoryMemoryRepositoryTest`. Si aparece otro archivo, frená y reportá. En esas pruebas, los dos constructores suman `List.of()` al final.

## Especificación

### Puerto (`domain.port`)

```java
/** One player's recipe model as stored (CT-30). */
public record StoredRecipeModel(PlayerId player, RecipeModelRecord record) { … requireNonNull de los dos … }

/** One player's trait sums as stored (CT-30). */
public record StoredTraits(PlayerId player, TraitSums sums) { … requireNonNull de los dos … }
```

- **`StoredGroup`:** componente nuevo **al final**, `List<StoredRecipeModel> recipeModels`, con copia inmodificable y `requireDistinct(recipeModels, StoredRecipeModel::player, "recipeModels")`.
- **`StoredState`:** componente nuevo **al final**, `List<StoredTraits> traits`, con copia inmodificable y sin jugadores repetidos. Si está repetido: `IllegalArgumentException("StoredState.traits has a duplicate entry " + jugador)`, en un método privado.

### Archivos (`persistence`)

```java
record RecipeModelEntry(
    String player, double[][] precision, double[] information, double observations, long lastTick) {}

record TraitEntry(
    String player, double shield, double ranged, double armor, double weight, long lastTick) {}
```

- **`GroupFile`:** suma al final `List<RecipeModelEntry> recipeModels`.
- **`StateFile`:** suma al final `List<TraitEntry> traits`.
- **`GroupFileMapper`:**
  - escribe y lee las dos listas nuevas, con `required(…, "GroupFile.recipeModels")` y `required(…, "StateFile.traits")` al leer;
  - el modelo se arma con `LinearPosterior.of(precision, information, observations)` y `new RecipeModelRecord(modelo, lastTick)`;
  - los rasgos, con `new TraitSums(shield, ranged, armor, weight, lastTick)`;
  - cada conversión va en un método privado de una tarea, como las que ya existen.

### `SchemaMigrator.java`

- `CURRENT_VERSION = 3`; constante `VERSION_THREE = 3`.
- Constantes nuevas: `RECIPE_MODELS_FIELD = "recipeModels"`, `TRAITS_FIELD = "traits"` y `SERVER_TICK_FIELD = "serverTick"`.
- `migrate`, con un paso por versión:

```java
if (version < VERSION_TWO) {
  fromVersionOne(file);
}
if (version < VERSION_THREE) {
  fromVersionTwo(file);
}
```

- **`fromVersionTwo`:**
  - archivo de grupo (tiene `members`) sin `recipeModels` → suma `recipeModels: []`;
  - `state.json` (tiene `serverTick`) sin `traits` → suma `traits: []`;
  - pone `schemaVersion` en 3.
- **Comentario** encima de los dos `if`: `// One step per version, oldest first; a version 1 file goes through every step.`

### `MemoryFiles.java` y `JsonMemoryRepository.java`

`MemoryFiles`, métodos nuevos:
- `Path backupFolder(int version)` → `root/backups/schema-v<version>`;
- `void backupOnce(int version)`:
  - si la carpeta existe, no hace nada;
  - si no, la crea con su subcarpeta `groups` y copia `state.json` (si existe) y cada archivo de `groupFiles()`, con el mismo nombre;
  - un error de disco → `UncheckedIOException("Could not back up the memories to " + carpeta, e)`.

`JsonMemoryRepository.load()`, después de `if (!files.rootExists())`:

```java
oldestVersion().filter(version -> version < SchemaMigrator.CURRENT_VERSION).ifPresent(files::backupOnce);
```

- **`oldestVersion()`** (privado, `OptionalInt`): el menor `schemaVersion` entre `state.json` (si existe) y los `groupFiles()`.
- **`peekVersion(Path)`** (privado, `OptionalInt`): lee el archivo, lo parsea y toma `schemaVersion` como entero. Si algo falla (JSON roto, campo ausente, error de lectura), devuelve vacío: ese archivo ya va a terminar en cuarentena en la lectura normal.
- Los bucles están acotados por la cantidad de archivos.

### `StoredMemoriesMapper.java`

- **`toStored(group)`:** suma `group.memory().recipeModels()` como lista de `StoredRecipeModel`, en un orden determinista: por `player().value()`.
- **`toGroup(stored, settings)`:** después de armar el grupo, `group.memory().storeRecipeModel(…)` por cada uno.

### `CoreServices.java`

- **`storedState()`:** suma los rasgos: `traitLedger.capture()` como lista de `StoredTraits`, ordenada por jugador.
- **`restore(state)`:** suma `traitLedger.restore(…)`, con el mapa armado desde `state.traits()`.
- **De dónde sale `traitLedger`:** es el de las `BrainParts` que arma `messaging`. Guardá las partes (o el ledger) en `CoreServices` o en `Messaging`, lo que menos cambie, y avisalo.

### `docs/arquitectura.md`

Fila nueva al final de la tabla «Nombres en el código»:

| Modelo de recetas y rasgos guardados, sus entradas en disco, copia de seguridad al migrar (CT-30) | `StoredRecipeModel`, `StoredTraits`, `RecipeModelEntry`, `TraitEntry`, `MemoryFiles.backupOnce` | Puerto y persistencia |

## Pruebas obligatorias

Modelo de las pruebas: `LinearPosterior.prior(media, 1.0)` de 60, con la media en 0,5 en el índice 0, y una observación (`withObservation(rasgos con el índice 0 en 1 y el resto en 0,1, 0.7, 0.01)`). Rasgos: `new TraitSums(0.5, 0, 0.25, 1.5, 7000)`.

### `SchemaMigratorTest` (+3)

| Prueba | Verifica |
| --- | --- |
| `versionTwoGroupGetsEmptyRecipeModels` | un grupo versión 2 sale con `recipeModels` vacío y `schemaVersion` 3 |
| `versionTwoStateGetsEmptyTraits` | un `state.json` versión 2 sale con `traits` vacío y versión 3 |
| `versionOneGoesThroughEveryStep` | un grupo versión 1 sale con `dangerRecords` y `recipeModels` vacíos, y versión 3 |

Ajustá las pruebas existentes que esperan versión 2 como final: ahora es 3. Avisalo con la lista.

### `JsonMemoryRepositoryTest` (+4)

| Prueba | Verifica |
| --- | --- |
| `recipeModelsAndTraitsSurviveSaveAndLoad` | guardar un grupo con un `StoredRecipeModel` y un estado con un `StoredTraits`, y cargar: misma media del modelo (`1e-9`), mismas observaciones y último tick; mismos rasgos |
| `migratingBacksUpTheOldFilesFirst` | un grupo escrito a mano en versión 2: después de `load()`, existe `backups/schema-v2/groups/<id>.json` con el contenido original |
| `aSecondLoadKeepsTheFirstBackup` | después de la prueba anterior, se reemplaza el contenido de la copia por `"marker"` y se vuelve a cargar: la copia sigue diciendo `"marker"` |
| `currentFilesMakeNoBackup` | guardar y cargar con el esquema actual: no existe la carpeta `backups` |

### `StoredDataTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `duplicateRecipeModelsAreRejected` | dos modelos del mismo jugador en `StoredGroup` → excepción con `recipeModels` en el mensaje |
| `duplicateTraitsAreRejected` | dos rasgos del mismo jugador en `StoredState` → `StoredState.traits has a duplicate entry …` |

### `SaveAndLoadMemoriesTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `aGroupKeepsItsRecipeModelAcrossARestart` | un grupo con `storeRecipeModel(jugador, record)`; `capture` → repositorio en memoria → `LoadMemories` → el grupo cargado tiene el modelo con la misma media y el mismo último tick |

### `CoreServicesTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `traitsTravelWithTheStoredState` | después de observar a un jugador bloqueando, `storedState().traits()` lo trae; otro `CoreServices` con `restore(eseEstado)` da los mismos rasgos para ese jugador |

Total: **11 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `fromVersionTwo` sin sumar `recipeModels` | el grupo versión 2 no carga (cuarentena) | `versionTwoGroupGetsEmptyRecipeModels` |
| 2 | `backupOnce` copia aunque la carpeta exista | la copia vuelve a tener el contenido original | `aSecondLoadKeepsTheFirstBackup` |
| 3 | `StoredMemoriesMapper.toStored` sin modelos de recetas | el grupo cargado no tiene modelo | `aGroupKeepsItsRecipeModelAcrossARestart` |
| 4 | `CoreServices.restore` sin restaurar los rasgos | rasgos `(0, 0, 0)` | `traitsTravelWithTheStoredState` |

## Verificación en el juego (Nico, después del merge)

1. **Antes de levantar el server,** copiá a mano la carpeta de memorias del plugin (`run/plugins/MobAI/`), por si acaso. El plugin además hace su propia copia en `backups/schema-v2/`.
2. Levantá el server: tienen que aparecer `backups/schema-v2/` con los archivos viejos y, después del primer guardado, los archivos con `"schemaVersion": 3`, `recipeModels` y `traits`.
3. Con `planner: "RECIPES"`, peleá un rato, `stop`, levantá de nuevo y seguí peleando contra el mismo grupo. Las líneas `PLAN` del log tienen que mostrar que recuerda: las recetas siguen pareciéndose a las de antes del reinicio, y `traits` arranca con tus valores en vez de 0.

## Procedimiento

1. Rama `wp-33g-recetas-en-disco` desde `origin/main` actualizado (con el WP-33F).
2. `StoredRecipeModel`, `StoredTraits`, `StoredGroup`, `StoredState`, `StoredDataTest` y el cambio mecánico. Commit: `feat: stored recipe models and traits (CT-30)`.
3. `RecipeModelEntry`, `TraitEntry`, `GroupFile`, `StateFile`, `GroupFileMapper`, `SchemaMigrator` y sus pruebas. Commit: `feat: schema 3 keeps recipe models and traits`.
4. `MemoryFiles.backupOnce`, `JsonMemoryRepository` y sus pruebas. Commit: `feat: back up the memories before migrating`.
5. `StoredMemoriesMapper`, `CoreServices` y sus pruebas. Commit: `feat: recipe models and traits survive a restart`.
6. `docs/arquitectura.md`. Commit: `docs: names for stored recipes and traits`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33G: recipes and traits on disk`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación y sus pruebas compilan con el JDK 21 del contenedor. La persistencia usa Gson: bajá `com/google/code/gson/gson/2.11.0` de Maven Central (si da 429, `https://repo.maven.apache.org/maven2/`) junto con `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10. Compilá `domain`, `application`, `persistence` y sus pruebas con los jars **explícitos** en `-cp`. `CoreServicesTest` y lo que use Paper los verifica el CI.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si la versión de Gson del CI no coincide con 2.11.0 y algo se comporta distinto, ajustá a lo que diga el CI y avisalo.

## Fuera de alcance

- Rasgos en los incidentes y su reproducción (WP-33H).
- La base del server y el modo entrenamiento (WP-33I).
- Borrar los registros por estrategia (cuando Nico lo decida).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 11 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
