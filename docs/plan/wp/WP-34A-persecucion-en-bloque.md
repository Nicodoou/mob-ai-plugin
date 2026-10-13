# WP-34A — Persecución en bloque

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-31A (mergeado) |
| Modelo | Opus |
| Rama | `wp-34a-persecucion-en-bloque` |

## Objetivo

Primera parte del CT-32, aprobado por Nico el 13 oct. Contra el patrón de Nico (se aleja, las arañas llegan solas y las mata, después los zombies), los mobs cuerpo a cuerpo **persiguen en bloque**.

- En cada decisión, el cerebro mide qué tan adelante va cada uno respecto del más atrasado.
- El que se adelantó demasiado recibe la orden nueva **`GATHER`**: se queda quieto mirando al objetivo hasta que el resto se acerca.
- El que ya está peleando no espera.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`: `planOrders`, `orderFor`, `phasedRole`, `spiderOrder` y el constructor
- `src/main/java/io/github/nicodoou/mobai/domain/brain/VolleyCycle.java`: como modelo de una pieza que el cerebro arma con `settings`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`, `domain/group/Role.java`
- `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `MobSnapshot.java`, `PlayerSnapshot.java`, `domain/geometry/PlayerPose.java`, `domain/shared/Vec3.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `VolleySettings.java`, `SettingsChecks.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/FallBackGoal.java` (modelo del goal nuevo), `GoalOrders.java`, `GoalInstaller.java`
- Pruebas: `SettingsValidationTest`, `ConfigLoaderTest`, `testsupport/TestSettings.java`, `testsupport/BrainFixture.java`, `domain/brain/BrainVolleyTest.java` (modelo de prueba del cerebro)
- Las que encuentre el grep de `new MobAiSettings(` (ver «Archivos»)

## Reglas de negocio

1. **Quiénes forman el bloque.** Las órdenes que el cerebro ya armó para el plan, con:
   - rol `PRESS` o `FLANK`, después de las fases: los de reserva de la receta y los que retroceden en la andanada ya salen como `FALL_BACK` y no cuentan;
   - objetivo igual al objetivo del plan: una araña que va contra otro jugador no cuenta.

   No cuentan las órdenes `RETREAT`, `SHOOT`, `VOLLEY`, `HOLD_FIRE` ni `FALL_BACK`.
2. **Distancia de cada uno:** la **horizontal** (sin la altura) entre su posición en la foto y la posición del objetivo (`PlayerSnapshot.pose().position()`).
3. **El más atrasado:** la distancia mayor del bloque.
4. **Espera** (`GATHER`) el mob del bloque que cumple las dos:
   - `más atrasado − su distancia > pack.max-spread-blocks`, estricto;
   - `su distancia > pack.engaged-distance-blocks`, estricto: a esa distancia o menos ya está peleando y sigue con su rol.
5. **La orden `GATHER`:**
   - mismo mob y objetivo;
   - rol `GATHER`;
   - sin ataque sugerido (`Optional.empty()`), como `FALL_BACK`: el que espera no pega;
   - `recovering` en `false` y sin punto de reunión.

   El ataque ya se sorteó al armar la orden original. Ese sorteo se mantiene, así que el orden del azar y la reproducción de los incidentes no cambian.
6. **Sin el objetivo en la foto, no cambia nada.** El bloque de un solo mob tampoco cambia (su distancia es la del más atrasado).
7. **Dónde se aplica:** en `Brain.planOrders`, sobre la lista completa. Vale al abrir el plan y en cada decisión mientras se ejecuta.
8. **El plan no guarda `GATHER`.**
   - Es solo una orden, como `FALL_BACK`.
   - El plan sigue con `PRESS` o `FLANK`, así que la retirada, la fuerza del grupo y el fin del plan no cambian.
   - En la decisión siguiente se recalcula desde cero, sin memoria.
