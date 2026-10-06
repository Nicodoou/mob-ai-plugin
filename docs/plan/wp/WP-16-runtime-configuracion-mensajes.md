# WP-16 — Runtime, configuración y mensajes

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | Puertas E1 (pasada) y E4 |
| Modelo | Sonnet |
| Rama | `wp-16-runtime-configuracion-mensajes` |

## Objetivo

Las implementaciones reales de los puertos y la configuración del plugin:

1. `ServerTickCounter` (puerto `ServerClock`): un contador de ticks propio que sobrevive a los reinicios.
2. `JdkRandomSource` (puerto `RandomSource`) y `RandomGroupIdSource` (puerto `GroupIdSource`, CT-10).
3. `config.yml` con los valores del catálogo y `ConfigLoader`, que lo convierte en `MobAiSettings`.
4. `messages.yml`, `MessageKey` y `Messages`, para los textos que ve el jugador.

Nada de esto se engancha al server todavía: el armado es del WP-20.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/port/ServerClock.java`, `RandomSource.java`, `GroupIdSource.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/` (todo)
- `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Reloj propio.** `Bukkit.getCurrentTick()` vuelve a 0 en cada arranque, y el olvido necesita un tiempo que no retroceda. `ServerTickCounter` avanza 1 por tick (lo agenda el WP-20) y el arranque lo restaura con el tick guardado (`LoadReport.state()`). Restaurar hacia atrás es un error.
2. **Azar.** `JdkRandomSource` usa `java.util.SplittableRandom` con una semilla que recibe; la semilla la elige el arranque. La reproducción de incidentes no depende de la semilla: el WP-29 graba los números sorteados.
3. **Ids de grupo** con `UUID.randomUUID()` (versión 4).
4. **`config.yml`** trae exactamente los valores del catálogo y de D19, con la velocidad de aprendizaje calibrada en **1,0** (CT-09). Las claves van en inglés con guiones; los comentarios, en castellano, porque los lee el dueño del server.
5. **`ConfigLoader` no valida rangos:** solo presencia y tipo. Los rangos los validan los records del dominio, y el cargador traduce su `IllegalArgumentException` a `InvalidConfigException` con el mismo mensaje. Así hay una sola fuente de verdad para cada límite.
6. **Errores de configuración** (`InvalidConfigException`, en `adapter.config`), con estos mensajes exactos:
   - clave ausente: `"config.yml: missing <ruta>"`;
   - no es número: `"config.yml: <ruta> must be a number, got <valor>"`;
   - número con decimales donde va un entero: `"config.yml: <ruta> must be a whole number, got <valor>"`;
   - enum desconocido: `"config.yml: <ruta> must be one of <valores separados por coma y espacio>, got <valor>"`. YAML lee `OFF` sin comillas como `false`: el mensaje es el mismo, con `got false`, así queda claro qué leyó;
   - rango inválido (del dominio): `"config.yml: " + mensaje del dominio`.
7. **Mensajes** en MiniMessage (`<red>`, `<gray>`…). Los valores que se insertan (nombres de jugador, motivos de error) van como texto literal (`Placeholder.unparsed`), así un nombre con `<` no rompe el formato. Faltante en `messages.yml`: `"messages.yml: missing <ruta>"`. Las claves viven en el enum `MessageKey`; los WPs de comandos agregan constantes y líneas a `messages.yml`.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/runtime/ServerTickCounter.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/runtime/JdkRandomSource.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/runtime/RandomGroupIdSource.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/config/InvalidConfigException.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/config/Messages.java` |
| Crear | `src/main/resources/config.yml` |
| Crear | `src/main/resources/messages.yml` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/runtime/ServerTickCounterTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/runtime/JdkRandomSourceTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/runtime/RandomGroupIdSourceTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/config/MessagesTest.java` |

**Sin dependencias nuevas:** `YamlConfiguration` (SnakeYAML 2.2) y Adventure con MiniMessage 5.2.0 vienen con `paper-api` y están en el classpath de pruebas. Las pruebas usan `new YamlConfiguration()` y `loadFromString`, sin server.

## Especificación

### `ServerTickCounter.java`

```java
/** Ticks counted by the plugin; unlike Bukkit's counter, it survives restarts. */
public final class ServerTickCounter implements ServerClock {
  private long tick;

  @Override
  public long currentTick() {
    return tick;
  }

  public void advance() {
    tick++;
  }

  public void restore(long savedTick) {
    if (savedTick < tick) {
      throw new IllegalArgumentException(
          "ServerTickCounter cannot go back from " + tick + " to " + savedTick);
    }
    tick = savedTick;
  }
}
```

### `JdkRandomSource.java`

