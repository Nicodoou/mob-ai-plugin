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
- **H1 aprobada y confirmada.** Pruebas que la reproducen: `CombatGeometryTest.arrowArrivesAtTheAimPoint` (8, 12 y 15 bloques) y `arrowArrivesAtATargetAboveOrBelow` (±3 bloques), con un simulador de la física de la flecha en la prueba. Fallaron las 5 con el código anterior.
- **Arreglo.** `CombatGeometry.leadShotVelocity` busca por bisección (30 pasos, entre −60° y 45°) el ángulo con el que la flecha, con el arrastre y la gravedad de Minecraft (`ARROW_DRAG_PER_TICK`, `ARROW_GRAVITY_PER_TICK`), llega al punto apuntado. Se borró `ARROW_ARC_FACTOR`.
- **Falta:** confirmar en el juego que los tiros pegan (el modelo no tiene en cuenta que la flecha sale 0,1 bloques debajo de los ojos).
- **Anotado para después (aprobado por Nico):** el esqueleto no debería disparar con un aliado en la línea de tiro (el tiro oportuno le pegó a aliados 4 de 9 veces).

## WP-24C, WP-24D, WP-23B, WP-23C y andanada — 7 oct 2026, corrida 4

**Qué vio Nico.**
- Los esqueletos se alejan y tratan de no pegarles a los compañeros, pero en algunos tiros anticipados les pegan igual.
- Los esqueletos siguen atacando "aun de espaldas" (falta aclarar qué significa).
- No hacen la animación de tensar el arco.
- Los zombies bien, salvo el esquivo frente a un jugador con el escudo levantado y la espada cargada: se acercan y se alejan sin parar, y pegarles es fácil (esperás a que se alejen y les pegás).
- Las arañas no se probaron.

**Log (último tramo, desde el tick 246.563).**
- Hay **3 planes**, todos `DIRECT_ASSAULT`. En los tramos anteriores del día no hay ningún plan `VOLLEY`: **la andanada no se vio en el juego**.

| Tiro | Acierto | Fallo | Neutral (aliado) | Neutral (invulnerable) |
| --- | --- | --- | --- | --- |
| `skeleton.direct_shot` | 1 | 4 | 3 | 2 |
| `skeleton.lead_shot` | 2 | 9 | 0 | 1 |
| `skeleton.opportunistic_shot` | 9 | 11 | 1 | 6 |

- Los aliados reciben 4 de 49 tiros (8 %; en la corrida 3 eran 6 de 43, el 14 %). En este tramo, los tiros a aliados son sobre todo directos.

### B-04 — Flechas que les pegan a los aliados

- **Hipótesis** (pendientes de aprobación):
  - **H1:** la línea de tiro se verifica hacia el centro del cuerpo del jugador, pero el anticipado apunta adonde va a estar. La flecha sale por otro carril, y ese carril no se verificó. Predicción: con un aliado en el carril del punto anticipado y no en el del centro, la verificación da libre.
  - H2: el aliado entra al carril durante el vuelo (a 20–30 bloques, la flecha tarda más de un segundo). Predicción: tiros a aliados también en el directo, con aliados que se mueven (los zombies que presionan). Los 3 directos de este tramo la apoyan.
  - H3: la línea es recta, pero la flecha hace un arco. Predicción: los aliados golpeados están cerca de la mitad del camino, más altos que la línea. Es la menos probable, porque el arco pasa por arriba de los aliados.
