# WP-33J — La base en disco y `/mobai train`

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33I (mergeado: `RecipeBase`, `CoreServices.recipeBase`) |
| Modelo | Sonnet |
| Rama | `wp-33j-base-en-disco-y-train` |

## Objetivo

CT-30, décima pieza: que el entrenamiento **se pueda usar en el juego** y que la base **sobreviva a un reinicio**.

1. **`base.json`:** la base del server se guarda junto a las memorias, en su propio archivo, y se carga al arrancar.
2. **`/mobai train on <jugador>`**, **`/mobai train off <jugador>`** y **`/mobai train status`**.

## Decisiones tomadas en este WP

1. **La base viaja en `StoredState`** (como los rasgos del WP-33G), así usa el guardado periódico, el guardado al apagar y la protección de `GuardedMemoryRepository` que ya existen. En disco va a **su propio archivo**, `base.json`, para poder copiarla a otro server o borrarla sin tocar las memorias.
2. **`state.json` no cambia ni sube de versión:** la base no va adentro. `base.json` tiene su propio `schemaVersion`, empezando en 1.
3. **Un `base.json` dañado** va a cuarentena como cualquier archivo dañado, y el resto (estado, rasgos, grupos) se carga igual.
4. **Sin base en memoria no se escribe `base.json`,** y uno que ya exista no se borra.
5. **Quién entrena no se guarda** (decidido con Nico al presentar el diseño): al reiniciar el server nadie está en entrenamiento.
6. **La lógica del comando vive en la aplicación** (`TrainPlayers`), que se prueba en JUnit. `TrainCommand` solo traduce nombres de jugador y mensajes.
7. **`on` y `off` piden un jugador conectado,** como `/mobai reset <jugador>`.
8. **Con `planner: STRATEGIES`, `status` avisa** que el entrenamiento no hace nada hasta pasar a `RECIPES`.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeBase.java`, `RecipeBaseCapture.java`
- `src/main/java/io/github/nicodoou/mobai/domain/learning/LinearPosterior.java` (`of`, `precision`, `information`, `observations`)
- `src/main/java/io/github/nicodoou/mobai/domain/port/StoredState.java`
- `src/main/java/io/github/nicodoou/mobai/persistence/`: `JsonMemoryRepository`, `MemoryFiles`, `GroupFileMapper`, `RecipeModelEntry` (como modelo del formato), `StateFile`
- `src/main/java/io/github/nicodoou/mobai/application/SettingsHolder.java` (`section`)
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`storedState`, `restore`) y `AdapterServices.java` (`mobAiCommand`)
- `src/main/java/io/github/nicodoou/mobai/adapter/command/`: `ResetCommand` (como modelo), `Subcommand`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java` y `src/main/resources/messages.yml`
- Pruebas: `persistence/JsonMemoryRepositoryTest`, `bootstrap/CoreServicesTest`, y las que construyen `StoredState` (`InMemoryMemoryRepositoryTest`, `PersistenceSchedulerTest`, `SaveAndLoadMemoriesTest`, `GuardedMemoryRepositoryTest`, `StoredDataTest`)
- `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredState.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/BaseFile.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/persistence/GroupFileMapper.java`, `MemoryFiles.java`, `JsonMemoryRepository.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/TrainPlayers.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/application/TrainingStatus.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/TrainCommand.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java`, `AdapterServices.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/application/TrainPlayersTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepositoryTest.java`, `src/test/java/io/github/nicodoou/mobai/bootstrap/CoreServicesTest.java` |
| Modificar (cambio mecánico) | las pruebas que construyen `StoredState` (lista en «Contexto a leer») |
| Modificar | `docs/arquitectura.md` |

## Especificación

### `StoredState.java`

Componente nuevo **al final**: `Optional<LinearPosterior> base`, con `requireNonNull`. Las pruebas que construyen `StoredState` pasan `Optional.empty()` (cambio mecánico; si aparece otro archivo, sumalo y avisalo).

### `BaseFile.java` (`persistence`, nuevo)

`record BaseFile(int schemaVersion, double[][] precision, double[] information, double observations)`, del mismo estilo que `RecipeModelEntry`.

### `GroupFileMapper.java`

- `static final int BASE_VERSION = 1`.
- `BaseFile toBaseFile(LinearPosterior base)`: versión 1 y los tres valores del modelo.
- `LinearPosterior fromBaseFile(BaseFile file)`:
  - versión distinta de 1 → `IllegalArgumentException("base.json has schema version " + v + ", this plugin reads 1")`;
  - si no, `LinearPosterior.of(...)`, con los mismos `required(...)` que `toRecipeModel`.

### `MemoryFiles.java`

`Path baseFile()`: `root.resolve("base.json")`, con el nombre en una constante.

### `JsonMemoryRepository.java`

