# WP-36B — Grabación del combate: daño, muertes y acciones del jugador

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-36A (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-36b-grabacion-eventos` |

## Objetivo

Segunda parte del CT-36. Mientras se graba, cada daño, muerte, golpe y disparo de una entidad grabada queda en la misma línea de tiempo que las muestras, con su causa y su autor. Así se sabe por qué bajó la vida de un mob: sol, barrido, flecha aliada o golpe del jugador.

Este WP no toca el dominio ni cambia el comportamiento de los mobs.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/debug/CombatSample.java`, `TraceLevels.java`, `TraceWriter.java`, `RecordingSelection.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/recording/CombatSampler.java` (regla de qué jugadores se graban), `SamplerParts.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/listener/DamageListener.java`, `DeathListener.java`, `ThreatListener.java` (modelos)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/RoleRegistry.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java`
- `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java`
- `src/main/java/io/github/nicodoou/mobai/domain/strategy/RecipeBase.java`, `domain/port/ServerClock.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (`listeners`)
- Pruebas: `adapter/debug/RecordingSelectionTest.java`, `CombatSampleLinesTest.java` (modelos)

## Reglas de negocio

1. **A quién se graba** (`RecordingScope`):
   - un mob, si pertenece a un grupo con nivel `FULL`;
   - un jugador, si la grabación está activa (`RecordingSelection.isActive`) y es objetivo de la orden de algún mob de un grupo grabado, o está en entrenamiento.

   Cualquier otra entidad se ignora. Es la misma regla de jugadores que el WP-36A, en un solo lugar: `CombatSampler` pasa a usarla.
2. **Daño:** todo `EntityDamageEvent` sobre una entidad grabada, de cualquier causa (sol, fuego, caída, golpe, flecha, barrido), **también los cancelados**, marcados como tales. Un daño de una entidad grabada contra una no grabada no se registra.
3. **Autor del daño:** si el evento es `EntityDamageByEntityEvent`, el `getDamager()`. Si es un proyectil, el autor es su tirador y además se guarda el tipo de proyectil.
4. **Muertes:** todo `EntityDeathEvent` de una entidad grabada, con la causa y el autor del último daño (`getLastDamageCause()`), que puede faltar.
5. **Acciones del jugador grabado:**
   - `SWING`: `PlayerArmSwingEvent`, con el ítem de la mano;
   - `SHOOT`: `EntityShootBowEvent` de un jugador, con el arma y la fuerza.
6. **Sin grabación activa, cada evento solo cuesta comprobarlo.**
7. **Orden con `DeathListener`:** al morir, un mob sale de su grupo en un listener con la misma prioridad. `RecordingListener` se registra **antes** en la lista de `AdapterServices.listeners`, para encontrar al grupo todavía. La prueba en el server lo confirma.
8. **Solo mira:** prioridad `MONITOR` y `ignoreCancelled = false`. No cancela ni modifica nada.
9. **Correcciones de la revisión del WP-36A:**
   - en `PluginRuntime.runTick`, `combatSampler().tick(now)` pasa a ser **la última línea**: si la grabación falla, no puede impedir que se cure a los mobs ni que se guarde la memoria;
   - falta una prueba: en la revisión, hacer que `isFullForAll` ignore la configuración no hizo fallar ninguna.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/recording/RecordingScope.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/recording/RecordingListener.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/debug/CombatSample.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/recording/CombatSampler.java`, `SamplerParts.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`entityName`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo armado), `PluginRuntime.java` (regla 9) |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/debug/RecordingSelectionTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/recording/RecordingScopeTest.java` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/debug/CombatSampleLinesTest.java` |
| Modificar | `docs/arquitectura.md`, `docs/actualizar-paper.md` |

**`RecordingScope` va en `adapter/recording`, no en `adapter/debug`:** necesita `RoleRegistry`, y `adapter/goal` ya depende de `adapter/debug`. Así no se crea un ciclo entre esos dos paquetes.

