# WP-24C — Esqueletos en formación

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24B y el arreglo B-03 (mergeados) |
| Modelo | Sonnet |
| Rama | `wp-24c-esqueletos-en-formacion` |

## Objetivo

CT-21, pedido de Nico después de probar el WP-24B:

1. **Más lejos:** los esqueletos pelean entre 20 y 30 bloques del jugador y se ubican a 25, en el medio. Así tienen tiempo de reaccionar si el jugador se acerca.
2. **Repartidos:** los esqueletos que apuntan al mismo jugador se reparten parejo alrededor de él: 2 quedan opuestos, 3 a 120°, 4 a 90°.
3. **Con arco:** el grupo de prueba les da un arco. Hoy parecen desarmados aunque disparen.
4. **Sin fuego amigo:** no disparan si un aliado está en la línea de tiro. Para tener la línea libre, se corren de a 30° alrededor del jugador. En la prueba del WP-24B le pegaban todo el tiempo a los zombies que atacaban.

La búsqueda de altura (CT-22) es el WP-24D y la andanada (CT-23), un WP propio.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java`, `BowShooter.java`, `Waypoints.java`, `RoleRegistry.java`, `RangeSituation.java`, `RangeMove.java`, `GoalOrders.java`, `FlankGoal.java` (cómo arma las posiciones de los flanqueadores)
- `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/command/GroupSpawner.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java`, `FlankFormation.java`, `FlankQuery.java`
- `src/main/resources/config.yml` (sección `attack`)
- Pruebas: `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java`, `FlankFormationTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java`, `RangeSituationTest.java`, `RoleRegistryTest.java`, `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/TestSettings.java`

## Reglas de negocio

1. **Distancia:** `attack.shoot-min-distance-blocks` pasa a 20 y `shoot-max-distance-blocks` a 30 en el `config.yml` incluido. El **radio de la formación** es el punto medio (`(min + max) / 2`, 25 por defecto). No hay una clave nueva.
2. **Formación de tiradores** (`ShooterFormation`, dominio), para un objetivo y sus tiradores (mobs con orden `SHOOT` y ese objetivo, cargados en su mundo, más el que pregunta):
   - el **ancla** es el tirador con el `MobId` más bajo: su puesto queda en la dirección en la que ya está respecto del jugador (rumbo horizontal: 0° hacia +Z, 90° hacia +X);
   - los demás siguen, en el orden en que ya están alrededor del jugador, girando desde el ancla; a igual rumbo, por `MobId`. Así nadie cruza por delante de otro;
   - el puesto `k` (el ancla es 0) está a `rumbo del ancla + k · 360° / n`, a la distancia del radio, a la altura del jugador.
3. **Línea de tiro limpia:** la línea va del ojo del tirador hasta **medio bloque antes** del centro del cuerpo del jugador (`ARROW_STOP_SHORT_BLOCKS`, 0,5: ahí la flecha ya chocó con su cuerpo). Está limpia si **ningún aliado** está a menos de `LINE_OF_FIRE_CLEARANCE_BLOCKS` (0,75) de ese segmento. Corrección de la revisión: con el segmento hasta el centro y 1 bloque de margen, un zombie pegado al jugador al costado o detrás bloqueaba todas las líneas, y los esqueletos no disparaban nunca. Se mide desde el centro del cuerpo del aliado. Aliados son todos los mobs con alguna orden contra ese mismo jugador (`RoleRegistry.mobsTargeting`), menos el tirador.
4. **Carril libre:** si desde su puesto la línea no está limpia, el tirador prueba el puesto girado alrededor del jugador 30°, −30°, 60°, −60°, 90° y −90°, en ese orden, a la misma distancia y altura. Va al primero con la línea limpia. Si no hay ninguno, va a su puesto igual y no dispara.
5. **Movimiento** (cada 10 ticks del reloj):
   - si no ve al jugador, se acerca a él, como hasta ahora;
   - si lo ve, camina a su puesto (o a su carril libre). Si ya está a 2 bloques o menos en horizontal (`SLOT_TOLERANCE_BLOCKS`), se queda quieto.
   - Esto reemplaza el acercarse, retroceder y quedarse del WP-24B: el puesto, a 25 bloques, ya cumple las dos cosas.
6. **Cuándo dispara:** le toca por ritmo, ve al jugador, está a 30 bloques o menos y **la línea está limpia**. Si no, no dispara, y la espera oportuna se reinicia como antes.
7. **Arco:** `spawngroup` les pone a los esqueletos un arco en la mano principal, con probabilidad de soltarlo en 0. Los zombies y las arañas siguen sin equipo.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/ShooterQuery.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/geometry/ShooterFormation.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/Waypoints.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/BowShooter.java` (usa `PoseReader.bodyCenterOf`) |
| Borrar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RangeSituation.java`, `RangeMove.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/snapshot/PoseReader.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/command/GroupSpawner.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Modificar | `src/main/resources/config.yml` |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/geometry/ShooterFormationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/domain/geometry/CombatGeometryTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/WaypointsTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistryTest.java` |
| Borrar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/RangeSituationTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/config/ConfigLoaderTest.java` (solo `8.0, 15.0` → `20.0, 30.0` en el valor esperado) |