9. **El goal `GatherGoal`** (cuerpo a cuerpo):
   - se activa y sigue activo mientras la orden del mob tiene rol `GATHER` y un objetivo válido (`GoalOrders`);
   - al activarse detiene el camino;
   - cada tick mira al objetivo;
   - al desactivarse detiene el camino;
   - tipos `MOVE` y `LOOK`.
10. **Configuración, sección nueva `pack`:**
    - `max-spread-blocks: 8.0`, positivo;
    - `engaged-distance-blocks: 4.0`, positivo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/brain/PackCohesion.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/settings/PackSettings.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GatherGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` (constructor y `planOrders`, nada más) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/Role.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalInstaller.java` |
| Modificar | los 8 archivos de prueba con `new MobAiSettings(` (`grep -rln "new MobAiSettings(" src/test`): `ConfigLoaderTest`, `RecordOutcomeTest`, `RecruitMobTest`, `SettingsHolderTest`, `SettingsValidationTest`, `RecipePlannerTest`, `LearningSimulation`, `TestSettings` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/PackCohesionTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainPackTest.java` |
| Modificar | `docs/catalogo-mvp.md` (tabla «Roles»), `docs/arquitectura.md` (tabla «Nombres en el código») y `docs/actualizar-paper.md` (fila de `GatherGoal`) |

Antes de empezar:
- buscá con grep que `PackCohesion`, `PackSettings`, `GatherGoal` y `GATHER` no existan en `src/`;
- buscá `Role.values()` y `switch` sobre `Role` en `src/` (hoy no hay ninguno). Si aparece alguno, frená y reportá.

## Especificación

### `PackSettings.java`

```java
package io.github.nicodoou.mobai.domain.settings;

/** How far ahead of the rest a melee fighter may run before it waits for them (CT-32). */
public record PackSettings(double maxSpreadBlocks, double engagedDistanceBlocks) {
  public PackSettings {
    SettingsChecks.requirePositive("PackSettings.maxSpreadBlocks", maxSpreadBlocks);
    SettingsChecks.requirePositive("PackSettings.engagedDistanceBlocks", engagedDistanceBlocks);
  }
}
```

### `MobAiSettings.java`

- Componente nuevo `PackSettings pack`, **el último** (después de `learning`).
- Su `Objects.requireNonNull(pack, "MobAiSettings.pack")`, como los demás.
- En todas las construcciones de las pruebas, `new PackSettings(8.0, 4.0)` al final.

### `Role.java`

Agregá `GATHER` **al final**, después de `VOLLEY`. El comentario que hoy dice que `FALL_BACK`, `HOLD_FIRE` y `VOLLEY` existen solo en las órdenes de una andanada se cambia por:

```java
  // Only in orders: FALL_BACK, HOLD_FIRE and VOLLEY in a volley plan (CT-23), GATHER for a fighter
  // that ran ahead of its pack (CT-32). A plan never stores them.
```

Va antes de `FALL_BACK`, en el lugar del comentario actual.

### `PackCohesion.java`

```java
package io.github.nicodoou.mobai.domain.brain;

/** Holds back the melee fighters that ran too far ahead of the rest of their pack (CT-32). */
public final class PackCohesion {
  public PackCohesion(Supplier<PackSettings> settings)

