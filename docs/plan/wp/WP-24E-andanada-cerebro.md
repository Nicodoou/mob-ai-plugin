# WP-24E — Andanada: estrategia y fases en el cerebro

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-23C (mergeado) |
| Modelo | **Opus** (cerebro) |
| Rama | `wp-24e-andanada-cerebro` |

## Objetivo

CT-23, pedido de Nico: una estrategia nueva, **`VOLLEY`** (andanada), que la memoria aprende como las otras tres. En vez de que los esqueletos le peguen a los zombies que atacan, el grupo se coordina en un ciclo:

1. **Presión** (60 ticks): los cuerpo a cuerpo presionan; los esqueletos se ubican y **no disparan**, así guardan el tiro.
2. **Apertura** (20 ticks): los cuerpo a cuerpo **se abren** (salen del alcance del jugador); los esqueletos siguen sin disparar.
3. **Fuego** (30 ticks): los esqueletos disparan **todos juntos**; los cuerpo a cuerpo siguen afuera hasta que las flechas llegan. Después vuelve la presión.

Este WP es solo el dominio: la estrategia, las fases y las órdenes nuevas. Los goals que las ejecutan son el WP-24F.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java`
- `src/main/java/io/github/nicodoou/mobai/domain/group/Role.java`, `Plan.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/` (todos)
- `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `SettingsChecks.java`, `RetreatSettings.java` (como modelo)
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java`, `src/main/resources/config.yml`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java`, `BrainExecutingTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/strategy/StrategyCatalogTest.java`, `PinAndShootStrategyTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `BrainFixture.java`, `src/test/java/io/github/nicodoou/mobai/simulation/OutcomeModel.java`, `LearningSimulation.java`, `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java`, `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java`

## Reglas de negocio

1. **Estrategia `VOLLEY`** (`VolleyStrategy`):
   - viable con **al menos 2 esqueletos y al menos 2 mobs cuerpo a cuerpo** (zombies o arañas);
   - reparte zombies y arañas a `PRESS` y esqueletos a `SHOOT`, igual que el ataque directo;
   - va **última** en el catálogo, después de `PIN_AND_SHOOT`, para que los índices de las otras no cambien.
2. **Fases** (`VolleyCycle`), según la **edad del plan** (`Plan.ageTicks`): con `press-ticks` 60, `fall-back-ticks` 20 y `fire-ticks` 30, el ciclo dura 110 ticks.
   - En cada ciclo, edad de 0 a 59: `PRESSING`; de 60 a 79: `FALLING_BACK`; de 80 a 109: `FIRING`.
   - Después se repite: en la edad 110 empieza otro `PRESSING`.
3. **Las fases cambian las órdenes, no el plan.** El plan guarda `PRESS` y `SHOOT` como siempre. Así no cambian ni la vuelta de la retirada ni el cierre por retirada del grupo, y `Plan.roleOf` no ve nunca los roles nuevos. Solo cambia el rol de la **orden**, en un plan `VOLLEY`:

   | Rol en el plan | `PRESSING` | `FALLING_BACK` | `FIRING` |
   | --- | --- | --- | --- |
   | `PRESS` (zombie o araña) | `PRESS`, con su ataque como siempre | `FALL_BACK`, sin ataque | `FALL_BACK`, sin ataque |
   | `SHOOT` (esqueleto) | `HOLD_FIRE`, sin ataque | `HOLD_FIRE`, sin ataque | `VOLLEY`, con su tiro sugerido |
   | `RETREAT` | `RETREAT` (la retirada manda, como siempre) | igual | igual |

4. **Sin sorteos de más:** las órdenes `FALL_BACK` y `HOLD_FIRE` no sortean ataque (no llaman al sugeridor). Así la reproducción y las pruebas no gastan azar en ataques que no se van a hacer.
5. **Objetivo de las órdenes nuevas:** el objetivo del plan. Las arañas en `FALL_BACK` no usan su regla de objetivo propio: se apartan del objetivo del plan.
6. **Roles nuevos** (`Role`): `FALL_BACK`, `HOLD_FIRE`, `VOLLEY`, después de `RETREAT`. Solo aparecen en órdenes de planes `VOLLEY`.
7. **Configuración** (sección nueva `volley`):
   - `press-ticks: 60`, `fall-back-ticks: 20` y `fire-ticks: 30`, enteros de 1 o más;
   - `fall-back-margin-blocks: 1.5`, cero o más: lo que el cuerpo a cuerpo se aleja más allá del alcance del jugador al abrirse (lo usa el WP-24F).
   - Los tiempos son múltiplos de 10 porque el cerebro decide cada 10 ticks.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/group/Role.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/settings/VolleySettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/VolleyStrategy.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/strategy/StrategyCatalog.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/brain/VolleyPhase.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/brain/VolleyCycle.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/brain/Brain.java` |
