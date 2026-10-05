# WP-14 — Puerto de persistencia y JSON

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E4 Aplicación y persistencia |
| Depende de | Puerta E3 (pasada) |
| Modelo | Sonnet |
| Rama | `wp-14-persistencia-json` |

## Objetivo

1. **Puerto `MemoryRepository`** en el dominio, con los datos guardados como records simples: estado global (reloj y ventana de reagrupamiento) y, por grupo, política, último número de plan, miembros y registros de memoria (D14).
2. **`JsonMemoryRepository`** en `persistence`: un archivo JSON por grupo y un archivo de estado, con versión de formato, escritura segura (temporal + reemplazo) y cuarentena de archivos rotos (D16).
3. **`InMemoryMemoryRepository`** en `testsupport`, para las pruebas de la aplicación (WP-15).

No convierte grupos vivos en datos guardados ni al revés: eso es el WP-15.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/group/Member.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/AttackRecord.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java`, `StrategyId.java`, `GroupId.java`, `MobId.java`, `PlayerId.java`, `MobKind.java`
- `src/main/java/io/github/nicodoou/mobai/domain/selection/SelectionPolicyType.java`
- `src/test/java/io/github/nicodoou/mobai/ArchitectureTest.java` (solo la regla de `persistence`)

## Reglas de negocio

1. **Qué se guarda.**
   - Estado: tick del reloj del server y ventana de reagrupamiento vigente (CT-07).
   - Por grupo: id, política, último número de plan (para que los `PlanId` no se repitan después de un reinicio), miembros con tipo y orden de ingreso (D14), registros de ataques y de estrategias por jugador.
   - **No** se guarda: plan en curso, estado del ciclo, amenaza ni objetivo comprometido. Al reiniciar, cada grupo vuelve a observar.
2. **Archivos.** Dentro de la carpeta raíz que recibe el repositorio:
   - `state.json`;
   - `groups/<uuid del grupo>.json`.
3. **Guardar reemplaza todo (D16).** Escribe el estado, escribe cada grupo y borra los `groups/*.json` de grupos que no están en lo guardado. No toca otros archivos (`.corrupt`, `.tmp`).
4. **Escritura segura.** Cada archivo se escribe en `<nombre>.tmp` al lado del real y después se mueve encima del real con `ATOMIC_MOVE` y `REPLACE_EXISTING`. Si el sistema no soporta el movimiento atómico (`AtomicMoveNotSupportedException`), se mueve solo con `REPLACE_EXISTING`.
5. **Cargar.**
   - Carpeta raíz inexistente: carga vacía (primer arranque).
   - Los archivos de grupos se leen en orden de nombre, así la carga es determinista. Los `.tmp` y `.corrupt` se ignoran.
   - **Archivo roto** (JSON inválido, campo faltante, valor inválido, ataque o política desconocidos, id que no coincide con el nombre del archivo, versión ausente o menor que 1): se renombra a `<nombre>.corrupt` (reemplazando uno anterior), se anota su nombre en `quarantinedFiles` y la carga sigue con los demás. Un `state.json` roto deja el estado vacío.
   - **Versión más nueva** que la soportada: la carga entera falla con `IllegalStateException` y el archivo no se toca. Si se siguiera, el próximo guardado borraría ese archivo por «no estar en lo guardado».
6. **Errores de disco** (permisos, disco lleno): `UncheckedIOException` con la ruta en el mensaje. Los atrapa el adaptador.
7. **Versión de formato.** Cada archivo lleva `"schemaVersion": 1`. `SchemaMigrator` es el lugar donde van a vivir las migraciones; hoy no hay versión anterior que migrar.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/MemoryRepository.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredState.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredAttackRecord.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredStrategyRecord.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredGroup.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/StoredMemories.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/port/MemoryLoad.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/StateFile.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/GroupFile.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/MemberEntry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/RecordEntry.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/GroupFileMapper.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/SchemaMigrator.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/AtomicFileWriter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/MemoryFiles.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepository.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/testsupport/InMemoryMemoryRepository.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/port/StoredDataTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/persistence/SchemaMigratorTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/persistence/AtomicFileWriterTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/persistence/JsonMemoryRepositoryTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/testsupport/InMemoryMemoryRepositoryTest.java` |

**Gson:** no se agrega ninguna dependencia. `paper-api` ya trae Gson 2.14.0 (`compileOnly` en producción, y el server la provee en tiempo de ejecución; en las pruebas llega por `testImplementation(libs.paper.api)`). La regla de ArchUnit de `persistence` ya permite `com.google.gson`.

## Especificación: dominio (`domain/port`)

### `MemoryRepository.java`

```java
/** Where the group memories live between restarts; the plugin never touches files directly. */
public interface MemoryRepository {
  /** Throws UncheckedIOException on a disk error and IllegalStateException on a newer format. */
  MemoryLoad load();

