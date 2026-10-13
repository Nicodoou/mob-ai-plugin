# WP-34B — Zombies que rodean

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-34A (mergeado, #73) |
| Modelo | Sonnet |
| Rama | `wp-34b-zombies-que-rodean` |

## Objetivo

Segunda parte del CT-32, aprobado por Nico el 13 oct. Hoy los zombies que presionan van todos derecho al jugador y llegan amontonados: un barrido de espada les pega a todos.

Con este WP, cada zombie que presiona toma un **lugar en un arco de 180° delante del grupo**, a un paso de golpe del jugador. Los lugares están **separados lo justo para que el barrido no alcance a dos**. Si no entran todos, el resto **espera su turno** fuera del alcance del jugador.

Las arañas no entran al arco (CT-32): siguen yendo derecho.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/geometry/ShooterFormation.java` y `ShooterQuery.java`: el modelo de la formación nueva (rumbos, desempates por id)
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`, `Vec3.java`, `MobId.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` (completo), `FlankGoal.java` (cómo junta las posiciones de los aliados), `Bodies.java`, `MobSpeed.java`, `GoalTiming.java`, `GoalContext.java`, `RoleRegistry.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`: solo `goalTools`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/PackSettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- Pruebas: `ShooterFormationTest`, `SettingsValidationTest`, `ConfigLoaderTest`, y las que encuentre el grep de `new PackSettings(` (ver «Archivos»)

## Reglas de negocio

1. **Separación contra el barrido.**
   - El barrido de la espada golpea a todo lo que toque la caja del mob golpeado agrandada `SWEEP_REACH_BLOCKS` (1) hacia los costados.
   - Dos mobs de ancho `w` quedan a salvo en cualquier orientación si sus centros están a `√2 × (w + SWEEP_REACH_BLOCKS)` o más.
   - Se suma `pack.surround-margin-blocks` (0,25). Con zombies (ancho 0,6), la separación da 2,5127416997969525 bloques.
2. **Radio de golpe:** `MELEE_REACH_BLOCKS − 0,2` = 1,8 bloques del jugador.
3. **Lugares en el arco.**
   - Ángulo entre lugares vecinos: `θ = 2 × asin(separación ÷ (2 × radio))`, en grados.
   - Lugares: `piso(180 ÷ θ) + 1`. Si la separación pasa de `2 × radio`, hay **1** lugar.
   - Con zombies y radio 1,8, θ = 88,53° y entran **3**.
4. **Quiénes golpean y quiénes esperan.**
   - Los zombies con orden `PRESS` contra el mismo jugador, incluido el que pregunta, se ordenan por distancia horizontal al jugador, de menor a mayor, y empatan por id.
   - Los primeros, tantos como lugares haya, **golpean**. Los demás **esperan**.
5. **Centro del arco.**
   - Es el rumbo, desde el jugador, del promedio de las posiciones de **todos** los que presionan, golpeen o esperen: el lado del que viene el grupo.
   - Rumbo como en `ShooterFormation`: 0° hacia +Z y 90° hacia +X. Un punto justo sobre el jugador cuenta como 0°.
6. **Lugar de los que golpean.**
   - Se ordenan por el giro firmado desde el centro del arco hasta su propio rumbo, en [−180°, 180°), de menor a mayor, y empatan por id. Así ninguno cruza por delante de otro.
   - Con `k` que golpean, el de índice `i` (desde 0) va al rumbo `centro + (i − (k − 1) ÷ 2) × θ`, a la distancia del radio de golpe.
   - La altura es la del jugador.
7. **Lugar de los que esperan:** sobre su propio rumbo desde el jugador, a `alcance del jugador según el arma + pack.wait-margin-blocks` (1). Así entran cuando se libera un lugar: el que se acerca más pasa a golpear.
8. **Movimiento en `PressGoal`, solo zombies.** Donde hoy el zombie camina hacia el jugador (`followIfDue` y la carga al salir de un esquive en `chaseOn`):
   - si **golpea** y ya está al alcance (distancia ≤ `MELEE_REACH_BLOCKS`), se queda quieto (`stopPathfinding`): golpea desde su lugar;
   - si **golpea**, no está al alcance y ya está a 1 bloque o menos (horizontal) de su lugar, camina hacia el jugador. El camino va por bloques y puede dejarlo corto;
   - si no, camina a su lugar.

   Las arañas siguen caminando hacia el jugador como hoy. El esquive (`sideStep`, `backOff`, `HOLD`) no cambia.
9. **Configuración, en la sección `pack`:**
   - `surround-margin-blocks: 0.25`, cero o positivo;
   - `wait-margin-blocks: 1.0`, cero o positivo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/SurroundFormation.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/SurroundQuery.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/SurroundRing.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/SurroundSpot.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/SurroundApproach.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MobKinds.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` (`SWEEP_REACH_BLOCKS`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/PackSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java`, `Bodies.java`, `GoalTiming.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalTools`) |