```java
public final class JdkRandomSource implements RandomSource {
  private final SplittableRandom random;

  public JdkRandomSource(long seed) {
    this.random = new SplittableRandom(seed);
  }

  @Override
  public double nextUnit() {
    return random.nextDouble();
  }

  @Override
  public double nextGaussian() {
    return random.nextGaussian();
  }

  @Override
  public int nextIndex(int bound) {
    if (bound < 1) {
      throw new IllegalArgumentException("JdkRandomSource.bound must be positive, got " + bound);
    }
    return random.nextInt(bound);
  }
}
```

### `RandomGroupIdSource.java`

```java
public final class RandomGroupIdSource implements GroupIdSource {
  @Override
  public GroupId nextGroupId() {
    return new GroupId(UUID.randomUUID());
  }
}
```

### `InvalidConfigException.java`

```java
public final class InvalidConfigException extends RuntimeException {
  public InvalidConfigException(String message) {
    super(message);
  }

  public InvalidConfigException(String message, Throwable cause) {
    super(message, cause);
  }
}
```

(Si `-Xlint:all` pide `serialVersionUID`, agregá `private static final long serialVersionUID = 1L;`.)

### `config.yml` (contenido exacto)

```yaml
# MobAI: configuración. Los valores son los del catálogo del MVP.
# Los tiempos van en ticks (20 ticks = 1 segundo) y las distancias, en bloques.
# Después de editar, aplicá los cambios con /mobai reload.

group:
  # Mobs por grupo como máximo.
  max-size: 12
  # Cada cuántos ticks decide el cerebro de cada grupo.
  decision-interval-ticks: 10
  # Distancia a la que un grupo ve a los jugadores.
  detection-radius-blocks: 24.0

memory:
  # Ticks para que lo aprendido pese la mitad (12.000 = 10 minutos de server prendido).
  half-life-ticks: 12000
  # Entre 0 y 1: más alto, aprende más rápido (1,0 calibrado en la simulación).
  learning-speed: 1.0
  # Cuánto vale un golpe bloqueado con escudo frente a uno que entra (0 a 1).
  partial-hit-weight: 0.5

selection:
  # THOMPSON_SAMPLING, EPSILON_GREEDY, EXPLORE_FIRST o RANDOM (entre comillas).
  default-policy: "THOMPSON_SAMPLING"
  memory-multiplier-min: 0.5
  memory-multiplier-max: 1.5
  # Para EPSILON_GREEDY: probabilidad de elegir al azar.
  epsilon: 0.1
  # Para EXPLORE_FIRST: intentos al azar antes de usar la memoria.
  explore-first-attempts: 10

target:
  # Cuánto tiempo cuenta el daño que hizo un jugador para su amenaza.
  threat-window-ticks: 600
  # Bonus por seguir con el mismo objetivo (0,2 = +20 %).
  commitment-bonus: 0.2
  base-threat: 1.0
  approach-speed-blocks-per-second: 3.0
  weakness-threat-multiplier-per-level: 0.5

plan:
  max-duration-ticks: 600
  target-lost-distance-blocks: 32.0
  target-lost-ticks: 200
  # Vida (fracción) a la que un mob pasa a retirarse.
  retreat-health-fraction: 0.3
  # Fracción de la vida del objetivo que hay que sacarle para un plan exitoso.
  full-success-damage-fraction: 0.5

attack:
  projectile-timeout-ticks: 60
  patient-strike-max-wait-ticks: 60
  opportunistic-shot-max-wait-ticks: 60
  shoot-min-distance-blocks: 8.0
  shoot-max-distance-blocks: 15.0
  flank-distance-blocks: 3.0
  retreat-distance-blocks: 16.0

spider:
  # Lentitud I por 3 segundos.
  slowness-duration-ticks: 60
  slowness-level: 1

persistence:
  # Cada cuánto se guardan las memorias (6.000 = 5 minutos); también se guardan al apagar.
  save-interval-ticks: 6000

debug:
  # OFF, DECISIONS o FULL (entre comillas: sin comillas, YAML lee OFF como false).
  default-trace-level: "OFF"
  flight-recorder-events: 200

retreat:
  # Vida (fracción) a la que un mob vuelve a pelear; tiene que ser mayor que retreat-health-fraction.
  recovery-health-fraction: 0.6
  # Sin jugadores a esta distancia, un mob en retirada se cura.
  heal-safe-distance-blocks: 12.0
  # Ventana de reagrupamiento: arranca en este valor y aprende entre el mínimo y el máximo.
  regroup-initial-ticks: 600
  regroup-min-ticks: 200
  regroup-max-ticks: 1200
  regroup-step-ticks: 50
```

### `ConfigLoader.java`

