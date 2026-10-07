# Puerta E5 — Registro de la prueba en el server

Lo que va saliendo al seguir `puerta-e5.md`: qué se vio, qué dicen los logs, si es un bug o lo esperado, y qué se hizo. Cada bug sigue `docs/resolucion-de-bugs.md` y tiene su número (B-NN).

## Resumen

| Paso | Estado | Evidencia |
| --- | --- | --- |
| Preparación | ok | Semilla, informe de carga y `enabled` en las cuatro sesiones; `/mobai debug all full` |
| 1. Crear el grupo | ok | Grupos `c53f5082`, `50d9b3ef` y `f2da4aec`, sin equipo. Tildes: confirmadas por Nico |
| 2. Estado | ok | `/mobai status` |
| 3. Persecución y golpe | ok (B-01 corregido y confirmado) | 18 de 22 huecos entre golpes del mismo mob, de 20 ticks |
| 4. Golpes que cuentan | ok | Una línea `ATTACK` por golpe |
| 5. Escudo, absorción, creativo | ok | 39 `PARTIAL rule=7` (escudo); `HIT rule=6` con absorción según Nico; en creativo, `TARGET_LOST` (00:09:27 y 00:11:33) |
| 6. Muertes | ok | `PLAN … plan=3 … reason=TARGET_DIED success=1.00 damage=21.0` (grupo `f2da4aec`); miembros de menos en los archivos guardados |
| 7. Guardado y reinicio | ok | `MobAI disabled` al `stop`; dos archivos de grupo con registros `zombie.front_strike` y `spider.bite` del jugador y las tres estrategias; `state.json` con tick 67684 y ventana 800; al levantar, `2 groups loaded, 0 skipped` |
| 8. Descarga del chunk | ok según Nico | Además, después del reinicio el grupo `f2da4aec` volvió a pegar (5 `spider.bite`, 2 `zombie.front_strike`): los goals se reinstalan al cargar los mobs |
| 9. Reset y recarga | ok según Nico | `/mobai reset papu123` y dos `/mobai reload` (válido e inválido) sin errores en la consola |
| 10. Trazas | ok, con una salvedad | `trace-58474.jsonl` con decisiones, planes y ataques. Después de `/mobai debug all off`, `mobai-debug.log` sigue sumando (4 `PLAN` posteriores). Salvedad: en esa sesión no se había vuelto a poner `full` (el nivel no se guarda entre reinicios), así que no se vio un `.jsonl` que deje de crecer |
| 11. Consola | ok | Ningún `ERROR` ni `Exception` de MobAI en las sesiones con el jar actual; ningún incidente |

**Resultado: puerta E5 pasada** (7 oct 2026). Un bug corregido (B-01), un cambio técnico para el WP-22 (CT-13) y dos notas para la etapa E6 (al final).

## Corrida 1 — 6 oct, 23:29 a 23:37: entorno

**Qué se vio.** `/mobai spawngroup THOMPSON_SAMPLING` daba «error ambiguo».

**Logs.** `NoClassDefFoundError: io/github/nicodoou/mobai/domain/shared/MobKind`. Antes, el `runServer` nuevo había fallado con «otro proceso tiene bloqueada una parte del archivo» (`session.lock` del mundo): el server de las 23:09 seguía vivo, y en ese server se hizo `/reload` de Bukkit después de que Gradle reemplazara el jar.

**Diagnóstico.** No es un bug del plugin. El jar se reemplazó con el plugin cargado, y `/reload` lo volvió a cargar de un archivo cambiado; las clases se cargan la primera vez que se usan, y `MobKind` se usa recién en `spawngroup`.

**Qué se hizo.** El guion avisa que hay que hacer `stop` antes de levantar otro server y que no se usa `/reload` (commit 4d55b92). Después, el build falló porque una carpeta de `build/` seguía tomada; se resolvió con `./gradlew --stop` y borrando `build/classes` y `build/test-results`.

## Corrida 2 — 6 oct, 23:42: de día, el grupo se quema

**Qué se vio.** Los mobs dejaron de tener IA, casi todos murieron y una araña quedó quieta para siempre.

**Traza (`trace-35083.jsonl`, grupo `92a6434c`).**
1. Zombies y esqueletos sin casco, de día: se queman, bajan al 30% y pasan a `RETREAT`, que todavía no tiene goal (WP-22): se quedan quietos y mueren quemados.
2. Tick 36457: más de la mitad retirada o muerta → el plan 1 cierra con `GROUP_RETREATED`; el grupo reagrupa, todos en `RETREAT`.
3. La araña `a752` (Nico le pegó) queda en `RETREAT` para siempre: sin curación (WP-22) nunca llega al 60%.
4. Cada ~650 ticks abre un plan nuevo y lo cierra en el acto con `GROUP_RETREATED`, éxito 0,00 (planes 2 a 6).

**Diagnóstico.** Lo esperado sin el WP-22, más un error del guion (decía `/time set day`). Pero el punto 4 muestra un hueco de diseño: la memoria aprende «`DIRECT_ASSAULT` falla contra papu123» de planes que nunca se jugaron. La curación del WP-22 no lo resuelve sola, porque solo cura sin jugadores a menos de 12 bloques.

