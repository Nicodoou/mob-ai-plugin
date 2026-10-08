# WP-23C — Alcance del jugador según su arma

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-23B (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-23c-alcance-del-jugador` |

## Objetivo

CT-24, pedido de Nico: **el alcance del jugador depende de su arma**. Con una lanza llega más lejos que con una espada. Dos comportamientos suponían 3 bloques fijos:

- el **flanqueador** se mantiene a `flank-distance-blocks` (4) mientras el jugador lo ve (CT-16);
- el **zombie esquivo** retrocede a `evasive-distance-blocks` (3,5) del golpe cargado (CT-19).

Ahora los dos se miden como **alcance real del jugador + un margen**:

- **alcance real:** el del arma en la mano principal si tiene el componente `ATTACK_RANGE` (las lanzas), o si no el atributo `ENTITY_INTERACTION_RANGE` del jugador (3 por defecto);
- **márgenes nuevos:** `flank-margin-blocks` (1,0) y `evasive-margin-blocks` (0,5). Con una espada da lo mismo que antes, 4 y 3,5.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`, `domain/settings/AttackSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java`, `Weapons.java`, `FlankGoal.java`, `PressGoal.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`)
- Pruebas: `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java`

## Reglas de negocio

1. **Alcance del jugador** (`VersionTranslator.playerReach`): si el ítem de la mano principal tiene el componente `ATTACK_RANGE`, su `maxReach()`; si no, el valor del atributo `ENTITY_INTERACTION_RANGE`; si no existe el atributo, `MinecraftConstants.PLAYER_REACH_BLOCKS` (3). Se lee en el momento, cada vez que hace falta: el jugador puede cambiar de arma en cualquier tick.
2. **Flanqueador:** mientras el jugador lo ve, nunca más cerca que `alcance + flank-margin-blocks`.
3. **Zombie esquivo:**
   - está «en peligro» si está a `alcance + evasive-margin-blocks` o menos;
   - retrocede hasta esa distancia.
   - El umbral de carga no cambia.
4. **Configuración:**
   - `flank-distance-blocks` se reemplaza por `flank-margin-blocks: 1.0`;
   - `evasive-distance-blocks` se reemplaza por `evasive-margin-blocks: 0.5`;
   - los dos márgenes pueden ser 0, pero no negativos.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PlayerReach.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PlayerTarget.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Weapons.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FlankGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |

Antes de empezar, buscá con grep en `src/`:
- `flankDistanceBlocks`, `evasiveDistanceBlocks`, `new AttackSettings(`, `new Weapons(`, `flankStep(` y `evadePoint(`: si aparecen fuera de la tabla, frená y reportá;
- que los nombres de las pruebas nuevas no existan ya en sus archivos.

**Métodos públicos nuevos:** `VersionTranslator.playerReach` (13 en total).

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`:
- `Attribute.ENTITY_INTERACTION_RANGE`;
- `DataComponentTypes.ATTACK_RANGE` (`DataComponentType.Valued<AttackRange>`);
- `AttackRange.maxReach()` (`float`);
- `ItemStack.hasData(DataComponentType)` e `ItemStack.getData(DataComponentType.Valued)`;
- `PlayerInventory.getItemInMainHand()`.

### `MinecraftConstants.java`

```java
  // A survival player's reach with a plain weapon; spears reach farther (their own component).
  public static final double PLAYER_REACH_BLOCKS = 3.0;
```

### `AttackSettings.java`, `ConfigLoader.java`, `config.yml`

- `flankDistanceBlocks` pasa a `flankMarginBlocks` y `evasiveDistanceBlocks` a `evasiveMarginBlocks`, en las mismas posiciones.
- Las dos se validan con `SettingsChecks.requireNonNegative("AttackSettings.flankMarginBlocks", …)` y `requireNonNegative("AttackSettings.evasiveMarginBlocks", …)`.
- `ConfigLoader` lee `attack.flank-margin-blocks` y `attack.evasive-margin-blocks` en esas posiciones.
- `config.yml`:

```yaml
  # Lo que el flanqueador se mantiene más allá del alcance del jugador mientras lo ve.
  flank-margin-blocks: 1.0
