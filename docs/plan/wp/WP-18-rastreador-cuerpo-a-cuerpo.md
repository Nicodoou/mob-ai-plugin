# WP-18 — Rastreador cuerpo a cuerpo

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E5 Esqueleto vivo |
| Depende de | WP-17 (mergeado) y puerta E4 (pasada) |
| Modelo | Sonnet |
| Rama | `wp-18-rastreador-cuerpo-a-cuerpo` |

## Objetivo

1. **`AttackTracker`**: abre un intento cuando un goal ataca cuerpo a cuerpo, junta los hechos del evento de daño, lo cierra, lo clasifica con `AttackClassifier` (dominio) y registra el resultado con `RecordOutcome`. **Es Java puro:** recibe valores simples, no eventos de Paper, así que se prueba entero con JUnit.
2. **Listeners de Paper** que traducen eventos a llamadas: golpes de nuestros mobs (`DamageListener`), golpes de jugadores a nuestros mobs (`ThreatListener`), y muertes y bajas (`DeathListener`).
3. **`TargetChecks`**: si el objetivo es invulnerable al abrir el intento y si sigue siendo válido al cerrarlo.

Lo usa el WP-19: su `MeleeAttacker` hace `openMelee`, después `mob.attack(target)` y después `closeMelee`.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 2, 3, 4, 6 y 7)
- `src/main/java/io/github/nicodoou/mobai/domain/attack/AttackFacts.java`, `AttackClassifier.java`, `AttackOutcome.java`, `Classification.java`, `ProjectileContact.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/AttemptId.java`, `Attack.java`
- `src/main/java/io/github/nicodoou/mobai/application/RecordOutcome.java`, `AttackResolution.java`, `RecordDamageTaken.java`, `DamageTaken.java`, `RecordPlayerDeath.java`, `RemoveMember.java`, `RemovalCause.java`, `ActiveGroups.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`absorbedDamage`, `wasBlocked`)
- `src/test/java/io/github/nicodoou/mobai/application/RecordOutcomeTest.java` (cómo se arma un grupo con un miembro en las pruebas)

## Reglas de negocio

1. **Un intento cuerpo a cuerpo vive dentro de una sola llamada** (hallazgo 2): el goal lo abre, llama a `mob.attack(target)` (el evento de daño llega adentro) y lo cierra enseguida. No hay plazo.
2. **Un mob tiene como máximo un intento cuerpo a cuerpo abierto.** Abrir otro con uno abierto es un error del goal: `IllegalStateException`.
3. **Hechos del evento** (`MeleeHit`), solo si el golpe es del mob que tiene el intento abierto **y** contra su objetivo; cualquier otro golpe (fuego amigo, otro jugador) se ignora. Solo cuenta el primer evento de un intento.
   - daño real = daño final + daño absorbido (hallazgo 4);
   - bloqueado = modificador `BLOCKING` distinto de 0 (hallazgo 3);
   - cancelado por otro plugin = `isCancelled()`.
4. **Al cerrar**, se arma `AttackFacts`:
   - `targetValid` lo informa quien cierra (el objetivo sigue conectado, vivo y en el mismo mundo que el mob);
   - `targetInvulnerableAtOpen` lo informó quien abrió;
   - `damageEventReceived` según llegó o no el evento;
   - para cuerpo a cuerpo: `projectileContact` `NONE`, `interruptedBeforeResolution`, `timedOut` y `shieldDisabled` en `false` (los mobs del MVP no usan hacha).
5. **Registrar:** `RecordOutcome` con el resultado clasificado. El daño que se suma al plan es el daño real si el golpe no fue cancelado; si fue cancelado, 0.
6. **Cancelar** un intento (el mob murió, se descargó o salió del grupo) lo descarta sin registrar nada.
7. **Invulnerable al abrir** (hallazgo 4): modo creativo o espectador, o `noDamageTicks` mayor que la mitad de `maximumNoDamageTicks`.
8. **Amenaza:** cada golpe no cancelado de un jugador (directo o con un proyectil que disparó él) a un mob con daño final mayor a 0 llama a `RecordDamageTaken`. `RecordDamageTaken` ignora a los mobs que no son miembros.
9. **Muertes y bajas** (hallazgo 6):
   - muere un jugador → `RecordPlayerDeath`;
   - muere un mob → `RemoveMember` con `DIED`;
   - `EntityRemoveEvent` de un mob con causa distinta de `DEATH` y de `UNLOAD` → `RemoveMember` con `DESPAWNED`. `DEATH` ya lo cubre la muerte; `UNLOAD` no es una baja (el spike mostró que la descarga no dispara este evento, pero la causa existe en la API y se ignora por las dudas).
   - `RemoveMember` con un mob que no es miembro no hace nada, así que no hace falta filtrar antes.
10. **Prioridad de los listeners:** `MONITOR`, para ver el resultado final de los demás plugins. `DamageListener` escucha también los cancelados (`ignoreCancelled = false`), porque la cancelación es un hecho del intento (regla 2 del rastreador); `ThreatListener` y `DeathListener` los ignoran.

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/MeleeOpening.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/MeleeHit.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/OpenAttempt.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/AttackTracker.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/tracker/TargetChecks.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/DamageListener.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/ThreatListener.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/listener/DeathListener.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/tracker/AttackTrackerTest.java` |