Antes de empezar, buscá con grep que `RecordingScope`, `RecordingListener`, `DamageSample`, `DeathSample`, `ActionSample` y `entityName` no existan en `src/`.

## Especificación

### `CombatSample.java`

Tres records nuevos. Los IDs de las entidades van como `UUID`, porque la víctima o el autor pueden no ser ni mob nuestro ni jugador.

```java
  record DamageSample(
      long tick,
      Optional<GroupId> group,
      UUID victim,
      String victimType,
      String cause,
      double damage,
      double finalDamage,
      double healthBefore,
      boolean blocked,
      boolean cancelled,
      Optional<UUID> attacker,
      Optional<String> attackerType,
      Optional<String> projectile)
      implements CombatSample {}

  record DeathSample(
      long tick,
      Optional<GroupId> group,
      UUID victim,
      String victimType,
      Optional<String> cause,
      Optional<UUID> attacker,
      Optional<String> attackerType)
      implements CombatSample {}

  record ActionSample(
      long tick, PlayerId player, PlayerAction action, String item, Optional<Double> force)
      implements CombatSample {}

  enum PlayerAction {
    SWING,
    SHOOT
  }
```

`requireNonNull` en las referencias, como en el WP-36A.

### `RecordingScope.java`

```java
package io.github.nicodoou.mobai.adapter.recording;

/** Who the recording follows right now (CT-36). */
public final class RecordingScope {
  public RecordingScope(ActiveGroups activeGroups, RoleRegistry roles, ScopeRules rules)

  public boolean isActive()

  /** The group of a recorded mob; empty if the mob is not being recorded. */
  public Optional<GroupId> recordedGroupOf(MobId mob)

  public boolean recordsPlayer(PlayerId player)

  public record ScopeRules(TraceLevels levels, RecipeBase recipeBase) {}
}
```

- `isActive`: `RecordingSelection.of(levels, idsDeTodosLosGrupos).isActive()`.
- `recordedGroupOf`: `activeGroups.groupOf(mob)`, si `levels.isFull(id)`.
- `recordsPlayer`:
  - `false` si `!isActive()`;
  - `true` si `recipeBase.isTraining(player)`;
  - `true` si algún mob de `roles.mobsTargeting(player)` tiene un grupo grabado;
  - `false` en otro caso.

`CombatSampler` usa `recordsPlayer` para decidir qué jugadores muestrea, en lugar de su regla propia. `SamplerParts` suma el componente `RecordingScope scope` y conserva `RecipeBase recipeBase`, porque el campo `training` de `PlayerSample` lo sigue leyendo.

### `VersionTranslator.java`

```java
  /** The entity's type name without its namespace, such as zombie. */
  public String entityName(Entity entity) {
    return entity.getType().getKey().getKey();
  }
```

Va acá porque `EntityType` solo se usa en esta clase (ArchUnit).

### `RecordingListener.java`

```java
package io.github.nicodoou.mobai.adapter.recording;

/** Writes what happens to the recorded entities, when it happens (CT-36). */
public final class RecordingListener implements Listener {
  public RecordingListener(RecordingParts parts)

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageEvent event)

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event)

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onSwing(PlayerArmSwingEvent event)

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onShoot(EntityShootBowEvent event)

  public record RecordingParts(
      RecordingScope scope, TraceWriter writer, VersionTranslator translator, ServerClock clock) {}
}
```

**Primera línea de cada handler:** `if (!parts.scope().isActive()) return;`.

**Quién es la víctima** (función privada común): si es `Player`, se graba si `recordsPlayer`, con grupo vacío. Si no, se graba si `recordedGroupOf(new MobId(uuid))` está presente, con ese grupo.

