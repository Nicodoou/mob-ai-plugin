# Verificaciones en el server de la etapa E6

Lo que se ve al probar en el juego cada WP de la etapa E6, con los logs y las trazas. Los bugs siguen `docs/resolucion-de-bugs.md` y se numeran a continuación de los de la puerta E5 (B-02 en adelante).

## WP-22B y WP-22C — 7 oct 2026, corrida 1

**Antes de empezar.** MobAI arrancó deshabilitado: `run/plugins/MobAI/config.yml` tenía `learning-speed: 5.0`, que quedó del paso 9 de la puerta E5. Es el comportamiento correcto ante una configuración inválida. Se volvió a poner en 1.0.

**Qué vio Nico.**
- Los mobs heridos se alejan para regenerarse: funciona.
- No vio a ningún mob intentando flanquear.

**Traza (`trace-73153.jsonl`, grupo `5dc08452`).**
- Estrategias elegidas: `DIRECT_ASSAULT` (210 decisiones), `PIN_AND_SHOOT` (60) y `FLANK` (59). Hubo 123 órdenes `FLANK`.
- Flanqueadores: las arañas `c162748f` y `8f098de5` (en `PIN_AND_SHOOT` y en `FLANK`) y el zombie `4e17b00e` (plan 3, `FLANK`, de los ticks 74983 a 75563).
- **Ninguno de los tres registró un solo ataque mientras flanqueaba.** En `mobai-debug.log` hay 0 líneas `zombie.flank_strike`.
- La traza no tiene posiciones: no se puede saber si los flanqueadores se movían.

**Estado.** B-02 abierto: los flanqueadores no golpean nunca. Faltan datos para separar las hipótesis (ver la respuesta a Nico del 7 oct). Además, Nico describió cómo tiene que ser el flanqueo: dos comportamientos, uno «triangular» moviéndose alrededor del jugador y otro buscando la espalda.
