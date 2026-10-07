# WP-21 — Comandos

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-20B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-21-comandos` |

## Objetivo

`/mobai` con permiso `mobai.admin` (D1): `spawngroup [política]`, `status [grupo]`, `reset [jugador]` y `reload`. El log de debug legible pasa al WP-29, junto con las trazas (cambio de plan: así el WP-21 queda en comandos). `/mobai memory` es del WP-26.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 2 y 9)
- `src/main/java/io/github/nicodoou/mobai/application/RecruitMob.java`, `RecruitRequest.java`, `RecruitResult.java`, `DescribeGroup.java`, `GroupStatusView.java`, `ResetMemories.java`, `SettingsHolder.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `Messages.java`, `MessageKey.java`, `InvalidConfigException.java`; `src/main/resources/messages.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java`, `adapter/translate/VersionTranslator.java` (`entityTypeOf`)
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java`, `CoreServices.java`
- `docs/actualizar-paper.md`

## Reglas de negocio

1. **Permiso** `mobai.admin` para todo `/mobai` (`BasicCommand.permission()`). Sin subcomando o con uno desconocido: `UNKNOWN_SUBCOMMAND`.
2. **`spawngroup [política]`** (solo jugadores; desde la consola, `PLAYER_ONLY`):
   - el grupo de prueba del catálogo: 4 zombies, 3 esqueletos y 2 arañas, **sin equipo** (`getEquipment().clear()`), con `setCanPickupItems(false)` y `setRemoveWhenFarAway(false)` (si no, un miembro que desaparece con el server apagado queda para siempre en su grupo guardado);
   - en un anillo de radio `SPAWN_RING_BLOCKS` = 4 alrededor del jugador, a su misma altura, repartidos en ángulos iguales, en este orden: zombies, esqueletos, arañas;
   - el tipo de entidad sale de `VersionTranslator.entityTypeOf`;
   - el primero **funda** el grupo con la política pedida (o la por defecto de la configuración si no se pide); los demás se suman con `RecruitRequest.near`; a cada uno, `GoalInstaller.install`;
   - política desconocida: `UNKNOWN_POLICY` con la lista de válidas; no spawnea nada.
   - responde `GROUP_SPAWNED` con el id corto y la política.
3. **`status`**: una línea por grupo (`STATUS_LINE`) o `NO_GROUPS`. **`status <id corto>`**: esa línea, o `GROUP_NOT_FOUND`. El objetivo se muestra con el nombre del jugador si está conectado, si no con el id corto de su UUID; sin objetivo, `-`.
4. **`reset`**: borra toda la memoria (`RESET_ALL` con la cantidad de grupos). **`reset <jugador>`**: el jugador conectado con ese nombre (`Bukkit.getPlayerExact`); si no está conectado, `PLAYER_NOT_FOUND`; si no, `RESET_PLAYER` con nombre y cantidad.
5. **`reload`**: `reloadConfig()` y `ConfigLoader.load(getConfig())`; si sale bien, `settings.replace(...)` y `RELOAD_DONE`. **Cualquier `RuntimeException`** del cargador (incluida una `ArithmeticException` por un entero enorme, riesgo del WP-16) deja la configuración anterior y responde `RELOAD_FAILED` con el mensaje. `messages.yml` no se recarga (se lee al arrancar).
6. **Autocompletado:** subcomandos; después de `spawngroup`, las políticas; después de `status`, los ids cortos; después de `reset`, los jugadores conectados.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/Subcommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/MobAiCommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/GroupSpawner.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/SpawnGroupCommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/StatusCommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/ResetCommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/ReloadCommand.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/SpawnRing.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/RecruitMob.java` (`foundWithPolicy`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java` y `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java` (armar y registrar el comando, cargar `messages.yml`) |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/command/SpawnRingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/RecruitMobTest.java` (+2) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/MessagesTest.java` (si su prueba de claves necesita ajuste por las nuevas) |

## Especificación

Imports a tu criterio; Spotless decide el formato. API verificada con `javap`: `BasicCommand` (`execute(CommandSourceStack, String[])`, `suggest`, `permission`), `CommandSourceStack.getSender()`, `JavaPlugin.registerCommand(String, String, BasicCommand)`, `LivingEntity.setRemoveWhenFarAway`, `setCanPickupItems`, `EntityEquipment.clear()`, `World.spawnEntity(Location, EntityType)`.

### `RecruitMob.foundWithPolicy` (aplicación)

```java
  public RecruitResult foundWithPolicy(RecruitRequest request, SelectionPolicyType policy) {
    if (activeGroups.groupOf(request.mob()).isPresent()) {
      return new RecruitResult.Rejected(RecruitResult.Rejection.ALREADY_IN_GROUP);
    }
    return found(request, policy);
  }
```

`found` y `newGroup` pasan a recibir la política; `execute` les pasa `settings.current().selection().defaultPolicy()`. Comportamiento de `execute` sin cambios. `RecruitMob` pasa de 1 a 2 métodos públicos.

### `SpawnRing.java` (Java puro)

```java
/** Where each mob of a test group appears: evenly spaced on a ring around the player. */
public final class SpawnRing {
  static final double SPAWN_RING_BLOCKS = 4.0;

  private SpawnRing() {}

  public static List<Vec3> positions(Vec3 center, int count) { … }
}
```

Posición `i` (desde 0): ángulo `2π·i/count`; `(center.x + R·cos, center.y, center.z + R·sin)`. `count < 1`: `IllegalArgumentException("SpawnRing.count must be at least 1, got " + count)`.

### `GroupSpawner.java`

`GroupSpawner(RecruitMob recruitMob, GoalInstaller installer, VersionTranslator translator)`; `public GroupId spawnTestGroup(Player player, SelectionPolicyType policy)`:
1. Tipos en orden: `ZOMBIE` ×4, `SKELETON` ×3, `SPIDER` ×2 (constantes con nombre: `TEST_ZOMBIES`, `TEST_SKELETONS`, `TEST_SPIDERS`, con el comentario de que es el grupo de prueba del catálogo).
2. Posiciones con `SpawnRing.positions`.
3. Por cada uno: `spawnEntity` (con `translator.entityTypeOf(kind)`, castear a `Mob`), equipo vacío, sin juntar ítems, sin despawn por distancia.
4. El primero `foundWithPolicy`; el resto `execute(RecruitRequest.near(...))`; a todos, `installer.install`.
5. Devuelve el id del grupo fundado. Si un reclutamiento no da `Founded`/`Joined` (no debería), `IllegalStateException` con el resultado.

### Comandos

```java
/** One /mobai subcommand. */
public interface Subcommand {
  void run(CommandSender sender, List<String> args);

  List<String> suggestions(List<String> args);
}
```

`MobAiCommand implements BasicCommand`: un `Map<String, Subcommand>` (`spawngroup`, `status`, `reset`, `reload`) y `Messages`; `execute` busca el primer argumento y le pasa el resto; `suggest` devuelve nombres de subcomandos para el primer argumento y delega después; `permission()` = `"mobai.admin"`.

Cada subcomando con 3 dependencias como máximo: `SpawnGroupCommand(GroupSpawner, SettingsHolder, Messages)`, `StatusCommand(DescribeGroup, Messages)`, `ResetCommand(ResetMemories, Messages)`, `ReloadCommand(Plugin, SettingsHolder, Messages)` (este crea su `ConfigLoader`).

### `MessageKey` y `messages.yml`

Constantes nuevas y su línea (MiniMessage, en castellano):

| Clave | Ruta | Texto |
| --- | --- | --- |
| `PLAYER_ONLY` | `player-only` | `<red>Este comando lo tiene que usar un jugador.` |
| `UNKNOWN_POLICY` | `unknown-policy` | `<red>Política desconocida: <gray><policy></gray>. Usá una de: <gray><valid>` |
| `GROUP_SPAWNED` | `group-spawned` | `<green>Grupo <white><group></white> creado con <white><policy></white>.` |
| `STATUS_LINE` | `status-line` | `<gray><group></gray> <white><state></white> · <policy> · <members> miembros · estrategia <strategy> · objetivo <target>` |
| `NO_GROUPS` | `no-groups` | `<gray>No hay grupos activos.` |
| `GROUP_NOT_FOUND` | `group-not-found` | `<red>No hay un grupo <gray><group></gray>.` |
| `RESET_ALL` | `reset-all` | `<green>Memoria borrada en <white><groups></white> grupos.` |
| `RESET_PLAYER` | `reset-player` | `<green>Memoria de <white><player></white> borrada en <white><groups></white> grupos.` |
| `PLAYER_NOT_FOUND` | `player-not-found` | `<red>No hay un jugador conectado llamado <gray><player></gray>.` |

### Armado

- `AdapterServices` crea `GroupSpawner` y los subcomandos, y expone `MobAiCommand mobAiCommand()` (componente nuevo del record).
- `PluginRuntime.start`: `saveResource("messages.yml", false)` si no existe en la carpeta del plugin, `Messages.load(YamlConfiguration.loadConfiguration(archivo))`, y `plugin.registerCommand("mobai", "MobAI admin commands", adapters.mobAiCommand())`. Un `messages.yml` inválido se trata como `config.yml` inválido (el plugin se deshabilita con el mensaje).

### `docs/actualizar-paper.md`

Filas para `adapter/command/*` (Brigadier `BasicCommand`, `CommandSourceStack`, `CommandSender`, `Bukkit.getPlayerExact`, `World.spawnEntity`, `EntityEquipment.clear`, `setRemoveWhenFarAway`, `setCanPickupItems`; riesgo **medio** para `BasicCommand`, que es API de Paper reciente) y la actualización de `PluginRuntime` (`registerCommand`, `saveResource`, `YamlConfiguration`).

## Pruebas obligatorias

| Clase | Prueba | Verifica |
| --- | --- | --- |
| `SpawnRingTest` | `positionsAreOnTheRing` | 9 posiciones alrededor de `(10, 64, -5)`: cada una a 4.0 (`within(1e-9)`) del centro en horizontal y con `y` 64 |
| `SpawnRingTest` | `positionsAreEvenlySpaced` | 4 posiciones: `(14, 64, -5)`, `(10, 64, -1)`, `(6, 64, -5)`, `(10, 64, -9)` con `within(1e-9)` |
| `SpawnRingTest` | `countMustBePositive` | `positions(c, 0)`: mensaje exacto |
| `RecruitMobTest` | `foundWithPolicyUsesTheGivenPolicy` | `foundWithPolicy(loose(mob(1), ZOMBIE), RANDOM)`: `Founded` y el grupo con política `RANDOM` (la configuración dice `THOMPSON_SAMPLING`) |
| `RecruitMobTest` | `foundWithPolicyRejectsAMobAlreadyInAGroup` | `mob(1)` ya fundó: `Rejected(ALREADY_IN_GROUP)` y ningún id nuevo consumido |
| `MessagesTest` | (las existentes) | siguen pasando con las claves nuevas en el `messages.yml` incluido |

Total: **5 pruebas nuevas**. Los comandos se verifican en el server en la puerta E5.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `SpawnRing`, `sin` y `cos` intercambiados | `positionsAreEvenlySpaced` (la primera da `(10, 64, -1)`) |
| 2 | `foundWithPolicy` ignora la política y usa la por defecto | `foundWithPolicyUsesTheGivenPolicy` |
| 3 | Sacar una clave nueva de `messages.yml` | `bundledMessagesHaveEveryKey` |

## Procedimiento

1. Rama `wp-21-comandos` desde `origin/main` actualizado.
2. `RecruitMob.foundWithPolicy` y `SpawnRing` con sus pruebas. Commit: `feat: found a group with a chosen policy and place a spawn ring`.
3. `MessageKey`, `messages.yml`, `Subcommand`, los comandos, `GroupSpawner`, `MobAiCommand`, el armado y `docs/actualizar-paper.md`. Commit: `feat: add /mobai spawngroup, status, reset and reload`.
4. Roturas (de a una), `spotlessApply`, `build jacocoTestReport jacocoTestCoverageVerification`, push, PR `WP-21: admin commands`, check `build` en verde, informe con la prueba que falló en cada rotura. **Nunca** `runServer`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `execute` de `BasicCommand` obliga a declarar `CommandSyntaxException` y `-Xlint` se queja, declarala o atrapala sin cambiar el comportamiento, y avisalo.
3. Si un constructor real no coincide con este WP, usá el real y avisalo; si falta una pieza, frená.

## Aceptación

- [ ] Los archivos de la tabla; 5 pruebas nuevas en verde; 3 roturas mordieron.
- [ ] El tipo de mob al spawnear sale de `VersionTranslator`.
- [ ] `docs/actualizar-paper.md` actualizado.
- [ ] Build, cobertura y CI en verde.