| Modificar | `docs/catalogo-mvp.md` (fila nueva en la tabla de estrategias y roles nuevos en la de roles) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/VolleyCycleTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/VolleyStrategyTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainVolleyTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/strategy/StrategyCatalogTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/brain/BrainObservingTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |
| Modificar | cada archivo de prueba con `new MobAiSettings(` (ver abajo) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/simulation/OutcomeModel.java` |

`new MobAiSettings(` aparece hoy en `ConfigLoader`, `ConfigLoaderTest`, `RecordOutcomeTest`, `RecruitMobTest` (2), `SettingsHolderTest`, `SettingsValidationTest` (2), `LearningSimulation` y `TestSettings`. Todos suman el componente nuevo al final. En las pruebas va `TestSettings.defaults().volley()` (o `base.volley()` donde ya hay un `base`). En `TestSettings` va `new VolleySettings(60, 20, 30, 1.5)`. Buscá con grep antes de empezar: si aparece en otro archivo, frená y reportá.

Antes de empezar, buscá también:
- `switch` sobre `Role` sin `default` en `src/`: un `case` faltante no compila, y lo tenés que reportar;
- que los nombres de las pruebas nuevas no existan.

**Métodos públicos:** ninguno nuevo en clases existentes. `MobAiSettings` suma un componente.

## Especificación

### `Role.java`

```java
public enum Role {
  PRESS,
  FLANK,
  SHOOT,
  RETREAT,
  // Only in the orders of a volley plan (CT-23); a plan never stores them.
  FALL_BACK,
  HOLD_FIRE,
  VOLLEY
}
```

### `VolleySettings.java` y `MobAiSettings.java`

```java
public record VolleySettings(
    long pressTicks, long fallBackTicks, long fireTicks, double fallBackMarginBlocks) {
  public VolleySettings {
    SettingsChecks.requireAtLeast("VolleySettings.pressTicks", pressTicks, 1);
    SettingsChecks.requireAtLeast("VolleySettings.fallBackTicks", fallBackTicks, 1);
    SettingsChecks.requireAtLeast("VolleySettings.fireTicks", fireTicks, 1);
    SettingsChecks.requireNonNegative("VolleySettings.fallBackMarginBlocks", fallBackMarginBlocks);
  }
}
```

`MobAiSettings` suma `VolleySettings volley` al final, con `requireNonNull(volley, "MobAiSettings.volley")`.

### `ConfigLoader.java` y `config.yml`

`ConfigLoader` lee la sección como las otras: `wholeNumber(root, "volley.press-ticks")`, `wholeNumber(root, "volley.fall-back-ticks")`, `wholeNumber(root, "volley.fire-ticks")`, `number(root, "volley.fall-back-margin-blocks")`. Al final de `config.yml`:

```yaml
volley:
  # Andanada (CT-23): los cuerpo a cuerpo presionan, se abren, y los esqueletos disparan juntos.
  # Múltiplos de 10: el grupo decide cada 10 ticks.
  press-ticks: 60
  fall-back-ticks: 20
  fire-ticks: 30
  # Cuánto más allá del alcance del jugador se abren los cuerpo a cuerpo.
  fall-back-margin-blocks: 1.5
```

### `VolleyStrategy.java`

```java
public final class VolleyStrategy implements GroupStrategy {
  public static final StrategyId ID = new StrategyId("VOLLEY");
  private static final int MIN_SKELETONS = 2;
  private static final int MIN_MELEE = 2;

  // id(), requirement() = "at least " + MIN_SKELETONS + " skeletons and " + MIN_MELEE + " melee mobs",
  // isViable: GroupComposition.of(snapshot).skeletons() >= MIN_SKELETONS && .melee() >= MIN_MELEE,
  // assignRoles: como PinAndShootStrategy, con roleFor(kind): SKELETON -> SHOOT; ZOMBIE, SPIDER -> PRESS
}
```

`StrategyCatalog`: `List.of(new DirectAssaultStrategy(), new FlankStrategy(geometry), new PinAndShootStrategy(), new VolleyStrategy())`.

### `VolleyPhase.java` y `VolleyCycle.java` (`domain.brain`)

```java
/** Where a volley plan is in its press, fall back and fire cycle. */
public enum VolleyPhase {
  PRESSING,
  FALLING_BACK,
  FIRING
}
```

```java
/** The volley cycle (CT-23): melee press, then open up, then the shooters fire together. */
public final class VolleyCycle {
  private final Supplier<VolleySettings> settings;

  public VolleyCycle(Supplier<VolleySettings> settings) {
    this.settings = Objects.requireNonNull(settings, "VolleyCycle.settings");
  }

  public VolleyPhase phaseAt(long planAgeTicks) {
    VolleySettings volley = settings.get();
    long cycle = volley.pressTicks() + volley.fallBackTicks() + volley.fireTicks();
    long inCycle = planAgeTicks % cycle;
    if (inCycle < volley.pressTicks()) {
      return VolleyPhase.PRESSING;
    }
    if (inCycle < volley.pressTicks() + volley.fallBackTicks()) {
      return VolleyPhase.FALLING_BACK;
    }
    return VolleyPhase.FIRING;
  }
}
```

### `Brain.java`

Campo nuevo `private final VolleyCycle volleyCycle;`, armado en el constructor con `new VolleyCycle(() -> settings.get().volley())`. No toca `BrainParts`.

`orderFor` queda así:

```java
  private RoleAssignment orderFor(Turn turn, MobSnapshot mob) {
    Plan plan = currentPlan(turn);
    Role planned = plan.roleOf(mob.id()).orElseThrow();
    if (planned == Role.RETREAT) {
      return retreatOrder(turn, mob, Optional.of(plan.target()));
    }
    Role role = phasedRole(turn, planned);
    if (role == Role.FALL_BACK || role == Role.HOLD_FIRE) {
      return new RoleAssignment(
          mob.id(), role, Optional.of(plan.target()), Optional.empty(), false);
    }
    if (mob.kind() == MobKind.SPIDER) {
      return spiderOrder(turn, mob, role);
    }
    return fighterOrder(turn, mob, role);
  }

  // CT-23: in a volley plan the phase decides what each planned role does right now; the plan
  // itself keeps PRESS and SHOOT, so retreats and group-retreat checks are unaffected.
  private Role phasedRole(Turn turn, Role planned) {
    Plan plan = currentPlan(turn);
    if (!plan.strategy().equals(VolleyStrategy.ID)) {
      return planned;
    }
    VolleyPhase phase = volleyCycle.phaseAt(plan.ageTicks(turn.snapshot().tick()));
    if (planned == Role.PRESS) {
      return phase == VolleyPhase.PRESSING ? Role.PRESS : Role.FALL_BACK;
    }
    if (planned == Role.SHOOT) {
      return phase == VolleyPhase.FIRING ? Role.VOLLEY : Role.HOLD_FIRE;
    }
    return planned;
  }
```

`fighterOrder` y `spiderOrder` no cambian: reciben el rol de la orden (`VOLLEY` para el esqueleto, que sortea su tiro como siempre). `chooseFighterAttack` solo mira `FLANK` para el zombie, así que `VOLLEY` usa el sugeridor.

### `docs/catalogo-mvp.md`

- Fila nueva en la tabla de estrategias: `| Andanada (`VOLLEY`) | Los cuerpo a cuerpo presionan; cada tanto se abren fuera del alcance del jugador y los esqueletos disparan todos juntos con la línea limpia; después vuelven a presionar (CT-23) | Al menos 2 esqueletos y 2 cuerpo a cuerpo | Cuerpo a cuerpo: `PRESS`; esqueletos: `SHOOT`. Las fases cambian las órdenes: `FALL_BACK`, `HOLD_FIRE` y `VOLLEY` |`
- Filas nuevas en la tabla de roles:
  - `FALL_BACK`: «Se aleja hasta quedar fuera del alcance del jugador, sin pegar (andanada)».
  - `HOLD_FIRE`: «El esqueleto se ubica pero no dispara (andanada)».
  - `VOLLEY`: «El esqueleto dispara ya, junto con los demás (andanada)».

### Pruebas existentes que cambian

| Archivo | Cambio |
| --- | --- |
| `StrategyCatalogTest` | `listsTheThreeStrategiesInFixedOrder` pasa a `listsTheFourStrategiesInFixedOrder` y espera las cuatro, `VOLLEY` última; `testGroupMakesEveryStrategyViable` espera las cuatro |
| `BrainObservingTest` | `plansAndExecutesInTheSameDecision`: `strategyChecks()` `hasSize(4)` (el índice 1 sigue siendo `FLANK`). `onlyViableStrategiesAreCandidates`: suma `tuple(VolleyStrategy.ID, false)` al final |
| `OutcomeModel` | Filas nuevas para esqueletos con rol `VOLLEY`. `BLOCKER`: `DIRECT_SHOT` `(0.25, 0.50, 0.25, 0)`, `LEAD_SHOT` `(0.30, 0.45, 0.25, 0)`, `OPPORTUNISTIC_SHOT` `(0.30, 0.45, 0.25, 0)`. `OPEN`: `DIRECT_SHOT` `(0.45, 0, 0.55, 0)`, `LEAD_SHOT` `(0.65, 0, 0.35, 0)`, `OPPORTUNISTIC_SHOT` `(0.65, 0, 0.35, 0)`. En la andanada, el oportuno dispara como el anticipado (WP-24F). Las órdenes `FALL_BACK` y `HOLD_FIRE` no tienen ataque y la simulación ya las saltea |
| `ConfigLoaderTest`, `SettingsValidationTest` y los demás `new MobAiSettings(` | el componente nuevo al final. En `SettingsValidationTest`, dos casos nuevos: `new VolleySettings(0, 20, 30, 1.5)` → `VolleySettings.pressTicks must be at least 1, got 0`; `new VolleySettings(60, 20, 30, -1.0)` → `VolleySettings.fallBackMarginBlocks must be zero or positive, got -1.0` |

Si `BrainInvariantsTest`, `BrainExecutingTest`, `LearningSimulationTest` u otra prueba existente falla por la estrategia nueva, **frená y reportá** la prueba, el mensaje y, si la imprime, la cobertura. No la cambies.

## Pruebas obligatorias

### `VolleyCycleTest` (5)

Con `new VolleySettings(60, 20, 30, 1.5)`.

| Prueba | Verifica |
| --- | --- |
| `startsPressing` | edad 0 y 59: `PRESSING` |
| `thenFallsBack` | edad 60 y 79: `FALLING_BACK` |
| `thenFires` | edad 80 y 109: `FIRING` |
| `theCycleRepeats` | edad 110: `PRESSING`; 170: `FALLING_BACK`; 190: `FIRING` |
| `phasesFollowTheSettings` | con `new VolleySettings(10, 10, 10, 0)`: edad 9 `PRESSING`, 10 `FALLING_BACK`, 20 `FIRING`, 30 `PRESSING` |

### `VolleyStrategyTest` (4)

| Prueba | Verifica |
| --- | --- |
| `needsTwoSkeletonsAndTwoMelee` | 2 esqueletos y 2 zombies: viable; 1 esqueleto y 3 zombies: no; 2 esqueletos y 1 araña: no; 2 esqueletos, 1 zombie y 1 araña: viable |
| `meleePressAndSkeletonsShoot` | zombie `PRESS`, araña `PRESS`, esqueleto `SHOOT`, en el orden de la foto |
| `describesItsRequirement` | id `VOLLEY` y requisito `at least 2 skeletons and 2 melee mobs` |
| `catalogListsVolleyLast` | `new StrategyCatalog(new CombatGeometry()).all()` termina en `VolleyStrategy.ID` |

### `BrainVolleyTest` (5)

`BrainFixture.choosingStrategy(3)`: con el grupo del catálogo las cuatro estrategias son viables y el índice 3 es `VOLLEY`. `mobs = fixture.catalogGroup()`: zombies 0 a 3, esqueletos 4 a 6, arañas 7 y 8. El plan empieza en `START_TICK`.

| Prueba | Decisión | Verifica |
| --- | --- | --- |
| `pressingKeepsTheShootersQuiet` | `START_TICK` | `decision().strategy()` `VOLLEY`. Zombies y arañas: `PRESS` con ataque. Esqueletos: `HOLD_FIRE`, sin ataque y con objetivo `ALICE` |
| `fallingBackPullsTheMeleeOut` | `START_TICK` y `START_TICK + 60` | En la segunda, zombies y arañas `FALL_BACK` sin ataque y con objetivo `ALICE`; esqueletos `HOLD_FIRE` |
| `firingLetsEveryShooterLoose` | `START_TICK`, `+60` y `+80` | En la tercera, esqueletos `VOLLEY` con un tiro sugerido de esqueleto; zombies y arañas `FALL_BACK` |
| `thePlanKeepsItsOwnRoles` | `START_TICK` y `+60` | `fixture.group().lifecycle().plan().orElseThrow().roleOf(zombie 0)` es `PRESS` y `roleOf(esqueleto 4)` es `SHOOT` |
| `aWoundedMemberStillRetreatsDuringTheVolley` | `START_TICK`; en `+60`, el zombie 0 con vida 5 | Su orden es `RETREAT`; los demás zombies, `FALL_BACK` |

Total: **14 pruebas nuevas**, más las existentes de la tabla actualizadas.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `phaseAt`, sin el `% cycle` | edad 110 da `FIRING` | `theCycleRepeats` |
| 2 | En `phasedRole`, el esqueleto dispara en `PRESSING` (devolver `SHOOT` en vez de `HOLD_FIRE`) | `SHOOT` con ataque | `pressingKeepsTheShootersQuiet` |
| 3 | En `orderFor`, sortear ataque también para `FALL_BACK` (llamar a `fighterOrder`) | la orden trae ataque | `fallingBackPullsTheMeleeOut` |
| 4 | En `VolleyStrategy.isViable`, `>= 1` esqueleto | 1 esqueleto y 3 zombies: viable | `needsTwoSkeletonsAndTwoMelee` |

## Procedimiento

1. Rama `wp-24e-andanada-cerebro` desde `origin/main` actualizado.
2. `Role`, `VolleySettings`, `MobAiSettings`, `ConfigLoader`, `config.yml` y todos los `new MobAiSettings(` (con `ConfigLoaderTest` y `SettingsValidationTest`). Commit: `feat: volley settings and roles (CT-23)`.
3. `VolleyStrategy`, `StrategyCatalog`, `VolleyPhase`, `VolleyCycle`, `Brain`, `OutcomeModel`, `catalogo-mvp.md` y las pruebas. Commit: `feat: volley strategy whose phases shape the orders (CT-23)`.
4. Pruebas que muerden (de a una; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-24E: volley strategy in the brain (CT-23)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una prueba existente fuera de la tabla falla, frená y reportá.

## Fuera de alcance

- Los goals: `FallBackGoal`, `ShootGoal` con `HOLD_FIRE` y `VOLLEY`, el instalador (WP-24F).
- Cambiar las otras estrategias o el sugeridor.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 14 pruebas nuevas con sus nombres exactos y las existentes actualizadas, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] El plan nunca guarda `FALL_BACK`, `HOLD_FIRE` ni `VOLLEY`.
- [ ] Build, cobertura y CI en verde.
