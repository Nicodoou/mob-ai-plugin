# WP-34C — Retirada del grupo y correa en el juego

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-31B y WP-31C (mergeados) |
| Modelo | Opus |
| Rama | `wp-34c-retirada-y-correa` |

## Objetivo

Última parte del CT-33: las dos perillas que el modelo ya aprende (`groupRetreatStrength` y `chaseCommitment`) empiezan a cambiar el juego.

- **Retirada del grupo:** un plan por receta se retira con su propio umbral, y el grupo vuelve a planificar según el umbral de su último plan.
- **Correa:** si el objetivo se aleja demasiado del terreno donde empezó la pelea, los cuerpo a cuerpo vuelven al terreno y esperan, salvo los que ya pelean. Los esqueletos siguen tirando.

Los planes por estrategia (`planner: STRATEGIES`) no cambian.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `PackCohesion.java`, `PlanEndDetector.java`, `RetreatRule.java`, `RallyPointRule.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Plan.java`, `PlanStart.java`, `PlanAftermath.java`, `PlanLifecycle.java`, `LifecycleCapture.java`, `Role.java`
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlay.java`, `RecipePlanner.java`, `RecipeRequest.java`, `PlanRecipe.java`
- `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java`, `domain/shared/Vec3.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/PackSettings.java`, `SettingsChecks.java`, `NamedSetting.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/GatherGoal.java`, `FallBackGoal.java` (modelo del ritmo con el reloj), `RallyRoute.java`, `GoalOrders.java`, `GoalTools.java`
- Pruebas: `domain/brain/BrainRecipesTest.java`, `PlanEndDetectorTest.java`, `RetreatRuleTest.java`, `PackCohesionTest.java`, `domain/group/GroupLifecycleTest.java`, `LifecycleCaptureTest.java`, `domain/strategy/RecipePlannerTest.java`, `domain/settings/SettingsValidationTest.java`, `testsupport/BrainFixture.java`, `TestSettings.java`
- Los que encuentren los grep de `new PackSettings(` y `new RecipePlay(` (ver «Archivos»)

## Reglas de negocio

### Retirada del grupo

1. **Durante un plan por receta,** `GROUP_RETREATED` compara `GroupStrength.ofPlan(plan)` contra `recipe.groupRetreatStrength()`, con `<` estricto como hoy. Sin receta, contra `plan.group-retreat-strength`.
2. **Al cerrar un plan,** su umbral queda en `PlanAftermath`: el de la receta, o vacío si el plan no tenía receta.
3. **Fuera de un plan,** `Brain.isGroupRetreated` usa el umbral recordado; si está vacío, el de la configuración. Vale para no abrir un plan (`observe`) y para seguir reagrupándose (`regroup`).
4. **Al abrir un plan,** el umbral recordado se olvida, igual que la vida perdida.
5. **Después de un reinicio** no hay umbral recordado (no se guarda en disco) y vale el de la configuración. Sí viaja en `LifecycleCapture`, para que los incidentes se reproduzcan.

### Correa

6. **El ancla.** Al abrir un plan por receta, el planificador guarda en `RecipePlay` un `ChaseAnchor` con:
   - `ground`: el centro de todos los mobs de la foto (el terreno), con la misma cuenta que `RallyPointRule`;
   - `openingDistanceBlocks`: la distancia horizontal entre ese centro y el objetivo.
7. **El largo de la correa:** `L = pack.min-chase-blocks + chaseCommitment × (pack.max-chase-blocks − pack.min-chase-blocks)`, de 8 a 32 bloques.
8. **Cuándo se tensa:** si la distancia horizontal entre el objetivo y `ground` es **estrictamente mayor** que `openingDistanceBlocks + L`.

   **Por qué se suma la distancia inicial** (enmienda del CT-33, aprobada por Nico el 3 nov): el radio de detección es de 24 bloques, así que un plan puede abrirse con el jugador ya más lejos que `L`. Sin sumarla, una receta con perilla baja no se acercaría nunca, ni siquiera a un jugador que no se aleja. La perilla mide cuánto persiguen al jugador **que se aleja**.
9. **A quiénes afecta:** a las órdenes con rol `PRESS` o `FLANK` (después de las fases) contra el objetivo del plan, salvo las que estén a `pack.engaged-distance-blocks` o menos del objetivo en horizontal: esas ya pelean. No afecta a `SHOOT`, `VOLLEY`, `HOLD_FIRE`, `FALL_BACK` ni `RETREAT`, ni a una araña que va contra otro jugador.
10. **La orden:** mismo mob y objetivo, rol `GATHER`, sin ataque sugerido, `recovering` en `false` y `rallyPoint` con `ground`. El sorteo de ataques ya se hizo, así que el orden del azar no cambia.
11. **Sin receta o sin el objetivo en la foto, no cambia nada.**
12. **Orden en el cerebro:** primero la correa, después `PackCohesion`. Así un mob atado vuelve al terreno en vez de quedarse parado por la regla del bloque, y no cuenta para el bloque.
13. **El plan no guarda nada:** se recalcula en cada decisión, sin memoria. Si el objetivo vuelve dentro de la correa, retoman su rol.
14. **`GatherGoal`:** el `GATHER` con `rallyPoint` camina hasta el punto y al llegar (`retreat.rally-arrival-blocks`, vía `RallyRoute`) se queda mirando al objetivo. El `GATHER` sin punto (`PackCohesion`) se comporta como hoy.

### Configuración

15. Dos claves nuevas en la sección `pack`:
    - `min-chase-blocks: 8.0`, positivo;
    - `max-chase-blocks: 32.0`, positivo y no menor que el mínimo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/ChaseAnchor.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/brain/ChaseLeash.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/snapshot/GroupSnapshot.java` (`mobCenter`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/RallyPointRule.java` (usa `mobCenter`, borra `centerOf`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipePlay.java`, `RecipePlanner.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`, `PlanEndDetector.java`, `RetreatRule.java`, `PackCohesion.java` (`isInPack` package-private) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/PlanAftermath.java`, `PlanLifecycle.java`, `LifecycleCapture.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java` (solo el Javadoc) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/PackSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GatherGoal.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/ChaseAnchorTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/ChaseLeashTest.java` |
| Modificar | `BrainRecipesTest`, `PlanEndDetectorTest`, `RetreatRuleTest`, `GroupLifecycleTest`, `LifecycleCaptureTest`, `RecipePlannerTest`, `SettingsValidationTest` |
| Modificar | los de `grep -rln "new PackSettings(" src/test` y `grep -rln "new RecipePlay(" src/test`, solo el constructor |
| Modificar | `docs/catalogo-mvp.md` (fila `GATHER`), `docs/arquitectura.md` (tabla «Nombres en el código») y `docs/actualizar-paper.md` (fila de `GatherGoal`) |

Antes de empezar, buscá con grep que `ChaseAnchor`, `ChaseLeash`, `mobCenter`, `lastPlanGroupRetreatStrength` y `isGroupRetreatedAt` no existan en `src/`, y que los nombres de prueba de este WP no existan en sus archivos.

## Especificación

### `GroupSnapshot.java`

```java
  /** The average position of the mobs; only a group with mobs has one. */
  public Vec3 mobCenter() {
    if (mobs.isEmpty()) {
      throw new IllegalStateException("GroupSnapshot.mobCenter: the group has no mobs");
    }
    Vec3 sum = Vec3.ZERO;
    for (MobSnapshot mob : mobs) {
      sum = sum.plus(mob.position());
    }
    return sum.times(1.0 / mobs.size());
  }
```

`RallyPointRule.pointFor` usa `snapshot.mobCenter()` después de su control de `mobs().isEmpty()`, y se borra `centerOf`. Sus pruebas no cambian.

### `ChaseAnchor.java`

```java
package io.github.nicodoou.mobai.domain.strategy;

/**
 * Where a recipe plan started (CT-33): the group's center and how far the target already was, so
 * the chase knob only measures how much the target moved away.
 */
public record ChaseAnchor(Vec3 ground, double openingDistanceBlocks) {
  public ChaseAnchor {
    Objects.requireNonNull(ground, "ChaseAnchor.ground");
    // !(x >= 0) también rechaza NaN
    if (!(openingDistanceBlocks >= 0) || !Double.isFinite(openingDistanceBlocks)) {
      throw new IllegalArgumentException(
          "ChaseAnchor.openingDistanceBlocks must be zero or positive, got "
              + openingDistanceBlocks);
    }
  }

  public static ChaseAnchor of(GroupSnapshot snapshot, PlayerId target)
}
```

`of`:
- `Objects.requireNonNull` con `"ChaseAnchor.snapshot"` y `"ChaseAnchor.target"`;
- objetivo fuera de la foto: `IllegalArgumentException` con `"ChaseAnchor: target <uuid> is not in the snapshot"` (`target.value()`);
- `ground = snapshot.mobCenter()`;
- `openingDistanceBlocks = ground.minus(targetPosition).horizontal().length()`.

No lleves el comentario `// !(x >= 0)…` al código: está acá solo para el WP.

### `RecipePlay.java` y `RecipePlanner.java`

- `RecipePlay` suma `ChaseAnchor chaseAnchor` como **último** componente, con `Objects.requireNonNull(chaseAnchor, "RecipePlay.chaseAnchor")`.
- `RecipePlanner.plan` pasa `ChaseAnchor.of(request.snapshot(), request.target())` como último argumento.
- En las pruebas que construyen `RecipePlay`: `new ChaseAnchor(Vec3.ZERO, 0)` al final.

### `ChaseLeash.java`

```java
package io.github.nicodoou.mobai.domain.brain;

/** Calls melee fighters back to their ground when the target ran further than the recipe chases (CT-33). */
public final class ChaseLeash {
  public ChaseLeash(Supplier<PackSettings> settings)

  /**
   * The same orders, in the same order, with GATHER back to the ground for every PRESS or FLANK
   * fighter of the plan's target that is not yet fighting, once the target left the leash.
   */
  public List<RoleAssignment> reinIn(List<RoleAssignment> orders, GroupSnapshot snapshot, Plan plan)
}
```

- `Objects.requireNonNull` con `"ChaseLeash.settings"`, `"ChaseLeash.orders"`, `"ChaseLeash.snapshot"` y `"ChaseLeash.plan"`.
- Devuelve una lista inmodificable. Cuando no cambia nada, `List.copyOf(orders)`.
- Una orden de un mob que no está en la foto: `IllegalArgumentException` con `"ChaseLeash: order for mob <uuid> has no snapshot"`.
- Membresía: `PackCohesion.isInPack(order, plan.target())` (pasa de `private` a package-private).
- La configuración se lee una vez por llamada. No modifica estado.

### `PlanEndDetector.java`

```java
  // CT-31, CT-33: the dead count as lost health, and a recipe plan brings its own threshold.
  private static boolean hasRetreated(Plan plan, PlanSettings settings) {
    double threshold =
        plan.recipe()
            .map(play -> play.recipe().groupRetreatStrength())
            .orElse(settings.groupRetreatStrength());
    return GroupStrength.ofPlan(plan) < threshold;
  }
```

### `RetreatRule.java`

```java
  public boolean isGroupRetreated(GroupSnapshot snapshot, double lastPlanLostHealth) {
    return isGroupRetreatedAt(snapshot, lastPlanLostHealth, plan.get().groupRetreatStrength());
  }

  public boolean isGroupRetreatedAt(
      GroupSnapshot snapshot, double lastPlanLostHealth, double strength) {
    return GroupStrength.ofSnapshot(snapshot, lastPlanLostHealth) < strength;
  }
```

El comentario actual queda sobre `isGroupRetreatedAt`.

### `PlanAftermath.java`

- Campo: `// null while the last plan had no recipe; exposed only as Optional` / `private Double lastPlanGroupRetreatStrength;`.
- Público: `public Optional<Double> lastPlanGroupRetreatStrength()`.
- Package-private: `void rememberGroupRetreatStrength(Optional<Double> strength)` y `void forgetGroupRetreatStrength()`.
- `restore` lo toma de la captura.

### `PlanLifecycle.java`

- `startPlan`: `aftermath.forgetGroupRetreatStrength();` después de `forgetLostHealth`.
- `closePlan`: después de `rememberLostHealth`,

  ```java
  aftermath.rememberGroupRetreatStrength(
      plan.recipe().map(play -> play.recipe().groupRetreatStrength()));
  ```
- `capture`: `aftermath.lastPlanGroupRetreatStrength()` como último argumento.

No agrega métodos públicos a `PlanLifecycle`.

### `LifecycleCapture.java`

Último componente `Optional<Double> lastPlanGroupRetreatStrength`, con su `requireNonNull`. Se usa `Optional<Double>` y no `OptionalDouble` porque el JSON de los incidentes ya sabe leer `Optional` (`OptionalTypeAdapterFactory`). Las pruebas que lo construyen suman `Optional.empty()`.

### `Brain.java`

1. Campo `private final ChaseLeash chaseLeash;`. En el constructor, después de `packCohesion`:

   ```java
   this.chaseLeash = new ChaseLeash(() -> settings.get().pack());
   ```
2. `isGroupRetreated`:

   ```java
   // CT-33: the group comes back by the same threshold its last plan retreated with.
   private boolean isGroupRetreated(Turn turn) {
     PlanAftermath aftermath = turn.lifecycle().aftermath();
     double lostHealth = aftermath.lastPlanLostHealth();
     return aftermath
         .lastPlanGroupRetreatStrength()
         .map(
             strength ->
                 parts.retreatRule().isGroupRetreatedAt(turn.snapshot(), lostHealth, strength))
         .orElseGet(() -> parts.retreatRule().isGroupRetreated(turn.snapshot(), lostHealth));
   }
   ```
3. El comentario de `shouldRetreat` pasa a: `// CT-30: a recipe plan brings its own retreat threshold.`
4. `planOrders`, con su comentario actual:

   ```java
   private List<RoleAssignment> planOrders(Turn turn) {
     List<RoleAssignment> orders =
         turn.snapshot().mobs().stream().map(mob -> orderFor(turn, mob)).toList();
     Plan plan = currentPlan(turn);
     List<RoleAssignment> leashed = chaseLeash.reinIn(orders, turn.snapshot(), plan);
     return packCohesion.closeRanks(leashed, turn.snapshot(), plan.target());
   }
   ```

### `RoleAssignment.java`

```java
 * @param rallyPoint where a regrouping mob meets the rest of its group (CT-29), or the ground a
 *     leashed fighter walks back to (CT-33)
```

### `PackSettings.java`

Dos componentes al final, `double minChaseBlocks, double maxChaseBlocks`:

```java
    SettingsChecks.requirePositive("PackSettings.minChaseBlocks", minChaseBlocks);
    SettingsChecks.requirePositive("PackSettings.maxChaseBlocks", maxChaseBlocks);
    SettingsChecks.requireNotAbove(
        new NamedSetting("PackSettings.minChaseBlocks", minChaseBlocks),
        new NamedSetting("PackSettings.maxChaseBlocks", maxChaseBlocks));
```

En todas las pruebas: `new PackSettings(8.0, 4.0, 0.25, 1.0, 8.0, 32.0)` (o los valores de ese caso, más `8.0, 32.0`).

### `ConfigLoader.java` y `config.yml`

`pack(root)` suma `number(root, "pack.min-chase-blocks")` y `number(root, "pack.max-chase-blocks")`. En `config.yml`, al final de la sección `pack`:

```yaml
  # Correa (CT-33): cuánto más se puede alejar el jugador del terreno donde empezó la pelea antes
  # de que los cuerpo a cuerpo vuelvan a él. La perilla aprendida chaseCommitment elige entre estos
  # dos valores.
  min-chase-blocks: 8.0
  max-chase-blocks: 32.0
```

### `GatherGoal.java`

- Javadoc de la clase:

  ```java
  /**
   * GATHER: a fighter that ran ahead of its pack stands and watches the target until the rest catch
   * up (CT-32); a leashed one walks back to its ground first (CT-33).
   */
  ```
- Constantes y campo, con el modelo de `FallBackGoal`:

  ```java
  // A multiplier over the mob's normal pathfinder speed, not blocks per tick.
  private static final double WALK_SPEED = 1.0;
  // The way back is long and the ground does not move: once a second is enough.
  private static final long MOVE_INTERVAL_TICKS = 20;
  private long nextMoveTick = Long.MIN_VALUE;
  ```
- `start()`: `nextMoveTick = Long.MIN_VALUE;` y `stopPathfinding()`.
- `tick()`:
  1. lee la orden (`GoalOrders.orderFor(mob, context.roles(), Role.GATHER)`) y, con su objetivo válido, `mob.lookAt`;
  2. si `now < nextMoveTick`, termina; si no, `nextMoveTick = now + MOVE_INTERVAL_TICKS`, con `context.tools().timing().clock().currentTick()`;
  3. `order.rallyPoint().flatMap(point -> context.tools().rallyRoute().next(position, point, Optional.empty()))`, con `position = PoseReader.positionOf(mob.getLocation())`;
  4. si hay paso, `moveTo` a esa posición con `WALK_SPEED`; si no, `stopPathfinding()`.

  Sin punto, el mob se queda quieto como hoy; y si una orden pasa de la correa al bloque sin que el goal se reinicie, el paso 4 frena el camino.
- `shouldActivate`, `shouldStayActive`, `stop`, clave y tipos, sin cambios.

**Sin desvío alrededor del jugador:** se pasa `Optional.empty()` como peligro, porque vuelven alejándose de él y un jugador que los alcanza los vuelve a poner a pelear.

### Documentos

- **`docs/catalogo-mvp.md`,** fila `GATHER`:

  | `GATHER` | Se adelantó más de 8 bloques al más atrasado del grupo: se queda quieto mirando al objetivo hasta que el resto se acerca, salvo que ya esté a 4 bloques o menos (CT-32). En un plan por receta, si el objetivo se alejó del terreno más que la correa, vuelve al terreno y espera ahí (CT-33) |
- **`docs/arquitectura.md`, tabla «Nombres en el código»,** fila nueva después de «Persecución en bloque»:

  | Correa de la persecución | `ChaseLeash` (dominio, cerebro), `ChaseAnchor` (dominio, estrategia) | Dominio |
- **`docs/actualizar-paper.md`,** fila de `GatherGoal`: suma `Mob.getPathfinder().moveTo`, `Mob.getLocation` y `Location`. Riesgo bajo.

## Pruebas obligatorias

### `ChaseAnchorTest` (5)

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `theGroundIsTheCenterOfAllTheMobs` | zombie en `(0, 64, 0)`, esqueleto en `(4, 66, 0)`; objetivo en `(2, 65, 10)` | `ground` = `(2, 65, 0)` por componente, `within(1e-9)` |
| `theOpeningDistanceIsHorizontal` | el mismo, con el objetivo en `(2, 90, 10)` | `openingDistanceBlocks` 10, `within(1e-9)` (en 3D daría ≈ 26,9) |
| `aTargetOutOfTheSnapshotIsRejected` | foto sin jugadores | el mensaje exacto |
| `aGroupWithNoMobsHasNoCenter` | foto sin mobs, con el objetivo | `IllegalStateException` con `"GroupSnapshot.mobCenter: the group has no mobs"` |
| `aNegativeOpeningDistanceIsRejected` | `new ChaseAnchor(Vec3.ZERO, -1)` | `"ChaseAnchor.openingDistanceBlocks must be zero or positive, got -1.0"` |

### `ChaseLeashTest` (14)

**Escenario de todas:**
- `new PackSettings(8.0, 4.0, 0.25, 1.0, 8.0, 32.0)`;
- plan contra `ALICE` con receta `new PlanRecipe(new RoleSplit(1, 0, 0), new RoleSplit(0, 0, 0), false, 25, 0, 0, chase)` y `new ChaseAnchor(GROUND, opening)`, donde `GROUND` es `(0, 64, 0)`;
- los mobs y Alice sobre el eje Z, en `(0, 64, z)`, salvo que se diga otra cosa.

Salvo que se diga otra cosa, `chase` 0,5 y `opening` 10: `L` = 8 + 0,5 × 24 = 20 y el límite es 30. Las órdenes se arman a mano, como en `PackCohesionTest`.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `beyondTheLeashFightersWalkBack` | Alice en 31, zombie `PRESS` en 20 | zombie `GATHER` |
| `theLeashOrderKeepsTheTargetAndDropsTheAttack` | el mismo | la orden es igual a `new RoleAssignment(zombie, GATHER, Optional.of(ALICE), Optional.empty(), false, Optional.of(GROUND))` |
| `exactlyAtTheLimitKeepsChasing` | Alice en 30, zombie en 20 | sin cambios |
| `engagedFighterKeepsFighting` | Alice en 31, zombie en 28 | sin cambios |
| `engagedAtTheEdgeKeepsFighting` | Alice en 31, zombie en 27 (4 justo) | sin cambios |
| `flankersAreCalledBackToo` | Alice en 31, zombie `FLANK` en 20 | `GATHER` |
| `noChaseMeansTheShortestLeash` | `chase` 0: límite 18. Alice en 18 y en 19 (dos llamadas), zombie en 5 | en 18 sin cambios; en 19 `GATHER` |
| `fullChaseMeansTheLongestLeash` | `chase` 1: límite 42. Alice en 42 y en 43, zombie en 20 | en 42 sin cambios; en 43 `GATHER` |
| `theOpeningDistanceIsAdded` | `chase` 0, `opening` 20: límite 28. Alice en 25, zombie en 5 | sin cambios |
| `heightDoesNotCount` | Alice en `(0, 100, 30)`, zombie en `(0, 64, 10)` | sin cambios (en 3D, Alice estaría a ≈ 46,9 del terreno y pasaría el límite) |
| `shootersReserveAndRetreatersAreNotCalled` | Alice en 31; en 20: `SHOOT`, `VOLLEY`, `HOLD_FIRE`, `FALL_BACK` y `RETREAT` | las cinco sin cambios |
| `aSpiderOnAnotherTargetIsNotCalled` | Alice en 31, Bob en `(0, 64, 40)`, araña `PRESS` contra Bob en 20 | sin cambios |
| `aPlanWithoutRecipeHasNoLeash` | plan sin receta, Alice en 100, zombie en 20 | sin cambios |
| `aTargetOutOfTheSnapshotChangesNothing` | foto sin jugadores, zombie en 20 | sin cambios |

### `PlanEndDetectorTest` (2 nuevas)

Con un helper privado `recipePlanWith(double groupRetreatStrength, MobId... mobs)` y `withHealth` (20 de vida de partida).

| Prueba | Vidas | Umbral de la receta | Espera |
| --- | --- | --- | --- |
| `aLowRecipeStrengthKeepsTheGroupFighting` | 6, 6, 6, 6 (fuerza 0,3; la configuración es 0,4) | 0,18 | vacío |
| `aHighRecipeStrengthRetreatsEarlier` | 9, 9, 9, 9 (fuerza 0,45) | 0,5 | `GROUP_RETREATED` |

### `RetreatRuleTest` (1 nueva)

`isGroupRetreatedAtUsesTheGivenStrength`: foto con mobs a 0,45 de su vida máxima y sin vida perdida. Con 0,5 da `true` y con 0,4 da `false`.

### `GroupLifecycleTest` (3 nuevas)

Con un `PlanStart` que lleva receta con `groupRetreatStrength` 0,3 (helper privado).

| Prueba | Verifica |
| --- | --- |
| `closingARecipePlanRemembersItsRetreatStrength` | después de `closePlan`, `lastPlanGroupRetreatStrength()` contiene 0,3 |
| `closingAStrategyPlanRemembersNoRetreatStrength` | primero un plan por receta cerrado; después, un plan sin receta cerrado: vacío |
| `openingAPlanForgetsTheRetreatStrength` | plan por receta cerrado y evaluado; `beginPlanning` y `startPlan` sin receta: vacío antes de cerrarlo |

### `LifecycleCaptureTest` (1 nueva)

`retreatStrengthSurvivesCaptureAndRestore`, con el modelo de `lostHealthSurvivesCaptureAndRestore`.

### `RecipePlannerTest` (1 nueva)

`thePlayCarriesItsChaseAnchor`: con `fourZombiesFlankRequest(NO_TRAITS)`, `play.chaseAnchor()` es igual a `ChaseAnchor.of(request.snapshot(), request.target())`.

### `BrainRecipesTest` (5 nuevas)

Agregá el helper privado:

```java
  private static List<MobSnapshot> atHealthFraction(List<MobSnapshot> mobs, double fraction) {
    return mobs.stream()
        .map(
            mob ->
                new MobSnapshot(
                    mob.id(),
                    mob.kind(),
                    mob.position(),
                    mob.maxHealth() * fraction,
                    mob.maxHealth()))
        .toList();
  }
```

**Perillas de las pruebas.** Los `TIE_BREAKERS` del archivo ya dejan la retirada individual en 0, a todos los zombies y arañas con `PRESS`, y sin andanada. El pico es `−w_lineal ÷ (2 × w_cuadrado)`, y se multiplica por 0,6 en la retirada del grupo:
- `Map.of(15, 0.6, 16, -1.0)`: pico 0,3 → umbral 0,18;
- `Map.of(15, 1.0, 16, -0.6)`: pico 0,8333 → umbral 0,5;
- `Map.of(17, -1.0)`: persecución 0 → `L` = 8.

Verificá cada umbral con `isCloseTo(…, within(0.01))` antes de lo demás: el modelo se sortea, aunque con mucha certeza.

| Prueba | Pasos | Espera |
| --- | --- | --- |
| `aLowRecipeStrengthFightsBelowTheConfiguredOne` | umbral 0,18. `decide(START_TICK, mobs, alice())` a vida llena; `decide(START_TICK + 10, atHealthFraction(mobs, 0.3), alice())` | sin `closedPlan`; estado `EXECUTING` |
| `aHighRecipeStrengthRetreatsAboveTheConfiguredOne` | umbral 0,5. Lo mismo, con 0,45 | `closedPlan` con `GROUP_RETREATED`; estado `REGROUPING` |
| `theGroupStaysRetreatedByItsLastPlanStrength` | el anterior; después `decide(START_TICK + 10 + fixture.regroupWindow().currentTicks(), atHealthFraction(mobs, 0.45), alice())` | sigue en `REGROUPING` (con el 0,4 de la configuración habría salido) |
| `theGroupRejoinsByItsLastPlanStrength` | umbral 0,18. A vida llena; `START_TICK + 10` con 0,1 (cierra `GROUP_RETREATED`); al vencer la ventana, con 0,3 | estado `OBSERVING` (con 0,4 seguiría reagrupándose) |
| `theLeashCallsFightersBackAndTheyReturnWhenTheTargetComesBack` | ver abajo | ver abajo |

**La de la correa.** Con `Map.of(17, -1.0)`, los 9 mobs en `(0, 64, 0)` (con `BrainFixture.withPosition`, de a uno):

1. `START_TICK`, Alice en `(0, 64, 10)`: verificá `chaseCommitment` 0 (`within(0.01)`), `ground` en x y z 0 (`within(1e-9)`) y `openingDistanceBlocks` 10 (`within(1e-9)`). El límite es 18.
2. `START_TICK + 10`, Alice en `(0, 64, 19)`, zombies en el origen y arañas en `(0, 64, 14)`:
   - zombies y arañas con `GATHER` y `rallyPoint` igual a `ground` (a 19 y a 5 bloques de Alice; ninguno pelea);
   - los 3 esqueletos con `SHOOT`.
3. `START_TICK + 20`, Alice en `(0, 64, 18)`, los mismos lugares, con las arañas en `(0, 64, 12)`:
   - zombies con `PRESS`;
   - arañas con `GATHER` y `rallyPoint` vacío: ya no es la correa sino el bloque (van a 6 de Alice y el más atrasado a 18: 12 > 8).

Si en el paso 2 el orden fuera bloque y después correa, las arañas tendrían `rallyPoint` vacío: eso es lo que prueba el orden.

### Otras

- **`SettingsValidationTest`,** tres casos en `outOfRangeValuesProvider`, con los mensajes de `requirePositive` y `requireNotAbove`:
  - `new PackSettings(8.0, 4.0, 0.25, 1.0, 0.0, 32.0)` → `PackSettings.minChaseBlocks`;
  - `new PackSettings(8.0, 4.0, 0.25, 1.0, 8.0, 0.0)` → `PackSettings.maxChaseBlocks`;
  - `new PackSettings(8.0, 4.0, 0.25, 1.0, 40.0, 32.0)` → `"PackSettings.minChaseBlocks must not exceed PackSettings.maxChaseBlocks, got 40.0 > 32.0"`.
- **`ConfigLoaderTest`:** alcanza con actualizar el constructor.

**Si cambia por estas reglas alguna prueba existente del cerebro, de las invariantes, de la simulación, de la calibración o de los incidentes (más allá de los constructores), frená y reportá cuál y cómo.** No ajustes valores esperados.

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `ChaseLeash`, no sumar `openingDistanceBlocks` (con `chase` 0 y `opening` 20, el límite da 8 y Alice en 25 lo pasa) | `theOpeningDistanceIsAdded` |
| 2 | En `ChaseLeash`, `>=` en lugar de `>` | `exactlyAtTheLimitKeepsChasing` |
| 3 | En `ChaseLeash`, distancia 3D entre el objetivo y el terreno (≈ 46,9 > 30) | `heightDoesNotCount` |
| 4 | En `PlanEndDetector`, usar siempre la configuración | `aHighRecipeStrengthRetreatsEarlier` y `aLowRecipeStrengthKeepsTheGroupFighting` |
| 5 | En `Brain.isGroupRetreated`, usar siempre `isGroupRetreated` (0,45 ≥ 0,4 sale; 0,3 < 0,4 se queda) | `theGroupStaysRetreatedByItsLastPlanStrength` y `theGroupRejoinsByItsLastPlanStrength` |
| 6 | En `Brain.planOrders`, `closeRanks` antes que `reinIn` | `theLeashCallsFightersBackAndTheyReturnWhenTheTargetComesBack` |
| 7 | En `PlanLifecycle.startPlan`, no olvidar el umbral | `openingAPlanForgetsTheRetreatStrength` |

## Verificación en el server (Nico, después del merge)

Opus suma `min-chase-blocks` y `max-chase-blocks` al `config.yml` de prueba y a `docs/plan/config-de-prueba.yml`. El server necesita `planner: "RECIPES"`.

- **Correa:** alejate corriendo sin pelear. En algún plan, los zombies y las arañas vuelven adonde empezaron y esperan, y los esqueletos siguen tirando. Cuando te acercás, retoman. Cuánto te siguen tiene que variar entre recetas: la línea `PLAN` del log muestra la perilla.
- **Retirada:** en una pelea larga, el grupo se retira con más o menos vida según la receta (`GROUP_RETREATED` en el log).

## Procedimiento

1. Rama `wp-34c-retirada-y-correa` desde `origin/main` actualizado.
2. Retirada del grupo: `PlanEndDetector`, `RetreatRule`, `PlanAftermath`, `PlanLifecycle`, `LifecycleCapture`, `Brain.isGroupRetreated` y sus pruebas (las de la retirada de `BrainRecipesTest`). Commit: `feat: recipe plans retreat by their own group strength (CT-33)`.
3. Configuración: `PackSettings`, `ConfigLoader`, `config.yml` y todas las pruebas con `new PackSettings(`. Commit: `feat: chase leash settings (CT-33)`.
4. Ancla: `GroupSnapshot.mobCenter`, `RallyPointRule`, `ChaseAnchor`, `RecipePlay`, `RecipePlanner` y sus pruebas. Commit: `feat: recipe plays remember where the fight started (CT-33)`.
5. Correa: `ChaseLeash`, `PackCohesion.isInPack`, `Brain.planOrders`, `RoleAssignment` y sus pruebas. Commit: `feat: fighters walk back when the target outruns the leash (CT-33)`.
6. `GatherGoal`. Commit: `feat: gather goal walks back to the ground (CT-33)`.
7. Documentos. Commit: `docs: chase leash in the catalog, the names table and the Paper map`.
8. Pruebas que muerden (de a una; sin commit).
9. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`: Nico puede tener su server corriendo con un daemon de Gradle.
10. Push, PR `WP-34C: group retreat and chase leash in play (CT-33)`, CI en verde e informe con:
    - la prueba exacta que falló en cada rotura;
    - los archivos de prueba que tocaste solo por constructores;
    - cualquier prueba existente que haya cambiado (no debería haber ninguna).

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá en un record privado, y avisalo (`codeUnitsHaveAtMostThreeParameters` lo verifica).
3. Si `GroupLifecycleTest` necesita otra forma de armar el helper con receta, seguí el patrón del archivo y avisalo.

## Fuera de alcance

- Costados al azar, mantener la distancia y `GatherGoal` retrocediendo si el jugador se acerca (WP-34D).
- Esquive del arco y golpe oportunista (WP-34E).
- Histéresis de la correa o memoria de quién estaba atado.
- Guardar el umbral recordado en disco.
- Mostrar el ancla en `/mobai memory`, en la línea `PLAN` o en `training-data.jsonl`.
- Cambiar la simulación o la recalibración.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 32 pruebas nuevas y los 3 casos de validación, en verde; la suite completa en verde, sin cambios en pruebas existentes salvo los constructores.
- [ ] Las 7 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
