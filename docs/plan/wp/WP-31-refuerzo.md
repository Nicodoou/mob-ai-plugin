# WP-31 — `/mobai reinforce <grupo>`

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (herramienta de prueba) |
| Depende de | WP-21 (comandos) y WP-26 (mergeados) |
| Modelo | Sonnet |
| Rama | `wp-31-refuerzo` |

## Objetivo

Comando de prueba **`/mobai reinforce <grupo>`**: suma el set de prueba (el mismo que `spawngroup`: 4 zombies, 3 esqueletos y 2 arañas) a un grupo **existente**, que **conserva su memoria**. Sirve para que Nico siga peleando contra un grupo que ya lo conoce cuando le quedan 1 o 2 vivos (pedido de Nico, 8 oct).

## Decisiones tomadas en este WP

1. **Dónde aparecen:** en el mismo anillo alrededor del jugador que usa `spawngroup` (`SpawnRing`). El jugador es el que está peleando contra el grupo. Por eso el comando lo tiene que usar un jugador (desde la consola, `player-only`).
2. **Tope del grupo:** se suman `min(9, max-size − miembros)` mobs. Si el grupo ya está lleno (o pasado, por ejemplo después de bajar `max-size` con `/mobai reload`), no se crea ninguno y se avisa con `group-full`. Los que no entran no se crean: nunca se funda un grupo nuevo.
3. **Cuáles, si no entran todos:** los primeros de la lista del set de prueba, en su orden (zombies, esqueletos, arañas). Con `max-size: 12` y 2 vivos entran los 9; con 4 vivos entran 8 (sin la última araña).
4. **La composición del set pasa a una clase pura,** `TestGroup`, que usan los dos comandos. Así el cálculo del tope se prueba sin Paper, como `SpawnRing`.
5. **Un grupo sin miembros ya no existe:** cuando muere el último, `RemoveMember` lo disuelve. El refuerzo solo llega a grupos activos (los que lista `/mobai status`); si no está, `group-not-found`.
6. **Cada mob se suma con `RecruitMob.execute(RecruitRequest.near(...))`,** como los miembros 2 a 9 de `spawngroup`. Como el tope se calcula antes, el resultado tiene que ser `Joined`. Si llega otra cosa, es un error de programación: `IllegalStateException`.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/command/`: `GroupSpawner`, `SpawnGroupCommand`, `DebugCommand` (búsqueda de un grupo por id corto y sugerencias), `SpawnRing`, `Subcommand`
- `src/main/java/io/github/nicodoou/mobai/application/`: `RecruitMob`, `RecruitRequest`, `RecruitResult`, `DescribeGroup`, `GroupStatusView`, `SettingsHolder`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java`, `src/main/resources/messages.yml`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (`mobAiCommand`)
- Pruebas de referencia: `src/test/java/io/github/nicodoou/mobai/adapter/command/SpawnRingTest.java`
- `docs/actualizar-paper.md` (sección 3, fila de los comandos), para actualizarla

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/TestGroup.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/command/GroupSpawner.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/command/ReinforceCommand.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/MessageKey.java` |
| Modificar | `src/main/resources/messages.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `mobAiCommand`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/command/TestGroupTest.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

Antes de empezar, buscá con grep `TEST_ZOMBIES` y `testGroupKinds` en `src/`. Si aparecen fuera de `GroupSpawner.java`, frená y reportá.

## Especificación

### `TestGroup.java` (puro, sin Paper)

```java
package io.github.nicodoou.mobai.adapter.command;

/** The test group of the MVP catalog and how much of it fits into an existing group. */
public final class TestGroup {
  // The test group of the MVP catalog: 4 zombies, 3 skeletons and 2 spiders.
  private static final int ZOMBIES = 4;
  private static final int SKELETONS = 3;
  private static final int SPIDERS = 2;

  private TestGroup() {}

  public static List<MobKind> kinds() { … }

  public static int reinforcementSize(int members, int maxGroupSize) { … }
}
```

- **`kinds()`:** la lista inmodificable con 4 `ZOMBIE`, 3 `SKELETON` y 2 `SPIDER`, en ese orden (el mismo armado que hoy hace `GroupSpawner.testGroupKinds()`, terminado con `List.copyOf`).
- **`reinforcementSize`:**
  - `members < 0` → `IllegalArgumentException("TestGroup.members must be zero or positive, got " + members)`;
  - `maxGroupSize < 1` → `IllegalArgumentException("TestGroup.maxGroupSize must be at least 1, got " + maxGroupSize)`;
  - devuelve `Math.max(0, Math.min(kinds().size(), maxGroupSize - members))`.
  - Las dos validaciones van en un método privado `requireValid(int members, int maxGroupSize)`.

### `GroupSpawner.java`