`ThreatListener` es nuevo respecto del mapa del código: separar la amenaza del rastreo de golpes deja a cada listener con una sola tarea y con 3 dependencias como máximo.

## Especificación

Imports a tu criterio; Spotless decide el formato.

### `MeleeOpening.java` y `MeleeHit.java`

```java
public record MeleeOpening(
    MobId mob, PlayerId target, Attack attack, long tick, boolean targetInvulnerable) {
  public MeleeOpening {
    Objects.requireNonNull(mob, "MeleeOpening.mob");
    Objects.requireNonNull(target, "MeleeOpening.target");
    Objects.requireNonNull(attack, "MeleeOpening.attack");
    if (tick < 0) {
      throw new IllegalArgumentException("MeleeOpening.tick must be zero or positive, got " + tick);
    }
  }
}

public record MeleeHit(
    MobId attacker, PlayerId victim, double realDamage, boolean blocked, boolean cancelled) {
  public MeleeHit {
    Objects.requireNonNull(attacker, "MeleeHit.attacker");
    Objects.requireNonNull(victim, "MeleeHit.victim");
    if (!(realDamage >= 0) || !Double.isFinite(realDamage)) {
      throw new IllegalArgumentException(
          "MeleeHit.realDamage must be zero or positive, got " + realDamage);
    }
  }
}
```

### `OpenAttempt.java` (package-private, `final`)

```java
/** The facts of one melee attempt, gathered between opening it and closing it. */
final class OpenAttempt {
  private final AttemptId id;
  private final MeleeOpening opening;
  private Optional<MeleeHit> hit = Optional.empty();

  OpenAttempt(AttemptId id, MeleeOpening opening) { … }

  MeleeOpening opening() { return opening; }

  // Only the first damage event of an attempt counts.
  void record(MeleeHit firstHit) {
    if (hit.isEmpty()) {
      hit = Optional.of(firstHit);
    }
  }

  AttackFacts facts(boolean targetValid) { … regla 4 … }

  double planDamage() {
    return hit.filter(found -> !found.cancelled()).map(MeleeHit::realDamage).orElse(0.0);
  }
}
```

`facts`: `new AttackFacts(id, opening.mob(), opening.target(), opening.attack(), opening.tick(), targetValid, opening.targetInvulnerable(), hit.isPresent(), cancelado, dañoReal, bloqueado, false, ProjectileContact.NONE, false, false)`, con cancelado, daño real y bloqueado del `hit` (o `false`, 0 y `false` si no hubo evento).

### `AttackTracker.java`

