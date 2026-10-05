# WP-07 — Selección de objetivo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E3 Dominio: grupo y cerebro |
| Depende de | WP-06 |
| Modelo | Sonnet |
| Rama | `wp-07-seleccion-de-objetivo` |

## Objetivo

Decidir a qué jugador ataca el grupo (RF-04) y a cuál muerde cada araña (RF-02), con fórmulas exactas y una explicación de cada elección para las trazas de depuración.

- `KillTimeEstimator`: cuántos segundos tardaría el grupo en matar a un jugador.
- `TargetSelector`: prioridad = amenaza ÷ tiempo para matarlo, con bonus de compromiso; elige el de mayor prioridad.
- `SpiderTargetRule`: cada araña sigue al jugador más cercano, con compromiso.

## Contexto a leer

1. `docs/plan/reglas-para-agentes.md` y este WP.
2. Código existente (solo leer):
   - `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`, `MobKind.java`, `EffectKind.java`, `PlayerId.java`, `Vec3.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/snapshot/PlayerSnapshot.java`, `MobSnapshot.java`, `GroupSnapshot.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/geometry/PlayerPose.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/threat/ThreatLedger.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/memory/GroupMemory.java` (solo las firmas públicas), `SuccessEstimate.java`, `AttackObservation.java`
   - `src/main/java/io/github/nicodoou/mobai/domain/settings/TargetSettings.java`, `AttackSettings.java`, `MemorySettings.java`
   - `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`, `PlayerSnapshotBuilder.java`, `MobSnapshotBuilder.java`, `GroupSnapshotBuilder.java`

## Reglas de negocio

### Tiempo para matarlo (`KillTimeEstimator`)

Todo en segundos. El tick de la foto (`snapshot.tick()`) es el tick de la consulta a la memoria.

1. **Vida efectiva** = `health + absorption`.
2. **Daño por segundo de cada mob** = tasa de éxito × daño reducido por golpe × golpes por segundo.
   - **Tasa de éxito:** `memory.kindEstimate(jugador, tipo del mob, tick).mean()`. Es la media, no un sorteo: el objetivo no se sortea (D6), y así la memoria influye sin hacer cambiar de objetivo al azar. Sin datos da 0,5.
   - **Daño por golpe** (normal): zombie 3, araña 2, flecha de esqueleto 4.
   - **Golpes por segundo** = 20 ÷ intervalo de ataque en ticks: cuerpo a cuerpo cada 20 ticks, esqueleto cada 40.
   - **Daño reducido** = daño × factor de armadura × factor de Protección × factor de resistencia, con las fórmulas de Minecraft:
     - Armadura: `1 − clamp(armadura − daño ÷ (2 + dureza ÷ 4), armadura × 0,2, 20) ÷ 25`.
     - Protección: `1 − min(protectionFactor, 20) ÷ 25`.
     - Resistencia: `max(0, 1 − 0,2 × nivel)`.
3. **Daño neto por segundo** = suma de los mobs − curación de Regeneración + daño de Veneno + daño de Wither. Cada uno de esos efectos cura o daña 1 punto cada `max(1, base >> (nivel − 1))` ticks, con base 50 (Regeneración), 25 (Veneno) y 40 (Wither); por segundo es `20 ÷ ese intervalo`. El corrimiento se limita a 30 posiciones: Java toma el corrimiento módulo 32, y con nivel 33 `25 >> 32` daría 25 en vez de 0.
4. **Tiempo de llegada** = promedio, sobre los mobs del grupo, de `max(0, distancia al jugador − distancia de enganche) ÷ velocidad de acercamiento`, multiplicado por `max(0, 1 − 0,15 × nivel de Lentitud)`.
   - Distancia de enganche: cuerpo a cuerpo, el alcance de 2 bloques; esqueleto, `AttackSettings.shootMaxDistanceBlocks` (15), porque dispara desde ahí.
   - Velocidad de acercamiento: `TargetSettings.approachSpeedBlocksPerSecond` (3).
   - Distancia en 3D entre `mob.position()` y `player.pose().position()`.
   - Sin mobs, la llegada es 0.
5. **Tiempo para matarlo** = llegada + vida efectiva ÷ daño neto. Si el daño neto es 0 o menos (la regeneración le gana al grupo, o el grupo no tiene mobs), es `Double.POSITIVE_INFINITY`.