  /** Replaces everything stored: groups that are not in {@code memories} are deleted. */
  void save(StoredMemories memories);
}
```

### `StoredState.java`

```java
public record StoredState(long serverTick, long regroupWindowTicks) {
  public StoredState {
    if (serverTick < 0) {
      throw new IllegalArgumentException(
          "StoredState.serverTick must be zero or positive, got " + serverTick);
    }
    if (regroupWindowTicks < 1) {
      throw new IllegalArgumentException(
          "StoredState.regroupWindowTicks must be at least 1, got " + regroupWindowTicks);
    }
  }
}
```

### `StoredAttackRecord.java` y `StoredStrategyRecord.java`

```java
public record StoredAttackRecord(PlayerId player, Attack attack, AttackRecord record) {
  public StoredAttackRecord {
    Objects.requireNonNull(player, "StoredAttackRecord.player");
    Objects.requireNonNull(attack, "StoredAttackRecord.attack");
    Objects.requireNonNull(record, "StoredAttackRecord.record");
  }
}
```

`StoredStrategyRecord(PlayerId player, StrategyId strategy, AttackRecord record)`: igual, con los mensajes `"StoredStrategyRecord.player"`, `"StoredStrategyRecord.strategy"` y `"StoredStrategyRecord.record"`.

### `StoredGroup.java`

```java
public record StoredGroup(
    GroupId id,
    SelectionPolicyType policy,
    long lastPlanSequence,
    List<Member> members,
    List<StoredAttackRecord> attackRecords,
    List<StoredStrategyRecord> strategyRecords) {
  public StoredGroup {
    Objects.requireNonNull(id, "StoredGroup.id");
    Objects.requireNonNull(policy, "StoredGroup.policy");
    if (lastPlanSequence < 0) {
      throw new IllegalArgumentException(
          "StoredGroup.lastPlanSequence must be zero or positive, got " + lastPlanSequence);
    }
    members = List.copyOf(members);
    attackRecords = List.copyOf(attackRecords);
    strategyRecords = List.copyOf(strategyRecords);
    requireDistinct(members, Member::id, "members");
    requireDistinct(members, Member::joinOrder, "join orders");
    requireDistinct(attackRecords, entry -> List.of(entry.player(), entry.attack()), "attackRecords");
    requireDistinct(
        strategyRecords, entry -> List.of(entry.player(), entry.strategy()), "strategyRecords");
  }

  private static <T> void requireDistinct(List<T> entries, Function<T, ?> key, String label) {
    Set<Object> seen = new HashSet<>();
    for (T entry : entries) {
      if (!seen.add(key.apply(entry))) {
        throw new IllegalArgumentException(
            "StoredGroup." + label + " has a duplicate entry " + key.apply(entry));
      }
    }
  }
}
```

### `StoredMemories.java`

```java
public record StoredMemories(StoredState state, List<StoredGroup> groups) {
  public StoredMemories {
    Objects.requireNonNull(state, "StoredMemories.state");
    groups = List.copyOf(groups);
    Set<GroupId> seen = new HashSet<>();
    for (StoredGroup group : groups) {
      if (!seen.add(group.id())) {
        throw new IllegalArgumentException(
            "StoredMemories.groups has a duplicate group " + group.id().shortId());
      }
    }
  }
}
```

### `MemoryLoad.java`

```java
public record MemoryLoad(
    Optional<StoredState> state, List<StoredGroup> groups, List<String> quarantinedFiles) {
  public MemoryLoad {
    Objects.requireNonNull(state, "MemoryLoad.state");
    groups = List.copyOf(groups);
    quarantinedFiles = List.copyOf(quarantinedFiles);
  }

  public static MemoryLoad empty() {
    return new MemoryLoad(Optional.empty(), List.of(), List.of());
  }
}
```

## Especificación: persistencia (`persistence`)

### Formato exacto

`state.json`:

```json
{
  "schemaVersion": 1,
  "serverTick": 123000,
  "regroupWindowTicks": 600
}
```

`groups/<uuid>.json`:

```json
{
  "schemaVersion": 1,
  "groupId": "00000000-0000-0000-0000-000000000001",
  "policy": "THOMPSON_SAMPLING",
  "lastPlanSequence": 3,
  "members": [
    { "mobId": "…", "kind": "ZOMBIE", "joinOrder": 1 }
  ],
  "attackRecords": [
    { "playerId": "…", "key": "zombie.front_strike", "successes": 1.5, "attempts": 2.0, "lastUpdateTick": 100 }
  ],
  "strategyRecords": [
    { "playerId": "…", "key": "FLANK", "successes": 0.4, "attempts": 1.0, "lastUpdateTick": 700 }
  ]
}
```

La `key` de un ataque es `Attack.id()` (estable aunque cambie el nombre de la constante); la de una estrategia, `StrategyId.value()`. El tipo de mob y la política van con el nombre de la constante del enum.

### Records de archivo

```java
record StateFile(int schemaVersion, long serverTick, long regroupWindowTicks) {}