**`onDamage`:**
- `victimType`: `translator.entityName(entity)`;
- `cause`: `event.getCause().name()`;
- `damage`: `event.getDamage()`; `finalDamage`: `event.getFinalDamage()`;
- `healthBefore`: `getHealth()` si la víctima es `LivingEntity`; si no, se ignora el evento;
- `blocked`: `translator.wasBlocked(event)`; `cancelled`: `event.isCancelled()`;
- **autor:** ver regla 3. Función privada `attackerOf(Entity damager)`: si `damager instanceof Projectile p && p.getShooter() instanceof Entity shooter`, el tirador; si no, el propio `damager`. `projectile`: `entityName(damager)` si es proyectil.

**`onDeath`:** `getLastDamageCause()` puede ser `null`: `cause` y autor quedan vacíos. Si es `EntityDamageByEntityEvent`, el autor sale de `attackerOf`.

**`onSwing`:** `ActionSample(tick, player, SWING, itemName(mano principal), Optional.empty())`, si `recordsPlayer`.

**`onShoot`:** solo si `event.getEntity() instanceof Player`. `ActionSample(tick, player, SHOOT, itemName(event.getBow()), Optional.of((double) event.getForce()))`. `getBow()` puede ser `null`: en ese caso el ítem es `"unknown"`, con una constante con nombre.

Funciones chicas, con una tarea cada una y hasta 3 parámetros. Nada de `instanceof Zombie` ni constantes de tipo.

### `AdapterServices.java`

- Arma `RecordingScope` una sola vez y lo comparte con `CombatSampler` y `RecordingListener`.
- `new RecordingListener(...)` va **primero** en la lista de `listeners`, antes de `DamageListener` y `DeathListener` (regla 7).

### Documentos

- **`docs/arquitectura.md`:** el párrafo del WP-36A suma: «También escribe, en el momento en que pasan, el daño a esas entidades con su causa y autor, sus muertes, y los golpes y disparos de los jugadores grabados (`RecordingListener`)». La fila «Grabación del combate» suma `RecordingScope` y `RecordingListener`.
- **`docs/actualizar-paper.md`, sección 3,** fila nueva:

  | `adapter/recording/RecordingListener` | `EntityDamageEvent` (`getCause`, `getDamage`, `getFinalDamage`), `EntityDamageByEntityEvent`, `EntityDeathEvent`, `Entity.getLastDamageCause`, `Projectile.getShooter`, `PlayerArmSwingEvent`, `EntityShootBowEvent` (`getBow`, `getForce`) | **Medio** | Que el orden entre listeners con la misma prioridad siga siendo el de registro: hace falta para grabar la muerte de un mob antes de que salga de su grupo. Que `PlayerArmSwingEvent` siga existiendo |
- **Misma sección:** suma `entityName` (`Entity.getType`, `EntityType.getKey`) a la fila de `VersionTranslator`.

## Pruebas obligatorias

### `RecordingScopeTest` (8)

Con `ActiveGroups`, `RoleRegistry`, `RecipeBase` y `TraceLevels` reales. Un grupo `G1` con el mob `M1`, salvo que se diga otra cosa.

| Prueba | Escenario | Espera |
| --- | --- | --- |
| `nothingIsRecordedWhenOff` | configuración `OFF`, Alice en entrenamiento, `M1` la tiene de objetivo | inactiva; `recordedGroupOf(M1)` vacío; `recordsPlayer(ALICE)` `false` |
| `aMobOfAFullGroupIsRecorded` | `set(G1, FULL)` | `recordedGroupOf(M1)` contiene `G1` |
| `aMobOutsideAnyGroupIsNotRecorded` | `setAll(FULL)`, mob sin grupo | vacío |
| `aMobOfAnotherLevelIsNotRecorded` | `G1` en `FULL`, `G2` en `DECISIONS`, con `M2` | `recordedGroupOf(M2)` vacío |
| `aTargetOfARecordedGroupIsRecorded` | `G1` en `FULL`, orden de `M1` contra Alice | `true` |
| `aTargetOfAnUnrecordedGroupIsNotRecorded` | `G1` en `FULL`; `G2` en `OFF`, orden de `M2` contra Bob | `false` para Bob |
| `aTrainingPlayerIsRecordedWhileActive` | `setAll(FULL)`, sin grupos, Alice en entrenamiento | `true` |
| `aBystanderIsNotRecorded` | `setAll(FULL)`, Bob sin orden ni entrenamiento | `false` |