- **H1 aprobada por Nico y confirmada.** La prueba `ShotAimTest.laneIsCheckedTowardsWhereTheShotFlies` falló con la verificación hacia el centro del cuerpo.
- **Arreglo (#47):**
  - `ShotAim.isLaneClear` verifica el carril hacia el punto al que apunta cada tiro.
  - `ShootGoal` elige el tiro primero y verifica después su carril.
- **Falta:** confirmar en el juego. Si siguen los tiros a aliados, se prueba la H2.

### B-05 — El esquivo entra y sale frente al escudo

- **Causa (por el código):** `EvasiveWait` espera como máximo `patient-strike-max-wait-ticks` (60) y después carga de frente (`CHARGE`). Con el escudo arriba y la espada cargada, el peligro no termina nunca: cada 3 s el zombie entra, recibe el golpe y vuelve a esquivar.
- **Hipótesis H1:** la carga por espera agotada entra aunque el jugador siga cargado. Predicción: en una prueba de `EvasiveWait` con peligro constante, sale `CHARGE` a los 60 ticks.
- **Pedido de Nico (diseño, CT nuevo):** la retirada tiene que arrancar antes de que el arma termine de cargar, de modo que al 100 % el zombie ya esté fuera del alcance. Hoy el umbral es fijo (0,8). Con el tiempo que le falta al arma para cargar y el tiempo que tarda el zombie en salir, la retirada arranca justo a tiempo.

### Pendientes de la corrida

- **Animación de tensar el arco** (no es un bug: el WP-24B dispara sin animación). Va con un CT.
- **B-06, esqueletos que disparan de espaldas (aclarado por Nico):** disparan mientras caminan hacia su puesto o se alejan, con el cuerpo de espaldas al jugador. `ShootGoal` dispara sin mirar hacia dónde está orientado el cuerpo. Va con la animación de tensar el arco, en el mismo CT, aprobado por Nico: el esqueleto se planta y gira hacia el jugador para tensar.
- **B-05:** Nico quiere debatir qué hace el zombie esquivo frente a un jugador que se queda cubierto y cargado.
- **Por verificar todavía:** la andanada (no salió ningún plan `VOLLEY`), las arañas, el alcance de la lanza y la rotación de la flecha.

## Arranque del 8 oct 2026, después del WP-26

**Qué vio Nico:** al iniciar el server, el plugin no funciona.

**Log:** `MobAI disabled: messages.yml: missing memory-empty`.

**Causa:** el `messages.yml` del server de prueba es una copia vieja. El plugin no lo pisa si ya existe, y el WP-26 sumó cuatro claves (`memory-*`). Es lo mismo que pasa con `config.yml`, pero esta vez Opus no actualizó la copia al mergear. No es un bug del código: el plugin hace lo que dice el WP-16 (falla con un mensaje claro si falta una clave).

**Arreglo:** Opus sumó las cuatro claves y la lista nueva de `unknown-subcommand` al `messages.yml` del server de prueba.

**Anotado:**
- regla del manual: al mergear un WP que suma mensajes, actualizar también el `messages.yml` de prueba;
- decisión abierta: que una clave de mensaje que falta use el texto del jar con un aviso, en vez de deshabilitar el plugin. Los servers reales van a pasar por esto en cada actualización.

## Test fuerte — 8 oct 2026, corrida 5

**Log (desde el tick 1.244.845; unos 4.000 ticks, 3,4 minutos).** Hubo 5 planes del mismo grupo, `b586bb1e`:

| Plan | Estrategia | Cierre | Éxito | Daño / rapidez / supervivencia | Peligro | Daño hecho |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | DIRECT_ASSAULT | TIMED_OUT | 0,43 | 0,52 / 0,26 / 0,60 | 0,00 | 5,2 |
| 2 | PIN_AND_SHOOT | GROUP_RETREATED | 0,43 | 0,21 / 0,23 / 0,64 | 0,76 | 2,1 |
| 3 | FLANK | TIMED_OUT | **0,64** | 0,14 / 0,07 / **1,00** | 1,00 | 1,4 |
| 4 | FLANK | GROUP_RETREATED | 0,28 | 0,00 / 0,00 / 0,48 | 0,95 | 0,0 |
| 5 | DIRECT_ASSAULT | GROUP_RETREATED | 0,22 | 0,14 / 0,13 / 0,27 | 1,00 | 1,4 |

- **El peligro funciona:** pasó de 0 a 0,76 con un solo plan. En ese plan el grupo perdió unos 80 de vida y le hizo 5,2 de daño a Nico.
- **Es poco para ver aprendizaje:** 5 planes, cuando hacen falta entre 10 y 20.
- **Tiros de esqueleto:** 9 aciertos y 4 parciales en 48 (19 %), 3 a aliados (todos anticipados) y 2 con Nico invulnerable. Uno de los esqueletos disparó 32 veces.
- **Zombies:**
  - el esquivo pegó 3 veces y 1 parcial;
  - el frontal, 2 y 1 parcial;
  - el paciente, 1.
- **Arañas:** 3 mordidas, todas con Nico invulnerable.
- **Andanada:** ningún plan `VOLLEY`.

### Hallazgo D-01 — Sobrevivir sin pelear puntúa como el mejor plan

El plan 3 sacó el mejor éxito (0,64) con casi nada de daño (1,4), porque contra un jugador muy bueno la supervivencia pesa 0,6 y la suma premia quedarse lejos. Con más planes, el grupo aprendería a no pelear. **Es un problema de diseño del CT-27, no un bug del código.** Va a Nico como decisión.

**Qué vio Nico (corrida 5).**
- **Equipo:** armadura de diamante sin encantar, escudo con Irrompibilidad III y espada de diamante.
- **Zombie esquivo:** se corre a los costados de la mira y no entra en su alcance, aunque no notó que se alejara. Lo que más lo desconcertó es el esquive de mira estando dentro de su alcance. Falta aclarar si eso está bien o mal.
- **Esqueletos:** tensan, se dan vuelta, disparan y siguen a su objetivo; **WP-24G verificado**. Algunos tiros anticipados les siguen pegando a aliados: **el B-04 sigue**, así que toca probar la H2.
- **Arañas:** atacan, pero Nico las mata enseguida.
- **Peligro:** llegó a 1 en el primer test y a 0,92 en el segundo, con dos `spawngroup`. No sabe si los grupos se unificaron.
- **Reclutamiento:** los mobs vanilla no se suman a los grupos y siguen con su IA, e incluso un esqueleto vanilla le pegó a uno del grupo. **No es un bug:** el reclutamiento por cercanía y la unificación de grupos son de la fase 2 (`requerimientos.md`, tabla de etapas). Opus le había dicho a Nico que los grupos reclutan; fue un error de Opus. Hoy solo `spawngroup` arma grupos.
- **D-01:** Nico aprobó que la supervivencia multiplique (WP-30D).

### B-04, H2 — aliados que entran al carril durante el vuelo (aprobada por Nico, con la condición de que no sea cara)

- **Prueba que la reproduce:** `ShotAimTest.laneIsCheckedWhereAlliesWillBeWhenTheArrowPasses`. Un aliado a 1,2 bloques del carril, caminando hacia él a 0,12 bloques por tick, con un tiro a 20 bloques. Falló con la verificación anterior.
- **Arreglo (#56):** cada aliado se proyecta con su movimiento horizontal por los ticks que tarda la flecha en llegar a su altura del camino (`CombatGeometry.arrowTicksToCover`, con el freno del aire). La roturas sin freno y sin proyección mordieron.
- **Costo:** solo al soltar cada flecha (un tiro cada 40 ticks por esqueleto). Por aliado, un lazo de hasta ~20 pasos. Nada por tick. Cumple la condición de Nico.
- **Falta:** confirmarlo en el juego.

**Esquivo de mira (respuesta de Nico):** "hace como un mini dash pero es rápido, está bueno". **WP-23D verificado.**

### Reagrupamiento sin reunión (observación de Nico)

Al retirarse, cada mob se va por su lado, y después no se vuelven a juntar. **No es un bug del código:** el CT-07 define el reagrupamiento como "todos se retiran y se curan", sin punto de reunión. Es un comportamiento que falta. Va a Nico como CT nuevo: un punto de reunión lejos del jugador y un camino que lo evite.