record GroupFile(
    int schemaVersion,
    String groupId,
    String policy,
    long lastPlanSequence,
    List<MemberEntry> members,
    List<RecordEntry> attackRecords,
    List<RecordEntry> strategyRecords) {}

record MemberEntry(String mobId, String kind, long joinOrder) {}

record RecordEntry(
    String playerId, String key, double successes, double attempts, long lastUpdateTick) {}
```

Los cuatro son package-private (sin `public`): solo los usa el paquete `persistence`. Gson los lee y los escribe como records.

### `GroupFileMapper.java` (package-private, `final`)

| Método | Hace |
| --- | --- |
| `GroupFile toFile(StoredGroup group)` | Convierte con `schemaVersion` = `SchemaMigrator.CURRENT_VERSION`, en el mismo orden de las listas |
| `StoredGroup fromFile(GroupFile file)` | Convierte y valida. Todo error es `IllegalArgumentException` |
| `StateFile toFile(StoredState state)` | Igual para el estado |
| `StoredState fromFile(StateFile file)` | Igual para el estado |

Reglas de `fromFile`:

- Campo de objeto o lista ausente (`null`): `IllegalArgumentException("<Record>.<campo> is missing")`, por ejemplo `"GroupFile.members is missing"` o `"RecordEntry.key is missing"`. Hacé un único método privado `required(T value, String field)` que lo resuelva.
- UUID con `UUID.fromString`, enums con `valueOf` (los dos ya lanzan `IllegalArgumentException`).
- Ataque: `Attack.fromId(key).orElseThrow(() -> new IllegalArgumentException("Unknown attack id '" + key + "'"))`.
- Los records del dominio (`Member`, `AttackRecord`, `StoredGroup`) validan el resto.

### `SchemaMigrator.java` (package-private, `final`)

```java
final class SchemaMigrator {
  static final int CURRENT_VERSION = 1;
  private static final String VERSION_FIELD = "schemaVersion";

  // Migrations from older versions will go here, one step per version, oldest first.
  JsonObject migrate(JsonObject file, String fileName) {
    int version = versionOf(file, fileName);
    if (version > CURRENT_VERSION) {
      throw new IllegalStateException(
          "Memory file " + fileName + " has schema version " + version
              + ", newer than the supported " + CURRENT_VERSION);
    }
    return file;
  }