```java
/** Opens, gathers and closes attack attempts; the domain decides what each one was worth. */
public final class AttackTracker {
  private final RecordOutcome recordOutcome;
  private final AttackClassifier classifier;
  private final Map<MobId, OpenAttempt> openMelee = new HashMap<>();
  private long lastAttemptNumber;

  public AttackTracker(RecordOutcome recordOutcome, AttackClassifier classifier) { … }

  public AttemptId openMelee(MeleeOpening opening) {
    if (openMelee.containsKey(opening.mob())) {
      throw new IllegalStateException(
          "Mob " + opening.mob().shortId() + " already has an open melee attempt");
    }
    AttemptId id = new AttemptId(++lastAttemptNumber);
    openMelee.put(opening.mob(), new OpenAttempt(id, opening));
    return id;
  }

  public boolean recordHit(MeleeHit hit) {
    OpenAttempt attempt = openMelee.get(hit.attacker());
    if (attempt == null || !attempt.opening().target().equals(hit.victim())) {
      return false;
    }
    attempt.record(hit);
    return true;
  }

  public Optional<Classification> closeMelee(MobId mob, boolean targetValid, long tick) {
    OpenAttempt attempt = openMelee.remove(mob);
    if (attempt == null) {
      return Optional.empty();
    }
    Classification classification = classifier.classify(attempt.facts(targetValid));
    recordOutcome.execute(resolution(attempt, classification, tick));
    return Optional.of(classification);
  }

  public void cancel(MobId mob) {
    openMelee.remove(mob);
  }

  public int openAttempts() {
    return openMelee.size();
  }

  private static AttackResolution resolution(OpenAttempt attempt, Classification classification, long tick) {
    MeleeOpening opening = attempt.opening();
    return new AttackResolution(
        opening.mob(), opening.target(), opening.attack(), classification.outcome(), attempt.planDamage(), tick);
  }
}
```

`closeMelee` devuelve la clasificación para que el log de debug (WP-21 y WP-29) pueda mostrar la regla que aplicó.

### `TargetChecks.java` (Paper)

```java
/** Target conditions the tracker needs, read from the live entities. */
public final class TargetChecks {
  private TargetChecks() {}

  // Inside the post-hit window a weaker hit raises no event at all (spike finding 4).
  public static boolean isInvulnerable(Player target) {
    GameMode mode = target.getGameMode();
    return mode == GameMode.CREATIVE
        || mode == GameMode.SPECTATOR
        || target.getNoDamageTicks() > target.getMaximumNoDamageTicks() / 2.0;
  }

  public static boolean isValidTarget(Player target, Mob attacker) {
    return target.isOnline() && !target.isDead() && target.getWorld().equals(attacker.getWorld());
  }
}
```

### `DamageListener.java` (Paper)

```java
/** Feeds the tracker with the damage our mobs deal to their targets. */
public final class DamageListener implements Listener {
  private final AttackTracker tracker;
  private final VersionTranslator translator;

  public DamageListener(AttackTracker tracker, VersionTranslator translator) { … }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Mob mob && event.getEntity() instanceof Player victim) {
      tracker.recordHit(hitOf(event, mob, victim));
    }
  }

  private MeleeHit hitOf(EntityDamageByEntityEvent event, Mob mob, Player victim) {
    return new MeleeHit(
        new MobId(mob.getUniqueId()),
        new PlayerId(victim.getUniqueId()),
        Math.max(0, event.getFinalDamage() + translator.absorbedDamage(event)),
        translator.wasBlocked(event),
        event.isCancelled());
  }
}
```

(`Math.max(0, …)`: con un evento cancelado, el daño final puede venir en valores raros; el hecho no admite negativos.)

### `ThreatListener.java` (Paper)