`TestSettings` **no cambia** (sigue con 8 y 15): las pruebas usan sus propios valores.

Antes de empezar, buscá con grep en `src/` `RangeSituation`, `RangeMove`, `backOffPoint` y `8.0, 15.0`: si aparecen en un archivo que no está en la tabla, frená y reportá. Buscá también que los nombres de las pruebas nuevas no existan ya en esos archivos (regla del WP-24A).

**Métodos públicos:**
- `CombatGeometry.isLineOfFireClear` y `clearLane` son nuevos (10 en total).
- `Waypoints` cambia `backOffPoint` por `shooterSlot`, `clearLane` e `isLineOfFireClear` (8 en total).
- `RoleRegistry.mobsTargeting` es nuevo (8).
- `PoseReader.bodyCenterOf` es nuevo (3).
- `VersionTranslator.armWithBow` es nuevo (12): `Material.BOW` es una constante de Paper.

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `EntityEquipment.setItemInMainHand(ItemStack)` y `setItemInMainHandDropChance(float)`, `new ItemStack(Material)`, `LivingEntity.getEyeHeight()`.

### `ShooterQuery.java` y `ShooterFormation.java` (dominio, `geometry`)

```java
/** One shooter asking for its place, given every shooter of the same target. */
public record ShooterQuery(
    Vec3 center, MobId self, Map<MobId, Vec3> shooters, double radiusBlocks) {
  // Igual que FlankQuery: requireNonNull de center, self y shooters ("ShooterQuery.<campo>");
  // shooters = Map.copyOf(shooters);
  // si no contiene a self: IllegalArgumentException
  //   "ShooterQuery.self must be one of the shooters, got " + self.value()
  // radio no positivo o no finito: IllegalArgumentException
  //   "ShooterQuery.radiusBlocks must be a positive number, got " + radiusBlocks
}
```

```java
/** Spreads the shooters of one target evenly around it, as far apart as their number allows. */
public final class ShooterFormation {
  private static final double FULL_TURN_DEGREES = 360.0;

  public Vec3 pointFor(ShooterQuery query) {
    List<MobId> ring = aroundTheTarget(query);
    double anchorBearing = bearingOf(query.center(), query.shooters().get(ring.get(0)));
    double bearing = anchorBearing + ring.indexOf(query.self()) * FULL_TURN_DEGREES / ring.size();
    return query.center().plus(directionOf(bearing).times(query.radiusBlocks()));
  }

  // The lowest id anchors the ring where it stands; the rest follow round in the order they
  // already are, so nobody crosses in front of another.
  private static List<MobId> aroundTheTarget(ShooterQuery query) {
    MobId anchor =
        query.shooters().keySet().stream().min(Comparator.comparing(MobId::value)).orElseThrow();
    double anchorBearing = bearingOf(query.center(), query.shooters().get(anchor));
    return query.shooters().entrySet().stream()
        .sorted(
            Comparator.comparingDouble(
                    (Map.Entry<MobId, Vec3> shooter) ->
                        turnFrom(anchorBearing, bearingOf(query.center(), shooter.getValue())))
                .thenComparing(shooter -> shooter.getKey().value()))
        .map(Map.Entry::getKey)
        .toList();
  }

  // 0° faces +Z and 90° faces +X; a shooter right on the target counts as 0°.
  private static double bearingOf(Vec3 center, Vec3 position) {
    Vec3 offset = position.minus(center).horizontal();
    if (offset.length() == 0) {
      return 0;
    }
    return Math.toDegrees(Math.atan2(offset.x(), offset.z()));
  }

  private static double turnFrom(double fromBearing, double toBearing) {
    double turn = (toBearing - fromBearing) % FULL_TURN_DEGREES;
    return turn < 0 ? turn + FULL_TURN_DEGREES : turn;
  }

  private static Vec3 directionOf(double bearingDegrees) {
    double radians = Math.toRadians(bearingDegrees);
    return new Vec3(Math.sin(radians), 0, Math.cos(radians));
  }
}
```

