# WP-33D — Rasgos del jugador y configuración del aprendizaje

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo (CT-30) |
| Depende de | WP-33C (mergeado, con `PlayerTraits`) |
| Modelo | Sonnet |
| Rama | `wp-33d-rasgos-y-configuracion` |

## Objetivo

CT-30, cuarta pieza. Prepara todo lo que el cerebro necesita para planificar con recetas (WP-33E), sin cambiar todavía ninguna decisión:

1. **Medir el arma a distancia:** la foto del jugador suma `holdingRanged` (arco o ballesta en la mano principal), leído por `VersionTranslator`.
2. **`TraitLedger`:** guarda, por jugador, el promedio con olvido de lo que vieron los grupos: escudo, a distancia y armadura. Da los `PlayerTraits` de cada uno.
3. **Configuración `learning`:** el conmutador `planner` (`STRATEGIES` o `RECIPES`), los números del modelo calibrados en el WP-33C, los límites de las recetas y la vida media de los rasgos. Por defecto, `STRATEGIES`: el juego sigue igual que hoy.

## Decisiones tomadas en este WP

1. **Promedio con olvido por tiempo:** para cada rasgo se guardan la suma ponderada y el peso, los dos multiplicados por `0,5^(Δticks / vida media)` antes de sumar la observación nueva (con peso 1). El rasgo es suma / peso. Sin observaciones nuevas, el promedio no cambia: el olvido solo pesa contra lo nuevo.
2. **Una observación por jugador y por tick:** varios grupos pueden ver al mismo jugador en el mismo tick. Si el tick es el mismo de la última observación, se ignora; si no, se contaría doble.
3. **Solo jugadores vivos** (`health() > 0`).
4. **Observaciones:** escudo = 1 si `blocking`; a distancia = 1 si `holdingRanged`; armadura = `min(1, armorPoints / ARMOR_MAX_POINTS)`, con la constante que ya existe en `MinecraftConstants` (20).
5. **Jugador desconocido:** `PlayerTraits(0, 0, 0)`.
6. **`capture()` y `restore()` desde ahora**, para guardarlo en disco (WP-33F) y en los incidentes (WP-33E).
7. **Nadie lo usa en este WP:** el cerebro lo observa y lo consulta a partir del WP-33E.
8. **Números calibrados (WP-33C, `docs/plan/calibracion-recetas.md`):** varianza del ruido 0,01; varianza del punto de partida 1; éxito de partida 0,5; exploración 1 en el juego y 2 en entrenamiento; demora de la reserva entre 20 y 400 ticks; umbral de retirada hasta 0,6. Vida media de los rasgos: 6000 ticks (5 minutos), para que un cambio de equipo pese en minutos y no en segundos.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/domain/snapshot/PlayerSnapshot.java`, `GroupSnapshot.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/PlayerTraits.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` (`ARMOR_MAX_POINTS`)
- `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java`, `RetreatSettings.java` (estilo), `SettingsChecks.java`, `NamedSetting.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` (`choice`, `number`, `wholeNumber`), `src/main/resources/config.yml`
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java` (`playerSnapshot`)
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- Pruebas: `testsupport/PlayerSnapshotBuilder`, `testsupport/TestSettings`, `domain/settings/SettingsValidationTest`, `adapter/config/ConfigLoaderTest`
- `docs/actualizar-paper.md` (fila de `VersionTranslator` y de `SnapshotFactory`) y `docs/arquitectura.md` (tabla «Nombres en el código»)

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/snapshot/PlayerSnapshot.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/settings/PlannerKind.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/settings/LearningSettings.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/settings/MobAiSettings.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/TraitLedger.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/strategy/TraitSums.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/SnapshotFactory.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/config/ConfigLoader.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `docs/plan/config-de-prueba.yml` (la sección `learning`, igual que en `config.yml`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/strategy/TraitLedgerTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/PlayerSnapshotBuilder.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/settings/SettingsValidationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` |
| Modificar | las demás pruebas que construyen `MobAiSettings` (solo el cambio mecánico; lista abajo) |
| Modificar | `docs/actualizar-paper.md` y `docs/arquitectura.md` |

**Cambio mecánico.** Antes de empezar corré `grep -rln "new MobAiSettings(\|new PlayerSnapshot(" src/`. Hoy da: `testsupport/TestSettings`, `adapter/config/ConfigLoaderTest`, `application/SettingsHolderTest`, `application/RecordOutcomeTest`, `application/RecruitMobTest`, `simulation/LearningSimulation`, `domain/settings/SettingsValidationTest`, `adapter/config/ConfigLoader`, `testsupport/PlayerSnapshotBuilder` y `adapter/snapshot/SnapshotFactory`. Si aparece otro archivo, frená y reportá. En las pruebas, `new MobAiSettings(…)` suma al final el `learning` de `TestSettings.defaults()` (o `defaults.learning()` si la prueba ya tiene un `defaults`).

## Especificación

### `PlayerSnapshot.java`

Componente nuevo **al final**: `boolean holdingRanged`, con `@param holdingRanged the main hand holds a bow or a crossbow` en el javadoc. Si el record no tiene javadoc, agregá solo esa línea en uno nuevo: `/** … @param holdingRanged … */`.

### `PlannerKind.java` y `LearningSettings.java`

```java
/** Who decides the plans (CT-30): the four fixed strategies or the learned recipes. */
public enum PlannerKind {
  STRATEGIES,
  RECIPES
}
```

```java
/** The recipe learning of CT-30: who plans, the model, the recipe ranges and the trait memory. */
public record LearningSettings(
    PlannerKind planner,
    double modelNoiseVariance,
    double priorVariance,
    double priorSuccess,
    double explorationScale,
    double trainingExplorationScale,
    long minReserveDelayTicks,
    long maxReserveDelayTicks,
    double maxRetreatHealthFraction,
    long traitsHalfLifeTicks) {
  public LearningSettings { … }
}
```

Validaciones, con `SettingsChecks` y estos nombres de campo (el texto lo da `SettingsChecks`):
- `Objects.requireNonNull(planner, "LearningSettings.planner")`;
- `requirePositive` para `LearningSettings.modelNoiseVariance`, `priorVariance`, `explorationScale` y `trainingExplorationScale`;
- `requireBetween(new NamedSetting("LearningSettings.priorSuccess", priorSuccess), 0, 1)`;
- `requireAtLeast("LearningSettings.minReserveDelayTicks", minReserveDelayTicks, 1)`;
- `requireNotAbove(new NamedSetting("LearningSettings.minReserveDelayTicks", …), new NamedSetting("LearningSettings.maxReserveDelayTicks", …))`. Si además son iguales, `IllegalArgumentException("LearningSettings.maxReserveDelayTicks must exceed LearningSettings.minReserveDelayTicks, got " + max + " <= " + min)`, en un método privado;
- `requireBetween(new NamedSetting("LearningSettings.maxRetreatHealthFraction", …), 0, 1)`. Además, 0 o 1 exactos lanzan `IllegalArgumentException("LearningSettings.maxRetreatHealthFraction must be strictly between 0 and 1, got " + v)`, en un método privado;
- `requireAtLeast("LearningSettings.traitsHalfLifeTicks", traitsHalfLifeTicks, 1)`.

Si alguno de esos métodos de `SettingsChecks` tiene otra firma, usá la real y avisalo.

### `MobAiSettings.java`

Componente nuevo **al final**: `LearningSettings learning`, con `requireNonNull(learning, "MobAiSettings.learning")`.

### `config.yml` y `config-de-prueba.yml`

Sección nueva **al final**:

```yaml
learning:
  # Quién decide los planes (CT-30): "STRATEGIES" (las 4 estrategias de siempre) o "RECIPES" (recetas aprendidas).
  planner: "STRATEGIES"
  # Modelo de recetas, calibrado con la simulación (docs/plan/calibracion-recetas.md).
  model-noise-variance: 0.01
  prior-variance: 1.0
  prior-success: 0.5
  # Exploración del sorteo: en el juego y en el modo entrenamiento.
  exploration-scale: 1.0
  training-exploration-scale: 2.0
  # Cuándo entra la reserva de zombies (ticks) y umbral máximo de retirada que se prueba.
  min-reserve-delay-ticks: 20
  max-reserve-delay-ticks: 400
  max-retreat-health-fraction: 0.6
  # Vida media de lo que se recuerda de cada jugador (escudo, arco, armadura).
  traits-half-life-ticks: 6000
```

### `ConfigLoader.java`

Método privado nuevo `learning(ConfigurationSection root)`, con `choice(root, "learning.planner", PlannerKind.class)`, `number` para los `double` y `wholeNumber` para los `long`, en el orden del record. Se suma al final del `new MobAiSettings(…)`.

### `TraitSums.java`

```java
/** The time-decayed sums behind one player's traits (CT-30). */
public record TraitSums(double shield, double ranged, double armor, double weight, long lastTick) {
  public TraitSums { … }

  PlayerTraits traits() { … }
}
```

- Validación: sumas y peso finitos y `>= 0`; `weight > 0`; `lastTick >= 0`. Mensaje: `"TraitSums values must be finite and zero or positive, got " + …` (los cinco separados por `/`).
- `traits()`: `new PlayerTraits(shield / weight, ranged / weight, armor / weight)`.

### `TraitLedger.java`

```java
/** What each player tends to do, as a time-decayed average of what the groups saw (CT-30). */
public final class TraitLedger {
  private static final PlayerTraits UNKNOWN = new PlayerTraits(0, 0, 0);
  private final Supplier<LearningSettings> settings;
  private final Map<PlayerId, TraitSums> sums = new HashMap<>();

  public TraitLedger(Supplier<LearningSettings> settings) { … }

  public void observe(GroupSnapshot snapshot) { … }
  public PlayerTraits traitsOf(PlayerId player) { … }
  public Map<PlayerId, TraitSums> capture() { … copia inmodificable … }
  public void restore(Map<PlayerId, TraitSums> captured) { … reemplaza todo … }
}
```

`observe`, por cada jugador de la foto con `health() > 0`, en una función privada de una tarea:
1. si hay sumas con `lastTick == snapshot.tick()`, no hace nada;
2. `keep` = sin sumas, 0; si no, `Math.pow(0.5, Math.max(0, tick − lastTick) / (double) traitsHalfLifeTicks)`;
3. sumas nuevas = vieja·keep + observación (escudo, a distancia, armadura de la decisión 4); peso nuevo = peso·keep + 1; `lastTick` = tick.

`traitsOf`: las sumas del jugador → `traits()`; si no hay, `UNKNOWN`.

### `VersionTranslator.java` y `SnapshotFactory.java`

`VersionTranslator`, método público nuevo:

```java
public boolean holdsRangedWeapon(Player player) {
  Material held = player.getInventory().getItemInMainHand().getType();
  return held == Material.BOW || held == Material.CROSSBOW;
}
```

(con `if`/`==`: nada de `switch` sobre constantes de Paper). `SnapshotFactory.playerSnapshot` pasa `translator.holdsRangedWeapon(player)` como último argumento.

### `PlayerSnapshotBuilder.java`

Campo `holdingRanged` (falso por defecto) y método `withHoldingRanged(boolean holdingRanged)`, como los demás.

### Documentos

- `docs/actualizar-paper.md`: en la fila de `VersionTranslator`, sumá `Material.BOW`, `Material.CROSSBOW` y `PlayerInventory.getItemInMainHand` (`holdsRangedWeapon`); en la de `SnapshotFactory`, nada nuevo de Paper.
- `docs/arquitectura.md`, fila nueva al final de la tabla «Nombres en el código»:

| Rasgos medidos de cada jugador, configuración del aprendizaje y conmutador de planificador (CT-30) | `TraitLedger`, `TraitSums`, `LearningSettings`, `PlannerKind`, `PlayerSnapshot.holdingRanged` | Dominio |

## Pruebas obligatorias

### `TraitLedgerTest` (8)

Configuración: `TestSettings.defaults().learning()` (vida media 6000) salvo que se diga otra. Jugadores con `PlayerSnapshotBuilder`; cada foto con un solo jugador salvo que se diga otra. Tolerancia `1e-9`.

| Prueba | Verifica |
| --- | --- |
| `anUnknownPlayerHasNoTraits` | `traitsOf` de un jugador nunca visto → `(0, 0, 0)` |
| `theFirstObservationIsTheTraits` | tick 1000, bloqueando, sin arco, armadura 10 → `(1, 0, 0.5)` |
| `olderObservationsWeighLess` | tick 1000: bloqueando, armadura 20; tick 7000: sin bloquear, armadura 0 → escudo 1/3 y armadura 1/3 |
| `theSameTickIsCountedOnce` | dos fotos del tick 1000, la primera bloqueando y la segunda no → escudo 1 |
| `deadPlayersAreNotObserved` | tick 1000, vida 0, bloqueando → `(0, 0, 0)` |
| `armorIsCappedAtOne` | armadura 30 → armadura 1 |
| `theHalfLifeComesFromTheSettings` | vida media 3000: tick 1000 bloqueando, tick 7000 sin bloquear → escudo 0,2 |
| `captureAndRestoreKeepTheTraits` | después de `theFirstObservation…`, otro ledger con `restore(capture())` da `(1, 0, 0.5)`; cambiar la copia no cambia el original |

### `SettingsValidationTest` (+10 casos)

Un caso por campo de `LearningSettings`, sobre `TestSettings.defaults().learning()` con un solo valor cambiado: varianza del ruido 0, varianza del punto de partida 0, éxito de partida 1,5, exploración 0, exploración de entrenamiento 0, demora mínima 0, demora máxima igual a la mínima (20), umbral máximo 1,0, umbral máximo 1,5 (lo rechaza `requireBetween`) y vida media 0. Cada uno con el mensaje que da su validación.

### `ConfigLoaderTest`

El `MobAiSettings` esperado suma `new LearningSettings(PlannerKind.STRATEGIES, 0.01, 1.0, 0.5, 1.0, 2.0, 20, 400, 0.6, 6000)`.

Total: **8 pruebas** y **10 casos de validación**. `holdsRangedWeapon` usa Paper y se verifica con el WP-33E, en el log de debug.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Sin ignorar el mismo tick | escudo 0,5 | `theSameTickIsCountedOnce` |
| 2 | `keep` siempre 1 (sin olvido) | escudo 0,5 | `olderObservationsWeighLess` |
| 3 | Armadura sin el `min(1, …)` | 1,5, rechazado por `PlayerTraits` | `armorIsCappedAtOne` |
| 4 | Vida media fija en 6000 | escudo 1/3 | `theHalfLifeComesFromTheSettings` |
| 5 | Observar también a los muertos | escudo 1 | `deadPlayersAreNotObserved` |

## Verificación en el juego (Nico, después del merge)

**Antes de levantar el server:** sumá la sección `learning` (copiala de `docs/plan/config-de-prueba.yml`) al `config.yml` del server de prueba. Si falta, el plugin se deshabilita.

Con `planner: "STRATEGIES"`, el juego tiene que seguir **exactamente igual** que antes. Los rasgos se ven a partir del WP-33E.

## Procedimiento

1. Rama `wp-33d-rasgos-y-configuracion` desde `origin/main` actualizado (con el WP-33C).
2. `PlannerKind`, `LearningSettings`, `MobAiSettings`, `ConfigLoader`, `config.yml`, `config-de-prueba.yml` y las pruebas de configuración (con el cambio mecánico). Commit: `feat: learning settings and planner switch (CT-30)`.
3. `PlayerSnapshot.holdingRanged`, `PlayerSnapshotBuilder`, `VersionTranslator.holdsRangedWeapon` y `SnapshotFactory`. Commit: `feat: snapshots see ranged weapons in hand`.
4. `TraitSums`, `TraitLedger` y su prueba. Commit: `feat: time-decayed player traits ledger`.
5. Documentos. Commit: `docs: map and names for player traits and learning settings`.
6. Pruebas que muerden, de a una y sin commit.
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-33D: player traits and learning settings`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- `TraitLedger`, `TraitSums`, `LearningSettings` y sus pruebas no usan Paper: compilan y se prueban con el JDK 21 del contenedor. Bajá `junit-platform-console-standalone` 1.11.4, `assertj-core` 3.26.3, `byte-buddy` 1.15.10 y, para las pruebas parametrizadas, `junit-jupiter-params` 5.11.4 si el launcher no lo trae (de Maven Central; si da 429, de `https://repo.maven.apache.org/maven2/`). Compilá `src/main/java/io/github/nicodoou/mobai/domain`, lo necesario de `testsupport` y las pruebas de `domain/`, con los jars **explícitos** en `-cp`.
- `ConfigLoader`, `ConfigLoaderTest`, `VersionTranslator` y `SnapshotFactory` usan Paper: los verifica el CI del PR. Esperá a que termine; si falla, leé el log, corregí y volvé a empujar.
- Formateá con google-java-format 1.36.1 (bajalo a una carpeta fuera del repo).

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si `VersionTranslator` pasa los 20 métodos públicos, frená y reportá (hoy tiene 17).

## Fuera de alcance

- Que el cerebro observe y use los rasgos, y que las recetas decidan (WP-33E).
- Guardar los rasgos en disco (WP-33F).
- Mostrar los rasgos en `/mobai memory` o en el log de debug (WP-33E).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 8 pruebas y los 10 casos de validación, en verde; la suite completa en verde.
- [ ] Las 5 roturas mordieron.
- [ ] Con `planner: "STRATEGIES"`, ninguna prueba existente cambió su resultado.
- [ ] Build, cobertura y CI en verde.