D7 decía que la resistencia «sube la vida efectiva»; dividir la vida o reducir el daño da el mismo tiempo. Se reduce el daño porque la armadura depende del daño de cada golpe.

**Simplificaciones aceptadas:** el veneno se cuenta aunque en Minecraft no mata (deja 1 punto); Veneno y Wither no pasan por armadura ni resistencia; los daños por golpe son los de dificultad normal. El objetivo de esta cuenta es **ordenar** a los jugadores, no predecir el segundo exacto.

### Prioridad (`TargetSelector`)

1. **Candidatos:** los jugadores de la foto con `health > 0`, en el orden de la foto.
2. **Amenaza** = `max(baseThreat, ledger.threatOf(jugador, tick))` × `weaknessThreatMultiplierPerLevel ^ nivel de Debilidad`. Con los valores por defecto: piso 1 y ×0,5 por nivel.
3. **Prioridad** = amenaza ÷ tiempo para matarlo (0 si el tiempo es infinito).
4. **Compromiso** (RF-04.5, D5): si el jugador es el objetivo comprometido (el del plan anterior), su prioridad se multiplica por `1 + commitmentBonus` (1,2).
5. **Elección:** la mayor prioridad. Empate: el primero en el orden de la foto. Sin candidatos: ningún objetivo.

### Arañas (`SpiderTargetRule`)

1. Candidatos: jugadores con `health > 0`.
2. El más cercano a la araña (distancia en 3D). Empate: el primero en el orden de la lista.
3. Si la araña tiene un objetivo actual y sigue entre los candidatos, lo mantiene salvo que el más cercano esté claramente más cerca: cambia solo si `distancia del más cercano × (1 + commitmentBonus) < distancia del actual`.

## Archivos

Rutas relativas a `src/main/java/io/github/nicodoou/mobai/` y `src/test/java/io/github/nicodoou/mobai/`.

| Acción | Ruta |
| --- | --- |
| Modificar | `domain/shared/MinecraftConstants.java` (agregar constantes) |
| Crear | `domain/target/KillTimeEstimate.java`, `KillTimeEstimator.java` |
| Crear | `domain/target/TargetQuery.java`, `TargetScore.java`, `TargetSelection.java`, `TargetSelector.java` |
| Crear | `domain/target/SpiderTargetRule.java` |
| Crear (prueba) | `domain/target/KillTimeEstimatorTest.java`, `TargetSelectorTest.java`, `SpiderTargetRuleTest.java` |

## Especificación

### 1. `MinecraftConstants`

Agregar al final, antes del constructor privado, con una línea en blanco entre grupos y estos comentarios:

```java
  // Damage per hit on normal difficulty. Zombie measured in the spike; spider and arrow (1.6 speed x
  // 2.0 base damage, rounded up) are vanilla values.
  public static final double ZOMBIE_HIT_DAMAGE = 3.0;
  public static final double SPIDER_HIT_DAMAGE = 2.0;
  public static final double SKELETON_ARROW_DAMAGE = 4.0;

  // Our goals attack at these vanilla rates; the kill time estimate assumes the same rates.
  public static final int MELEE_ATTACK_INTERVAL_TICKS = 20;
  public static final int SKELETON_ATTACK_INTERVAL_TICKS = 40;
  public static final double MELEE_REACH_BLOCKS = 2.0;

  // Vanilla damage reduction: armor, armor toughness, Protection and Resistance.
  public static final double ARMOR_TOUGHNESS_BASE = 2.0;
  public static final double ARMOR_TOUGHNESS_DIVISOR = 4.0;
  public static final double ARMOR_MIN_FRACTION = 0.2;
  public static final double ARMOR_MAX_POINTS = 20.0;
  public static final int PROTECTION_MAX_FACTOR = 20;
  public static final double DAMAGE_REDUCTION_SCALE = 25.0;
  public static final double RESISTANCE_REDUCTION_PER_LEVEL = 0.2;

  // Vanilla periodic effects heal or hurt one point every (base >> (level - 1)) ticks.
  public static final int REGENERATION_BASE_INTERVAL_TICKS = 50;
  public static final int POISON_BASE_INTERVAL_TICKS = 25;
  public static final int WITHER_BASE_INTERVAL_TICKS = 40;
  public static final double SLOWNESS_SPEED_REDUCTION_PER_LEVEL = 0.15;
```