El ancla siempre es el primero de la lista (su giro desde sí mismo es 0). Si dos tiradores tienen el mismo `MobId` mínimo, no puede pasar: las claves de un mapa son únicas.

### `CombatGeometry.java`: línea de tiro y carril libre

```java
  // An ally this close to the line between a shooter's eye and its target would take the arrow
  // (a mob's half width plus an arrow's, with room to spare).
  static final double LINE_OF_FIRE_CLEARANCE_BLOCKS = 0.75;
  // The arrow meets the target's body before its center: allies beside or behind it are safe.
  static final double ARROW_STOP_SHORT_BLOCKS = 0.5;
  // Turns tried around the target, nearest first, to find a lane without allies.
  private static final List<Double> LANE_TURNS_DEGREES =
      List.of(0.0, 30.0, -30.0, 60.0, -60.0, 90.0, -90.0);

  public boolean isLineOfFireClear(Vec3 from, Vec3 to, List<Vec3> allies) {
    Vec3 impact = shortOf(from, to);
    return allies.stream()
        .noneMatch(ally -> distanceToSegment(ally, from, impact) < LINE_OF_FIRE_CLEARANCE_BLOCKS);
  }

  private static Vec3 shortOf(Vec3 from, Vec3 to) {
    Vec3 line = to.minus(from);
    if (line.length() <= ARROW_STOP_SHORT_BLOCKS) {
      return from;
    }
    return to.minus(line.normalized().times(ARROW_STOP_SHORT_BLOCKS));
  }

  /** The first spot round the target, from {@code slot}, with a clear line of fire to it. */
  public Optional<Vec3> clearLane(Vec3 slot, Vec3 target, List<Vec3> allies) {
    Vec3 offset = slot.minus(target).horizontal();
    return LANE_TURNS_DEGREES.stream()
        .map(turn -> rotateAroundVertical(offset, turn))
        .map(turned -> new Vec3(target.x() + turned.x(), slot.y(), target.z() + turned.z()))
        .filter(spot -> isLineOfFireClear(spot, target, allies))
        .findFirst();
  }

  private static double distanceToSegment(Vec3 point, Vec3 from, Vec3 to) {
    Vec3 segment = to.minus(from);
    double lengthSquared = segment.dot(segment);
    if (lengthSquared == 0) {
      return point.distanceTo(from);
    }
    double along = Math.clamp(point.minus(from).dot(segment) / lengthSquared, 0.0, 1.0);
    return point.distanceTo(from.plus(segment.times(along)));
  }
```

### `Waypoints.java`

- `backOffPoint` se borra.
- Campo nuevo `private final ShooterFormation formation = new ShooterFormation();`. Es una clase sin estado ni dependencias, y así el constructor sigue con 3 parámetros.
- Métodos nuevos:

```java
  public Vec3 shooterSlot(Vec3 center, MobId self, Map<MobId, Vec3> shooters) {
    AttackSettings attack = settings.get();
    double radius = (attack.shootMinDistanceBlocks() + attack.shootMaxDistanceBlocks()) / 2;
    return formation.pointFor(new ShooterQuery(center, self, shooters, radius));
  }

  public Optional<Vec3> clearLane(Vec3 slot, Vec3 target, List<Vec3> allies) {
    return geometry.clearLane(slot, target, allies);
  }

  public boolean isLineOfFireClear(Vec3 from, Vec3 to, List<Vec3> allies) {
    return geometry.isLineOfFireClear(from, to, allies);
  }
```


### `RoleRegistry.java`

```java
  public Set<MobId> mobsTargeting(PlayerId target) {
    return assignments.values().stream()
        .filter(order -> order.target().equals(Optional.of(target)))
        .map(RoleAssignment::mob)
        .collect(Collectors.toUnmodifiableSet());
  }
```

### `PoseReader.java`

```java
  // The middle of the body: what an arrow should hit, and what stands in its way.
  private static final double BODY_CENTER_FRACTION = 0.5;

  public static Vec3 bodyCenterOf(LivingEntity entity) {
    Location feet = entity.getLocation();
    return new Vec3(
        feet.getX(), feet.getY() + entity.getHeight() * BODY_CENTER_FRACTION, feet.getZ());
  }
```

`BowShooter` borra su `centerOf` y su `BODY_CENTER_FRACTION`, y usa `PoseReader.bodyCenterOf(target)`.

### `ShootGoal.java`

- Se borran `keepInRangeIfDue`, `backOffFrom` y el uso de `RangeSituation`.
- Constante nueva: `// Close enough to its place: walking the last blocks only makes it wobble.` `private static final double SLOT_TOLERANCE_BLOCKS = 2.0;`

| Función | Hace |
| --- | --- |
| `void engage(RoleAssignment order, Player target)` | `mob.lookAt(target)`; `keepPositionIfDue(target)`; `shootIfReady(order, target)` |
| `void keepPositionIfDue(Player target)` | Si `rhythm.shouldRepath()`: si `!mob.hasLineOfSight(target)`, `moveTo(target, WALK_SPEED)`; si no, `walkToFiringSpot(target)`. Después `rhythm.markRepath()` |
| `void walkToFiringSpot(Player target)` | `slot = waypoints().shooterSlot(position(target), self(), shooterPositions(target))`; `eyeSlot = slot` más `mob.getEyeHeight()` en `y`; `spot = waypoints().clearLane(eyeSlot, PoseReader.bodyCenterOf(target), allyCenters(target))`, bajado de nuevo a los pies, o `slot` si no hay carril. Si la distancia horizontal de `mobPosition()` a `spot` es `<= SLOT_TOLERANCE_BLOCKS`: `stopPathfinding()`; si no, `moveTo(new Location(mob.getWorld(), spot…), WALK_SPEED)` |
| `Map<MobId, Vec3> shooterPositions(Player target)` | Como `FlankGoal.flankerPositions`, pero con `Role.SHOOT`: cada `MobId` de `mobsWith(Role.SHOOT, objetivo)` que sea un `Mob` válido en el mundo del objetivo, con su posición. Siempre `self()` con la posición de este mob |
| `List<Vec3> allyCenters(Player target)` | Cada `MobId` de `context.roles().mobsTargeting(objetivo)` distinto de `self()` que sea un `Mob` válido en el mundo del objetivo, con `PoseReader.bodyCenterOf(mob)`. El orden no importa |
| `void shootIfReady(RoleAssignment order, Player target)` | La condición de no disparar suma `\|\| !waypoints().isLineOfFireClear(PoseReader.positionOf(mob.getEyeLocation()), PoseReader.bodyCenterOf(target), allyCenters(target))`. El resto no cambia |
| `MobId self()` | `new MobId(mob.getUniqueId())` |
| `Waypoints waypoints()` | `context.tools().waypoints()` |

`focusOf`, `shotNow`, `distanceTo`, `mobPosition` y `position` no cambian. `distanceTo` sigue sirviendo para el máximo de 30.

### `VersionTranslator.java` y `GroupSpawner.java`