  private static int versionOf(JsonObject file, String fileName) {
    JsonElement version = file.get(VERSION_FIELD);
    if (version == null || !version.isJsonPrimitive() || !version.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("Memory file " + fileName + " has no schemaVersion");
    }
    if (version.getAsInt() < 1) {
      throw new IllegalArgumentException(
          "Memory file " + fileName + " has an invalid schemaVersion " + version.getAsInt());
    }
    return version.getAsInt();
  }
}
```

### `AtomicFileWriter.java` (package-private, `final`)

`void write(Path target, String content)`:

1. Crea las carpetas padre (`Files.createDirectories`).
2. Escribe `content` en UTF-8 en `target.resolveSibling(target.getFileName() + ".tmp")`.
3. Mueve el temporal encima de `target` con `ATOMIC_MOVE, REPLACE_EXISTING`; ante `AtomicMoveNotSupportedException`, con `REPLACE_EXISTING` solo.
4. Toda `IOException` sale como `new UncheckedIOException("Could not write " + target, exception)`.

Una función por paso (por ejemplo `write`, `writeTemporary`, `moveOver`).

### `MemoryFiles.java` (package-private, `final`)

Conoce la disposición de los archivos. Constructor `MemoryFiles(Path root)`.

| Método | Devuelve o hace |
| --- | --- |
| `boolean rootExists()` | `Files.isDirectory(root)` |
| `Path stateFile()` | `root/state.json` |
| `Path groupFile(GroupId id)` | `root/groups/<uuid>.json` |
| `List<Path> groupFiles()` | Los `groups/*.json` existentes (no `.tmp` ni `.corrupt`), ordenados por nombre. Lista vacía si no existe `groups/` |
| `String quarantine(Path file)` | Mueve `file` a `<nombre>.corrupt` con `REPLACE_EXISTING` y devuelve el nombre original (`file.getFileName().toString()`) |
| `void delete(Path file)` | `Files.deleteIfExists` |

Errores de disco: `UncheckedIOException` con la ruta, como en `AtomicFileWriter`.

### `JsonMemoryRepository.java` (`public final`, implementa `MemoryRepository`)

Constructor `JsonMemoryRepository(Path root)`; arma `MemoryFiles`, `AtomicFileWriter`, `GroupFileMapper`, `SchemaMigrator` y `new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()`.

**`save(StoredMemories memories)`**, en este orden:

1. Escribe `state.json`.
2. Escribe cada grupo en `groupFile(id)`.
3. Borra cada archivo de `groupFiles()` cuyo nombre no sea el de un grupo guardado.

**`load()`**:

1. Si `!rootExists()`: `MemoryLoad.empty()`.
2. Estado: si `state.json` no existe, vacío. Si existe, se lee con la misma función genérica que los grupos.
3. Grupos: cada archivo de `groupFiles()`, en orden. Además de lo que valida el mapper, el `groupId` tiene que coincidir con el nombre del archivo (sin `.json`); si no, `IllegalArgumentException("Memory file <nombre> holds group <groupId>")`.
4. Leer un archivo (función privada única, genérica en el tipo de record):
   1. `Files.readString` (UTF-8).
   2. `JsonParser.parseString`; si el resultado no es un objeto, `IllegalArgumentException("Memory file <nombre> is not a JSON object")`.
   3. `migrator.migrate(objeto, nombre)`.
   4. `gson.fromJson(objeto, tipo)` y el mapper.
5. Si leer un archivo lanza `JsonParseException` o `IllegalArgumentException`: `files.quarantine(archivo)`, se anota el nombre devuelto y se sigue. `IllegalStateException` (versión más nueva) y `UncheckedIOException` no se atrapan.

Funciones chicas: una para el estado, una para un grupo, una para leer y validar un archivo, una para la cuarentena.

### `testsupport/InMemoryMemoryRepository.java`

```java
public final class InMemoryMemoryRepository implements MemoryRepository {
  private Optional<StoredMemories> saved = Optional.empty();
  private int saveCount;

  @Override
  public MemoryLoad load() {
    return saved
        .map(memories -> new MemoryLoad(Optional.of(memories.state()), memories.groups(), List.of()))
        .orElseGet(MemoryLoad::empty);
  }

  @Override
  public void save(StoredMemories memories) {
    saved = Optional.of(memories);
    saveCount++;
  }

  public int saveCount() {
    return saveCount;
  }
}
```

## Pruebas obligatorias

Convenciones: `mob(n)` = `new MobId(new UUID(1, n))`, `player` = `new PlayerId(new UUID(2, 1))`, `groupId(n)` = `new GroupId(new UUID(0, n))`. Un grupo de ejemplo `sampleGroup(n)`:

```java
new StoredGroup(
    groupId(n),
    SelectionPolicyType.THOMPSON_SAMPLING,
    3,
    List.of(new Member(mob(1), MobKind.ZOMBIE, 1), new Member(mob(2), MobKind.SKELETON, 2)),
    List.of(new StoredAttackRecord(player, Attack.ZOMBIE_FRONT_STRIKE, new AttackRecord(1.37, 2.5, 100))),
    List.of(new StoredStrategyRecord(player, new StrategyId("FLANK"), new AttackRecord(0.4, 1.0, 700))))
```

y `sampleState()` = `new StoredState(123_000, 600)`. Las pruebas de persistencia usan `@TempDir Path root`.

### `StoredDataTest` (6)

| Prueba | Verifica |
| --- | --- |
| `storedGroupRejectsADuplicateMember` | dos `Member` con `mob(1)` (órdenes 1 y 2): mensaje que empieza con `"StoredGroup.members has a duplicate entry "` |
| `storedGroupRejectsADuplicateJoinOrder` | `mob(1)` y `mob(2)` con orden 1: mensaje que empieza con `"StoredGroup.join orders has a duplicate entry "` |
| `storedGroupRejectsADuplicateAttackRecord` | dos registros de `player` y `ZOMBIE_FRONT_STRIKE`: mensaje que empieza con `"StoredGroup.attackRecords has a duplicate entry "` |
| `storedGroupRejectsANegativePlanSequence` | `-1`: mensaje exacto `"StoredGroup.lastPlanSequence must be zero or positive, got -1"` |
| `storedGroupCopiesItsLists` | se construye con un `ArrayList` de miembros y después se le agrega otro: `members()` sigue con 2 |
| `storedMemoriesRejectsADuplicateGroup` | dos `sampleGroup(1)`: mensaje exacto `"StoredMemories.groups has a duplicate group " + groupId(1).shortId()` |

### `SchemaMigratorTest` (3)

| Prueba | Verifica |
| --- | --- |
| `currentVersionPassesUnchanged` | `{"schemaVersion": 1, "x": 2}` vuelve igual (`isEqualTo`) |
| `missingVersionIsRejectedAsCorrupt` | `{"x": 2}` con nombre `"a.json"`: `IllegalArgumentException` con `"Memory file a.json has no schemaVersion"` |
| `newerVersionIsRejected` | `{"schemaVersion": 2}`: `IllegalStateException` con `"Memory file a.json has schema version 2, newer than the supported 1"` |

### `AtomicFileWriterTest` (3)

| Prueba | Verifica |
| --- | --- |
| `writeCreatesMissingFoldersAndTheFile` | `write(root/a/b/file.json, "x")`: el archivo existe con contenido `"x"` |
| `writeReplacesAnExistingFile` | dos escrituras, `"first"` y `"second"`: queda `"second"` |
| `writeLeavesNoTemporaryFile` | después de escribir, no existe `file.json.tmp` |

### `JsonMemoryRepositoryTest` (13)

| Prueba | Verifica |
| --- | --- |
| `saveThenLoadReturnsTheSameMemories` | `save(new StoredMemories(sampleState(), List.of(sampleGroup(1))))` y `load()` en una instancia **nueva** sobre la misma carpeta: `state()` = `Optional.of(sampleState())`, `groups()` = `[sampleGroup(1)]` (`isEqualTo`), `quarantinedFiles()` vacío |
| `loadOfAMissingFolderIsEmpty` | carpeta `root/missing`: `isEqualTo(MemoryLoad.empty())` |
| `loadReturnsGroupsInFileNameOrder` | se guardan `sampleGroup(2)` y `sampleGroup(1)`, en ese orden: los ids cargados son `[groupId(1), groupId(2)]` |
| `saveDeletesTheFilesOfGroupsNoLongerSaved` | se guardan los grupos 1 y 2; después solo el 1: en `groups/` queda un único `.json` y la carga devuelve solo el grupo 1 |
| `saveKeepsQuarantinedFiles` | existe `groups/x.json.corrupt`; después de un `save`, sigue existiendo |
| `groupFileHasTheDocumentedShape` | después de guardar `sampleGroup(1)`, el archivo parseado con `JsonParser` tiene `schemaVersion` 1, `groupId` = `"00000000-0000-0000-0000-000000000001"`, `policy` = `"THOMPSON_SAMPLING"`, `lastPlanSequence` 3, `members[1].kind` = `"SKELETON"` y `attackRecords[0].key` = `"zombie.front_strike"` |
| `loadQuarantinesACorruptGroupFileAndKeepsTheRest` | grupos 1 y 2 guardados; se pisa el archivo del 2 con `{not json`; la carga devuelve solo el grupo 1, `quarantinedFiles` = `[<uuid del 2>.json]`, existe `<uuid del 2>.json.corrupt` y ya no existe el `.json` |
| `loadQuarantinesAGroupFileWithAnUnknownAttack` | se reemplaza `"zombie.front_strike"` por `"zombie.unknown"` en el archivo: queda en cuarentena |
| `loadQuarantinesAGroupFileWithAMissingField` | se borra la propiedad `members` del JSON (con `JsonParser` y `remove`): queda en cuarentena |
| `loadQuarantinesAGroupFileWhoseIdDoesNotMatchItsName` | se copia el archivo del grupo 1 con el nombre del grupo 2 y se borra el del 1: en cuarentena el del nombre 2 |
| `loadWithACorruptStateFileHasNoState` | se pisa `state.json` con `[]`: `state()` vacío, `quarantinedFiles` = `["state.json"]` y los grupos se cargan igual |
| `loadRejectsANewerSchemaVersionAndKeepsTheFile` | se cambia `schemaVersion` del grupo 1 a 2: `load()` lanza `IllegalStateException` y el `.json` sigue existiendo con su contenido |
| `loadIgnoresLeftoverTemporaryFiles` | existe `groups/<uuid>.json.tmp` con basura: la carga no lo pone en cuarentena y no falla |

### `InMemoryMemoryRepositoryTest` (2)

| Prueba | Verifica |
| --- | --- |
| `loadBeforeAnySaveIsEmpty` | `isEqualTo(MemoryLoad.empty())` |
| `loadReturnsTheLastSave` | dos `save`; la carga devuelve el segundo; `saveCount()` = 2 |

Total: **27 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `save`, saltear el paso 3 (borrado) | la carga devuelve los grupos 1 y 2 | `saveDeletesTheFilesOfGroupsNoLongerSaved` |
| 2 | En `load`, atrapar solo `JsonParseException` | el ataque desconocido lanza `IllegalArgumentException` y la carga falla | `loadQuarantinesAGroupFileWithAnUnknownAttack` |
| 3 | En `SchemaMigrator.migrate`, `>` por `>=` | la versión 1 se rechaza | `currentVersionPassesUnchanged` |
| 4 | En `GroupFileMapper`, intercambiar `successes` y `attempts` al leer | `AttackRecord(2.5, 1.37, 100)` es inválido (éxitos > intentos): el grupo queda en cuarentena | `saveThenLoadReturnsTheSameMemories` |

## Procedimiento

1. Rama `wp-14-persistencia-json` desde `main`.
2. Records del puerto, `MemoryRepository`, `StoredDataTest`, `InMemoryMemoryRepository` y su prueba. Commit: `feat: add memory repository port and stored data`.
3. `SchemaMigrator`, `AtomicFileWriter`, `MemoryFiles` con sus pruebas. Commit: `feat: add schema migrator and atomic file writer`.
4. Records de archivo, `GroupFileMapper`, `JsonMemoryRepository` con su prueba. Commit: `feat: add json memory repository`.
5. Pruebas que muerden (sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-14: memory repository port and json persistence`, esperar el check `build` en verde, informe.

## Correcciones permitidas

1. Formato de Spotless.
2. Si Gson no puede leer o escribir los records package-private por visibilidad, hacelos `public` (siguen en `persistence`) y avisalo.
3. Si `-Xlint` se queja del método genérico de lectura, ajustá solo su declaración y avisalo.

Cualquier otra cosa (por ejemplo, que Gson no esté en el classpath): frená y reportá.

## Fuera de alcance

- Convertir grupos vivos en `StoredGroup` y restaurarlos (WP-15), incluido el método del dominio para restaurar el número de plan.
- Guardar en otro hilo y en el apagado (WP-20).
- Memoria global por equipo (fase 2).
- Trazas e incidentes (WP-28).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas, el formato y los mensajes especificados.
- [ ] Las 27 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ninguna dependencia nueva.
- [ ] Build, cobertura y CI en verde.