### 2. `domain.target.KillTimeEstimate`

```java
public record KillTimeEstimate(
    double effectiveHealth,
    double damagePerSecond,
    double arrivalSeconds,
    double killTimeSeconds) {}
```

`damagePerSecond` es el daño **neto** (puede ser 0 o negativo). `killTimeSeconds` puede ser `Double.POSITIVE_INFINITY`. Sin validación: es una salida del estimador, que ya garantiza los valores.

### 3. `domain.target.KillTimeEstimator`

```java
public final class KillTimeEstimator {
  public KillTimeEstimator(
      Supplier<TargetSettings> targetSettings, Supplier<AttackSettings> attackSettings) { ... }

  public KillTimeEstimate estimate(
      PlayerSnapshot player, GroupSnapshot snapshot, GroupMemory memory) { ... }
}
```

Constructor: `requireNonNull` con mensajes `"KillTimeEstimator.targetSettings"` y `"KillTimeEstimator.attackSettings"`. Los `Supplier` se leen en cada uso, nunca se guarda el record.

Constantes privadas: `private static final int MAX_INTERVAL_SHIFT = 30;` (con el comentario `// Java masks int shifts to five bits; capping keeps huge effect levels at a one-tick interval.`).

`estimate` coordina, en este orden:

```java
double effectiveHealth = player.health() + player.absorption();
double damagePerSecond = groupDamagePerSecond(player, snapshot, memory) + effectDamagePerSecond(player);
double arrivalSeconds = arrivalSeconds(player, snapshot.mobs());
double killTimeSeconds = killTime(effectiveHealth, damagePerSecond, arrivalSeconds);
return new KillTimeEstimate(effectiveHealth, damagePerSecond, arrivalSeconds, killTimeSeconds);
```

Métodos privados, uno por tarea (respetá nombres y fórmulas):

| Método | Devuelve |
| --- | --- |
| `double groupDamagePerSecond(PlayerSnapshot player, GroupSnapshot snapshot, GroupMemory memory)` | Suma de `mobDamagePerSecond` de cada mob de `snapshot.mobs()` (stream, `mapToDouble`, `sum`) |
| `double mobDamagePerSecond(MobSnapshot mob, PlayerSnapshot player, double successRate)` | `successRate * reducedHit(hitDamage(mob.kind()), player) * hitsPerSecond(mob.kind())`. La tasa la busca `groupDamagePerSecond` con `memory.kindEstimate(player.id(), mob.kind(), snapshot.tick()).mean()` |
| `static double hitDamage(MobKind kind)` | `switch` con flechas: `ZOMBIE -> ZOMBIE_HIT_DAMAGE`, `SKELETON -> SKELETON_ARROW_DAMAGE`, `SPIDER -> SPIDER_HIT_DAMAGE` |
| `static double hitsPerSecond(MobKind kind)` | `(double) TICKS_PER_SECOND / attackIntervalTicks(kind)` |
| `static int attackIntervalTicks(MobKind kind)` | `SKELETON -> SKELETON_ATTACK_INTERVAL_TICKS`; `ZOMBIE, SPIDER -> MELEE_ATTACK_INTERVAL_TICKS` |
| `static double reducedHit(double damage, PlayerSnapshot player)` | `damage * armorFactor(damage, player) * protectionFactor(player) * resistanceFactor(player)` |
| `static double armorFactor(double damage, PlayerSnapshot player)` | `double toughnessDivisor = ARMOR_TOUGHNESS_BASE + player.armorToughness() / ARMOR_TOUGHNESS_DIVISOR;` `double effectiveArmor = Math.clamp(player.armorPoints() - damage / toughnessDivisor, player.armorPoints() * ARMOR_MIN_FRACTION, ARMOR_MAX_POINTS);` devuelve `1 - effectiveArmor / DAMAGE_REDUCTION_SCALE` |
| `static double protectionFactor(PlayerSnapshot player)` | `1 - Math.min(player.protectionFactor(), PROTECTION_MAX_FACTOR) / DAMAGE_REDUCTION_SCALE` |
| `static double resistanceFactor(PlayerSnapshot player)` | `Math.max(0, 1 - RESISTANCE_REDUCTION_PER_LEVEL * player.effectLevel(EffectKind.RESISTANCE))` |
| `static double effectDamagePerSecond(PlayerSnapshot player)` | `pointsPerSecond(POISON_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.POISON)) + pointsPerSecond(WITHER_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.WITHER)) - pointsPerSecond(REGENERATION_BASE_INTERVAL_TICKS, player.effectLevel(EffectKind.REGENERATION))` |
| `static double pointsPerSecond(int baseIntervalTicks, int level)` | Si `level == 0`, `0`. Si no: `int interval = Math.max(1, baseIntervalTicks >> Math.min(level - 1, MAX_INTERVAL_SHIFT));` devuelve `(double) TICKS_PER_SECOND / interval` |
| `double arrivalSeconds(PlayerSnapshot player, List<MobSnapshot> mobs)` | Si `mobs` está vacía, `0`. Si no: promedio (`average().orElse(0)`) de `mobArrivalSeconds(mob, player)` por `slownessFactor(player)` |
| `double mobArrivalSeconds(MobSnapshot mob, PlayerSnapshot player)` | `Math.max(0, mob.position().distanceTo(player.pose().position()) - engageDistance(mob.kind())) / targetSettings.get().approachSpeedBlocksPerSecond()` |
| `double engageDistance(MobKind kind)` | `SKELETON -> attackSettings.get().shootMaxDistanceBlocks()`; `ZOMBIE, SPIDER -> MELEE_REACH_BLOCKS` |
| `static double slownessFactor(PlayerSnapshot player)` | `Math.max(0, 1 - SLOWNESS_SPEED_REDUCTION_PER_LEVEL * player.effectLevel(EffectKind.SLOWNESS))` |
| `static double killTime(double effectiveHealth, double damagePerSecond, double arrivalSeconds)` | Si `damagePerSecond <= 0`, `Double.POSITIVE_INFINITY`; si no, `arrivalSeconds + effectiveHealth / damagePerSecond` |