```java
  // Natural skeletons carry a bow; the test group spawns bare, and one without it looks unarmed.
  // A drop chance of 0 keeps players from farming bows off the test group.
  public void armWithBow(Mob mob) {
    EntityEquipment equipment = mob.getEquipment();
    equipment.setItemInMainHand(new ItemStack(Material.BOW));
    equipment.setItemInMainHandDropChance(0f);
  }
```

En `GroupSpawner.spawn`, después de `mob.getEquipment().clear();`: `if (kind == MobKind.SKELETON) { translator.armWithBow(mob); }`.

### `config.yml`

```yaml
  # Distancia a la que pelean los esqueletos; se ubican en el medio (CT-21).
  shoot-min-distance-blocks: 20.0
  shoot-max-distance-blocks: 30.0
```

### `docs/actualizar-paper.md`

- Fila `adapter/translate/VersionTranslator`: sumar `Material.BOW`, `ItemStack`, `EntityEquipment.setItemInMainHand` y `setItemInMainHandDropChance` (`armWithBow`).
- Fila `adapter/snapshot/PoseReader`: sumar `LivingEntity.getHeight()` (`bodyCenterOf`).
- Fila `adapter/goal/*`: sumar `LivingEntity.getEyeHeight()`.

## Pruebas obligatorias

### `ShooterFormationTest` (7)

Jugador en `(0,64,0)`, radio 25, tolerancia `1e-9`. `mob(n)` = `new MobId(new UUID(0, n))`.

| Prueba | Tiradores | Verifica |
| --- | --- | --- |
| `loneShooterKeepsItsBearing` | `mob(1)` en `(0,64,10)` | `(0, 64, 25)` |
| `twoShootersStandOpposite` | `mob(1)` en `(0,64,10)`, `mob(2)` en `(10,64,0)` | `mob(1)` `(0, 64, 25)`; `mob(2)` `(0, 64, -25)` |
| `threeShootersStandAHundredTwentyApart` | `mob(1)` `(0,64,10)`, `mob(2)` `(10,64,0)`, `mob(3)` `(-10,64,0)` | `mob(2)` `(21.65063509461097, 64, -12.5)`; `mob(3)` `(-21.650635094610962, 64, -12.5)` |
| `theLowestIdAnchorsTheRing` | `mob(2)` en `(0,64,10)`, `mob(1)` en `(10,64,0)` | `mob(1)` `(25, 64, 0)`; `mob(2)` `(-25, 64, 0)` |
| `shootersKeepTheirOrderAroundTheTarget` | `mob(1)` `(0,64,10)`, `mob(3)` `(10,64,0)` (90°), `mob(2)` `(-10,64,0)` (270°) | `mob(3)` (segundo en el giro) `(21.65…, 64, -12.5)`; `mob(2)` (tercero) `(-21.65…, 64, -12.5)` |
| `queryRejectsASelfThatIsNotAShooter` | `self` `mob(9)`, tiradores `{mob(1)}` | `IllegalArgumentException`: `ShooterQuery.self must be one of the shooters, got 00000000-0000-0000-0000-000000000009` |
| `queryRejectsNonPositiveRadius` | radio 0 | `IllegalArgumentException`: `ShooterQuery.radiusBlocks must be a positive number, got 0.0` |

### `CombatGeometryTest` (+8)

| Prueba | Verifica |
| --- | --- |
| `lineWithoutAlliesIsClear` | de `(0,0,0)` a `(0,0,20)`, sin aliados: `true` |
| `allyOnTheLineBlocksIt` | aliado en `(0,0,10)`: `false` |
| `allyBesideTheLineDoesNotBlockIt` | aliado en `(1.2,0,10)`: `true` |
| `allyHuggingTheTargetBlocksTheLine` | aliado en `(0.5,0,19.5)` (delante del jugador): `false` |
| `allyBesideTheTargetDoesNotBlock` | aliado en `(0.7,0,20)` (al costado del jugador; a 0,86 del segmento): `true` |
| `allyBehindTheTargetDoesNotBlock` | aliado en `(0,0,20.7)` (detrás del jugador; a 1,2 del segmento): `true` |
| `clearLaneTurnsAroundTheTarget` | puesto `(0,65,25)`, objetivo `(0,65,0)`, aliado en `(0,65,12)`: `(-12.499999999999998, 65, 21.65063509461097)` (el giro de 30°) |
| `noClearLaneWhenAnAllyStandsOnTheTarget` | aliado en `(0,65,0.5)`: vacío |

