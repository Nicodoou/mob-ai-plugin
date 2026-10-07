# WP-23 — Golpe paciente

## Ficha

| Campo | Valor |
| --- | --- |
| Etapa | E6 Comportamiento completo |
| Depende de | WP-22D (mergeado) |
| Modelo | Sonnet |
| Rama | `wp-23-golpe-paciente` |

## Objetivo

Que el zombie con rol `PRESS` al que el cerebro le sugiere `ZOMBIE_PATIENT_STRIKE` **espere la apertura** en vez de pegar enseguida (catálogo: «se queda en alcance y golpea cuando el jugador baja el escudo o termina su propio ataque, con espera máxima de 3 s»). Es el ataque que castiga al jugador que alterna bloquear y atacar. Hasta ahora todo zombie que presiona pega de frente.

1. `PatientWait`: cuándo pega el zombie paciente (Java puro, probado).
2. `PressGoal` lo usa cuando la orden sugiere el golpe paciente, y registra el ataque que **ejecutó** (WP-19).
3. `GoalTiming`: el reloj y la configuración de ataque juntos, para que los goals lean la espera máxima.

## Contexto a leer

`docs/plan/reglas-para-agentes.md`, este WP y estos archivos (nada más):

- `docs/plan/hallazgos-api.md` (secciones 3 y 7)
- `docs/actualizar-paper.md` (sección 3, para actualizar la fila de los goals)
- `src/main/java/io/github/nicodoou/mobai/adapter/goal/` (todos)
- `src/main/java/io/github/nicodoou/mobai/domain/decision/RoleAssignment.java`, `domain/shared/Attack.java`, `domain/settings/AttackSettings.java`
- `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` (solo `goalInstaller`)

## Reglas de negocio

1. **Quién es paciente:** un zombie con rol `PRESS` cuya orden trae `suggestedAttack` = `ZOMBIE_PATIENT_STRIKE`. Cualquier otra sugerencia (frontal, o de flanco a un zombie que presiona) pega de frente como hasta ahora, y la araña sigue mordiendo.
2. **Postura del jugador** (`PlayerStance`), leída de Paper en el momento:
   - `BLOCKING`: `Player.isBlocking()`. El hallazgo 3 dice que sirve para la intención, aunque no para clasificar.
   - `RECOVERING_FROM_SWING`: no bloquea y `Player.getAttackCooldown()` es menor que 1. Según el hallazgo 7, el jugador acaba de pegar y su golpe se está recargando.
   - `READY`: ninguna de las dos.
3. **Apertura:** el jugador está `RECOVERING_FROM_SWING` («terminó su propio ataque»), o pasa a `READY` justo después de estar `BLOCKING` («bajó el escudo»). Un jugador que no bloquea ni pega **no** es una apertura: el zombie paciente lo espera.
4. **Cuándo se consulta:** solo cuando el zombie podría pegar, es decir, en alcance y con el intervalo de ataque cumplido (`MeleeRhythm.canStrike`). Si no puede, la espera se reinicia; lo último que vio de la postura del jugador se conserva.
5. **Movimientos** (`PatientMove`):
   - `STRIKE_PATIENT`: hay apertura. Pega y registra `ZOMBIE_PATIENT_STRIKE`.
   - `WAIT`: no hay apertura y la espera no venció. No pega.
   - `GIVE_UP`: pasaron `attack.patient-strike-max-wait-ticks` (60, 3 s) desde que empezó a esperar. **No se abre un intento paciente** (catálogo: «si un ataque con espera vence su espera sin atacar, no cuenta»). El zombie pega de frente y registra `ZOMBIE_FRONT_STRIKE`, porque eso fue lo que ejecutó. Después empieza una espera nueva.
6. **Ritmo:** todo con el reloj del plugin (regla B-01).

## Archivos