```

  en lugar de `flank-distance-blocks` (y su comentario), y

```yaml
  # Golpe esquivo: carga del arma del jugador (0 a 1) desde la que el zombie se aparta, y cuánto más allá del alcance del jugador.
  evasive-charge-threshold: 0.8
  evasive-margin-blocks: 0.5
```

  en lugar de las dos líneas del WP-23B.

### Pruebas existentes que cambian

| Archivo | Cambio |
| --- | --- |
| `TestSettings` | `new AttackSettings(60, 60, 60, 8.0, 15.0, 1.0, 16.0, 0.8, 0.5)` |
| `ConfigLoaderTest` | el esperado pasa a `1.0` en la sexta posición y `0.5` en la última |
| `SettingsValidationTest` | los `new AttackSettings(…)` existentes usan `1.0` y `0.5` en esas posiciones (`boundaryValuesAreAccepted` puede usar `0.0` en las dos: es el límite). El caso de `evasiveDistanceBlocks` pasa a `AttackSettings.evasiveMarginBlocks must be zero or positive, got -1.0`, con `-1.0` |
| `WaypointsTest` | `flankStepKeepsTheConfiguredDistanceWhileSeen`: `flankStep(new PlayerTarget(pose, 3.0), mob(1), flankers)`; sigue esperando 4 bloques. `evadePointStepsOutOfReach` y `zombieAlreadyOutOfReachDoesNotMove`: `evadePoint(…, …, 3.0)`; siguen esperando 3,5 |

### `PlayerReach.java` y `PlayerTarget.java` (`adapter.goal`)

```java
/** How far the player can hit right now, with the weapon in hand. */
@FunctionalInterface
public interface PlayerReach {
  double blocksOf(Player player);
}
```

```java
/** The player as a flanker sees it: where it looks, and how far it hits. */
public record PlayerTarget(PlayerPose pose, double reachBlocks) {
  // requireNonNull(pose, "PlayerTarget.pose"); reach no positivo o no finito:
  // IllegalArgumentException "PlayerTarget.reachBlocks must be a positive number, got " + reachBlocks
}
```

### `Weapons.java`

`record Weapons(MeleeAttacker melee, BowShooter bow, PlayerReach playerReach)`, con `requireNonNull(playerReach, "Weapons.playerReach")` y el Javadoc «How our goals hurt the target, and how far the target hurts back.».

### `VersionTranslator.java`

```java
  // A spear carries its own reach; any other hand uses the player's interaction range.
  public double playerReach(Player player) {
    ItemStack held = player.getInventory().getItemInMainHand();
    if (held.hasData(DataComponentTypes.ATTACK_RANGE)) {
      AttackRange range = held.getData(DataComponentTypes.ATTACK_RANGE);
      if (range != null) {
        return range.maxReach();
      }
    }
    return attributeValue(player, Attribute.ENTITY_INTERACTION_RANGE)
        .orElse(MinecraftConstants.PLAYER_REACH_BLOCKS);
  }
```

(`attributeValue` ya existe y devuelve un opcional; si su tipo es otro, adaptalo sin cambiar el comportamiento y avisalo.)

### `Waypoints.java`

```java
  public FlankStep flankStep(PlayerTarget target, MobId self, Map<MobId, Vec3> flankers) {
    double keepOut = target.reachBlocks() + settings.get().flankMarginBlocks();
    return maneuver.next(new FlankQuery(target.pose(), self, flankers, keepOut));
  }

  // Empty once the zombie is already out of the player's reach.
  public Optional<Vec3> evadePoint(Vec3 mobPosition, Vec3 dangerPosition, double reachBlocks) {
    double missing =
        reachBlocks
            + settings.get().evasiveMarginBlocks()
            - horizontalDistance(mobPosition, dangerPosition);
    if (missing <= 0) {
      return Optional.empty();
    }
    return Optional.of(geometry.retreatPoint(mobPosition, dangerPosition, missing));
  }