- **`save`:** después de `state.json`, si `memories.state().base()` está presente, escribe `base.json` con el mismo `writer` atómico.
- **`load`:**
  - `loadState` lee `state.json` como hoy;
  - si hay estado y existe `base.json`, lo lee con `readOrQuarantine` (un archivo dañado va a cuarentena y la lista `quarantined` lo trae);
  - el estado cargado lleva la base (`Optional.empty()` si no hay archivo o se puso en cuarentena).
- Sin `state.json`, `base.json` no se lee: queda en disco y se carga en el próximo arranque que tenga estado.

### `TrainingStatus.java` (`application`, nuevo)

`public record TrainingStatus(List<PlayerId> trainers, double basePlans, long weightCapPlans, PlannerKind planner)`, con `List.copyOf` y `requireNonNull` de `planner`.

### `TrainPlayers.java` (`application`, nuevo)

`public final class TrainPlayers`, constructor `TrainPlayers(RecipeBase base, Supplier<LearningSettings> learning)`.

| Método | Qué hace |
| --- | --- |
| `void start(PlayerId player)` | `base.startTraining(player)` |
| `void stop(PlayerId player)` | `base.stopTraining(player)` |
| `TrainingStatus status()` | `base.capture().trainers()`, las observaciones del modelo (0 sin modelo), `baseWeightPlans` y `planner` de la configuración |

### `TrainCommand.java` (`adapter.command`, nuevo)

`/mobai train <on|off> <jugador>` y `/mobai train status`. Constructor `TrainCommand(TrainPlayers trainPlayers, Messages messages)`.

- **Sin argumentos, o con una acción desconocida, o `on`/`off` sin jugador:** `TRAIN_USAGE`.
- **`on <jugador>` / `off <jugador>`:**
  - jugador no conectado (`Bukkit.getPlayerExact`): `PLAYER_NOT_FOUND`, como `ResetCommand`;
  - si no, `start` o `stop` y `TRAIN_ON` o `TRAIN_OFF` con `player`.
- **`status`:**
  - `TRAIN_STATUS` con `plans` (redondeado a entero), `cap` y `trainers`. Los nombres salen de `Bukkit.getOfflinePlayer(uuid).getName()`; si es `null`, el UUID. Separados por `", "`. Con la lista vacía, `TRAIN_STATUS_NOBODY` (con `plans` y `cap`);
  - con `planner == STRATEGIES`, además `TRAIN_STRATEGIES`.
- **Sugerencias:** primer argumento `on`, `off`, `status` filtrados por prefijo; segundo argumento, después de `on` u `off`, los jugadores conectados, como `ResetCommand`.

### `MessageKey.java` y `messages.yml`

Claves nuevas, al final del enum y del archivo:

```yaml
train-usage: "<red>Usá: <gray>/mobai train <on|off> <jugador></gray> o <gray>/mobai train status</gray>."
train-on: "<green><white><player></white> entra en entrenamiento: sus peleas le enseñan a la base del server."
train-off: "<green><white><player></white> sale del entrenamiento."
train-status: "<gray>Base del server: <white><plans></white> planes (pesa hasta <white><cap></white>). En entrenamiento: <white><trainers></white>."
train-status-nobody: "<gray>Base del server: <white><plans></white> planes (pesa hasta <white><cap></white>). Nadie en entrenamiento."
train-strategies: "<yellow>El planificador es STRATEGIES: el entrenamiento no hace nada hasta poner <gray>planner: \"RECIPES\"</gray>."
```

`unknown-subcommand` suma `train` a la lista de subcomandos.

### `CoreServices.java` y `AdapterServices.java`

- `CoreServices.storedState()`: el cuarto argumento es `recipeBase.model()`.
- `CoreServices.restore(state)`: `state.base().ifPresent(recipeBase::replace)`.
- `AdapterServices.mobAiCommand`: `subcommands.put("train", new TrainCommand(new TrainPlayers(core.recipeBase(), core.settings().section(MobAiSettings::learning)), messages))`, después de `reinforce`.

### `docs/arquitectura.md`

Fila nueva después de la de la base del server:

| La base en disco y el comando de entrenamiento (CT-30) | `base.json`, `BaseFile`, `TrainPlayers`, `TrainingStatus`, `TrainCommand` (`/mobai train`) | Persistencia, aplicación y adaptadores |

## Pruebas obligatorias

Modelo de las pruebas: `LinearPosterior.prior(media de 60 con 0,5 en el índice 0, 1.0)` con 3 observaciones `withObservation(rasgos en 0,5, 1.0, 0.01)`.

### `JsonMemoryRepositoryTest` (+4)

