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

### B-02 — Los flanqueadores nunca golpean

- **Hipótesis.**
  - **H1 (confirmada por Nico: los zombies nunca se quedaron quietos, pero nunca parecían flanquear):** el punto de flanqueo se calcula según la mirada del jugador; si el jugador gira para seguir al mob, el punto gira con él y el mob da vueltas sin salir nunca del arco del escudo.
  - H2 (descartada por lo mismo): el goal de flanqueo nunca arranca; los flanqueadores se quedarían quietos.
  - H3 (descartada por lo mismo): el camino al punto falla; se quedarían quietos o darían pasitos.
  - **H4 (encontrada al releer el código):** el punto de flanqueo está a 3 bloques y el alcance de golpe es de 2: un flanqueador que llega a su punto no puede golpear.
- **Diseño nuevo (Nico):** el jugador tiene un abanico de vista y alcance. El flanqueador primero sale de la vista por el camino más corto sin meterse en el alcance, y recién fuera de la vista busca la espalda. La vista son los 90° más un margen para un movimiento corto del mouse.
- **Arreglo:** WP-22D (CT-16). La regresión de la H4 es `FlankManeuverTest.closeInPointIsWithinStrikeReach`.

## WP-22D — 7 oct 2026, corrida 2

**Qué vio Nico:** todo funciona. El flanqueador esquiva fuera de la vista y golpea por la espalda; la retirada a cubierto y la curación siguen andando. **B-02 cerrado.** Los WP-22A a WP-22D quedan verificados en el juego.

## WP-22E, WP-23 y WP-24B — 7 oct 2026, corrida 3

**Qué vio Nico.** Buena experiencia en general. Los esqueletos parecían tirar siempre el tiro directo y casi siempre fallaban.

**Log (`mobai-debug.log`).**

| Tiro | Acierto | Fallo | Neutral (aliado) | Neutral (invulnerable) |
| --- | --- | --- | --- | --- |
| `skeleton.direct_shot` | 5 | 17 | 1 | 1 |
| `skeleton.lead_shot` | 4 | 12 | 1 | 3 |
| `skeleton.opportunistic_shot` | 3 | 2 | 4 | 0 |

- Los tres tiros salieron (20 anticipados): a la vista se ven iguales, cambia solo el punto de mira.
- **Acierto total: 12 de 43 (28 %).** En el spike, el anticipado acertó 8 de 9.
- El tiro oportuno pega a aliados 4 de 9 veces: dispara justo cuando el jugador pelea con otros mobs, que están en la línea de tiro.

### B-03 — Las flechas pasan por encima del jugador

- **Simulación** (física de la flecha de Minecraft: arrastre 0,99 y gravedad 0,05 por tick, velocidad 1,6): con nuestra puntería (centro del cuerpo + 20 % de la distancia horizontal de elevación), la flecha llega a **1,97 a 1,99 bloques sobre los pies** del jugador a 8 y 12 bloques: por encima de la cabeza (el jugador mide 1,8). Vanilla apunta a un tercio de la altura y llega a 1,68.
- **Hipótesis** (pendientes de aprobación):
  - **H1:** la elevación fija de vanilla (`ARROW_ARC_FACTOR`) supone que se apunta a un tercio de la altura; nosotros apuntamos al centro y sumamos la misma elevación, así que la flecha llega un bloque alta. Predicción: los fallos son flechas que pasan por arriba; una prueba que simule la física da más de 0,5 bloques de error a 12 bloques.
  - H2: el anticipado calcula mal el movimiento. Predicción: el directo acertaría mucho más que el anticipado. Los datos no lo muestran (23 % contra 25 %).
  - H3: terreno en la línea de tiro. Predicción: fallos con contacto `BLOCK` antes del jugador en terreno plano. No explica un 72 % de fallos en un terreno de prueba plano.