  /**
   * The same orders, in the same order, with GATHER for every PRESS or FLANK fighter of the target
   * that is more than the allowed spread ahead of the slowest one and not yet fighting.
   */
  public List<RoleAssignment> closeRanks(
      List<RoleAssignment> orders, GroupSnapshot snapshot, PlayerId target)
}
```

- `Objects.requireNonNull` en el constructor (`"PackCohesion.settings"`) y en los tres parámetros de `closeRanks` (`"PackCohesion.orders"`, `"PackCohesion.snapshot"`, `"PackCohesion.target"`).
- La lista que devuelve es inmodificable (`toList()`).
- **Una orden de un mob que no está en la foto** no puede pasar: el cerebro arma las órdenes desde la foto. Si pasa, `IllegalArgumentException` con el mensaje `"PackCohesion: order for mob <uuid> has no snapshot"`.
- Funciones privadas sugeridas (una tarea cada una, hasta 20 líneas):
  - `isInPack(order, target)`: rol `PRESS` o `FLANK` y objetivo igual;
  - `distanceOf(order, snapshot, targetPosition)`: distancia horizontal;
  - `rearDistance(...)`: el máximo;
  - `shouldGather(distance, rear, settings)`;
  - `gatherOrder(order)`.
- **No modifica estado.** La configuración se lee una vez por llamada (`settings.get()`).

### `Brain.java`

1. **Campo nuevo** `private final PackCohesion packCohesion;`. En el constructor, después de `volleyCycle`:

   ```java
   this.packCohesion = new PackCohesion(() -> settings.get().pack());
   ```

   **No** va en `BrainParts`: igual que `VolleyCycle`, no tiene estado propio que la reproducción necesite inyectar.
2. **`planOrders`** queda así, con el comentario actual:

   ```java
   // Snapshot order fixes the order in which attacks draw randomness, so incidents can be replayed.
   private List<RoleAssignment> planOrders(Turn turn) {
     List<RoleAssignment> orders =
         turn.snapshot().mobs().stream().map(mob -> orderFor(turn, mob)).toList();
     return packCohesion.closeRanks(orders, turn.snapshot(), currentPlan(turn).target());
   }
   ```

Nada más cambia en `Brain`.

### `GatherGoal.java`

Copiá la estructura de `FallBackGoal`:
- constructor `GatherGoal(Mob mob, GoalContext context)`;
- `GoalKey` `"gather"`;
- `shouldActivate`: `currentTarget().isPresent()`, y `shouldStayActive` igual;
- `start()` y `stop()`: `mob.getPathfinder().stopPathfinding()`;
- `tick()`: `currentTarget().ifPresent(mob::lookAt)`;
- tipos `MOVE` y `LOOK`.

Javadoc de la clase:

```java
/** GATHER: a fighter that ran ahead of its pack stands and watches the target until the rest catch up (CT-32). */
```

Si pasa de 100 columnas, Spotless lo parte; dejalo como lo deje.

### `GoalInstaller.java`

En la rama `isMelee()`, después de `FallBackGoal`:

```java
goals.addGoal(mob, GOAL_PRIORITY, new GatherGoal(mob, context));
```

### `config.yml`

Sección nueva al final del archivo, después de `learning`, con una línea en blanco antes:

```yaml
pack:
  # Persecución en bloque (CT-32): un mob cuerpo a cuerpo que va más de esto por delante del más
  # atrasado espera al resto (orden GATHER).
  max-spread-blocks: 8.0
  # A esta distancia del objetivo o menos ya está peleando y no espera a nadie.
  engaged-distance-blocks: 4.0