1. Se borran las constantes `TEST_ZOMBIES`, `TEST_SKELETONS` y `TEST_SPIDERS`, su comentario y `testGroupKinds()`. `spawnTestGroup` usa `TestGroup.kinds()`. Nada más cambia en `spawnTestGroup`.
2. Javadoc de la clase: `/** Spawns the test group around a player, as a new group or as reinforcements. */`
3. Método público nuevo:

```java
public int reinforce(Player player, GroupId groupId, int count) { … }
```

- `count < 1` → `IllegalArgumentException("GroupSpawner.count must be at least 1, got " + count)`.
- `kinds = TestGroup.kinds().subList(0, count)`; posiciones con `SpawnRing.positions(<posición del jugador>, count)`, igual que en `spawnTestGroup`.
- Por cada índice: `spawn(world, kind, position)` y después `reinforceWith(mob, kind, groupId)`.
- Devuelve `count`.

4. Método privado nuevo:

```java
private void reinforceWith(Mob mob, MobKind kind, GroupId groupId) {
  RecruitResult result =
      recruitMob.execute(RecruitRequest.near(new MobId(mob.getUniqueId()), kind, groupId));
  if (!(result instanceof RecruitResult.Joined)) {
    throw new IllegalStateException(
        "Reinforcement did not join group " + groupId.shortId() + ": " + result);
  }
  installer.install(mob);
}
```

Si `spawnTestGroup` y `reinforce` repiten la lectura de la posición del jugador (`Location` a `Vec3`), sacala a un método privado `static Vec3 positionOf(Player player)` y usala en los dos.

### `ReinforceCommand.java`

El comando necesita cuatro colaboradores (`GroupSpawner`, `DescribeGroup`, `SettingsHolder` y `Messages`) y la regla es de 3 parámetros: los tres primeros van en un record anidado público.

```java
/** /mobai reinforce [group]: adds the test group to an existing group, which keeps its memory. */
public final class ReinforceCommand implements Subcommand {
  public record ReinforceParts(
      GroupSpawner spawner, DescribeGroup describeGroup, SettingsHolder settings) {
    … requireNonNull con "ReinforceParts.spawner", "ReinforceParts.describeGroup" y "ReinforceParts.settings" …
  }

  public ReinforceCommand(ReinforceParts parts, Messages messages) { … }
}
```

El constructor hace `requireNonNull` con `"ReinforceCommand.parts"` y `"ReinforceCommand.messages"`.

**`run(sender, args)`**, en este orden:
1. Si `sender` no es `Player`: `PLAYER_ONLY` y termina.
2. Si `args` está vacío: `REINFORCE_USAGE` y termina.
3. `parts.describeGroup().find(args.get(0))`. Vacío: `GROUP_NOT_FOUND` con `group` = `args.get(0)` y termina.
4. `members = view.members().size()`; `count = TestGroup.reinforcementSize(members, parts.settings().current().group().maxGroupSize())`.
5. Si `count == 0`: `GROUP_FULL` con `group` (id corto) y `members` (como texto) y termina.
6. `added = parts.spawner().reinforce(player, view.id(), count)` y `GROUP_REINFORCED` con `group` (id corto), `added` y `members` (= `members + added`), todo como texto.

Separá `run` en métodos privados para no pasar las 20 líneas (por ejemplo, `reinforce(Player player, GroupStatusView view)` para los pasos 4 a 6).

**`suggestions(args)`:** con `args.size() == 1`, los ids cortos de `parts.describeGroup().all()` que empiezan con `args.get(0)`, como en `DebugCommand`. Si no, `List.of()`.

### `MessageKey.java` y `messages.yml`

**`MessageKey`:** al final, `REINFORCE_USAGE("reinforce-usage")`, `GROUP_FULL("group-full")` y `GROUP_REINFORCED("group-reinforced")`.

**`messages.yml`**, al final (`[grupo]` va entre corchetes porque MiniMessage tomaría `<grupo>` como una etiqueta):

```yaml
reinforce-usage: "<red>Usá: <gray>/mobai reinforce [grupo]</gray>. Los grupos están en <gray>/mobai status</gray>."
group-full: "<yellow>El grupo <white><group></white> ya tiene <white><members></white> miembros: no entra nadie más."
group-reinforced: "<green>Grupo <white><group></white> reforzado con <white><added></white> mobs (<members> en total)."
```

Y `unknown-subcommand` suma `reinforce` después de `spawngroup`: `/mobai <spawngroup|reinforce|status|memory|reset|reload|debug>`.

### `AdapterServices.java` (`mobAiCommand`)

Después de `spawngroup`:

```java
subcommands.put(
    "reinforce",
    new ReinforceCommand(
        new ReinforceCommand.ReinforceParts(
            command.spawner(), core.describeGroup(), core.settings()),
        messages));
```

Si `mobAiCommand` pasa las 20 líneas, sacá el armado del `ReinforceCommand` a un método privado `reinforceCommand(CoreServices core, CommandParts command)` y avisalo.

