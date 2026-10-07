# WP-24A — Rastreo de flechas

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-22E (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-24a-rastreo-de-flechas` |

## Objetivo

Que el rastreador de ataques (WP-18, cuerpo a cuerpo) también siga **flechas**: cada disparo de un esqueleto abre un intento que se resuelve cuando la flecha toca algo o vence el plazo, y se clasifica con las mismas reglas del dominio (`AttackClassifier`). Es la base del WP-24B (`ShootGoal` y `BowShooter`), que es el que dispara.

1. `AttackTracker` suma los intentos de proyectil, indexados por el UUID de la flecha (hallazgo 5).
2. `ProjectileListener`: el impacto (`ProjectileHitEvent`) y el daño (`EntityDamageByEntityEvent` con una flecha como atacante).
3. `ProjectileResolver`: en cada tick del plugin cierra los intentos cuya flecha ya tocó algo y los que pasaron el plazo, y los manda a la traza.

El dominio no cambia: `AttackFacts`, `ProjectileContact` y las reglas 1 a 8 ya contemplan los proyectiles.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 4 y 5)
- `docs/actualizar-paper.md` (sección 3, para sumar las filas)
- `src/main/java/io/github/nicodoou/mobai/adapter/tracker/` (todos)
- `src/main/java/io/github/nicodoou/mobai/adapter/listener/DamageListener.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java` (cómo se avisa a `TraceHub`)
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`absorbedDamage`, `wasBlocked`)
- `src/main/java/io/github/nicodoou/mobai/domain/attack/AttackFacts.java`, `AttackClassifier.java`, `ProjectileContact.java`
- `src/main/java/io/github/nicodoou/mobai/application/ActiveGroups.java`, `RecordOutcome.java`, `AttackResolution.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/AttackSettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java`, `PluginRuntime.java`
- Prueba: `src/test/java/io/github/nicodoou/mobai/adapter/tracker/AttackTrackerTest.java`

## Reglas de negocio

1. **Un intento por flecha:** se abre cuando el esqueleto dispara (lo hará el WP-24B), con el mob, el objetivo, el ataque, el tick y si el objetivo era invulnerable en ese momento. Un mob puede tener **varias** flechas en vuelo (`arquitectura.md`). Abrir dos veces la misma flecha es un error de programación.
2. **Contacto** (`ProjectileHitEvent`), solo el primero de cada flecha:
   - sin entidad tocada: `BLOCK`;
   - el jugador objetivo de ese intento: `TARGET`;
   - un mob que es miembro de cualquier grupo activo: `ALLY` (regla 4, neutral);
   - cualquier otra entidad, incluido otro jugador: `OTHER_ENTITY`.
3. **Daño** (`EntityDamageByEntityEvent` cuyo atacante es la flecha): cuenta solo si la víctima es el objetivo del intento, y solo el primero. Lleva el daño real (final más lo absorbido), si el escudo bloqueó y si otro plugin lo canceló, igual que en el cuerpo a cuerpo.
4. **Cierre:** en cada tick del plugin se cierran, en el orden en que se abrieron:
   - los intentos que ya tienen contacto. El daño llega en el mismo tick que el contacto (hallazgo 5), y el cierre corre al empezar el tick siguiente, así que ya están los dos;
   - los que llevan `attack.projectile-timeout-ticks` (60) o más sin contacto: se cierran como vencidos (`timedOut`), que por la regla 8 es un fallo.
5. **Validez del objetivo al cerrar** (regla 1): el jugador está conectado y vivo, y, si el tirador sigue cargado, en su mismo mundo (`TargetChecks.isValidTarget`).
6. **Impactos tardíos:** un contacto o un daño de una flecha cuyo intento ya se cerró se ignoran (hallazgo 5: una flecha tardó 69 ticks en caer).
7. **Cada cierre** se registra en la memoria (`RecordOutcome`, como el cuerpo a cuerpo) y se manda a `TraceHub.attacked`, así aparece en el `mobai-debug.log` y en las trazas.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/ProjectileOpening.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/ProjectileHit.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/ProjectileClosure.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/TargetValidity.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/OpenProjectile.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/AttackTracker.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/ProjectileListener.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/scheduler/ProjectileResolver.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/PluginRuntime.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `src/test/java/io/github/nicodoou/mobai/adapter/tracker/AttackTrackerTest.java` |

**Métodos públicos nuevos en `AttackTracker`:** `openProjectile`, `recordProjectileContact`, `recordProjectileHit`, `targetOf` y `closeProjectiles` (10 en total). El rastreador es el único que conoce los intentos abiertos; separarlo en dos clases duplicaría la numeración de intentos y el registro.

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `ProjectileHitEvent` (`getEntity()` devuelve el `Projectile`, `getHitEntity()` y `getHitBlock()` pueden ser `null`), `Projectile.getShooter()`, `EntityDamageByEntityEvent.getDamager()`.

### Records y tipos nuevos (`adapter.tracker`)

```java
public record ProjectileOpening(
    UUID projectile, MobId mob, PlayerId target, Attack attack, long tick, boolean targetInvulnerable) {
  // requireNonNull de projectile, mob, target y attack con "ProjectileOpening.<campo>";
  // tick negativo: IllegalArgumentException "ProjectileOpening.tick must be zero or positive, got " + tick
}
```

```java
public record ProjectileHit(
    UUID projectile, PlayerId victim, double realDamage, boolean blocked, boolean cancelled) {
  // requireNonNull de projectile y victim; realDamage como en MeleeHit:
  // "ProjectileHit.realDamage must be zero or positive, got " + realDamage
}
```

```java
/** A projectile attempt that was just resolved, for the trace. */
public record ProjectileClosure(MobId mob, Classification classification) {
  // requireNonNull de los dos
}
```

```java
/** Whether the target of an attempt can still be hit when it is resolved (rule 1). */
@FunctionalInterface
public interface TargetValidity {
  boolean isValid(MobId mob, PlayerId target);
}
```

### `OpenProjectile.java` (package-private)

```java
/** The facts of one projectile attempt, gathered while the arrow flies. */
final class OpenProjectile {
  private final AttemptId id;
  private final ProjectileOpening opening;
  private ProjectileContact contact = ProjectileContact.NONE;
  private Optional<ProjectileHit> hit = Optional.empty();

  OpenProjectile(AttemptId id, ProjectileOpening opening) { … requireNonNull … }

  ProjectileOpening opening() { return opening; }

  boolean hasContact() { return contact != ProjectileContact.NONE; }

  // Only the first contact and the first damage of an arrow count.
  void touch(ProjectileContact first) {
    if (contact == ProjectileContact.NONE) {
      contact = first;
    }
  }

  void record(ProjectileHit firstHit) {
    if (hit.isEmpty()) {
      hit = Optional.of(firstHit);
    }
  }

  AttackFacts facts(boolean targetValid, boolean timedOut) {
    return new AttackFacts(
        id,
        opening.mob(),
        opening.target(),
        opening.attack(),
        opening.tick(),
        targetValid,
        opening.targetInvulnerable(),
        hit.isPresent(),
        hit.map(ProjectileHit::cancelled).orElse(false),
        hit.map(ProjectileHit::realDamage).orElse(0.0),
        hit.map(ProjectileHit::blocked).orElse(false),
        false,
        contact,
        false,
        timedOut);
  }

  double planDamage() {
    return hit.filter(found -> !found.cancelled()).map(ProjectileHit::realDamage).orElse(0.0);
  }
}
```

### `AttackTracker.java`

Campo nuevo: `private final Map<UUID, OpenProjectile> openProjectiles = new LinkedHashMap<>();` (el orden de apertura es el orden de cierre). `openAttempts()` pasa a devolver `openMelee.size() + openProjectiles.size()`.

```java
  public AttemptId openProjectile(ProjectileOpening opening) {
    if (openProjectiles.containsKey(opening.projectile())) {
      throw new IllegalStateException(
          "Projectile " + opening.projectile() + " already has an open attempt");
    }
    AttemptId id = new AttemptId(++lastAttemptNumber);
    openProjectiles.put(opening.projectile(), new OpenProjectile(id, opening));
    return id;
  }

  // Contacts of attempts already closed are ignored: an arrow can land after its timeout.
  public boolean recordProjectileContact(UUID projectile, ProjectileContact contact) {
    OpenProjectile attempt = openProjectiles.get(projectile);
    if (attempt == null) {
      return false;
    }
    attempt.touch(contact);
    return true;
  }

  public boolean recordProjectileHit(ProjectileHit hit) {
    OpenProjectile attempt = openProjectiles.get(hit.projectile());
    if (attempt == null || !attempt.opening().target().equals(hit.victim())) {
      return false;
    }
    attempt.record(hit);
    return true;
  }

  public Optional<PlayerId> targetOf(UUID projectile) {
    return Optional.ofNullable(openProjectiles.get(projectile))
        .map(attempt -> attempt.opening().target());
  }

  /** Closes, in opening order, the attempts whose arrow has landed and those past the timeout. */
  public List<ProjectileClosure> closeProjectiles(
      long now, long timeoutTicks, TargetValidity validity) {
    List<ProjectileClosure> closures = new ArrayList<>();
    for (OpenProjectile attempt : List.copyOf(openProjectiles.values())) {
      closeIfResolved(attempt, new Resolution(now, timeoutTicks, validity))
          .ifPresent(closures::add);
    }
    return closures;
  }
```

Con un record privado `private record Resolution(long now, long timeoutTicks, TargetValidity validity) {}`, para respetar los 3 parámetros, y estas funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<ProjectileClosure> closeIfResolved(OpenProjectile attempt, Resolution resolution)` | `timedOut = !attempt.hasContact() && resolution.now() - attempt.opening().tick() >= resolution.timeoutTicks()`. Si no tiene contacto y no venció: vacío. Si no, `closeProjectile(attempt, timedOut, resolution)` |
| `ProjectileClosure closeProjectile(OpenProjectile attempt, boolean timedOut, Resolution resolution)` | Saca el intento del mapa; `targetValid = resolution.validity().isValid(mob, target)`; clasifica `attempt.facts(targetValid, timedOut)`; `recordOutcome.execute(new AttackResolution(mob, target, attack, outcome, attempt.planDamage(), resolution.now()))`; devuelve `new ProjectileClosure(mob, classification)` |

### `ProjectileListener.java` (`adapter.listener`)

```java
/** Feeds the tracker with where our mobs' arrows land and the damage they deal. */
public final class ProjectileListener implements Listener {
  private final AttackTracker tracker;
  private final VersionTranslator translator;
  private final ActiveGroups activeGroups;

  public ProjectileListener(
      AttackTracker tracker, VersionTranslator translator, ActiveGroups activeGroups) { … }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onHit(ProjectileHitEvent event) {
    UUID projectile = event.getEntity().getUniqueId();
    tracker
        .targetOf(projectile)
        .ifPresent(target -> tracker.recordProjectileContact(projectile, contactOf(event, target)));
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Projectile projectile
        && event.getEntity() instanceof Player victim) {
      tracker.recordProjectileHit(hitOf(event, projectile, victim));
    }
  }
}
```

| Función privada | Hace |
| --- | --- |
| `ProjectileContact contactOf(ProjectileHitEvent event, PlayerId target)` | Regla 2: `getHitEntity()` `null` → `BLOCK`; un `Player` con el UUID del objetivo → `TARGET`; un `Mob` con `activeGroups.groupOf(new MobId(…)).isPresent()` → `ALLY`; cualquier otra → `OTHER_ENTITY` |
| `ProjectileHit hitOf(EntityDamageByEntityEvent event, Projectile projectile, Player victim)` | `new ProjectileHit(projectile.getUniqueId(), new PlayerId(victim.getUniqueId()), Math.max(0, event.getFinalDamage() + translator.absorbedDamage(event)), translator.wasBlocked(event), event.isCancelled())` |

`DamageListener` no cambia: solo mira atacantes que son `Mob`, y una flecha no lo es.

### `ProjectileResolver.java` (`adapter.scheduler`)

```java
/** Closes the arrows that landed or ran out of time, once per tick, and traces each one. */
public final class ProjectileResolver {
  private final AttackTracker tracker;
  private final TraceHub hub;
  private final Supplier<AttackSettings> settings;

  public ProjectileResolver(AttackTracker tracker, TraceHub hub, Supplier<AttackSettings> settings) { … }

  public void tick(long now) {
    for (ProjectileClosure closure :
        tracker.closeProjectiles(now, settings.get().projectileTimeoutTicks(), this::isValid)) {
      hub.attacked(closure.mob(), now, closure.classification());
    }
  }

  // The shooter may have died while its arrow flew: then only the player is checked.
  private boolean isValid(MobId mob, PlayerId target) {
    Player player = Bukkit.getPlayer(target.value());
    if (player == null) {
      return false;
    }
    if (Bukkit.getEntity(mob.value()) instanceof Mob shooter) {
      return TargetChecks.isValidTarget(player, shooter);
    }
    return player.isOnline() && !player.isDead();
  }
}
```

### Armado

- `AdapterServices.listeners`: suma `new ProjectileListener(parts.tracker(), parts.translator(), core.activeGroups())` después de `DamageListener`.
- `AdapterServices` suma el componente `ProjectileResolver projectileResolver` **al final** del record, armado con `new ProjectileResolver(parts.tracker(), parts.debug().hub(), core.settings().section(MobAiSettings::attack))`.
- `PluginRuntime.runTick`: `adapters.projectileResolver().tick(now);` justo después de `adapters.movementSampler().sampleOnlinePlayers();` (antes de las decisiones: el daño de las flechas ya cuenta para el plan de ese tick).

### `docs/actualizar-paper.md`

- Fila nueva `adapter/listener/ProjectileListener`: `ProjectileHitEvent` (`getEntity`, `getHitEntity`), `EntityDamageByEntityEvent` (`getDamager`, `getEntity`, `getFinalDamage`, `isCancelled`), `Projectile`, `Mob`, `Player` | **Medio** | Que el impacto y el daño de una flecha sigan llegando en el mismo tick (hallazgo 5) y que el atacante del daño siga siendo la flecha y no el tirador.
- Fila nueva `adapter/scheduler/ProjectileResolver`: `Bukkit.getPlayer`, `Bukkit.getEntity`, `Player.isOnline`/`isDead` | Bajo | —.

## Pruebas obligatorias

### `AttackTrackerTest` (+11)

En el constructor de la prueba se suma `activeGroups.join(groupId(1), mob(2), MobKind.SKELETON);`. Helpers nuevos: `private static UUID arrow(long n) { return new UUID(3, n); }`, `private ProjectileOpening shot(UUID arrow, boolean invulnerable)` = `new ProjectileOpening(arrow, mob(2), player, Attack.SKELETON_DIRECT_SHOT, TICK, invulnerable)` y `private static final TargetValidity VALID = (mob, target) -> true;`. El registro se lee con `group.memory().attackRecords().get(player).get(Attack.SKELETON_DIRECT_SHOT)`.

| Prueba | Verifica |
| --- | --- |
| `arrowThatHitsTheTargetIsAHit` | `openProjectile(shot(arrow(1), false))`, contacto `TARGET`, `recordProjectileHit(arrow(1), player, 4.0, false, false)` devuelve `true`; `closeProjectiles(TICK + 6, 60, VALID)`: una clausura de `mob(2)`, `Hit`, regla 6; registro `AttackRecord(1.0, 1.0, TICK + 6)`; `openAttempts()` 0 |
| `arrowIntoABlockIsAMiss` | contacto `BLOCK`: `Miss`, regla 8 |
| `arrowIntoAnAllyIsNeutral` | contacto `ALLY`: `Neutral(ALLY_HIT)`, regla 4; sin registro en la memoria |
| `arrowAgainstARaisedShieldIsPartial` | contacto `TARGET` y daño `0.0` bloqueado: `Partial`, regla 7 |
| `arrowInFlightStaysOpenUntilTheTimeout` | sin contacto: `closeProjectiles(TICK + 59, 60, VALID)` vacío y `openAttempts()` 1; en `TICK + 60`: `Miss`, regla 8, con `trace().facts().timedOut()` `true` |
| `lateLandingAfterTheTimeoutIsIgnored` | cerrado por plazo en `TICK + 60`; después `recordProjectileContact(arrow(1), TARGET)` devuelve `false` y `targetOf(arrow(1))` está vacío |
| `damageToAnotherPlayerIsNotRecorded` | `recordProjectileHit(arrow(1), otherPlayer, 4.0, false, false)` devuelve `false`; contacto `OTHER_ENTITY`: `Miss`, regla 8 |
| `invalidTargetOfAnArrowIsNeutral` | contacto `TARGET` y daño 4.0, `closeProjectiles` con `(mob, target) -> false`: `Neutral(TARGET_INVALID)`, regla 1 |
| `severalArrowsCloseInOpeningOrder` | `arrow(1)` y `arrow(2)` abiertas en ese orden, las dos con contacto `BLOCK`: dos clausuras, la primera con `trace().facts().attemptId()` menor que la segunda |
| `onlyTheFirstContactCounts` | contacto `ALLY` y después `TARGET`, con daño 4.0 al objetivo: `Neutral(ALLY_HIT)`, regla 4 (el primer contacto manda) |
| `sameArrowCannotOpenTwice` | segundo `openProjectile(shot(arrow(1), false))`: `IllegalStateException` con `Projectile 00000000-0000-0003-0000-000000000001 already has an open attempt` |

`ProjectileListener` y `ProjectileResolver` usan Paper y se verifican con el WP-24B en el server.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `closeIfResolved`, cerrar solo por plazo (ignorar el contacto) | en `TICK + 6` no hay clausuras | `arrowThatHitsTheTargetIsAHit` |
| 2 | Plazo con `>` en vez de `>=` | en `TICK + 60` sigue abierta | `arrowInFlightStaysOpenUntilTheTimeout` |
| 3 | En `recordProjectileHit`, no comparar la víctima | devuelve `true` | `damageToAnotherPlayerIsNotRecorded` |
| 4 | En `OpenProjectile.touch`, guardar siempre el último contacto | queda `TARGET` y da `Hit` | `onlyTheFirstContactCounts` |

## Procedimiento

1. Rama `wp-24a-rastreo-de-flechas` desde `origin/main` actualizado.
2. Los records, `TargetValidity`, `OpenProjectile`, `AttackTracker` y `AttackTrackerTest`. Commit: `feat: track projectile attempts by arrow`.
3. `ProjectileListener`, `ProjectileResolver`, `AdapterServices`, `PluginRuntime`. Commit: `feat: resolve landed and timed-out arrows every tick`.
4. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the arrow tracker`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-24A: arrow tracking`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `RecordOutcome` rechaza un ataque de esqueleto de un mob unido como esqueleto, frená y reportá el mensaje.
3. Si la regla de ArchUnit de métodos públicos se queja de `AttackTracker`, frená y reportá.

## Fuera de alcance

- Disparar (`ShootGoal`, `BowShooter`), instalar goals en esqueletos y el disparo oportuno: WP-24B.
- Cambiar el dominio o las reglas del rastreador.
- Flechas de jugadores o de mobs que no son del plugin (el rastreador las ignora porque no tienen intento abierto).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 11 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