| Acción | Ruta |
| --- | --- |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PlayerStance.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PatientMove.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PatientWait.java` |
| Crear | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTiming.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/GoalTools.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/PressGoal.java` |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/FlankGoal.java` (solo `tools().clock()` → `tools().timing().clock()`) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/adapter/goal/RetreatGoal.java` (ídem) |
| Modificar | `src/main/java/io/github/nicodoou/mobai/bootstrap/AdapterServices.java` |
| Modificar | `docs/actualizar-paper.md` |
| Crear | `src/test/java/io/github/nicodoou/mobai/adapter/goal/PatientWaitTest.java` |

Antes de empezar, buscá con grep `tools().clock()` y `new GoalTools(` en `src/`: si aparecen en un archivo que no está en la tabla, frená y reportá.

## Especificación

API de Paper verificada con `javap` contra `paper-api 26.3.build.151-beta`: `HumanEntity.isBlocking()` y `HumanEntity.getAttackCooldown()` (`float`, de 0 a 1).

### `PlayerStance.java` y `PatientMove.java`

```java
/** What the player is doing with shield and weapon, as a patient zombie reads it. */
public enum PlayerStance {
  BLOCKING,
  RECOVERING_FROM_SWING,
  READY
}
```

```java
/** What a patient zombie in reach does this tick. */
public enum PatientMove {
  WAIT,
  STRIKE_PATIENT,
  GIVE_UP
}
```

### `PatientWait.java` (package-private, Java puro)

```java
/** When a patient strike lands: at the player's first opening, or never if the wait runs out. */
final class PatientWait {
  private static final long NOT_WAITING = Long.MIN_VALUE;

  private long waitStartTick = NOT_WAITING;
  private PlayerStance lastStance = PlayerStance.READY;

  PatientMove next(PlayerStance stance, long now, long maxWaitTicks) {
    boolean opening = isOpening(stance);
    lastStance = stance;
    if (opening) {
      waitStartTick = NOT_WAITING;
      return PatientMove.STRIKE_PATIENT;
    }
    if (waitStartTick == NOT_WAITING) {
      waitStartTick = now;
      return PatientMove.WAIT;
    }
    if (now - waitStartTick >= maxWaitTicks) {
      waitStartTick = NOT_WAITING;
      return PatientMove.GIVE_UP;
    }
    return PatientMove.WAIT;
  }

  // Out of reach or still recharging: the next wait starts over, but a shield seen up stays known.
  void reset() {
    waitStartTick = NOT_WAITING;
  }

  // Right after a swing the player cannot hit back at full strength, and a shield coming down
  // does not block for a moment.
  private boolean isOpening(PlayerStance stance) {
    return stance == PlayerStance.RECOVERING_FROM_SWING
        || (stance == PlayerStance.READY && lastStance == PlayerStance.BLOCKING);
  }
}
```

### `GoalTiming.java` y `GoalTools.java`

```java
/** The plugin's clock and the attack settings, for the goals' rhythms and waits. */
public record GoalTiming(ServerClock clock, Supplier<AttackSettings> attack) {
  public GoalTiming {
    Objects.requireNonNull(clock, "GoalTiming.clock");
    Objects.requireNonNull(attack, "GoalTiming.attack");
  }
}
```

`GoalTools` queda `record GoalTools(MeleeAttacker attacker, GoalTiming timing, Waypoints waypoints)`, con `requireNonNull(timing, "GoalTools.timing")` y el Javadoc «What our goals use besides the orders: the attacker, the timing and the waypoints.». En `FlankGoal` y `RetreatGoal`, cada `context.tools().clock()` pasa a `context.tools().timing().clock()`; nada más cambia en esos dos archivos.

### `PressGoal.java`

Campos nuevos: `private final PatientWait patience = new PatientWait();`. `rhythm` se arma con `context.tools().timing().clock()`. Javadoc de la clase: `/** PRESS: walk to the target and strike it head-on, or wait for an opening if told to be patient. */`.

`tick()` pasa a leer la orden completa, porque necesita la sugerencia:

```java
  @Override
  public void tick() {
    currentOrder()
        .ifPresent(order -> GoalOrders.validTarget(order, mob).ifPresent(target -> pressOn(order, target)));
  }
```