```

### `ConfigLoader.java`

En `load`, `pack(root)` después de `learning(root)`. Método nuevo:

```java
private PackSettings pack(ConfigurationSection root) {
  return new PackSettings(
      number(root, "pack.max-spread-blocks"), number(root, "pack.engaged-distance-blocks"));
}
```

### Documentos

- **`docs/catalogo-mvp.md`, tabla «Roles»,** fila nueva después de `VOLLEY`:

  | `GATHER` | Se adelantó más de 8 bloques al más atrasado del grupo: se queda quieto mirando al objetivo hasta que el resto se acerca, salvo que ya esté a 4 bloques o menos (CT-32) |
- **`docs/arquitectura.md`, tabla «Nombres en el código»,** fila nueva después de la de «Andanada»:

  | Persecución en bloque | `PackCohesion`, `PackSettings` (dominio), `GatherGoal` (adaptadores); rol `GATHER` | Dominio y adaptadores |
- **`docs/actualizar-paper.md`:** fila de `GatherGoal` con el mismo formato que la de `FallBackGoal`. Usa `Goal`, `GoalKey`, `GoalType`, `Mob.getPathfinder().stopPathfinding()` y `Mob.lookAt`. Riesgo bajo.

## Pruebas obligatorias

### `PackCohesionTest` (11)

**Escenario de todas:**
- objetivo `ALICE` en `(0, 64, 0)`;
- mobs sobre el eje Z en `(0, 64, z)`, salvo que se diga otra cosa;
- configuración `new PackSettings(8.0, 4.0)`.

Armá las órdenes a mano: `new RoleAssignment(id, role, Optional.of(target), Optional.of(attack), false, Optional.empty())`, con ataque `ZOMBIE_FRONT_STRIKE` para zombies y `SPIDER_BITE` para arañas. La foto, con `GroupSnapshotBuilder` o `MobSnapshotBuilder`/`PlayerSnapshotBuilder`.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `leaderTooFarAheadGathers` | zombie `PRESS` en z=20, araña `PRESS` en z=10 | araña `GATHER`; zombie igual que antes |
| `withinTheSpreadEveryonePresses` | zombie en 17, araña en 10 | las dos órdenes sin cambios |
| `exactlyTheSpreadDoesNotGather` | zombie en 18, araña en 10 (8 justo) | sin cambios |
| `engagedFighterKeepsFighting` | zombie en 20, araña en 3 | sin cambios |
| `engagedAtTheEdgeKeepsFighting` | zombie en 20, araña en 4 (4 justo) | sin cambios |
| `flankersCountInThePack` | zombie `FLANK` en 20, araña `PRESS` en 10, zombie `FLANK` en 11 | araña y zombie de 11 `GATHER`; el de 20 sin cambios |
| `reserveRetreatAndShootersDoNotHoldThePackBack` | zombie `FALL_BACK` en 30, zombie `RETREAT` en 30, esqueleto `SHOOT` en 30, zombie `PRESS` en 10, araña `PRESS` en 12 | las cinco sin cambios |
| `spiderOnAnotherTargetIsIgnored` | araña `PRESS` contra `BOB` en z=30 (Bob en `(0, 64, 40)`), zombie `PRESS` en 10, zombie `PRESS` en 12 | las tres sin cambios |
| `heightDoesNotCount` | araña `PRESS` en `(0, 84, 10)`, zombie `PRESS` en `(0, 64, 20)` | araña `GATHER` |
| `gatherDropsTheAttackAndKeepsTheRest` | el de `leaderTooFarAheadGathers` | la orden de la araña es igual a `new RoleAssignment(spider, GATHER, Optional.of(ALICE), Optional.empty(), false, Optional.empty())` y las órdenes salen en el mismo orden en que entraron |
| `targetOutOfTheSnapshotChangesNothing` | el de `leaderTooFarAheadGathers`, con la foto sin jugadores | sin cambios |

**Cuentas.**
- `heightDoesNotCount`:
  - en horizontal, la araña está a 10 y el zombie a 20, así que espera;
  - con distancia 3D, la araña quedaría a √(10² + 20²) ≈ 22,4 y sería la más atrasada; ninguno esperaría.
- `flankersCountInThePack`:
  - el más atrasado está en 20;
  - la araña va 10 adelante y el zombie de 11 va 9; los dos pasan de 8 y están a más de 4.

### `BrainPackTest` (2)

Con `BrainFixture.choosingStrategy(0)` (asalto directo, `DIRECT_ASSAULT_INDEX` en `BrainExecutingTest`) y `fixture.catalogGroup()`:
- 4 zombies en los índices 0 a 3, 3 esqueletos en los índices 4 a 6 y 2 arañas en los índices 7 y 8;
- Alice en `(0, 64, 0)`;
- los zombies se mueven a `(0, 64, 20)` y las arañas a `(0, 64, 10)` con `BrainFixture.withPosition` (cambia un índice por llamada; encadenalo o usá un helper privado de la prueba);
- los esqueletos quedan donde los pone el armador.

| Prueba | Verifica |
| --- | --- |
| `spidersAheadOfTheZombiesWaitForThem` | primera decisión (abre el plan): las 2 arañas con `GATHER`, objetivo Alice y sin ataque; los 4 zombies con `PRESS`; los 3 esqueletos con `SHOOT` |
| `gatheredSpidersRejoinOnceThePackCatchesUp` | después de esa decisión, otra en `START_TICK + 10` con las arañas en `(0, 64, 14)`: las 2 arañas vuelven a `PRESS` con ataque sugerido |

### Otras

- **`SettingsValidationTest`:** dos casos nuevos en `outOfRangeValuesProvider`, con el mensaje exacto de `requirePositive` (copialo de un caso existente de `requirePositive`):
  - `new PackSettings(0.0, 4.0)` → `PackSettings.maxSpreadBlocks`;
  - `new PackSettings(8.0, 0.0)` → `PackSettings.engagedDistanceBlocks`.
- **`ConfigLoaderTest.bundledConfigLoadsTheCatalogValues`:** si compara contra `TestSettings.defaults()`, alcanza con sumar `PackSettings` en `TestSettings`. Si compara sección por sección, sumá `pack`.

**Si cambia por esta regla alguna prueba existente del cerebro, de las invariantes (`BrainInvariantsTest`), de la simulación (`LearningSimulation`, `OutcomeModel`) o de los incidentes, frená y reportá cuál y cómo.** No ajustes valores esperados.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | `>=` en lugar de `>` en la regla de la separación | `exactlyTheSpreadDoesNotGather` |
| 2 | `<` en lugar de `<=` en la regla de «ya está peleando» (o sea, esperar con `distancia >= engaged`) | `engagedAtTheEdgeKeepsFighting` |
| 3 | Distancia 3D (`minus(...).length()` sin `horizontal()`) | `heightDoesNotCount` |
| 4 | En `Brain.planOrders`, devolver `orders` sin `closeRanks` | `spidersAheadOfTheZombiesWaitForThem` |

## Verificación en el server (Nico, después del merge)

Opus suma la sección `pack` al `config.yml` de prueba y a `docs/plan/config-de-prueba.yml`.

- Spawneá un grupo con arañas y zombies y alejate corriendo: las arañas frenan antes de llegarte y esperan a los zombies.
- Si te acercás a las que esperan, pelean.

## Procedimiento

1. Rama `wp-34a-persecucion-en-bloque` desde `origin/main` actualizado.
2. `PackSettings`, `MobAiSettings`, `ConfigLoader`, `config.yml` y las 8 pruebas con `new MobAiSettings(`. Build en verde. Commit: `feat: pack settings (CT-32)`.
3. `Role.GATHER`, `PackCohesion`, `PackCohesionTest`, `Brain` y `BrainPackTest`. Commit: `feat: melee fighters wait for their pack instead of arriving one by one (CT-32)`.
4. `GatherGoal` y `GoalInstaller`. Commit: `feat: gather goal (CT-32)`.
5. Documentos. Commit: `docs: pack cohesion in the catalog, the names table and the Paper map`.
6. Pruebas que muerden (de a una; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`: Nico puede tener su server corriendo con un daemon de Gradle.
8. Push, PR `WP-34A: pack cohesion (CT-32)`, CI en verde e informe con:
   - la prueba exacta que falló en cada rotura;
   - cada archivo de prueba que tocaste por `MobAiSettings` y cómo;
   - cualquier prueba existente que haya cambiado (no debería haber ninguna).

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si `ConfigLoaderTest` necesita otra forma de comparar la sección `pack` que la de las demás secciones, seguí el patrón de las demás y avisalo.

## Fuera de alcance

- Zombies que rodean (WP-34B).
- La perilla aprendida `chaseCommitment` (WP-34C).
- Guardar desde cuándo espera cada mob.
- Mostrar `GATHER` en `/mobai memory` o en mensajes.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 13 pruebas nuevas y los 2 casos de validación, en verde; la suite completa en verde, sin cambios en pruebas existentes salvo los constructores de `MobAiSettings`.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