| Prueba | Verifica |
| --- | --- |
| `baseSurvivesSaveAndLoad` | guardar un estado con la base y cargar: el estado cargado trae una base igual (`equals`) |
| `stateWithoutBaseWritesNoBaseFile` | guardar un estado sin base: no existe `base.json` |
| `corruptBaseIsQuarantinedAndTheStateStillLoads` | `base.json` con `"{"`: el estado carga con la base vacía, `quarantined` nombra `base.json` y existe `base.json.corrupt` |
| `unknownBaseVersionIsQuarantined` | `base.json` válido con `schemaVersion` 2: va a cuarentena y el estado carga sin base |

### `CoreServicesTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `baseTravelsWithTheStoredState` | `recipeBase().replace(modelo)`; `storedState().base()` lo trae; otro `CoreServices` con `restore(eseEstado)` tiene la misma base |
| `trainingIsNotStored` | Alice en entrenamiento; otro `CoreServices` con `restore(storedState())`: Alice no está en entrenamiento |

### `TrainPlayersTest` (+4, nuevo)

| Prueba | Verifica |
| --- | --- |
| `startPutsThePlayerInTraining` | `start(Alice)`: `base.isTraining(Alice)` |
| `stopTakesThePlayerOut` | `start(Alice)` y `stop(Alice)`: ya no entrena |
| `statusCountsTheBasePlans` | base con 3 observaciones, Bob y Alice entrenando: `basePlans` 3 (`1e-9`), `weightCapPlans` 600 y los dos en orden |
| `statusReportsThePlanner` | con `TestSettings.withRecipes()`, `planner` es `RECIPES`; con `defaults()`, `STRATEGIES` |

`MessagesTest.bundledMessagesHaveEveryKey` cubre las claves nuevas sin cambios.

Total: **10 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | `save` no escribe `base.json` | el estado carga sin base | `baseSurvivesSaveAndLoad` |
| 2 | `base.json` se lee sin `readOrQuarantine` | la carga entera lanza | `corruptBaseIsQuarantinedAndTheStateStillLoads` |
| 3 | `CoreServices.restore` sin la base | base vacía | `baseTravelsWithTheStoredState` |
| 4 | `TrainPlayers.stop` no hace nada | Alice sigue entrenando | `stopTakesThePlayerOut` |

## Verificación en el juego (Nico, después del merge)

1. **Antes de levantar el server:** el `messages.yml` del server de prueba (`run/plugins/MobAI/messages.yml`) es una copia vieja y no tiene las claves nuevas: si falta una, el plugin no arranca. Borralo para que se regenere, o copiale las 6 claves `train-*` y la línea nueva de `unknown-subcommand`.
2. Con `planner: "RECIPES"`: `/mobai train on <tu nombre>`, peleá unos planes y `/mobai train status`: la base tiene que sumar un plan por cada plan cerrado contra vos.
3. Reiniciá el server: `status` muestra los mismos planes y nadie en entrenamiento; existe `plugins/MobAI/base.json`.

## Procedimiento

1. Rama `wp-33j-base-en-disco-y-train` desde `origin/main` actualizado (con el WP-33I).
2. `StoredState` y el cambio mecánico. Commit: `feat: the stored state carries the recipe base (CT-30)`.
3. `BaseFile`, `GroupFileMapper`, `MemoryFiles`, `JsonMemoryRepository` y sus pruebas. Commit: `feat: base.json next to the memories`.
4. `CoreServices` y sus pruebas. Commit: `feat: the recipe base survives a restart`.
5. `TrainingStatus`, `TrainPlayers`, su prueba, `TrainCommand`, `MessageKey`, `messages.yml` y `AdapterServices`. Commit: `feat: /mobai train on, off and status`.
6. `docs/arquitectura.md`. Commit: `docs: names for the base on disk and training`.
7. Pruebas que muerden, de a una y sin commit.
8. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
9. Push, PR `WP-33J: base on disk and /mobai train`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- El dominio, la aplicación, la persistencia y sus pruebas compilan con el JDK 21 del contenedor. Bajá de Maven Central (si da 429, `https://repo.maven.apache.org/maven2/`) `gson` 2.11.0, `slf4j-api`, `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3 y `byte-buddy` 1.15.10, con los jars **explícitos** en `-cp`.
- Lo que use Paper (`TrainCommand`, `MessageKey`/`MessagesTest`, `CoreServices`/`CoreServicesTest`, `AdapterServices`) lo verifica el CI.
- Formateá con google-java-format 1.36.1 y `--skip-reflowing-long-strings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá y avisalo.
3. Si `JsonMemoryRepository` pasa las 20 funciones públicas o una función pasa las 20 líneas, separá la lectura de la base en una función privada y avisalo.

## Fuera de alcance

- `training-data.jsonl` y `/mobai memory` con recetas (WP-33K).
- Borrar o reiniciar la base desde el juego (si hace falta, se borra `base.json` con el server apagado).
- Guardar quién entrena.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 10 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