Los nombres de constantes de la tabla son de `MinecraftConstants` (importalas con `import static` o con el nombre de la clase, como prefieras, pero igual en todo el archivo).

### 4. `TargetQuery`, `TargetScore` y `TargetSelection`

```java
/** Everything the selector reads for one group decision. */
public record TargetQuery(
    GroupSnapshot snapshot,
    GroupMemory memory,
    ThreatLedger threat,
    Optional<PlayerId> committedTarget) { ... }   // requireNonNull de los 4: "TargetQuery.<componente>"

/** Why a player got its priority; goes into the decision trace. */
public record TargetScore(
    PlayerId player,
    double rawThreat,
    double threat,
    KillTimeEstimate killTime,
    boolean committed,
    double priority) { ... }   // requireNonNull de player y killTime: "TargetScore.<componente>"

public record TargetSelection(Optional<PlayerId> target, List<TargetScore> scores) {
  // requireNonNull de los 2: "TargetSelection.<componente>"; scores = List.copyOf(scores)
}
```

### 5. `domain.target.TargetSelector`

```java
public final class TargetSelector {
  public TargetSelector(Supplier<TargetSettings> settings, KillTimeEstimator killTimeEstimator) { ... }

  public TargetSelection select(TargetQuery query) { ... }
}
```

Constructor: `requireNonNull` con `"TargetSelector.settings"` y `"TargetSelector.killTimeEstimator"`.

`select` coordina:

```java
List<TargetScore> scores =
    query.snapshot().players().stream()
        .filter(player -> player.health() > 0)
        .map(player -> score(player, query))
        .toList();
return new TargetSelection(best(scores), scores);
```

Métodos privados:

| Método | Cuerpo |
| --- | --- |
| `TargetScore score(PlayerSnapshot player, TargetQuery query)` | `double rawThreat = query.threat().threatOf(player.id(), query.snapshot().tick());` `double threat = threat(rawThreat, player);` `KillTimeEstimate killTime = killTimeEstimator.estimate(player, query.snapshot(), query.memory());` `boolean committed = query.committedTarget().filter(player.id()::equals).isPresent();` devuelve `new TargetScore(player.id(), rawThreat, threat, killTime, committed, priority(threat, killTime, committed))` |
| `double threat(double rawThreat, PlayerSnapshot player)` | `TargetSettings target = settings.get();` devuelve `Math.max(target.baseThreat(), rawThreat) * Math.pow(target.weaknessThreatMultiplierPerLevel(), player.effectLevel(EffectKind.WEAKNESS))` |
| `double priority(double threat, KillTimeEstimate killTime, boolean committed)` | `double base = threat / killTime.killTimeSeconds();` (con tiempo infinito da 0) devuelve `committed ? base * (1 + settings.get().commitmentBonus()) : base` |
| `static Optional<PlayerId> best(List<TargetScore> scores)` | Recorre con un `for` y se queda con el primero de mayor prioridad (comparación estricta `>`, así el empate queda con el primero). Lista vacía: `Optional.empty()` |

### 6. `domain.target.SpiderTargetRule`

```java
public final class SpiderTargetRule {
  public SpiderTargetRule(Supplier<TargetSettings> settings) { ... }   // "SpiderTargetRule.settings"

  public Optional<PlayerId> choose(
      MobSnapshot spider, List<PlayerSnapshot> players, Optional<PlayerId> currentTarget) { ... }
}
```

`choose` coordina:
1. `List<PlayerSnapshot> alive = players.stream().filter(player -> player.health() > 0).toList();`
2. `Optional<PlayerSnapshot> nearest = nearest(spider, alive);` si está vacío, `Optional.empty()`.
3. `Optional<PlayerSnapshot> current = currentTarget.flatMap(id -> find(alive, id));` si está vacío, devuelve el ID del más cercano.
4. Devuelve `shouldSwitch(spider, nearest.get(), current.get()) ? nearest.get().id() : current.get().id()`.

Métodos privados:

| Método | Cuerpo |
| --- | --- |
| `static Optional<PlayerSnapshot> nearest(MobSnapshot spider, List<PlayerSnapshot> players)` | `players.stream().min(Comparator.comparingDouble(player -> distance(spider, player)))` (`Stream.min` devuelve el primero ante un empate) |
| `static Optional<PlayerSnapshot> find(List<PlayerSnapshot> players, PlayerId id)` | `stream().filter(...).findFirst()` |
| `boolean shouldSwitch(MobSnapshot spider, PlayerSnapshot nearest, PlayerSnapshot current)` | `distance(spider, nearest) * (1 + settings.get().commitmentBonus()) < distance(spider, current)` |
| `static double distance(MobSnapshot spider, PlayerSnapshot player)` | `spider.position().distanceTo(player.pose().position())` |

## Pruebas obligatorias

Tolerancia `within(1e-9)`. Settings: `TestSettings.defaults()` (aprendizaje 0,7, piso de amenaza 1, debilidad ×0,5, compromiso 0,2, acercamiento 3, tiro máximo 15). En todas: `memory = new GroupMemory(() -> TestSettings.defaults().memory())`, el jugador por defecto de `PlayerSnapshotBuilder` (en `(0, 64, 0)`, vida 20) y el tick de la foto, 1000 (el del builder).

Mobs explícitos con `MobSnapshotBuilder`: «zombie a z» es `withId(new MobId(new UUID(1, n)))`, `withPosition(new Vec3(0, 64, z))`, con `n` distinto para cada mob de la prueba; «esqueleto a z», igual con `withKind(SKELETON)`.

**`KillTimeEstimatorTest`**