### `WaypointsTest` (`backOffPointReachesTheMinimumRange` se borra; +2)

Con `TestSettings` (mínimo 8, máximo 15: radio 11,5).

| Prueba | Verifica |
| --- | --- |
| `shooterSlotSitsMidwayInTheBowRange` | tirador solo en `(0,64,5)`, centro en el origen a altura 64: `(0, 64, 11.5)` |
| `lineOfFireAndLanesComeFromTheGeometry` | `isLineOfFireClear((0,0,0), (0,0,20), [(0,0,10)])` es `false` y `clearLane` con los datos de `clearLaneTurnsAroundTheTarget` da el mismo punto |

### `RoleRegistryTest` (+1)

| Prueba | Verifica |
| --- | --- |
| `mobsTargetingIncludesEveryRole` | `PRESS` de `mob(1)` y `SHOOT` de `mob(2)` sobre `player`, `RETREAT` de `mob(3)` sin objetivo, `FLANK` de `mob(4)` sobre otro jugador: `mobsTargeting(player)` es exactamente `{mob(1), mob(2)}` |

Total: **18 pruebas nuevas**, 1 borrada en `WaypointsTest` y el archivo `RangeSituationTest` borrado.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | Separación fija de 90° en vez de `360 / n` | dos tiradores quedan a 90° | `twoShootersStandOpposite` |
| 2 | Ancla con el `MobId` más alto (`max`) | `mob(2)` ancla en 0° | `theLowestIdAnchorsTheRing` |
| 3 | `LANE_TURNS_DEGREES` con solo `0.0` | sin carril libre: vacío | `clearLaneTurnsAroundTheTarget` |
| 4 | En `mobsTargeting`, filtrar también por `order.role() == Role.PRESS` | `{mob(1)}` | `mobsTargetingIncludesEveryRole` |

## Verificación en el server (Nico, después del merge)

Con `/mobai debug all full`, de noche, con `spawngroup`:

1. Los esqueletos tienen arco en la mano.
2. Se ubican a unos 25 bloques y repartidos alrededor tuyo: con 3 esqueletos, a unos 120° entre sí.
3. Mientras los zombies te pegan, los esqueletos no les tiran: en el `mobai-debug.log` bajan mucho los `NEUTRAL:ALLY_HIT`. Se corren de costado para tener la línea libre.
4. Si te acercás a uno, se aleja a su puesto; si te escondés, se acerca hasta verte.

## Procedimiento

1. Rama `wp-24c-esqueletos-en-formacion` desde `origin/main` actualizado.
2. `ShooterQuery`, `ShooterFormation`, `CombatGeometry` y sus pruebas. Commit: `feat: shooter formation and clear lines of fire (CT-21)`.
3. `Waypoints`, `RoleRegistry`, `PoseReader`, `BowShooter`, `ShootGoal`, borrar `RangeSituation`, `RangeMove` y `RangeSituationTest`, `WaypointsTest`, `RoleRegistryTest`. Commit: `feat: skeletons take their place in the ring and hold fire over allies`.
4. `VersionTranslator`, `GroupSpawner`, `config.yml`, `ConfigLoaderTest`. Commit: `feat: skeletons carry bows and fight from 20 to 30 blocks`.
5. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the shooter formation`.
6. Pruebas que muerden (de a una, en secuencia; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
8. Push, PR `WP-24C: skeletons in formation (CT-21)`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca una deprecación en un método de Paper usado acá, frená y reportá; no la suprimas.
3. Si ArchUnit se queja de `Material` o `ItemStack` fuera de `VersionTranslator`, frená y reportá.
4. Si otra prueba existente falla por el cambio de `config.yml`, frená y reportá cuál.

## Fuera de alcance

- Buscar altura (WP-24D) y la andanada (CT-23).
- Cambiar `TestSettings`.
- Que los zombies se aparten de la línea de tiro (es la andanada).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 18 pruebas nuevas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