**Qué se hizo.**
- El guion pide noche y sin mobs naturales, y avisa que `RETREAT` deja al mob quieto y sin curarse (commit b352982).
- **CT-13** (aprobado por Nico): no abrir un plan mientras el grupo cumple la condición de retirada. Va en el WP-22.

## Corrida 3 — 6 oct, 23:47: de noche

**Qué se vio.** Las arañas a veces quietas y a veces atacan; los zombies persiguen, buscan la espalda y chocan con el jugador sin rodearlo; al volar, los zombies se apagan.

**Traza (grupo `c53f5082`).**

| Plan | Estrategia | Arañas | Cierre |
| --- | --- | --- | --- |
| 1 | `PIN_AND_SHOOT` | `FLANK` (quietas) | `TIMED_OUT` (Nico en un pilar) |
| 2 | `FLANK` | `FLANK` (quietas) | `TIMED_OUT` |
| 3 | `DIRECT_ASSAULT` | `PRESS`: atacan; escudo → `PARTIAL rule=7` | `TIMED_OUT`, éxito 0,30 |
| 4 | `FLANK` | `FLANK` (quietas) | `TARGET_LOST` al volar |

**Diagnóstico.** Lo esperado sin el WP-22: `FLANK` todavía no tiene goal. Los zombies en `PRESS` caminan hacia el jugador; el rodeo que se ve es el pathfinding vanilla. Volar saca al jugador de la foto (solo supervivencia y aventura), el plan cierra y los goals quedan sin objetivo.

### B-01 — El golpe sale cada 40 ticks, no cada 20

- **Evidencia.** En `mobai-debug.log`, 44 de 54 huecos entre golpes del mismo mob son de exactamente 40 ticks; ninguno es menor a 40.
- **Hipótesis.**
  - **H1 (aprobada y confirmada):** Paper llama al `tick()` de un goal activo cada dos ticks del juego, y `MeleeRhythm` contaba llamadas. Predicción: hueco mínimo siempre 40. Se cumple.
  - H2: la distancia corta el golpe en ticks impares. Predicción: huecos irregulares. Descartada por los datos.
  - H3: un cooldown de Paper frena el golpe. Descartada: el registro es anterior al daño y vería los intentos cada 20.
- **Prueba que reproduce.** `MeleeRhythmTest.strikesOncePerAttackIntervalOfGameTicksWhenTickedEveryOtherTick` y `repathsEveryTenGameTicksWhenTickedEveryOtherTick` (el reloj avanza de a 2 por llamada). Fallaron con el código anterior.
- **Arreglo.** `MeleeRhythm` mide con el reloj del plugin (`ServerClock`): guarda el tick del próximo repath y del próximo golpe. Llega a `PressGoal` por `GoalContext.melee()` (`MeleeTools`: atacante y reloj). Suite completa: 633 pruebas.
- **Confirmado en el server** (corrida 4): 18 de 22 huecos son de 20 ticks; los demás son más largos (el mob fuera de alcance).

## Corrida 4 — 7 oct, 00:01: con el arreglo del B-01

**Qué se vio.** Las arañas se apagan y se vuelven a prender.

**Traza (`trace-58474.jsonl`, grupo `50d9b3ef`).** Las arañas `42dd` y `b531` tienen `PRESS` en los planes de `DIRECT_ASSAULT` (1 y 2) y `FLANK` en los de `FLANK` y `PIN_AND_SHOOT` (3, 4 y en parte el 5). Se apagan exactamente cuando arranca un plan que les da `FLANK`.

**Diagnóstico.** Lo esperado sin el WP-22: el catálogo manda las arañas a flanco primero en `FLANK` y siempre en `PIN_AND_SHOOT`, y `FLANK` todavía no tiene goal. No es un bug. El ritmo de golpe ya es de 20 ticks (B-01 confirmado).

**Nota.** Al arrancar se cargó el grupo `c53f5082` de la corrida 3 (`1 groups loaded`); `/kill` lo vació y quedó observando sin miembros en el tick 59309, sin errores.

## Corrida 5 — 7 oct, 00:02 a 00:11: pasos 5 a 11

Ver la tabla del resumen. Dos cosas que no son bugs del código de E5, pero que la etapa E6 tiene que tener en cuenta:

1. **Planes de solo esqueletos.** Al grupo `50d9b3ef` le quedaron tres esqueletos (`SHOOT`, IA vanilla hasta el WP-24) y un zombie en `RETREAT`. Abrió y cerró por tiempo 11 planes de `DIRECT_ASSAULT` con éxito 0,00: los esqueletos pegan, pero no se registra nada. Es el mismo problema que el CT-13 (la memoria aprende de planes que no se jugaron) y lo resuelve el WP-24 al registrar los disparos. Nota en el WP-24.
2. **El plan en curso al apagar se pierde sin registro** (plan 4 de `f2da4aec`, plan 12 de `50d9b3ef`). Es lo que decidió el WP-14: no se guarda el plan en curso y al reiniciar cada grupo vuelve a observar.

También salió una vez `NEUTRAL:TARGET_INVALID rule=1`: el golpe que llegó cuando el jugador ya estaba muerto.