`shouldActivate()` sigue siendo `currentTarget().isPresent()`.

Funciones privadas, una tarea cada una:

| Función | Hace |
| --- | --- |
| `Optional<RoleAssignment> currentOrder()` | `GoalOrders.orderFor(mob, context.roles(), Role.PRESS)` |
| `Optional<Player> currentTarget()` | `currentOrder().flatMap(order -> GoalOrders.validTarget(order, mob))` |
| `void pressOn(RoleAssignment order, Player target)` | `mob.lookAt(target)`; `followIfDue(target)`; `strikeIfReady(order, target)` |
| `void followIfDue(Player target)` | Sin cambios |
| `void strikeIfReady(RoleAssignment order, Player target)` | Si `!rhythm.canStrike(distancia)`: `patience.reset()` y vuelve. Si no: `attackNow(order, target).ifPresent(attack -> { context.tools().attacker().strike(mob, target, attack); rhythm.markStrike(); })` |
| `Optional<Attack> attackNow(RoleAssignment order, Player target)` | Si `!isPatient(order)`: `patience.reset()` y `Optional.of(executedAttack())`. Si es paciente: `switch (patience.next(stanceOf(target), context.tools().timing().clock().currentTick(), context.tools().timing().attack().get().patientStrikeMaxWaitTicks()))`: `WAIT -> Optional.empty()`, `STRIKE_PATIENT -> Optional.of(Attack.ZOMBIE_PATIENT_STRIKE)`, `GIVE_UP -> Optional.of(Attack.ZOMBIE_FRONT_STRIKE)` |
| `boolean isPatient(RoleAssignment order)` | `kind == MobKind.ZOMBIE && order.suggestedAttack().equals(Optional.of(Attack.ZOMBIE_PATIENT_STRIKE))` |
| `static PlayerStance stanceOf(Player player)` | `isBlocking()` → `BLOCKING`; si no, `getAttackCooldown() < FULL_ATTACK_COOLDOWN` → `RECOVERING_FROM_SWING`; si no, `READY` |
| `Attack executedAttack()` | Sin cambios (el golpe que no es paciente) |

Constante nueva: `// Paper's attack cooldown is 1 once the player's weapon has fully recharged.` `private static final float FULL_ATTACK_COOLDOWN = 1.0f;`

En `GIVE_UP`, el comentario de una línea: `// The wait ran out: no patient attempt is opened (catalog); it strikes head-on instead.`

### `AdapterServices.java`

En `goalInstaller`: `GoalTools tools = new GoalTools(attacker, new GoalTiming(core.clock(), core.settings().section(MobAiSettings::attack)), waypoints);`.

### `docs/actualizar-paper.md`

Fila `adapter/goal/*`: sumar `HumanEntity.isBlocking()` y `getAttackCooldown()`. En «Qué revisar», sumar: «que `getAttackCooldown()` siga bajando cerca de 0 con cada golpe del jugador y subiendo hasta 1 (hallazgo 7), y que `isBlocking()` siga queriendo decir escudo levantado (solo para la intención del golpe paciente)».

## Pruebas obligatorias

### `PatientWaitTest` (7)

Espera máxima 60 en todas.

| Prueba | Secuencia (`next(postura, tick)`) | Verifica |
| --- | --- | --- |
| `strikesRightAfterThePlayersSwing` | `RECOVERING_FROM_SWING`, 1000 | `STRIKE_PATIENT` |
| `strikesWhenTheShieldComesDown` | `BLOCKING`, 1000; `READY`, 1002 | `WAIT`; `STRIKE_PATIENT` |
| `waitsWhileThePlayerBlocks` | `BLOCKING` en 1000, 1030 y 1059 | `WAIT` las tres |
| `aPlayerJustStandingThereIsNoOpening` | `READY`, 1000; `READY`, 1030 | `WAIT`; `WAIT` |
| `givesUpWhenTheWaitRunsOut` | `BLOCKING` en 1000, 1059, 1060, 1062 y 1122 | `WAIT`, `WAIT`, `GIVE_UP`, `WAIT` (espera nueva), `GIVE_UP` |
| `resetStartsTheWaitAgain` | `BLOCKING`, 1000; `reset()`; `BLOCKING` en 1050, 1100 y 1110 | `WAIT`; `WAIT`, `WAIT`, `GIVE_UP` |
| `resetKeepsTheRaisedShieldInMind` | `BLOCKING`, 1000; `reset()`; `READY`, 1020 | `WAIT`; `STRIKE_PATIENT` |