```java
/** Turns damage that players deal to our mobs into threat. */
public final class ThreatListener implements Listener {
  private final RecordDamageTaken recordDamageTaken;
  private final ServerClock clock;

  public ThreatListener(RecordDamageTaken recordDamageTaken, ServerClock clock) { … }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDamage(EntityDamageByEntityEvent event) {
    if (!(event.getEntity() instanceof Mob mob) || event.getFinalDamage() <= 0) {
      return;
    }
    attackingPlayer(event.getDamager())
        .ifPresent(player -> recordDamageTaken.execute(damageTaken(event, mob, player)));
  }

  private static Optional<Player> attackingPlayer(Entity damager) { … jugador directo, o el que disparó el proyectil … }

  private DamageTaken damageTaken(EntityDamageByEntityEvent event, Mob mob, Player player) { … con clock.currentTick() … }
}
```

`attackingPlayer`: si `damager` es `Player`, ese; si es `Projectile` y `getShooter()` es `Player`, ese; si no, vacío.

### `DeathListener.java` (Paper)

```java
/** Members leave their group when they die or are removed; a dying target closes its plans. */
public final class DeathListener implements Listener {
  private final RemoveMember removeMember;
  private final RecordPlayerDeath recordPlayerDeath;
  private final ServerClock clock;

  public DeathListener(RemoveMember removeMember, RecordPlayerDeath recordPlayerDeath, ServerClock clock) { … }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onDeath(EntityDeathEvent event) {
    long tick = clock.currentTick();
    if (event.getEntity() instanceof Player player) {
      recordPlayerDeath.execute(new PlayerId(player.getUniqueId()), tick);
      return;
    }
    removeMember.execute(new MobId(event.getEntity().getUniqueId()), RemovalCause.DIED, tick);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onRemove(EntityRemoveEvent event) {
    if (isMemberExit(event.getCause())) {
      removeMember.execute(
          new MobId(event.getEntity().getUniqueId()), RemovalCause.DESPAWNED, clock.currentTick());
    }
  }

  // DEATH is handled by onDeath; UNLOAD keeps the mob in its group.
  private static boolean isMemberExit(EntityRemoveEvent.Cause cause) {
    return cause != EntityRemoveEvent.Cause.DEATH && cause != EntityRemoveEvent.Cause.UNLOAD;
  }
}
```

Los nombres de eventos y de las causas están verificados con `javap` contra `paper-api 26.3.build.151-beta` (`EntityRemoveEvent.Cause` tiene `DEATH`, `DESPAWN`, `UNLOAD`, `PLUGIN`, entre otras). Usá `==` con las constantes, no `switch` (la lección del WP-17).

## Pruebas obligatorias

### `AttackTrackerTest` (13)

Armado: `ActiveGroups` con un grupo (`newGroup(1)`) y `mob(1)` zombie unido; `SettingsHolder(TestSettings.defaults())`; `AttackTracker(new RecordOutcome(activeGroups, settings), new AttackClassifier())`. `opening()` = `new MeleeOpening(mob(1), player, Attack.ZOMBIE_FRONT_STRIKE, 100, false)`. Para leer la memoria: `group.memory().attackRecords().get(player).get(Attack.ZOMBIE_FRONT_STRIKE)`.