| Prueba | Caso | Resultado esperado |
| --- | --- | --- |
| `unarmoredPlayerAgainstOneZombieInReach` | Zombie a 2 | Vida efectiva 20, daño 1,5, llegada 0, tiempo 13,333333333333334 |
| `fullDiamondProtectionFourCutsZombieDamage` | Zombie a 2, jugador `fullDiamondProtectionFour()` | Daño 0,1242 (0,5 × 3 × 0,23 × 0,36), tiempo 161,0305958132045 |
| `armorToughnessReducesDamageFurther` | Zombie a 2, armadura 20 y dureza 0 | Tiempo 51,28205128205128 (factor 0,26, mayor que el 0,23 con dureza 8) |
| `absorptionAddsToEffectiveHealth` | Zombie a 2, absorción 4 | Vida efectiva 24, tiempo 16 |
| `resistanceReducesDamage` | Zombie a 2, Resistencia 2 | Tiempo 22,222222222222225 |
| `regenerationLowersNetDamage` | Zombie a 2, Regeneración 1 | Daño 1,1, tiempo 18,18181818181818 |
| `poisonAndWitherRaiseNetDamage` | Zombie a 2, Veneno 1 y Wither 2 | Daño 3,3, tiempo 6,0606060606060606 |
| `regenerationStrongerThanTheGroupMakesTheTargetUnkillable` | Zombie a 2, Regeneración 3 | Daño negativo, tiempo `POSITIVE_INFINITY` |
| `veryHighEffectLevelsUseAOneTickInterval` | Zombie a 2, Veneno 33 | Daño 21,5, tiempo 0,9302325581395349 |
| `arrivalUsesDistanceBeyondReachAndApproachSpeed` | Zombie a 14 y esqueleto a 20 | Llegada 2,8333333333333335 (promedio de 4 y 1,6667), daño 2,5 (1,5 + 1,0), tiempo 10,833333333333334 |
| `slownessShortensArrival` | Zombie a 14, Lentitud 2 | Llegada 2,8, tiempo 16,133333333333333 |
| `memoryRaisesTheExpectedDamage` | Zombie a 2. Antes: 10 veces `memory.recordAttack(new AttackObservation(jugador, Attack.ZOMBIE_FRONT_STRIKE, 1.0, 900))` | Daño 3 × 0,6887148217049778, tiempo 9,679865245476657 |
| `groupWithoutMobsCannotKill` | Sin mobs | Llegada 0, daño 0, tiempo `POSITIVE_INFINITY` |

**`TargetSelectorTest`.** `ledger = new ThreatLedger(() -> TestSettings.defaults().target())`; los daños se registran en el tick 900. `ALICE` y `BOB` son jugadores con `new PlayerId(new UUID(0, 10))` y `new PlayerId(new UUID(0, 11))`, los dos en `(0, 64, 0)` salvo que se diga otra cosa, y el grupo tiene un zombie a 2 (tiempo para matar a un jugador sin armadura: 13,333333333333334).

| Prueba | Caso | Resultado esperado |
| --- | --- | --- |
| `noPlayersMeansNoTarget` | Foto sin jugadores | Objetivo vacío, `scores` vacía |
| `deadPlayersAreNotCandidates` | ALICE con vida 0 | Objetivo vacío, `scores` vacía |
| `threatHasAFloorOfBaseThreat` | ALICE sin daño | Amenaza cruda 0, amenaza 1, prioridad 0,075 |
| `higherThreatWins` | ALICE 10 de daño, BOB ninguno | Objetivo ALICE, prioridad 0,75 |
| `easierKillWinsWhenThreatIsEqual` | ALICE `fullDiamondProtectionFour()`, BOB sin armadura, ninguno pegó | Objetivo BOB; prioridad de ALICE 0,00621 |
| `weaknessHalvesThreatPerLevel` | ALICE 8 de daño y Debilidad 2 | Amenaza 2 |
| `commitmentKeepsTheTargetAgainstASmallDifference` | ALICE 10, BOB 11, comprometido ALICE | Objetivo ALICE; prioridad de ALICE 0,9, de BOB 0,825; `committed` de ALICE `true` y de BOB `false` |
| `commitmentYieldsToAClearlyBetterTarget` | ALICE 10, BOB 13, comprometido ALICE | Objetivo BOB |
| `tiesGoToTheFirstPlayerInTheSnapshot` | BOB y después ALICE en la foto, ninguno pegó | Objetivo BOB |
| `unkillableTargetHasZeroPriority` | ALICE con Regeneración 3 y 10 de daño, BOB sin daño | Prioridad de ALICE 0, objetivo BOB |
| `scoresExplainEveryCandidateInSnapshotOrder` | BOB (vida 0), ALICE, y un tercer jugador `new PlayerId(new UUID(0, 12))` | `scores` tiene 2 entradas, ALICE y el tercero en ese orden, cada una con su `killTime` |

**`SpiderTargetRuleTest`.** La araña está en `(0, 64, 0)`. Los jugadores, en `(0, 64, z)`.