```

### `FlankGoal.java` y `PressGoal.java`

- `FlankGoal.walkRoundIfDue`: `flankStep(new PlayerTarget(pose, reachOf(target)), self(), flankerPositions(target))`, con `private double reachOf(Player target) { return context.tools().weapons().playerReach().blocksOf(target); }`.
- `PressGoal`:
  - `threatOf` compara la distancia con `reachOf(target) + attack().evasiveMarginBlocks()`;
  - `backOff` llama `evadePoint(…, …, reachOf(target))`;
  - `reachOf` es la misma función privada que en `FlankGoal`.

### `AdapterServices.java`

`new Weapons(attacker, bow, parts.translator()::playerReach)`.

### `docs/actualizar-paper.md`

Fila `adapter/translate/VersionTranslator`: sumar `Attribute.ENTITY_INTERACTION_RANGE`, `DataComponentTypes.ATTACK_RANGE`, `AttackRange.maxReach`, `ItemStack.hasData`/`getData`, `PlayerInventory.getItemInMainHand` (`playerReach`). En «Qué revisar», sumar: «que las lanzas sigan trayendo su alcance en `ATTACK_RANGE` (la API de componentes es reciente) y que `ENTITY_INTERACTION_RANGE` siga siendo el alcance del jugador para pegar».

## Pruebas obligatorias

### `WaypointsTest` (+3)

Con `TestSettings` (márgenes 1,0 y 0,5).

| Prueba | Verifica |
| --- | --- |
| `longerReachKeepsFlankersFarther` | flanqueador en `(0,0,2)` a la vista, `PlayerTarget(pose, 4.5)`: el punto queda a 5,5 bloques en horizontal |
| `evadePointGrowsWithReach` | zombie `(0,64,2)`, jugador `(0,64,0)`, alcance 4.5: `(0, 64, 5)` |
| `playerTargetRejectsNonPositiveReach` | `new PlayerTarget(pose, 0)`: `IllegalArgumentException` con `PlayerTarget.reachBlocks must be a positive number, got 0.0` |

Total: **3 pruebas nuevas** y las existentes de la tabla actualizadas. `playerReach` usa Paper y se verifica en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `flankStep`, `keepOut = settings.get().flankMarginBlocks()` (sin el alcance) | queda a 1 bloque (el paso usa el pie de la perpendicular: 1,41) | `longerReachKeepsFlankersFarther` |
| 2 | En `evadePoint`, sin sumar `reachBlocks` | sin punto (2 > 0,5) | `evadePointGrowsWithReach` |

## Verificación en el server (Nico, después del merge)

Opus actualiza el `config.yml` del server de prueba con las claves nuevas al mergear.

1. Con espada: todo igual que antes (flanqueadores a 4 bloques mientras los mirás; el zombie esquivo se aparta a 3,5).
2. Con una **lanza** en la mano: los flanqueadores esquivan más lejos y el zombie esquivo se aparta más (según el alcance de la lanza).

## Procedimiento

1. Rama `wp-23c-alcance-del-jugador` desde `origin/main` actualizado.
2. `MinecraftConstants`, `AttackSettings`, `ConfigLoader`, `config.yml` y las pruebas existentes de la tabla. Commit: `refactor: flank and evasive distances become margins over the player's reach (CT-24)`.
3. `VersionTranslator.playerReach`, `PlayerReach`, `PlayerTarget`, `Weapons`, `Waypoints`, `FlankGoal`, `PressGoal`, `AdapterServices` y las pruebas nuevas. Commit: `feat: the player's reach comes from the weapon in hand`.
4. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used to read the player's reach`.
5. Pruebas que muerden (de a una; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-23C: the player's reach depends on the weapon (CT-24)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `DataComponentTypes` o `AttackRange` están marcados como experimentales y `-Xlint`/`-Werror` lo rechaza, frená y reportá el mensaje.
3. Si ArchUnit se queja de `DataComponentTypes` o `Attribute` fuera de `VersionTranslator`, frená y reportá.

## Fuera de alcance

- El alcance del jugador en el dominio (cerebro o selección de objetivo).
- El alcance mínimo de la lanza (`minReach`).
- La distancia de los esqueletos (20 a 30 está muy por encima de cualquier alcance cuerpo a cuerpo).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 3 pruebas nuevas y las existentes actualizadas, en verde; la suite completa en verde.
- [ ] Las 2 roturas mordieron.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