### `docs/actualizar-paper.md` (sección 3)

En la fila de `adapter/command/SpawnGroupCommand`, `StatusCommand`, … sumá `ReinforceCommand` a la lista de clases de la primera columna. No cambia lo que usan de Paper.

### `docs/arquitectura.md` (tabla «Nombres en el código»)

Fila nueva, al final de la tabla:

| Set de prueba, refuerzo de un grupo | `TestGroup`, `ReinforceCommand`, `GroupSpawner.reinforce` | Adaptador |

## Pruebas obligatorias

### `TestGroupTest` (6)

| Prueba | Verifica |
| --- | --- |
| `testGroupIsFourZombiesThreeSkeletonsAndTwoSpiders` | `kinds()` es exactamente `[ZOMBIE ×4, SKELETON ×3, SPIDER ×2]`, en ese orden |
| `aSmallGroupGetsTheWholeTestGroup` | `reinforcementSize(2, 12)` = 9 |
| `reinforcementIsCutAtTheFreeSlots` | `reinforcementSize(4, 12)` = 8 |
| `aFullGroupGetsNothing` | `reinforcementSize(12, 12)` = 0 |
| `aGroupOverTheMaximumGetsNothing` | `reinforcementSize(15, 12)` = 0 |
| `negativeMembersAreRejected` | `reinforcementSize(-1, 12)` lanza `IllegalArgumentException` con mensaje `TestGroup.members must be zero or positive, got -1` |

Total: **6 pruebas**. `MessagesTest` ya verifica que cada `MessageKey` tenga su texto. `GroupSpawner.reinforce` y `ReinforceCommand` usan Paper y se verifican en el juego.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Sin `Math.max(0, …)` | -3 | `aGroupOverTheMaximumGetsNothing` |
| 2 | Sin el `Math.min(kinds().size(), …)` | 10 | `aSmallGroupGetsTheWholeTestGroup` |
| 3 | `maxGroupSize - members - 1` (contar mal el tope) | 7 | `reinforcementIsCutAtTheFreeSlots` |
| 4 | En `kinds()`, 3 zombies | lista distinta | `testGroupIsFourZombiesThreeSkeletonsAndTwoSpiders` |

## Verificación en el juego (Nico, después del merge)

**Antes de levantar el server:** sumá las tres claves nuevas y la lista nueva de `unknown-subcommand` al `messages.yml` del server de prueba (`run/plugins/MobAI/messages.yml`). Si falta una clave, el plugin se deshabilita (pasó el 8 oct).

1. `/mobai spawngroup`, peleá hasta dejar 2 vivos y mirá `/mobai memory`.
2. `/mobai reinforce <id>` (el id sale de `/mobai status`, y el tab lo completa): aparecen 9 mobs alrededor tuyo y el mensaje dice «reforzado con 9 mobs (11 en total)».
3. `/mobai memory` muestra la misma memoria que antes del refuerzo, y `/mobai status` el mismo id con 11 miembros.
4. Otra vez `/mobai reinforce <id>` con 11 vivos: suma 1 (un zombie).
5. Con 12: «ya tiene 12 miembros: no entra nadie más».
6. `/mobai reinforce abc` con un id que no existe: «No hay un grupo abc». Sin id: el mensaje de uso.

## Procedimiento

1. Rama `wp-31-refuerzo` desde `origin/main` actualizado.
2. `TestGroup`, su prueba y `GroupSpawner`. Commit: `feat: test group composition and reinforcement size`.
3. `ReinforceCommand`, `MessageKey`, `messages.yml` y `AdapterServices`. Commit: `feat: /mobai reinforce adds the test group to an existing group`.
4. `docs/actualizar-paper.md` y `docs/arquitectura.md`. Commit: `docs: map and names for /mobai reinforce`.
5. Pruebas que muerden, de a una y sin commit (ver «Entorno sin compilación»).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-31: /mobai reinforce`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Formateá a mano con el estilo de google-java-format: 2 espacios, 100 columnas y la misma forma de partir líneas que el código vecino.
- El CI del PR (check `build`) es la verificación: esperá a que termine. Si falla, leé el log, corregí y volvé a empujar.
- Las pruebas que muerden no se pueden correr con Gradle: en el informe, decí que quedaron sin correr, para que las corra la revisión.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Elegir el tipo de los refuerzos o su cantidad por argumento.
- Spawnear cerca del grupo en vez de cerca del jugador.
- Revivir un grupo ya disuelto (sin miembros) desde su archivo guardado.
- Que la regla de retirada (`hasRetreated`) cuente bien a los que se suman a mitad de plan: es un pendiente de limpieza aparte.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 6 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron (o quedaron listadas para la revisión si no se pudo compilar).
- [ ] Build, cobertura y CI en verde.