```java
public final class ConfigLoader {
  public MobAiSettings load(ConfigurationSection root) {
    try {
      return new MobAiSettings(
          group(root), memory(root), selection(root), target(root), plan(root),
          attack(root), spider(root), persistence(root), debug(root), retreat(root));
    } catch (IllegalArgumentException exception) {
      throw new InvalidConfigException("config.yml: " + exception.getMessage(), exception);
    }
  }
  …
}
```

- Una función privada por sección (`group`, `memory`, …, `retreat`), cada una arma su record leyendo las claves de `config.yml` con su ruta completa (`"group.max-size"`).
- Tres lectores privados, uno por tipo:
  - `double number(ConfigurationSection root, String path)`: el valor de `root.get(path)` tiene que ser `Number`; si falta, «missing»; si no es número, «must be a number».
  - `long wholeNumber(ConfigurationSection root, String path)`: usa `number` y exige que no tenga decimales (`value != Math.rint(value)` → «must be a whole number»). Los `int` de los records se pasan con `Math.toIntExact`.
  - `<E extends Enum<E>> E choice(ConfigurationSection root, String path, Class<E> type)`: el valor tiene que ser un `String` igual al nombre de una constante; si no (incluido `Boolean`), «must be one of».
- `InvalidConfigException` que lanzan los lectores **no** se envuelve otra vez: el `catch` es solo para `IllegalArgumentException`.

### `MessageKey.java`

```java
public enum MessageKey {
  NO_PERMISSION("no-permission"),
  UNKNOWN_SUBCOMMAND("unknown-subcommand"),
  RELOAD_DONE("reload-done"),
  RELOAD_FAILED("reload-failed");

  private final String path;

  MessageKey(String path) {
    this.path = path;
  }

  public String path() {
    return path;
  }
}
```

### `messages.yml` (contenido exacto)

```yaml
# MobAI: textos que ven los jugadores, en formato MiniMessage (https://docs.advntr.dev/minimessage/format.html).
# Lo que va entre <...> sin formato (por ejemplo <reason>) lo completa el plugin.

no-permission: "<red>No tenés permiso para usar este comando."
unknown-subcommand: "<red>Subcomando desconocido. <gray>Usá: /mobai <spawngroup|status|memory|reset|reload>"
reload-done: "<green>Configuración recargada."
reload-failed: "<red>No se recargó la configuración: <gray><reason>"
```

### `Messages.java`

```java
public final class Messages {
  private final Map<MessageKey, String> templates;

  private Messages(Map<MessageKey, String> templates) {
    this.templates = templates;
  }

  public static Messages load(ConfigurationSection root) { … }

  public Component render(MessageKey key, Map<String, String> values) {
    TagResolver[] placeholders =
        values.entrySet().stream()
            .map(entry -> Placeholder.unparsed(entry.getKey(), entry.getValue()))
            .toArray(TagResolver[]::new);
    return MiniMessage.miniMessage().deserialize(templates.get(key), placeholders);
  }
}
```

`load` lee cada `MessageKey` (en orden del enum) con `root.getString(key.path())`; si falta, `InvalidConfigException("messages.yml: missing " + key.path())`. Guarda un `EnumMap` inmutable (`Collections.unmodifiableMap`).

## Pruebas obligatorias

`TestSettings.defaults()` difiere del catálogo solo en `memory` (usa 0,7 a propósito). El valor esperado de la configuración incluida es `catalogSettings()`, un método privado de `ConfigLoaderTest` que arma `MobAiSettings` igual que `TestSettings.defaults()` pero con `new MemorySettings(12_000, 1.0, 0.5)`.

Para leer los archivos incluidos: `new String(getClass().getResourceAsStream("/config.yml").readAllBytes(), UTF_8)` y `loadFromString`.

### `ServerTickCounterTest` (3)

| Prueba | Verifica |
| --- | --- |
| `startsAtZeroAndAdvancesOneTickAtATime` | 0, y 3 después de tres `advance()` |
| `restoreJumpsToTheSavedTick` | `restore(123_000)`, después `advance()`: 123.001 |
| `restoreCannotGoBack` | después de `restore(50)`, `restore(10)` lanza con `"ServerTickCounter cannot go back from 50 to 10"` |

### `JdkRandomSourceTest` (3)

| Prueba | Verifica |
| --- | --- |
| `sameSeedGivesTheSameSequence` | dos fuentes con semilla 42: las mismas 5 llamadas alternadas (`nextUnit`, `nextGaussian`, `nextIndex(10)`) dan lo mismo |
| `unitsStayInsideZeroToOne` | 1.000 valores de `nextUnit` en `[0, 1)` |
| `indexRejectsANonPositiveBound` | `nextIndex(0)` lanza con `"JdkRandomSource.bound must be positive, got 0"` |

### `RandomGroupIdSourceTest` (1)

| Prueba | Verifica |
| --- | --- |
| `idsAreRandomVersionFourUuids` | dos ids distintos y `value().version()` = 4 |

