# WP-25 — Arañas: mordida con lentitud

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-24 (mergeado), regla de 3 parámetros (PR #53, mergeado) |
| Modelo | Sonnet |
| Rama | `wp-25-aranas` |

## Objetivo

Que la **mordida de la araña aplique Lentitud** al jugador, como dice el catálogo: Lentitud I por 3 s, sin renovarla ni acumularla si el jugador ya tiene Lentitud.

Lo demás de la araña ya existe y no se toca:
- `SpiderTargetRule` (el más cercano, con compromiso) llega a los goals por `RoleAssignment.target` (WP-07 y WP-10B).
- `PressGoal` y `FlankGoal` ya pegan con `Attack.SPIDER_BITE` y la mordida pasa por el rastreador (WP-19 y WP-22B).
- `SpiderSettings` (`slowness-duration-ticks: 60`, `slowness-level: 1`) ya se carga y se valida, pero **nadie la usa**. Las arañas vanilla no ralentizan: hoy la mordida no deja ningún efecto.

## Decisiones tomadas en este WP

1. **Solo un acierto ralentiza** (`AttackOutcome.Hit`, daño mayor a 0). Una mordida bloqueada con el escudo (`Partial`) no llegó al jugador y no deja efecto, como el veneno de la araña de cueva en vanilla. Un fallo o un neutral, tampoco.
2. **"No se renueva ni se acumula":** si el jugador tiene Lentitud de **cualquier** nivel (de una araña, de una poción o de otra fuente), la mordida no hace nada. No hay ventana de inmunidad aparte: la duración del efecto ya es esa ventana.
3. **La regla es del dominio** (`BiteSlowness`, pura y con prueba) y **el efecto lo pone un adaptador** (`HitEffects`). El `PotionEffect` lo arma `VersionTranslator`, porque la conversión de nivel a amplificador y el tipo de efecto dependen de Paper.
4. **Dónde se engancha:** en `MeleeAttacker.strike`, el único lugar que pega cuerpo a cuerpo. Así cubre todos los goals que muerden (`PressGoal`, `FlankGoal` y los que vengan). El efecto se aplica **después** de registrar el resultado en la memoria.
5. **Regla de 3 parámetros:** `MeleeAttacker` ya tiene 3 parámetros en el constructor. El cuarto colaborador se agrupa con `TraceHub` en el record `StrikeFollowUps(TraceHub hub, HitEffects effects)`: lo que pasa después de un golpe clasificado.
6. **La configuración se lee en cada mordida** (`Supplier<SpiderSettings>`), para que `/mobai reload` la cambie en caliente (D17).
7. **Duración en Paper:** `PotionEffect` recibe la duración como `int`. Una duración configurada mayor a `Integer.MAX_VALUE` se recorta a ese valor, que en el juego es infinito. No se agrega validación nueva a `SpiderSettings`.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java`, `Weapons.java`
- `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` (`potionEffectOf`, `effectLevels`)
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (`goalInstaller`)
- `src/main/java/io/github/nicodoou/mobai/domain/attack/AttackOutcome.java`, `NeutralCause.java`, `Classification.java`
- `src/main/java/io/github/nicodoou/mobai/domain/settings/SpiderSettings.java`
- `src/main/java/io/github/nicodoou/mobai/domain/shared/Attack.java`, `EffectKind.java`
- `docs/actualizar-paper.md` (sección 3) y `docs/arquitectura.md` (tabla «Nombres en el código»), para actualizarlos
- Prueba de referencia de estilo: `src/test/java/io/github/nicodoou/mobai/domain/attack/AttackClassifierTest.java`

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/attack/EffectGrant.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/domain/attack/BiteSlowness.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/HitEffects.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/StrikeFollowUps.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/MeleeAttacker.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/translate/VersionTranslator.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`) |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/attack/EffectGrantTest.java` |
| Crear | `src/test/java/io/github/nicodoou/mobai/domain/attack/BiteSlownessTest.java` |
| Modificar | `docs/actualizar-paper.md` |
| Modificar | `docs/arquitectura.md` (tabla «Nombres en el código») |

Antes de empezar, buscá con grep `new MeleeAttacker(` en `src/`. Si aparece fuera de `AdapterServices.java`, frená y reportá.

## Especificación

### `EffectGrant.java` (dominio)

```java
package io.github.nicodoou.mobai.domain.attack;

/** An effect a landed attack leaves on the player, at a level counted from 1. */
public record EffectGrant(EffectKind kind, int level, long durationTicks) {
  public EffectGrant { … }
}
```

Validaciones, en este orden y con estos mensajes exactos:
- `Objects.requireNonNull(kind, "EffectGrant.kind")`
- `level < 1` → `IllegalArgumentException("EffectGrant.level must be at least 1, got " + level)`
- `durationTicks < 1` → `IllegalArgumentException("EffectGrant.durationTicks must be at least 1, got " + durationTicks)`

`EffectKind` se importa de `io.github.nicodoou.mobai.domain.shared`.

### `BiteSlowness.java` (dominio)

```java
package io.github.nicodoou.mobai.domain.attack;

/** A spider bite that lands slows the player; an active slowness is neither renewed nor stacked. */
public final class BiteSlowness {
  private final Supplier<SpiderSettings> settings;

  public BiteSlowness(Supplier<SpiderSettings> settings) { … requireNonNull(settings, "BiteSlowness.settings") … }

  public Optional<EffectGrant> afterAttack(
      Attack attack, AttackOutcome outcome, int currentSlownessLevel) { … }
}
```

`afterAttack`, en este orden:
1. `Objects.requireNonNull(attack, "BiteSlowness.attack")` y `Objects.requireNonNull(outcome, "BiteSlowness.outcome")`.
2. `currentSlownessLevel < 0` → `IllegalArgumentException("BiteSlowness.currentSlownessLevel must be zero or positive, got " + currentSlownessLevel)`. Las validaciones van en un método privado `requireValid(...)`, para que `afterAttack` coordine y no valide y decida a la vez.
3. Devuelve `Optional.empty()` si `attack != Attack.SPIDER_BITE`, si `!(outcome instanceof AttackOutcome.Hit)` o si `currentSlownessLevel > 0`. Usá un método privado `boolean slows(Attack attack, AttackOutcome outcome, int currentSlownessLevel)` con un retorno por condición.
4. Si no, lee `settings.get()` **una vez en esta llamada** y devuelve `Optional.of(new EffectGrant(EffectKind.SLOWNESS, spider.slownessLevel(), spider.slownessDurationTicks()))`.

Comentario de porqué, encima de la condición del `Hit` (el único comentario de la clase):
`// A bite the shield stopped never reached the player, so it carries no venom.`

### `HitEffects.java` (adaptador)

```java
package io.github.nicodoou.mobai.adapter.goal;

/** Effects a landed strike leaves on the player, such as the spider's slowness. */
public final class HitEffects {
  private static final int ABSENT_LEVEL = 0;

  private final BiteSlowness biteSlowness;
  private final VersionTranslator translator;

  public HitEffects(BiteSlowness biteSlowness, VersionTranslator translator) { … requireNonNull con "HitEffects.biteSlowness" y "HitEffects.translator" … }

  public void apply(Player target, Attack attack, AttackOutcome outcome) {
    int currentSlowness =
        translator.effectLevels(target).getOrDefault(EffectKind.SLOWNESS, ABSENT_LEVEL);
    biteSlowness
        .afterAttack(attack, outcome, currentSlowness)
        .ifPresent(grant -> target.addPotionEffect(translator.potionEffectOf(grant)));
  }
}
```

`Player` es `org.bukkit.entity.Player`. `addPotionEffect(PotionEffect)` devuelve `boolean`: se ignora. No uses la sobrecarga `addPotionEffect(PotionEffect, boolean)`, que está deprecada.

### `StrikeFollowUps.java` (adaptador)

```java
package io.github.nicodoou.mobai.adapter.goal;

/** What follows a classified melee strike: its trace and the effects it leaves. */
public record StrikeFollowUps(TraceHub hub, HitEffects effects) {
  public StrikeFollowUps { … requireNonNull con "StrikeFollowUps.hub" y "StrikeFollowUps.effects" … }
}
```

### `MeleeAttacker.java`

- **Constructor:** pasa a `MeleeAttacker(AttackTracker tracker, ServerClock clock, StrikeFollowUps followUps)`, con `requireNonNull(followUps, "MeleeAttacker.followUps")`. El campo `hub` se reemplaza por `followUps`.
- **`strike`:** la línea `classification.ifPresent(found -> hub.attacked(mobId, tick, found));` pasa a ser estas dos, en este orden:

```java
classification.ifPresent(found -> followUps.hub().attacked(mobId, tick, found));
classification.ifPresent(
    found -> followUps.effects().apply(target, attack, found.outcome()));
```

Nada más cambia en la clase (el javadoc y `attackOrCancel` quedan igual).

### `VersionTranslator.java`

1. Constante privada nueva: `private static final int AMPLIFIER_OFFSET = 1;`, con el comentario `// Paper counts amplifiers from 0: level I is amplifier 0.`
2. En `effectLevels`, `effect.getAmplifier() + 1` pasa a `effect.getAmplifier() + AMPLIFIER_OFFSET`.
3. Método público nuevo, debajo de `potionEffectOf(EffectKind)`:

```java
public PotionEffect potionEffectOf(EffectGrant grant) {
  return new PotionEffect(
      potionEffectOf(grant.kind()), durationOf(grant), grant.level() - AMPLIFIER_OFFSET);
}

// Paper durations are ints; anything longer already lasts forever in the game.
private static int durationOf(EffectGrant grant) {
  return (int) Math.min(grant.durationTicks(), Integer.MAX_VALUE);
}
```

`VersionTranslator` pasa de 16 a 17 métodos públicos. Se justifica porque es la única clase que puede convertir a tipos de efecto de Paper.

### `AdapterServices.java` (`goalInstaller`)

Reemplazá la línea del `MeleeAttacker` por:

```java
MeleeAttacker attacker =
    new MeleeAttacker(
        parts.tracker(),
        core.clock(),
        new StrikeFollowUps(
            parts.debug().hub(),
            new HitEffects(
                new BiteSlowness(core.settings().section(MobAiSettings::spider)),
                parts.translator())));
```

Si `core.settings().section(...)` no devuelve un `Supplier<SpiderSettings>`, frená y reportá.

### `docs/actualizar-paper.md` (sección 3)

- En la fila de `adapter/translate/VersionTranslator` (la primera), sumá al final de «Qué usa»: `` `new PotionEffect(PotionEffectType, int, int)` (`potionEffectOf(EffectGrant)`) ``. Y al final de «Qué revisar»: `Que el constructor de PotionEffect con duración y amplificador siga sin estar deprecado y que el amplificador siga contando desde 0.`
- Fila nueva, debajo de la de `adapter/goal/*`:

| `adapter/goal/HitEffects` | `LivingEntity.addPotionEffect(PotionEffect)` | Bajo | Que la versión de un parámetro siga sin estar deprecada (la de dos, con `force`, ya lo está) y que no pise un efecto más fuerte |

### `docs/arquitectura.md` (tabla «Nombres en el código»)

Fila nueva, debajo de la de `SpiderTargetRule`:

| Lentitud de la mordida, efecto que deja un golpe | `BiteSlowness`, `EffectGrant` | Dominio |

Y otra fila, debajo de esa:

| Efectos de un golpe en el jugador, lo que sigue a un golpe cuerpo a cuerpo | `HitEffects`, `StrikeFollowUps` | Adaptador |

## Pruebas obligatorias

Usá `new AttackOutcome.Hit()`, `new AttackOutcome.Partial()`, `new AttackOutcome.Miss()` y `new AttackOutcome.Neutral(NeutralCause.INTERRUPTED)`.

### `EffectGrantTest` (3)

| Prueba | Verifica |
| --- | --- |
| `levelBelowOneIsRejected` | `new EffectGrant(SLOWNESS, 0, 60)` lanza `IllegalArgumentException` con mensaje `EffectGrant.level must be at least 1, got 0` |
| `durationBelowOneIsRejected` | `new EffectGrant(SLOWNESS, 1, 0)` lanza con mensaje `EffectGrant.durationTicks must be at least 1, got 0` |
| `kindIsRequired` | `new EffectGrant(null, 1, 60)` lanza `NullPointerException` con mensaje `EffectGrant.kind` |

### `BiteSlownessTest` (9)

Salvo que la fila diga otra cosa, la configuración es `new SpiderSettings(60, 1)` detrás de un supplier.

| Prueba | Verifica |
| --- | --- |
| `aBiteThatHitsGrantsTheConfiguredSlowness` | `SPIDER_BITE`, `Hit`, nivel actual 0 → `Optional.of(new EffectGrant(SLOWNESS, 1, 60))` |
| `levelAndDurationComeFromTheSettings` | configuración `(100, 2)`; `SPIDER_BITE`, `Hit`, 0 → `EffectGrant(SLOWNESS, 2, 100)` |
| `anActiveSlownessIsNotRenewed` | `SPIDER_BITE`, `Hit`, nivel actual 1 → vacío |
| `aWeakerSlownessIsNotUpgraded` | configuración `(60, 2)`; `SPIDER_BITE`, `Hit`, nivel actual 1 → vacío |
| `aBlockedBiteGrantsNothing` | `SPIDER_BITE`, `Partial`, 0 → vacío |
| `aMissedOrNeutralBiteGrantsNothing` | `SPIDER_BITE` con `Miss` y con `Neutral(INTERRUPTED)`, 0 → los dos vacíos |
| `otherAttacksGrantNothing` | `ZOMBIE_FRONT_STRIKE`, `Hit`, 0 → vacío |
| `settingsAreReadOnEveryBite` | supplier sobre un `AtomicReference<SpiderSettings>`: primera mordida con `(60, 1)` da nivel 1 y 60; se cambia a `(100, 2)` y la segunda da `EffectGrant(SLOWNESS, 2, 100)` |
| `negativeCurrentLevelIsRejected` | nivel actual -1 lanza `IllegalArgumentException` con mensaje `BiteSlowness.currentSlownessLevel must be zero or positive, got -1` |

Total: **12 pruebas**. `HitEffects`, `StrikeFollowUps`, `MeleeAttacker` y `VersionTranslator.potionEffectOf(EffectGrant)` usan Paper (`PotionEffectType` necesita un server para inicializarse) y se verifican en el juego.

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `slows`, sacar la condición `currentSlownessLevel > 0` | `EffectGrant(SLOWNESS, 1, 60)` en vez de vacío | `anActiveSlownessIsNotRenewed` (y `aWeakerSlownessIsNotUpgraded`) |
| 2 | Cambiar `!(outcome instanceof AttackOutcome.Hit)` por `outcome instanceof AttackOutcome.Miss` | `Partial` da `EffectGrant(SLOWNESS, 1, 60)` | `aBlockedBiteGrantsNothing` (y `aMissedOrNeutralBiteGrantsNothing`, por el neutral) |
| 3 | Leer `settings.get()` una sola vez, en el constructor, y guardar el `SpiderSettings` | la segunda mordida da `(1, 60)` | `settingsAreReadOnEveryBite` |
| 4 | Usar el nivel fijo `1` en vez de `spider.slownessLevel()` | `EffectGrant(SLOWNESS, 1, 100)` | `levelAndDurationComeFromTheSettings` |

## Verificación en el juego (Nico, después del merge)

Con `/mobai spawngroup` de noche, en supervivencia:

1. Una araña te muerde: aparece el ícono de Lentitud I y dura 3 s.
2. Con la Lentitud activa, otra mordida no la renueva: el contador no vuelve a 3 s.
3. Una mordida contra el escudo levantado no deja Lentitud.
4. Si tomás una poción de Lentitud II y te muerde, sigue la II con su duración.
5. `/mobai reload` con `slowness-level: 2`: la próxima mordida da Lentitud II.

## Procedimiento

1. Rama `wp-25-aranas` desde `origin/main` actualizado.
2. `EffectGrant`, `BiteSlowness` y sus pruebas. Commit: `feat: spider bites that land slow the player`.
3. `HitEffects`, `StrikeFollowUps`, `MeleeAttacker`, `VersionTranslator` y `AdapterServices`. Commit: `feat: melee strikes apply their hit effects`.
4. `docs/actualizar-paper.md` y `docs/arquitectura.md`. Commit: `docs: map and names for the spider slowness`.
5. Pruebas que muerden, de a una y sin commit (ver «Entorno sin compilación» si no podés compilar).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-25: spider bites slow the player`, CI en verde e informe con la prueba exacta que falló en cada rotura.

## Entorno sin compilación

Si este WP se implementa en una sesión en la nube sin JDK 25 ni acceso a `repo.papermc.io`, `./gradlew` no corre. En ese caso:
- Formateá a mano con el estilo de google-java-format: 2 espacios, 100 columnas y la misma forma de partir líneas que el código vecino.
- El CI del PR (check `build`) es la verificación: esperá a que termine. Si falla, leé el log, corregí y volvé a empujar.
- Las pruebas que muerden no se pueden correr: en el informe, decí que quedaron sin correr, para que las corra la revisión local.

## Correcciones permitidas

1. Formato de Spotless.
2. Si una función pasa las 20 líneas, separala y avisalo.
3. Si el CI marca con `-Werror` que `new PotionEffect(PotionEffectType, int, int)` o `addPotionEffect(PotionEffect)` están deprecados, frená y reportá con el mensaje exacto (no busques otra sobrecarga por tu cuenta).

## Fuera de alcance

- El veneno de la araña de cueva y otros mobs nuevos.
- Que la araña elija su ataque con la política: sigue con su regla simple.
- Registrar la Lentitud aplicada en el `mobai-debug.log`. Se ve en el juego, con el ícono.
- Validar un tope para `slowness-duration-ticks` en `SpiderSettings`.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 12 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron (o quedaron listadas para la revisión local si no se pudo compilar).
- [ ] Build, cobertura y CI en verde.
