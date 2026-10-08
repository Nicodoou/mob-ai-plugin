# WP-24G — Tensar el arco

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-23D (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24g-tensar-el-arco` |

## Objetivo

CT-26, aprobado por Nico; arregla el B-06. Hoy el esqueleto dispara en el acto, sin animación, y puede hacerlo caminando de espaldas al jugador. Con este WP dispara como en vanilla:

1. **Se planta:** deja de caminar.
2. **Gira el cuerpo hacia el jugador.**
3. **Levanta los brazos y tensa el arco 20 ticks.**
4. **Recién ahí suelta.** La puntería se calcula al soltar, con la posición más reciente del jugador.

El tensado se aprovecha también en la andanada y en el tiro oportuno:
- en `HOLD_FIRE`, el esqueleto ya ubicado tensa y **se queda tenso**; cuando llega `VOLLEY`, suelta en el acto, y todos juntos;
- el tiro oportuno espera **con el arco tenso** a que el jugador se distraiga.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/actualizar-paper.md` (sección 3)
- `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/`: `ShootGoal`, `ShotRhythm`, `BowShooter`, `ShotParts`, `OpportunisticWait`
- Pruebas: `src/test/java/io/github/nicodoou/mobai/adapter/goal/ShotRhythmTest.java`, `src/test/java/io/github/nicodoou/mobai/testsupport/FakeServerClock.java`

## Reglas de negocio

1. **Empezar a tensar.** El esqueleto que no está tensando empieza cuando se cumplen todas estas condiciones:
   - el ritmo lo permite: faltan 20 ticks o menos para su próximo tiro (`ShotRhythm.canDraw`), así que la cadencia sigue siendo un tiro cada 40 ticks;
   - ve al jugador (`hasLineOfSight`);
   - está a `shoot-max-distance-blocks` o menos;
   - en `HOLD_FIRE`, además, **ya está en su puesto**: la última vez que se ubicó estaba a `SLOT_TOLERANCE_BLOCKS` o menos de su destino. En `SHOOT` y `VOLLEY` no hace falta, porque si no encuentra puesto igual dispara.
2. **Al empezar:**
   - deja de caminar (`stopPathfinding`);
   - levanta los brazos y usa el arco (`BowShooter.draw`, con Paper detrás del traductor);
   - gira el cuerpo hacia el jugador.
3. **Mientras tensa**, en cada tick del goal:
   - no se reubica (no llama a `keepPositionIfDue`);
   - mira al jugador y le apunta el cuerpo;
   - **cancela** (baja el arco) si deja de verlo o queda fuera de distancia.
4. **Con el arco tenso** (20 ticks o más, `BOW_FULL_DRAW_TICKS`):
   - `HOLD_FIRE`: se queda tenso, no suelta, y la espera oportuna se reinicia;
   - si no, elige el tiro con `shotNow`, igual que hoy. Si el tiro oportuno dice `WAIT`, se queda tenso esperando;
   - con un tiro elegido, verifica su carril (B-04):
     - **libre:** suelta (`bow.shoot`), marca el ritmo (`markShot`) y baja el arco;
     - **tapado:** baja el arco **sin marcar el ritmo**, para volver a ubicarse en un carril libre y tensar de nuevo.
5. **Al terminar el goal** (`stop`), si estaba tensando, baja el arco.
6. **Todo con el reloj del plugin** (regla B-01). El goal corre uno sí y uno no de los ticks del juego: por eso el tensado se mide con el reloj y no contando llamadas.

## Archivos

| Acción | Ruta |
| --- | --- |
| Modificar | `src/main/java/io/github/nicodoou/mobai/domain/shared/MinecraftConstants.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShotRhythm.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/BowDraw.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/BodyFacing.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/BowShooter.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/ShootGoal.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/goal/ShotRhythmTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/BowDrawTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/BodyFacingTest.java` |

Antes de empezar, buscá con grep:
- que los nombres de las pruebas nuevas no existan;
- `new BowShooter(` y `new ShotRhythm(`: si aparecen fuera de `AdapterServices`, `ShootGoal` y `ShotRhythmTest`, frená y reportá.

**Métodos públicos nuevos:**
- `VersionTranslator.drawBow` y `lowerBow`;
- `BowShooter.draw` y `lower`;
- `ShotRhythm.canDraw`.

Si alguna clase pasa de 20, frená y reportá.

## Especificación

**API de Paper nueva (toda dentro de `VersionTranslator`, salvo `setBodyYaw`):**
- `Mob.setAggressive(boolean)`: brazos arriba;
- `LivingEntity.startUsingItem(EquipmentSlot.HAND)`: la animación de tensar;
- `LivingEntity.clearActiveItem()`;
- `LivingEntity.setBodyYaw(float)`.

Si alguno no existe en Paper 26.3, **frená y reportá**.

`EquipmentSlot.HAND` es una constante de Paper: solo puede aparecer en `VersionTranslator`.

### `MinecraftConstants.java`

```java
  // A bow reaches full power after 20 ticks of drawing; vanilla skeletons release it then.
  public static final int BOW_FULL_DRAW_TICKS = 20;
```

### `VersionTranslator.java`

```java
  // Raised arms and a drawn bow are what players read as "about to shoot".
  public void drawBow(Mob mob) {
    mob.setAggressive(true);
    mob.startUsingItem(EquipmentSlot.HAND);
  }

  public void lowerBow(Mob mob) {
    mob.clearActiveItem();
    mob.setAggressive(false);
  }
```

El arco del esqueleto dura 72.000 ticks en uso: nunca termina solo, y `clearActiveItem` lo corta sin disparar nada (`BowItem.releaseUsing` no hace nada para un mob). La flecha la sigue lanzando `BowShooter.shoot`.

### `ShotRhythm.java`

```java
  /** The draw starts early enough for the arrow to leave on the rhythm, not 20 ticks late. */
  public boolean canDraw() {
    return clock.currentTick() + MinecraftConstants.BOW_FULL_DRAW_TICKS >= nextShotTick;
  }
```

Sumá del lado de `currentTick`, no restes de `nextShotTick`: arranca en `Long.MIN_VALUE` y restarle desborda.

### `BowDraw.java` (pura)

```java
/** How long a shooter has been drawing its bow, on the plugin's clock. */
final class BowDraw {
  private static final long NOT_DRAWING = Long.MIN_VALUE;

  private final ServerClock clock;
  private long drawStartTick = NOT_DRAWING;

  BowDraw(ServerClock clock) { … requireNonNull … }

  boolean isDrawing() { return drawStartTick != NOT_DRAWING; }

  void start() { drawStartTick = clock.currentTick(); }

  boolean isFull() {
    return isDrawing()
        && clock.currentTick() - drawStartTick >= MinecraftConstants.BOW_FULL_DRAW_TICKS;
  }

  void release() { drawStartTick = NOT_DRAWING; }
}
```

### `BodyFacing.java` (pura)

```java
/** Minecraft's yaw for an entity's body: 0 faces +Z (south) and it grows towards -X (west). */
final class BodyFacing {
  private BodyFacing() {}

  static float yawTowards(Vec3 from, Vec3 to) {
    Vec3 offset = to.minus(from);
    return (float) Math.toDegrees(Math.atan2(-offset.x(), offset.z()));
  }
}
```

Es otra convención que la de la flecha (`ShotAim.rotationOf`, que usa `atan2(x, z)`). Agregá ese comentario arriba del método, para que nadie las "unifique".

### `BowShooter.java`

```java
  public void draw(Mob shooter) {
    parts.translator().drawBow(shooter);
  }

  public void lower(Mob shooter) {
    parts.translator().lowerBow(shooter);
  }
```

Actualizá el Javadoc de la clase: `/** The only place that draws bows and looses arrows; every arrow opens an attempt in the attack tracker. */`

### `ShootGoal.java`

Campos nuevos:
- `private final BowDraw draw;`, armado en el constructor con `new BowDraw(context.tools().timing().clock())`;
- `private boolean inPlace;`.

- **`walkToFiringSpot`:** `inPlace = true` en la rama que se queda (dentro de `SLOT_TOLERANCE_BLOCKS`) y `inPlace = false` en la que camina.
- **`keepPositionIfDue`:** en la rama sin vista, `inPlace = false`.

```java
  @Override
  public void stop() {
    lowerBow();
    mob.getPathfinder().stopPathfinding();
  }

  private void engage(RoleAssignment order, Player target) {
    mob.lookAt(target);
    if (draw.isDrawing()) {
      holdDraw(order, target);
      return;
    }
    keepPositionIfDue(target);
    startDrawIfReady(order, target);
  }
```

| Función | Hace |
| --- | --- |
| `boolean canAim(Player target)` | `mob.hasLineOfSight(target) && distanceTo(target) <= attack().shootMaxDistanceBlocks()` |
| `void startDrawIfReady(RoleAssignment order, Player target)` | Si `!shots.canDraw()`, o `!canAim(target)`, o (`order.role() == Role.HOLD_FIRE && !inPlace`): `opportunism.reset()` y vuelve. Si no: `mob.getPathfinder().stopPathfinding()`, `draw.start()`, `bow().draw(mob)`, `faceBodyTowards(target)` |
| `void holdDraw(RoleAssignment order, Player target)` | Si `!canAim(target)`: `opportunism.reset()`, `lowerBow()` y vuelve. `faceBodyTowards(target)`. Si `!draw.isFull()` vuelve. Si `order.role() == Role.HOLD_FIRE`: `opportunism.reset()` y vuelve. `shotNow(order, target).ifPresent(attack -> releaseOrLower(target, attack))` |
| `void releaseOrLower(Player target, Attack attack)` | Si `isLaneClear(target, attack)`: `bow().shoot(mob, target, attack)` y `shots.markShot()`. En los dos casos, `lowerBow()` |
| `void lowerBow()` | Si `draw.isDrawing()`: `bow().lower(mob)` y `draw.release()` |
| `void faceBodyTowards(Player target)` | `mob.setBodyYaw(BodyFacing.yawTowards(mobPosition(), position(target)))` |
| `BowShooter bow()` | `context.tools().weapons().bow()`. `isLaneClear` pasa a usarlo |

**Se borra `shootIfReady`:** su trabajo lo hacen ahora `startDrawIfReady` y `holdDraw`.

`shotNow`, `opportunisticShot`, `chosenAttack`, `focusOf` e `isLaneClear` no cambian.

### `docs/actualizar-paper.md`, sección 3

- **Fila del traductor:** suma `Mob.setAggressive`, `LivingEntity.startUsingItem`, `EquipmentSlot.HAND` y `LivingEntity.clearActiveItem` (`drawBow`, `lowerBow`). El riesgo: "que el arco de un mob en uso siga mostrando la animación de tensar, y que `clearActiveItem` lo corte sin disparar".
- **Fila de `adapter/goal/*`:** suma `LivingEntity.setBodyYaw(float)`, con la convención de yaw de Minecraft para entidades (0 hacia +Z, crece hacia −X).

## Pruebas obligatorias

### `BowDrawTest` (4), con `FakeServerClock(1_000)`

| Prueba | Verifica |
| --- | --- |
| `notDrawingUntilStarted` | nuevo: `isDrawing` false e `isFull` false |
| `fullAfterTwentyTicks` | `start()`; a los 19 ticks `isFull` false; a los 20, true; `isDrawing` true en los dos |
| `releaseStopsTheDraw` | `start()`, 25 ticks, `release()`: `isDrawing` false e `isFull` false |
| `restartingCountsFromTheNewStart` | `start()`, 15 ticks, `start()` otra vez, 15 ticks: `isFull` false; 5 ticks más: true |

### `ShotRhythmTest` (+2)

| Prueba | Verifica |
| --- | --- |
| `canDrawBeforeAnyShot` | nuevo: `canDraw` true (sin desbordar) |
| `drawStartsTwentyTicksBeforeTheShot` | `markShot()`; a los 19 ticks `canDraw` false; a los 20, `canDraw` true y `canShoot` false; a los 40, `canShoot` true |

### `BodyFacingTest` (2)

| Prueba | Verifica |
| --- | --- |
| `yawFollowsMinecraftsConvention` | desde `(0,64,0)`: hacia `(0,64,5)` 0°; hacia `(5,64,0)` −90°; hacia `(-5,64,0)` 90°; hacia `(0,64,-5)` 180° en valor absoluto (tolerancia 1e-4) |
| `heightDoesNotTurnTheBody` | hacia `(0,80,5)` da 0°, igual que hacia `(0,64,5)` |

Total: **8 pruebas**. El goal usa Paper y se verifica en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `BowDraw.isFull`, `>` en lugar de `>=` | lleno recién a los 21 | `fullAfterTwentyTicks` |
| 2 | `canDraw` sin los 20 ticks (`currentTick >= nextShotTick`) | a los 20 da false | `drawStartsTwentyTicksBeforeTheShot` |
| 3 | `canDraw` como `currentTick >= nextShotTick - BOW_FULL_DRAW_TICKS` | desborda: false antes del primer tiro | `canDrawBeforeAnyShot` |
| 4 | En `BodyFacing`, `atan2(offset.x(), offset.z())` | +X da 90° | `yawFollowsMinecraftsConvention` |

## Verificación en el server (Nico, después del merge)

De noche, `/mobai debug all full` y `spawngroup` con esqueletos:

1. **Antes de cada tiro:** el esqueleto se planta, gira hacia vos, levanta los brazos y tensa el arco (se ve la cuerda tirante) más o menos 1 s.
2. **Ya no dispara caminando ni de espaldas.**
3. **Sigue disparando cada 2 s más o menos:** en el `mobai-debug.log`, `skeleton.*` del mismo esqueleto cada ~40 ticks.
4. **Tiro oportuno:** el esqueleto se queda tenso mientras lo mirás, y suelta cuando mirás para otro lado.
5. **Andanada** (si sale `VOLLEY` en `/mobai status`): mientras los zombies presionan, los esqueletos se ubican y quedan tensos; cuando los zombies se abren, sueltan todos juntos.

## Procedimiento

1. Rama `wp-24g-tensar-el-arco` desde `origin/main` actualizado.
2. `MinecraftConstants`, `ShotRhythm.canDraw`, `BowDraw`, `BodyFacing` y sus pruebas. Commit: `feat: bow draw timing and body facing for shooters`.
3. `VersionTranslator`, `BowShooter`, `ShootGoal` y `docs/actualizar-paper.md`. Commit: `feat: skeletons plant, turn and draw before they shoot (CT-26)`.
4. Pruebas que muerden (de a una; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-24G: skeletons draw their bows (CT-26)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.

## Fuera de alcance

- Moverse de costado mientras tensa (vanilla lo hace; acá se planta).
- Cambiar la cadencia de tiro o la puntería.
- La flecha visible en el arco: vanilla no la muestra para mobs.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 8 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