| Modificar | los archivos de prueba con `new PackSettings(` (`grep -rln "new PackSettings(" src/test`): hoy son `ConfigLoaderTest`, `RecordOutcomeTest`, `RecruitMobTest`, `SettingsHolderTest`, `SettingsValidationTest`, `RecipePlannerTest`, `LearningSimulation`, `TestSettings` y `PackCohesionTest` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/geometry/SurroundFormationTest.java` |
| Modificar | `docs/catalogo-mvp.md` (tabla «Roles», fila `PRESS`), `docs/arquitectura.md` (tabla «Nombres en el código») y `docs/actualizar-paper.md` (fila de `SurroundApproach`) |

Antes de empezar:
- buscá con grep que `SurroundFormation`, `SurroundQuery`, `SurroundRing`, `SurroundSpot`, `SurroundApproach`, `MobKinds` y `SWEEP_REACH_BLOCKS` no existan en `src/`;
- buscá `new Bodies(` y `new GoalTiming(` en `src/`: hoy solo están en `AdapterServices`. Si aparecen en otro lado, sumalos y avisá.

## Especificación

### `MinecraftConstants.java`

Junto a `MELEE_REACH_BLOCKS`:

```java
  // A sword's sweep hits whatever touches the struck mob's hitbox grown by this much sideways.
  public static final double SWEEP_REACH_BLOCKS = 1.0;
```

### `PackSettings.java`

Quedan cuatro componentes, en este orden:

```java
public record PackSettings(
    double maxSpreadBlocks,
    double engagedDistanceBlocks,
    double surroundMarginBlocks,
    double waitMarginBlocks)
```

- Validación de los nuevos con `SettingsChecks.requireNonNegative("PackSettings.surroundMarginBlocks", …)` y `("PackSettings.waitMarginBlocks", …)`.
- El javadoc de la clase pasa a: `/** How melee fighters keep together while they chase and how zombies surround the target (CT-32). */`.
- En todas las construcciones existentes, `new PackSettings(8.0, 4.0)` pasa a `new PackSettings(8.0, 4.0, 0.25, 1.0)`. En los casos de validación del WP-34A, `new PackSettings(0.0, 4.0)` pasa a `new PackSettings(0.0, 4.0, 0.25, 1.0)` y `new PackSettings(8.0, 0.0)` pasa a `new PackSettings(8.0, 0.0, 0.25, 1.0)`.

### `SurroundRing.java`

```java
package io.github.nicodoou.mobai.domain.geometry;

/**
 * The shape of the surround: how far from the target the strikers stand, how far apart, and how
 * far out the rest wait for their turn.
 */
public record SurroundRing(double strikeRadiusBlocks, double spacingBlocks, double waitRadiusBlocks)
```

Los tres tienen que ser positivos y finitos, con el mismo chequeo y mensaje que `ShooterQuery.radiusBlocks`: `"SurroundRing.<campo> must be a positive number, got <valor>"`.

### `SurroundQuery.java`

```java
/** One pressing zombie asking for its place, given every zombie pressing the same target. */
public record SurroundQuery(Vec3 center, MobId self, Map<MobId, Vec3> pressers, SurroundRing ring)
```

- Igual que `ShooterQuery`: `requireNonNull` de cada campo y `Map.copyOf`.
- Si `self` no está en `pressers`: `"SurroundQuery.self must be one of the pressers, got <uuid>"`.

### `SurroundSpot.java`

```java
/** Where a pressing zombie goes, and whether it strikes from there or waits its turn. */
public record SurroundSpot(Vec3 point, boolean striking)
```

Con `requireNonNull` de `point`.

### `SurroundFormation.java`

```java
package io.github.nicodoou.mobai.domain.geometry;

/**
 * Spreads the zombies pressing one target over an arc in front of their pack, far enough apart that
 * a sword's sweep reaches only one; the ones that do not fit wait out of reach for their turn.
 */
public final class SurroundFormation {
  public double sweepSafeSpacingBlocks(double mobWidthBlocks, double marginBlocks)

  public int strikeSlots(SurroundRing ring)

  public SurroundSpot spotFor(SurroundQuery query)
}
```

- **`sweepSafeSpacingBlocks`:**
  - `Math.sqrt(2) * (mobWidthBlocks + MinecraftConstants.SWEEP_REACH_BLOCKS) + marginBlocks`;
  - `mobWidthBlocks` tiene que ser positivo: si no, `"SurroundFormation.mobWidthBlocks must be a positive number, got <valor>"`;
  - `marginBlocks`, cero o positivo: si no, `"SurroundFormation.marginBlocks must be zero or positive, got <valor>"`.

  Un comentario explica el porqué: dos cajas se tocan si sus centros están a menos de `w + 1` en los dos ejes a la vez, y en diagonal eso es `√2 × (w + 1)`.
- **`strikeSlots`:**
  - si `spacing > 2 × strikeRadius`, devuelve 1;
  - si no, `(int) Math.floor(HALF_TURN_DEGREES / slotAngleDegrees(ring)) + 1`, con `HALF_TURN_DEGREES = 180.0` y `slotAngleDegrees = Math.toDegrees(2 * Math.asin(spacing / (2 * strikeRadius)))`.
- **`spotFor`:** reglas 4 a 7, en funciones privadas de una tarea cada una. Por ejemplo:
  - `strikers(query)`: los primeros por distancia y luego por id;
  - `arcCenterBearing(query)`;
  - `strikeSpot(...)`;
  - `waitSpot(...)`;
  - `bearingOf`, `signedTurn` y `directionOf`.

  Copiá `bearingOf` y `directionOf` de `ShooterFormation` (son privadas allá; no las hagas públicas ni toques `ShooterFormation`). `signedTurn(from, to)` devuelve el giro en [−180°, 180°).
- Todo horizontal: las distancias y rumbos ignoran la altura. Los puntos salen con la altura de `center`.
- Sin estado.

### `MobKinds.java`

```java
package io.github.nicodoou.mobai.adapter.goal;

/** Which of our mob kinds a mob is, read through the version translator. */
@FunctionalInterface
public interface MobKinds {
  Optional<MobKind> of(Mob mob);
}
```

### `Bodies.java` y `GoalTiming.java`

- **`Bodies`:** tercer componente `MobKinds mobKinds`, con su `requireNonNull`. Javadoc: `/** What the goals read of bodies on the field: how far the player hits, how fast a mob walks and what kind it is. */`.
- **`GoalTiming`:** cuarto componente `Supplier<PackSettings> pack`, con su `requireNonNull`. Javadoc: `/** The plugin's clock and the attack, volley and pack settings, for the goals' rhythms and waits. */`.
- **`AdapterServices.goalTools`:**
  - `new Bodies(parts.translator()::playerReach, parts.translator()::movementSpeed, mob -> parts.translator().mobKindOf(mob.getType()))`;
  - en `GoalTiming`, `core.settings().section(MobAiSettings::pack)` al final.

### `SurroundApproach.java`

```java
package io.github.nicodoou.mobai.adapter.goal;

/** Where a pressing zombie walks: its slot in the arc in front of its pack, or its turn (CT-32). */
final class SurroundApproach {
  // A fifth of a block inside the zombie's reach, so it strikes from its slot without stepping in.
  private static final double STRIKE_SLOT_SLACK_BLOCKS = 0.2;

  private final GoalContext context;
  private final SurroundFormation formation = new SurroundFormation();

  SurroundApproach(GoalContext context)

  SurroundSpot spotFor(Mob zombie, Player target)
}
```

- **Los que presionan:** recorré `context.roles().mobsWith(Role.PRESS, new PlayerId(target.getUniqueId()))` como `FlankGoal.flankerPositions`: `Bukkit.getEntity(id.value()) instanceof Mob presser`, `isValid()` y el mismo mundo que el jugador. Quedate solo con los que `context.tools().weapons().bodies().mobKinds().of(presser)` da `Optional.of(MobKind.ZOMBIE)`. Sumá siempre al propio `zombie`.
- **Ancho:** el mayor `getWidth()` entre esos zombies.
- **Anillo:**

  ```java
  new SurroundRing(
      MinecraftConstants.MELEE_REACH_BLOCKS - STRIKE_SLOT_SLACK_BLOCKS,
      formation.sweepSafeSpacingBlocks(widest, pack.surroundMarginBlocks()),
      reach + pack.waitMarginBlocks())
  ```

  donde `reach = context.tools().weapons().bodies().playerReach().blocksOf(target)` y `pack = context.tools().timing().pack().get()`.
- **Centro:** `PoseReader.positionOf(target.getLocation())`. Las posiciones de los zombies, con `PoseReader.positionOf(…getLocation())`.

### `PressGoal.java`

1. **Campo** `private final SurroundApproach surround;`, creado en el constructor con `new SurroundApproach(context)`.
2. **Constante:**

   ```java
   // Paths run over whole blocks, so a zombie can stop a little short of its slot: once that close,
   // it steps in to the player instead.
   private static final double SLOT_ARRIVAL_BLOCKS = 1.0;
   ```
3. **Función nueva `approach(Player target)`** (regla 8):

   ```java
   private void approach(Player target) {
     if (kind != MobKind.ZOMBIE) {
       mob.getPathfinder().moveTo(target, WALK_SPEED);
       return;
     }
     SurroundSpot spot = surround.spotFor(mob, target);
     if (spot.striking() && isInReach(target)) {
       mob.getPathfinder().stopPathfinding();
       return;
     }
     if (spot.striking() && isNear(spot.point())) {
       mob.getPathfinder().moveTo(target, WALK_SPEED);
       return;
     }
     walkTo(spot.point());
   }
   ```

   Con estas funciones privadas:
   - `isInReach(target)`: `mob.getLocation().distance(target.getLocation()) <= MinecraftConstants.MELEE_REACH_BLOCKS`;
   - `isNear(point)`: distancia horizontal entre `PoseReader.positionOf(mob.getLocation())` y `point` ≤ `SLOT_ARRIVAL_BLOCKS`;
   - `walkTo(point)`: `moveTo(new Location(mob.getWorld(), x, y, z), WALK_SPEED)`.

   `sideStep` y `backOff` arman hoy el mismo `Location`. Si querés que usen `walkTo`, está permitido (corrección 3).
4. **`followIfDue`:** `mob.getPathfinder().moveTo(target, WALK_SPEED);` pasa a `approach(target);`.
5. **`chaseOn`:** dentro del `if` de la salida del esquive, `mob.getPathfinder().moveTo(target, WALK_SPEED);` pasa a `approach(target);`.

   El comentario de `chaseOn` sigue valiendo: entra en el acto, sin esperar el repath.

Nada más cambia en `PressGoal`.

### `config.yml` y `ConfigLoader.java`

Al final de la sección `pack`:

```yaml
  # Zombies que rodean (CT-32): margen sobre la separación que evita que el barrido de la espada
  # alcance a dos.
  surround-margin-blocks: 0.25
  # Los zombies que esperan turno se quedan esto más allá del alcance del jugador.
  wait-margin-blocks: 1.0
```

En `ConfigLoader.pack`, `number(root, "pack.surround-margin-blocks")` y `number(root, "pack.wait-margin-blocks")`, en ese orden.

### Documentos

- **`docs/catalogo-mvp.md`, tabla «Roles», fila `PRESS`:** al final de la descripción, agregá «. Los zombies toman cada uno un lugar en un arco de 180° delante del grupo, separados para que el barrido no alcance a dos; los que no entran esperan su turno fuera del alcance del jugador (CT-32)».
- **`docs/arquitectura.md`, tabla «Nombres en el código»,** fila nueva después de «Persecución en bloque»:

  | Zombies que rodean | `SurroundFormation`, `SurroundQuery`, `SurroundRing`, `SurroundSpot` (dominio), `SurroundApproach`, `MobKinds` (adaptadores) | Dominio y adaptadores |
- **`docs/actualizar-paper.md`:** fila `adapter/goal/SurroundApproach`, después de la de `adapter/goal/GatherGoal`:
  - APIs: `Bukkit.getEntity`, `Entity.isValid`, `getWorld`, `getWidth`, `getType` (vía `MobKinds`) y `Player.getLocation`;
  - riesgo Bajo;
  - qué revisar: «que el barrido de la espada siga golpeando lo que toca la caja del golpeado agrandada 1 bloque (`SWEEP_REACH_BLOCKS`)».

## Pruebas obligatorias

### `SurroundFormationTest` (12)

**Escenario común:**
- `center = (0, 64, 0)`;
- ids `new MobId(new UUID(0, n))`: A = 1, B = 2, C = 3, D = 4;
- `ring = new SurroundRing(1.8, 2.5127416997969525, 4.0)`.

Tolerancia `within(1e-9)`. Compará `x`, `y` y `z` del punto por separado.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `zombieSpacingClearsTheSweep` | `sweepSafeSpacingBlocks(0.6, 0.25)` | 2,5127416997969525 |
| `widerMobsNeedMoreRoom` | `sweepSafeSpacingBlocks(1.4, 0.25)` | 3,644112549695428 |
| `threeZombiesFitInFrontAtStrikeRange` | `strikeSlots(ring)` | 3 |
| `tooWideForTwoLeavesOneSlot` | `strikeSlots(new SurroundRing(1.8, 3.65, 4.0))` | 1 |
| `aLonePresserStrikesStraightOn` | A en (0, 64, 10) | (0, 64, 1,8), golpea |
| `arcFollowsThePack` | A en (10, 64, 0) | (1,8, 64, 0), golpea |
| `threePressersSpreadAcrossTheArc` | A en (−1, 64, 10), B en (0, 64, 10), C en (1, 64, 10) | A (−1,7994083626765378, 64, 0,046146986139311647); B (0, 64, 1,8); C (1,7994083626765378, 64, 0,046146986139311647); los tres golpean; la distancia entre los puntos de A y B es 2,5127416997969525 |
| `twoPressersSitHalfASlotEachSide` | A en (−1, 64, 10), B en (1, 64, 10) | A (−1,2563708498984762, 64, 1,2890043783965128); B (1,2563708498984762, 64, 1,2890043783965128) |
| `theFourthWaitsOutsideTheReach` | los tres de `threePressersSpreadAcrossTheArc` y D en (0, 64, −12) | D: (0, 64, −4), no golpea; B sigue en (0, 64, 1,8) |
| `theClosestOnesGetTheSlots` | A en (−1, 64, 10), B en (0, 64, 10), C en (1, 64, 10), D en (0, 64, 5) | C: (0,39801487608399566, 64, 3,9801487608399566), no golpea; D golpea |
| `queryWithoutSelfIsRejected` | `new SurroundQuery(center, D, {A en (0, 64, 10)}, ring)` | `IllegalArgumentException` con `"SurroundQuery.self must be one of the pressers, got 00000000-0000-0000-0000-000000000004"` |
| `ringNeedsPositiveSizes` | `new SurroundRing(0.0, 2.5, 4.0)` | `IllegalArgumentException` con `"SurroundRing.strikeRadiusBlocks must be a positive number, got 0.0"` |

**Cuentas.**
- **θ** = 2 × asin(2,5127 ÷ 3,6) = 88,5309°.
- **`threePressersSpreadAcrossTheArc`:**
  - el promedio está en (0, 64, 10), así que el centro del arco es 0°;
  - A está a −5,71°, B a 0° y C a +5,71°, así que reciben los rumbos −88,53°, 0° y +88,53°;
  - a 1,8 bloques: x = 1,8 × sen(88,53°) = 1,79941 y z = 1,8 × cos(88,53°) = 0,04615.
- **`twoPressersSitHalfASlotEachSide`:** rumbos ∓44,27°.
- **`theFourthWaitsOutsideTheReach`:**
  - el promedio está en z = (10 + 10 + 10 − 12) ÷ 4 = 4,5, así que el centro sigue en 0°;
  - D es el más lejano (12 contra 10 y 10,05);
  - su rumbo es 180°, así que va a (0, 64, −4).
- **`theClosestOnesGetTheSlots`:**
  - distancias: D = 5, B = 10, A y C = 10,0499;
  - entre A y C empata la distancia y gana el id menor (A);
  - C espera sobre su rumbo de atan2(1, 10) = 5,71°, a 4 bloques.

### Otras

- **`SettingsValidationTest`:** dos casos nuevos, con el mensaje de `requireNonNegative`:
  - `new PackSettings(8.0, 4.0, -0.1, 1.0)` → `"PackSettings.surroundMarginBlocks must be zero or positive, got -0.1"`;
  - `new PackSettings(8.0, 4.0, 0.25, -0.1)` → `"PackSettings.waitMarginBlocks must be zero or positive, got -0.1"`.
- **`ConfigLoaderTest`:** con `TestSettings` actualizado alcanza (compara el `MobAiSettings` completo).

Si cambia alguna prueba existente que no sea por el constructor de `PackSettings`, **frená y reportá**.

`PressGoal` y `SurroundApproach` no llevan prueba unitaria: dependen de Paper, como `FlankGoal`. Las verifica Nico en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `strikeSlots`, 360 en lugar de 180 | `piso(360 ÷ 88,53) + 1` = 5 | `threeZombiesFitInFrontAtStrikeRange` |
| 2 | En `sweepSafeSpacingBlocks`, sin `√2` | 1,85 | `zombieSpacingClearsTheSweep` |
| 3 | Elegir a los que golpean por id en lugar de distancia | golpean A, B y C, y C no espera | `theClosestOnesGetTheSlots` |
| 4 | Rumbo del lugar `centro + i × θ`, sin centrar | A va a (0, 64, 1,8) | `twoPressersSitHalfASlotEachSide` |

## Verificación en el server (Nico, después del merge)

Opus suma `surround-margin-blocks` y `wait-margin-blocks` a la sección `pack` del `config.yml` de prueba y de `docs/plan/config-de-prueba.yml`.

- Spawneá un grupo con 4 o más zombies y quedate quieto:
  - tienen que llegarte de frente y de los costados, no amontonados;
  - pegan a la vez como mucho 3, y el resto espera un poco más lejos.
- Con la espada cargada, un barrido debería pegarle a uno solo, o a lo sumo a dos si el camino por bloques los dejó cerca.
- Si matás a uno, entra otro de los que esperan.
- **Ojo:** el camino va por bloques, así que los lugares son aproximados (más o menos medio bloque). Si los zombies quedan lejos sin pegar o tiemblan en el lugar, avisá: es el riesgo de este WP.

## Procedimiento

1. Rama `wp-34b-zombies-que-rodean` desde `origin/main` actualizado.
2. `SWEEP_REACH_BLOCKS`, `SurroundRing`, `SurroundQuery`, `SurroundSpot`, `SurroundFormation` y `SurroundFormationTest`. Commit: `feat: surround formation against the sword's sweep (CT-32)`.
3. `PackSettings` (dos claves), `ConfigLoader`, `config.yml`, las pruebas con `new PackSettings(` y los 2 casos de validación. Build en verde. Commit: `feat: surround settings (CT-32)`.
4. `MobKinds`, `Bodies`, `GoalTiming`, `AdapterServices`, `SurroundApproach` y `PressGoal`. Commit: `feat: pressing zombies take their slot around the player (CT-32)`.
5. Documentos. Commit: `docs: surround in the catalog, the names table and the Paper map`.
6. Pruebas que muerden (de a una; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`: Nico puede tener su server corriendo con un daemon de Gradle.
8. Push, PR `WP-34B: zombies surround the player (CT-32)`, CI en verde e informe con la prueba exacta que falló en cada rotura y cada archivo de prueba que tocaste por `PackSettings`.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o usá un record privado y avisalo.
3. Que `sideStep` y `backOff` usen `walkTo`.
4. Si la cobertura de `domain.geometry` baja del umbral por una rama defensiva, sumá la prueba que la cubra y avisalo.

## Fuera de alcance

- Arañas en el arco.
- Que los flanqueadores (`FLANK`) compartan lugares con los que presionan.
- La perilla `chaseCommitment` (WP-34C).
- Cambiar `ShooterFormation` o `MeleeRhythm`.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 12 pruebas nuevas y los 2 casos de validación, en verde; la suite completa en verde, sin cambios en pruebas existentes salvo los constructores de `PackSettings`.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
