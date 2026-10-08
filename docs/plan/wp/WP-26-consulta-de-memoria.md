# WP-26 — Consulta de memoria

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-30B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-26-consulta-de-memoria` |

## Objetivo

Comando **`/mobai memory [jugador]`** para ver qué aprendió cada grupo de un jugador. Es la herramienta del test fuerte de Nico: muestra la adaptación en vez de intuirla.

Por cada grupo que recuerda al jugador muestra:

1. una cabecera con el grupo y el **nivel de peligro** (CT-27), más la vida que perdió el grupo y el daño que le hizo, ya con el olvido;
2. las **estrategias**, de la mejor a la peor según la tasa estimada, con cuántos planes las respaldan;
3. los **ataques**, de la misma forma.

El caso de uso `DescribePlayerMemory` ya existe (WP-15). Este WP le suma el peligro y arma el comando.

**Métricas:** la parte "métricas" del plan original ya la cubre el `mobai-debug.log`, con una línea por plan y por ataque (WP-29B). Las métricas en SQLite son de la fase 2. Este WP no agrega métricas.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/application/DescribePlayerMemory.java`, `PlayerMemoryView.java`, `SettingsHolder.java`
- `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (`dangerRecord`, `dangerRecords`), `DangerRecord.java`, `SuccessEstimate.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/DangerLevel.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/command/`: `ResetCommand`, `StatusCommand`, `SpawnGroupCommand`, `Subcommand`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (`tools`), `AdapterServices.java` (donde se registran los subcomandos)
- Pruebas: `DescribePlayerMemoryTest`, `MessagesTest`

## Reglas de negocio

1. **A quién consulta:**
   - `/mobai memory <nombre>` consulta a ese jugador, que tiene que estar conectado, como en `/mobai reset`. Si no está, `player-not-found`.
   - Sin nombre, el jugador que manda el comando se consulta a sí mismo. Desde la consola sin nombre, `player-only`.
2. **Qué grupos aparecen:** los activos que tienen algún registro del jugador, sea de ataques, de estrategias o de peligro. Si no hay ninguno, `memory-empty`.
3. **Cabecera de cada grupo:** id corto, nivel de peligro con dos decimales (`DangerLevel.of` con el registro decaído al tick actual), vida perdida y daño hecho con un decimal.
4. **Líneas:**
   - estrategias y después ataques, cada lista ordenada por la media de la estimación (`SuccessEstimate.mean()`) de mayor a menor;
   - a igual media, por nombre;
   - la tasa va en porcentaje entero (`Math.round(mean × 100)`) y el respaldo con un decimal (`observedAttempts`).
5. **Números con `Locale.ROOT`:** punto decimal, sin depender del idioma del server.
6. **El tick** sale del reloj del plugin (`core.clock().currentTick()`).

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/DescribePlayerMemory.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/application/PlayerMemoryView.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/CoreServices.java` (una línea en `tools`) |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/MessageLine.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/MemoryReport.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/MemoryCommand.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java` |
| Modificar | `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (registrar `memory`) |
| Modificar | `src/main/resources/paper-plugin.yml` o `plugin.yml`, **solo** si ahí se listan los subcomandos o sus permisos (si no, no lo toques) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/application/DescribePlayerMemoryTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/command/MemoryReportTest.java` |

Antes de empezar, buscá con grep `new DescribePlayerMemory(` y `new PlayerMemoryView(` en `src/`. Si aparecen fuera de los archivos de la tabla, frená y reportá.

## Especificación

### `PlayerMemoryView.java` y `DescribePlayerMemory.java`

**`PlayerMemoryView`:** dos componentes nuevos al final, `DangerRecord dangerRecord` (con `requireNonNull`) y `double danger`.

**`DescribePlayerMemory`:**
- El constructor pasa a `DescribePlayerMemory(ActiveGroups activeGroups, Supplier<SuccessSettings> success)`.
- En `CoreServices.tools`: `new DescribePlayerMemory(activeGroups, foundation.settings().section(MobAiSettings::success))`.
- `remembers` también es verdadero si `group.memory().dangerRecords().containsKey(player)`.
- `view` arma la vista con:
  - `record = memory.dangerRecord(player, tick)`;
  - `danger = DangerLevel.of(record, success.get())`.

### `MessageLine.java` y `MemoryReport.java` (puras, sin Paper)

```java
/** A message to send: its key and the values for its placeholders. */
record MessageLine(MessageKey key, Map<String, String> values) { … copia inmodificable … }
```

```java
/** The lines /mobai memory sends for one player, best strategies and attacks first. */
final class MemoryReport {
  private MemoryReport() {}

  static List<MessageLine> linesFor(String playerName, List<PlayerMemoryView> views) { … }
}
```

**Sin vistas:** devuelve una sola línea, `MEMORY_EMPTY` con `player`.

**Por cada vista:**
- `MEMORY_GROUP` con `group` (id corto), `danger` (`%.2f`), `lost` (`%.1f`, `healthLost`) y `dealt` (`%.1f`, `damageDealt`);
- después, una `MEMORY_STRATEGY` por estrategia, con `strategy` (`StrategyId.value()`), `rate` (porcentaje entero) y `support` (`%.1f`, `observedAttempts`);
- después, una `MEMORY_ATTACK` por ataque, con `attack` (`Attack.id()`), `rate` y `support`.

El orden de estrategias y ataques sigue la regla 4.

**Formato:** todo con `String.format(Locale.ROOT, …)`.

### `MemoryCommand.java`

`/** /mobai memory [player]: what each group has learned about a player. */`

- **Constructor:** `MemoryCommand(DescribePlayerMemory describePlayerMemory, LongSupplier currentTick, Messages messages)`.
- **`run`:** resuelve el jugador (regla 1), llama a `describePlayerMemory.execute(new PlayerId(uuid), currentTick.getAsLong())` y manda cada `MessageLine` con `messages.render(line.key(), line.values())`.
- **`suggestions`:** los jugadores conectados, igual que en `ResetCommand`.

**En `AdapterServices`:** `subcommands.put("memory", new MemoryCommand(core.describePlayerMemory(), core.clock()::currentTick, messages));`. Si el reloj no se llama así en `core`, usá el accessor que exista y avisá.

### `MessageKey.java` y `messages.yml`

**`MessageKey`:** `MEMORY_EMPTY("memory-empty")`, `MEMORY_GROUP("memory-group")`, `MEMORY_STRATEGY("memory-strategy")` y `MEMORY_ATTACK("memory-attack")`, al final.

**`messages.yml`:**

```yaml
memory-empty: "<gray>Ningún grupo activo recuerda a <white><player></white>."
memory-group: "<gold>Grupo <white><group></white> · peligro <white><danger></white> <gray>(perdió <lost> de vida, le hizo <dealt> de daño)"
memory-strategy: "<gray>  estrategia <white><strategy></white>: <white><rate>%</white> · <support> planes"
memory-attack: "<gray>  ataque <white><attack></white>: <white><rate>%</white> · <support> intentos"
```

`unknown-subcommand` suma `memory` a la lista: `/mobai <spawngroup|status|memory|reset|reload|debug>`.

## Pruebas obligatorias

### `DescribePlayerMemoryTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `viewCarriesTheDanger` | grupo con `recordDanger` de 80/10 en el tick `TICK`: `danger` 0,5 (con `TestSettings`: low 2, high 8, previo 10) y `dangerRecord` 80/10 |
| `groupRememberedOnlyByItsDangerIsListed` | grupo sin ataques ni estrategias, solo con peligro: aparece |

El constructor existente pasa a recibir `() -> TestSettings.defaults().success()`.

### `MemoryReportTest` (4)

| Prueba | Verifica |
| --- | --- |
| `noViewsGiveTheEmptyLine` | una línea `MEMORY_EMPTY` con `player` = el nombre |
| `headerShowsGroupAndDanger` | vista con peligro 0,5 y registro 80/10: primera línea `MEMORY_GROUP` con `danger` `"0.50"`, `lost` `"80.0"` y `dealt` `"10.0"` |
| `strategiesComeBestFirst` | tres estrategias con medias 0,3, 0,8 y 0,5: salen 0,8, 0,5 y 0,3, con `rate` `"80"`, `"50"` y `"30"` |
| `attacksComeAfterStrategiesBestFirst` | una estrategia y dos ataques: el orden es cabecera, estrategia y ataques de mayor a menor; `support` con un decimal |

Para las estimaciones usá `new SuccessEstimate(alpha, beta, attempts)`, con alpha y beta que den la media pedida (por ejemplo 8 y 2 para 0,8).

Total: **6 pruebas**. `MessagesTest` ya verifica que cada `MessageKey` tenga su texto. `MemoryCommand` usa Paper y se verifica en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `MemoryReport`, orden ascendente | 30, 50, 80 | `strategiesComeBestFirst` |
| 2 | En `DescribePlayerMemory.remembers`, sin mirar el peligro | lista vacía | `groupRememberedOnlyByItsDangerIsListed` |
| 3 | En `MemoryReport`, `%.2f` sin `Locale.ROOT` y con `Locale.GERMANY` | `"0,50"` | `headerShowsGroupAndDanger` |

## Verificación en el server (Nico, después del merge)

1. `/mobai memory` sin pelear: "Ningún grupo activo recuerda a papu123".
2. Peleá un rato contra un grupo y repetí `/mobai memory`: aparece el grupo con su peligro, las estrategias que probó con su tasa y los ataques.
3. Después de varios planes, la estrategia que mejor le funciona contra vos queda arriba. Si lo destrozás, el peligro sube.
4. `/mobai memory OtroNombre` con alguien desconectado: "No hay un jugador conectado…".

## Procedimiento

1. Rama `wp-26-consulta-de-memoria` desde `origin/main` actualizado.
2. `PlayerMemoryView`, `DescribePlayerMemory`, `CoreServices` y sus pruebas. Commit: `feat: the player memory view carries the danger`.
3. `MessageLine`, `MemoryReport` y su prueba. Commit: `feat: memory report lines, best first`.
4. `MemoryCommand`, `MessageKey`, `messages.yml` y `AdapterServices`. Commit: `feat: /mobai memory command`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-26: /mobai memory`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Jugadores desconectados (haría falta buscarlos por nombre fuera de línea).
- El umbral de retirada aprendido (llega con el WP-30C, que lo sumará al informe).
- Métricas en SQLite.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 6 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 3 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