## Pruebas que muerden

| # | Rotura | Valor con la rotura | Prueba que falla |
| --- | --- | --- | --- |
| 1 | En `isOpening`, `stance == READY` sin mirar `lastStance` | `READY` en 1000 da `STRIKE_PATIENT` | `aPlayerJustStandingThereIsNoOpening` |
| 2 | `now - waitStartTick > maxWaitTicks` | en 1060 da `WAIT` | `givesUpWhenTheWaitRunsOut` |
| 3 | En `reset`, también `lastStance = PlayerStance.READY` | en 1020 da `WAIT` | `resetKeepsTheRaisedShieldInMind` |
| 4 | En `GIVE_UP`, sin `waitStartTick = NOT_WAITING` | en 1062 da `GIVE_UP` | `givesUpWhenTheWaitRunsOut` |

## Verificación en el server (Nico, después del merge)

Con `/mobai debug all full`, de noche, con escudo (`/give @s shield`), mirando de frente a los zombies que presionan:

1. Con el escudo arriba, algunos zombies **no te pegan**: esperan.
2. Bajás el escudo o pegás: te pegan enseguida. En `mobai-debug.log`: `attack=zombie.patient_strike outcome=HIT`.
3. Con el escudo arriba más de 3 s: se cansan y pegan al escudo. En `mobai-debug.log`: `attack=zombie.front_strike outcome=PARTIAL`.
4. Sin escudo y sin pegar, el zombie paciente espera 3 s y después pega de frente.

## Procedimiento

1. Rama `wp-23-golpe-paciente` desde `origin/main` actualizado.
2. `PlayerStance`, `PatientMove`, `PatientWait` y `PatientWaitTest`. Commit: `feat: patient wait for the player's opening`.
3. `GoalTiming`, `GoalTools`, `PressGoal`, `FlankGoal`, `RetreatGoal`, `AdapterServices`. Commit: `feat: patient strike in the press goal`.
4. `docs/actualizar-paper.md`. Commit: `docs: map the Paper API used by the patient strike`.
5. Pruebas que muerden (de a una, en secuencia; sin commit).
6. `./gradlew spotlessApply` y `./gradlew build jacocoTestReport jacocoTestCoverageVerification` en verde.
7. Push, PR `WP-23: patient strike`, esperar el check `build` en verde (si no aparece ninguna corrida a los 2 minutos, avisalo) e informe con la prueba exacta que falló en cada rotura.

## Correcciones permitidas

1. Formato de Spotless.
2. Si `-Xlint` marca una deprecación en `isBlocking` o `getAttackCooldown`, frená y reportá; no la suprimas.
3. Si Spotless deja una línea de `tick()` mal partida, extraé la lambda a una función privada `pressOnIfValid(RoleAssignment order)` con el mismo comportamiento y avisalo.

## Fuera de alcance

- Que el cerebro deje de sugerir el golpe de flanco a un zombie que presiona (anotado en «Decisiones abiertas» del tablero).
- Arañas y esqueletos.
- Una línea en `mobai-debug.log` cuando el zombie se cansa de esperar.

## Aceptación

- [ ] Exactamente los archivos de la tabla, con las firmas especificadas.
- [ ] Las 7 pruebas con sus nombres exactos, en verde; la suite completa en verde.
- [ ] Las 4 roturas mordieron.
- [ ] El ataque registrado es siempre el ejecutado: paciente solo con apertura, frontal al cansarse.
- [ ] `docs/actualizar-paper.md` coincide con los imports de Paper.
- [ ] Build, cobertura y CI en verde.