| Prueba | Verifica |
| --- | --- |
| `hitRecordsAHit` | abrir, `recordHit(new MeleeHit(mob(1), player, 3.0, false, false))`, cerrar con `targetValid` `true` en 100: `Hit`, regla 6, memoria `AttackRecord(1.0, 1.0, 100)` |
| `blockedHitRecordsAPartial` | hit con daño 0 y `blocked` `true`: `Partial`, regla 7, memoria `successes` 0.5 |
| `noEventRecordsAMiss` | cerrar sin hit: `Miss`, regla 8, memoria `AttackRecord(0.0, 1.0, 100)` |
| `invulnerableTargetWithoutEventIsNeutral` | `MeleeOpening` con `targetInvulnerable` `true`, sin hit: `Neutral(TARGET_INVULNERABLE)`, regla 3, y la memoria sin registros de `player` |
| `cancelledDamageIsNeutral` | hit con daño 3 y `cancelled` `true`: `Neutral(DAMAGE_CANCELLED)`, regla 2 |
| `invalidTargetIsNeutral` | hit con daño 3, cerrar con `targetValid` `false`: `Neutral(TARGET_INVALID)`, regla 1 |
| `hitOnAnotherPlayerIsIgnored` | `recordHit` contra `otherPlayer` devuelve `false`; al cerrar, `Miss` |
| `onlyTheFirstEventCounts` | primero hit bloqueado con daño 0, después hit con daño 3: `Partial` |
| `secondOpeningIsRejected` | dos `openMelee` del mismo mob: `"Mob " + mob(1).shortId() + " already has an open melee attempt"` |
| `closingWithoutAnOpenAttemptReturnsEmpty` | `closeMelee(mob(1), true, 100)` sin abrir: vacío y memoria sin cambios |
| `cancelDiscardsWithoutRecording` | abrir, `cancel(mob(1))`, cerrar: vacío, `openAttempts()` 0, memoria sin registros |
| `everyAttemptEndsExactlyOnce` | abrir, cerrar dos veces: la primera presente, la segunda vacía, `openAttempts()` 0 y memoria con `attempts` 1.0 |
| `planDamageCountsOnlyUncancelledHits` | grupo ejecutando contra `player` (`PlanStart` como en el WP-13); un intento con hit de 3 no cancelado y otro (después de cerrar el primero) con hit de 4 cancelado: `plan().orElseThrow().damageDealt()` = 3.0 |

Además, los ids de intento crecen: en `everyAttemptEndsExactlyOnce`, un segundo `openMelee` después de cerrar devuelve `new AttemptId(2)`.

Total: **13 pruebas**. Los listeners y `TargetChecks` se verifican en el server en la puerta E5 (D20: sin MockBukkit).

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `recordHit`, no comparar la víctima con el objetivo | el golpe a `otherPlayer` cuenta: `Hit` | `hitOnAnotherPlayerIsIgnored` |
| 2 | En `OpenAttempt.record`, quedarse con el último evento | `Hit` en vez de `Partial` | `onlyTheFirstEventCounts` |
| 3 | En `planDamage`, no filtrar los cancelados | daño del plan 7.0 | `planDamageCountsOnlyUncancelledHits` |
| 4 | En `closeMelee`, usar `get` en vez de `remove` | el segundo cierre vuelve a registrar: `attempts` 2.0 | `everyAttemptEndsExactlyOnce` |

## Procedimiento

1. Rama `wp-18-rastreador-cuerpo-a-cuerpo` desde `origin/main` actualizado.
2. `MeleeOpening`, `MeleeHit`, `OpenAttempt`, `AttackTracker` con `AttackTrackerTest`. Commit: `feat: track melee attempts and record their outcome`.
3. `TargetChecks`, `DamageListener`, `ThreatListener`, `DeathListener`. Commit: `feat: listen to damage, deaths and removals`.
4. Pruebas que muerden (de a una, en secuencia; sin commit).
5. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
6. Push, PR `WP-18: melee attack tracker and listeners`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo en el informe), informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca alguna advertencia en los listeners (por ejemplo por un método deprecado de Paper que no está en este WP), frená y reportá cuál: no la suprimas.

## Fuera de alcance

- Quién llama a `openMelee` y `closeMelee` (`MeleeAttacker`, WP-19), y cancelar intentos al descargarse un chunk (WP-19).
- Registrar los listeners en el plugin (WP-20).
- Proyectiles, plazos e interrupciones (WP-24).
- Cambios de objetivo vanilla (`TargetListener`, WP-19).

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas y mensajes especificados.
- [ ] Las 13 pruebas con sus nombres exactos, en verde.
- [ ] Las 4 roturas mordieron.
- [ ] Ninguna constante sensible a la versión fuera de `VersionTranslator` (lo verifica `ArchitectureTest`).
- [ ] Build, cobertura y CI en verde.