| Prueba | Caso | Resultado esperado |
| --- | --- | --- |
| `noPlayersMeansNoTarget` | Lista vacía | Vacío |
| `picksTheNearestPlayer` | ALICE a 6, BOB a 4, sin objetivo actual | BOB |
| `keepsTheCurrentTargetWithinTheCommitmentMargin` | Actual ALICE a 5, BOB a 4,5 | ALICE (4,5 × 1,2 = 5,4, no es menor que 5) |
| `switchesWhenAnotherPlayerIsClearlyCloser` | Actual ALICE a 5, BOB a 4 | BOB (4 × 1,2 = 4,8 < 5) |
| `missingCurrentTargetFallsBackToTheNearest` | Actual `new PlayerId(new UUID(9, 9))`, ALICE a 6, BOB a 4 | BOB |
| `ignoresDeadPlayers` | ALICE a 2 con vida 0, BOB a 4 | BOB |
| `tiesGoToTheFirstPlayer` | ALICE y BOB a 3, en ese orden | ALICE |

### Pruebas que muerden (obligatorio, va en el informe)

| Cambio temporal | Tiene que fallar |
| --- | --- |
| En `pointsPerSecond`, quitar el `Math.min(level - 1, MAX_INTERVAL_SHIFT)` y usar `level - 1` | `veryHighEffectLevelsUseAOneTickInterval` |
| En `armorFactor`, usar `ARMOR_TOUGHNESS_BASE` como divisor (sin la dureza) | `fullDiamondProtectionFourCutsZombieDamage` |
| En `threat`, aplicar el piso después de la debilidad (`Math.max(baseThreat, rawThreat * multiplicador)`) | `weaknessAppliesToTheThreatFloorToo` (agregada por el implementador: con 8 de daño y Debilidad 2 las dos fórmulas dan 2; con 2 de daño, 0,5 contra 1) |
| En `best`, `>=` en vez de `>` | `tiesGoToTheFirstPlayerInTheSnapshot` |
| En `shouldSwitch`, quitar el `(1 + commitmentBonus)` | `keepsTheCurrentTargetWithinTheCommitmentMargin` |

## Procedimiento

1. Rama `wp-07-seleccion-de-objetivo` desde `main`.
2. `MinecraftConstants`, `KillTimeEstimate`, `KillTimeEstimator` y `KillTimeEstimatorTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add kill time estimator with vanilla damage reduction`.
3. `TargetQuery`, `TargetScore`, `TargetSelection`, `TargetSelector` y `TargetSelectorTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add target selector with threat, kill time and commitment`.
4. `SpiderTargetRule` y `SpiderTargetRuleTest`. `./gradlew spotlessApply build`. Commit: `feat(domain): add nearest-player rule for spiders`.
5. Pruebas que muerden.
6. `./gradlew jacocoTestReport jacocoTestCoverageVerification` tiene que pasar.
7. Push, PR `WP-07: target selection`, esperar el check `build` en verde antes del informe.

## Correcciones permitidas sin preguntar

1. Si `spotlessCheck` falla, `./gradlew spotlessApply`.
2. Si un valor esperado difiere solo en la última cifra decimal, la tolerancia `within(1e-9)` lo cubre; no cambies los valores esperados. Si difiere en más de 1e-9, frená y reportá.

Cualquier otra cosa: frená y reportá.

## Fuera de alcance

- Guardar el objetivo comprometido y el de cada araña (`Group` y `Member`, WP-08).
- Llamar al selector en cada decisión y armar la `DecisionTrace` (WP-10).
- «Foco o reparto» entre objetivos (fase 2, D6).
- Usar estas constantes de daño e intervalo en los goals (WP-19 y WP-24 tienen que usar las mismas).

## Aceptación

- [ ] Existen exactamente los archivos de la tabla «Archivos».
- [ ] Firmas, nombres, fórmulas y mensajes idénticos a los del WP.
- [ ] Ninguna función hace más de una tarea; ningún bucle sin límite.
- [ ] Todas las pruebas obligatorias pasan con su nombre exacto.
- [ ] Las 5 pruebas que muerden fallaron con su cambio temporal y el código quedó revertido.
- [ ] Cobertura del dominio ≥ 80 %.
- [ ] 3 commits con los mensajes indicados.
- [ ] PR abierto con el check `build` en verde, verificado antes del informe.