### `RecordingSelectionTest` (1 nueva)

`theConfiguredDefaultIsActiveWithoutGroups`: configuración `FULL`, sin candidatos: sin grupos; activa.

### `CombatSampleLinesTest` (2 nuevas)

- `aDamageSampleKeepsItsCauseAndAttacker`: `DamageSample` con causa `"FIRE_TICK"` y un autor: la línea empieza con `{"kind":"DamageSample"` y contiene la causa y el UUID del autor.
- `anActionSampleKeepsItsActionAndForce`: `ActionSample` `SHOOT` con fuerza 1,0: contiene `"action":"SHOOT"` y `"force":1.0`.

**Si cambia alguna prueba existente, frená y reportá.**

## Pruebas que muerden

| # | Rotura | Prueba que falla |
| --- | --- | --- |
| 1 | En `recordsPlayer`, sacar `if (!isActive()) return false` | `nothingIsRecordedWhenOff` |
| 2 | En `recordsPlayer`, no filtrar por grupo grabado | `aTargetOfAnUnrecordedGroupIsNotRecorded` |
| 3 | En `recordedGroupOf`, no mirar el nivel | `aMobOfAnotherLevelIsNotRecorded` |
| 4 | En `TraceLevels.isFullForAll`, `levelForAll.orElse(TraceLevel.OFF)` | `theConfiguredDefaultIsActiveWithoutGroups` |

## Verificación en el server (Opus, después del merge)

`RecordingListener` no tiene prueba unitaria, porque depende de eventos de Paper. Opus lo verifica con un bot y `/mobai debug all full`:

- grupo de día: aparece el daño de fuego por el sol (`FIRE_TICK` o la causa que dé Paper) en zombies y esqueletos, y sus muertes con causa;
- el bot pega y dispara: aparecen `SWING`, `SHOOT` y el daño con su autor;
- un esqueleto le pega a un aliado: el daño muestra al esqueleto como autor y `arrow` como proyectil;
- **cada muerte de un mob lleva su grupo** (regla 7).

## Procedimiento

1. Rama `wp-36b-grabacion-eventos` desde `origin/main` actualizado.
2. La regla 9 (`PluginRuntime` y la prueba). Commit: `fix: a recording failure cannot block saving (CT-36)`.
3. `CombatSample`, `RecordingScope`, sus pruebas y `CombatSampler` usándolo. Commit: `feat: one rule for who the recording follows (CT-36)`.
4. `VersionTranslator.entityName`, `RecordingListener` y `AdapterServices`. Commit: `feat: record damage, deaths and player actions (CT-36)`.
5. Documentos. Commit: `docs: recorded events in the architecture and the Paper map`.
6. Pruebas que muerden (de a una; sin commit).
7. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon` en verde. **Nunca** `./gradlew --stop` ni `runServer`.
8. Push, PR `WP-36B: recorded damage, deaths and player actions (CT-36)`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas o los 3 parámetros, separala o agrupá en un record privado, y avisalo.
3. Si la cobertura de `adapter` baja y falla la verificación, frená y reportá el número: no cambies los umbrales.

## Fuera de alcance

- Límite de tamaño, rotación o compresión.
- Registrar proyectiles lanzados por mobs (ya están en `AttackEvent`).
- Cambiar `DamageListener`, `ThreatListener` o `DeathListener`.
- Herramientas para analizar los archivos.
- Tocar el dominio.

## Aceptación

- [ ] Exactamente los archivos indicados.
- [ ] Las 11 pruebas nuevas, en verde; la suite completa en verde, sin cambios en pruebas existentes.
- [ ] Las 4 roturas mordieron.
- [ ] Build, cobertura y CI en verde.