### `ConfigLoaderTest` (8)

| Prueba | Verifica |
| --- | --- |
| `bundledConfigLoadsTheCatalogValues` | el `config.yml` incluido carga igual a `catalogSettings()` (`isEqualTo`) |
| `missingKeyIsReported` | el incluido sin `memory.half-life-ticks` (`set(path, null)`): `"config.yml: missing memory.half-life-ticks"` |
| `textWhereANumberGoesIsReported` | `group.max-size: "doce"`: `"config.yml: group.max-size must be a number, got doce"` |
| `decimalWhereAWholeNumberGoesIsReported` | `group.max-size: 12.5`: `"config.yml: group.max-size must be a whole number, got 12.5"` |
| `unknownPolicyIsReported` | `selection.default-policy: "SMART"`: `"config.yml: selection.default-policy must be one of THOMPSON_SAMPLING, EXPLORE_FIRST, EPSILON_GREEDY, RANDOM, got SMART"` |
| `unquotedOffIsReportedWithWhatYamlRead` | el texto `debug:\n  default-trace-level: OFF` en un YAML completo (el incluido con esa línea sin comillas): mensaje que termina en `", got false"` |
| `outOfRangeValuesUseTheDomainMessage` | `memory.learning-speed: 1.5`: `"config.yml: MemorySettings.learningSpeed must be between 0.0 and 1.0, got 1.5"` |
| `crossSectionRulesUseTheDomainMessage` | `retreat.recovery-health-fraction: 0.2` (menor que 0,3): el mensaje empieza con `"config.yml: RetreatSettings.recoveryHealthFraction must exceed PlanSettings.retreatHealthFraction"` |

Para las variantes, cargá el incluido y cambiá la clave con `set(path, valor)` antes de `load`, salvo en `unquotedOff…`, que reemplaza la línea en el texto.

### `MessagesTest` (4)

| Prueba | Verifica |
| --- | --- |
| `bundledMessagesHaveEveryKey` | `Messages.load` sobre el `messages.yml` incluido no lanza, y cada `MessageKey` se puede renderizar |
| `renderFillsPlaceholdersAsPlainText` | `RELOAD_FAILED` con `reason` = `"x<red>y"`: `PlainTextComponentSerializer.plainText().serialize(...)` = `"No se recargó la configuración: x<red>y"` |
| `renderAppliesTheFormat` | `RELOAD_DONE` renderizado tiene color `NamedTextColor.GREEN` (`component.color()` o el del primer hijo, según cómo lo arme MiniMessage: comprobalo y dejá la verificación que corresponda) |
| `missingMessageIsReported` | sección sin `reload-done`: `"messages.yml: missing reload-done"` |

Total: **19 pruebas**.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `config.yml`, `learning-speed: 0.7` | carga 0,7 en vez de 1,0 | `bundledConfigLoadsTheCatalogValues` |
| 2 | En `wholeNumber`, sacar la verificación de decimales | 12,5 pasa a 12 y no lanza | `decimalWhereAWholeNumberGoesIsReported` |
| 3 | En `Messages.render`, `Placeholder.parsed` en vez de `unparsed` | `<red>` se interpreta y el texto plano queda `"…: xy"` | `renderFillsPlaceholdersAsPlainText` |
| 4 | En `ServerTickCounter.restore`, sacar el `if` | no lanza | `restoreCannotGoBack` |

## Procedimiento

1. Rama `wp-16-runtime-configuracion-mensajes` desde `origin/main` actualizado.
2. `adapter/runtime` con sus pruebas. Commit: `feat: add server tick counter and jdk random sources`.
3. `config.yml`, `InvalidConfigException`, `ConfigLoader` con su prueba. Commit: `feat: add bundled config and config loader`.
4. `messages.yml`, `MessageKey`, `Messages` con su prueba. Commit: `feat: add messages with minimessage templates`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-16: runtime, config and messages`, esperar el check `build` en verde, informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. `serialVersionUID` en `InvalidConfigException` si lo pide `-Xlint`.
3. En `renderAppliesTheFormat`, elegir dónde leer el color según la estructura del componente (ver la tabla).
4. Si `YamlConfiguration` o MiniMessage no se pueden usar en JUnit sin server (error de inicialización), frená y reportá con el error exacto: no cambies el diseño.

## Fuera de alcance

- Agendar `advance()` cada tick, elegir la semilla, `saveDefaultConfig`, `/mobai reload` y el uso de los mensajes (WP-20 y WP-21).
- Mensajes de los demás comandos (WP-21 y WP-26 agregan claves).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con el contenido y los mensajes especificados.
- [ ] Las 19 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ninguna dependencia nueva.
- [ ] Build, cobertura y CI en verde.
