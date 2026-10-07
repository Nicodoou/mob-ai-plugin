# Cambios técnicos

Registro de los cambios de diseño hechos **después** de aprobar el plan maestro: qué cambió, por qué, a qué afecta y qué alternativas se descartaron. Las decisiones originales están en `README.md` (D1 a D24); las nuevas se numeran a partir de D25 y se resumen ahí. Cada cambio nuevo se agrega al final, con fecha y quién lo pidió.

| Cambio | Fecha | Pedido por | Estado |
| --- | --- | --- | --- |
| [CT-01](#ct-01--correcciones-del-spike-al-rastreador) Correcciones del spike al rastreador | 5 oct 2026 | Spike (WP-01) | Aplicado en WP-05 |
| [CT-02](#ct-02--una-tarea-por-función-y-bucles-acotados) Una tarea por función y bucles acotados | 5 oct 2026 | Nico | Aplicado en #5 y en las reglas |
| [CT-03](#ct-03--la-foto-lleva-la-dirección-del-jugador-y-no-la-amenaza-ni-el-rol) La foto lleva la dirección del jugador y no la amenaza ni el rol | 5 oct 2026 | Opus (WP-06) | Aplicado en WP-06 |
| [CT-04](#ct-04--infinitos-en-las-trazas) Infinitos en las trazas | 5 oct 2026 | Nico (opción B) | Pendiente, obligatorio en WP-28 |
| [CT-05](#ct-05--el-wp-10-se-divide-y-cambian-de-lugar-las-salidas-del-cerebro) El WP-10 se divide y cambian de lugar las salidas del cerebro | 5 oct 2026 | Opus | Aplicado en el plan |
| [CT-06](#ct-06--máximo-de-20-métodos-públicos-por-clase-y-división-de-group) Máximo de 20 métodos públicos por clase y división de `Group` | 5 oct 2026 | Nico | En curso: WP-08B |
| [CT-07](#ct-07--retirada-táctica-reagrupamiento-y-ventana-adaptativa) Retirada táctica, reagrupamiento y ventana adaptativa | 5 oct 2026 | Nico | Dominio aplicado (WP-08B, WP-10A, WP-10B); faltan WP-13, WP-14 y WP-22 |
| [CT-09](#ct-09--velocidad-de-aprendizaje-10-calibrada-por-simulación) Velocidad de aprendizaje 1,0, calibrada por simulación | 5 oct 2026 | Nico (opción A) | Aplicado en el catálogo; WP-16 la pone en `config.yml` |
| [CT-10](#ct-10--puerto-groupidsource) Puerto `GroupIdSource` | 5 oct 2026 | Opus (especificación del WP-12) | WP-12 crea el puerto y el fake; WP-16, la implementación real |
| [CT-11](#ct-11--un-solo-camino-para-el-resultado-del-plan-y-causa-de-salida) Un solo camino para el resultado del plan y causa de salida | 5 oct 2026 | Opus (cierre del WP-12, especificación del WP-13) | WP-13 |
| [CT-12](#ct-12--estado-completo-del-grupo-y-división-del-wp-28) Estado completo del grupo y división del WP-28 | 6 oct 2026 | Nico | WP-28A y WP-28B |
| [CT-13](#ct-13--no-abrir-un-plan-con-el-grupo-todavía-en-retirada) No abrir un plan con el grupo todavía en retirada | 6 oct 2026 | Nico (puerta E5, corrida 2) | Regla elegida (opción 1); WP-22A |
| [CT-14](#ct-14--formación-de-flanqueo-y-golpe-de-flanco-en-el-wp-22) Formación de flanqueo y golpe de flanco en el WP-22 | 7 oct 2026 | Opus (especificación del WP-22) | WP-22A y WP-22B |
| [CT-08](#ct-08--el-zombie-que-flanquea-usa-siempre-el-golpe-de-flanco) El zombie que flanquea usa siempre el golpe de flanco | 5 oct 2026 | Opus (WP-11), aprobado por Nico | En curso: WP-11 |

## CT-01 — Correcciones del spike al rastreador

- **Qué cambió:** tres reglas del rastreador de ataques y dos obligaciones de los adaptadores.
  - Daño real = daño final + lo absorbido (regla 6).
  - Bloqueo por el modificador `BLOCKING`, no por `isBlocking()` (regla 7).
  - Invulnerabilidad detectada al abrir el intento (regla 3).
  - Reinstalar los goals en `EntityAddToWorldEvent`.
  - Medir el movimiento del jugador tick a tick (`MovementTracker`).
- **Por qué:** lo que se observó en Paper 26.3 (`hallazgos-api.md`). Con la regla 6 original, todo golpe contra alguien con absorción contaba como fallo.
- **Impacto:** `arquitectura.md`, WP-05 (mergeado) y WP-17 a WP-24.

## CT-02 — Una tarea por función y bucles acotados

- **Qué cambió:**
  - Ninguna función hace más de una tarea, también las privadas.
  - Ningún bucle corre sin límite de iteraciones: los que dependen del azar llevan un tope y fallan con un mensaje claro.
- **Por qué:** pedido de Nico al revisar el WP-04. `BetaSampler` sumaba y decidía en la misma función, y con una forma rota podía colgar el server.
- **Impacto:** `politica-de-codigo.md`, `reglas-para-agentes.md`, la checklist de revisión y el PR #5 (`BetaSampler` y `GroupMemory`).

## CT-03 — La foto lleva la dirección del jugador y no la amenaza ni el rol

- **Qué cambió:**
  - `PlayerSnapshot` suma la dirección hacia la que mira el jugador (`PlayerPose`), la dureza de armadura y el nivel de Protección.
  - `MobSnapshot` deja de llevar rol y grupo.
  - `GroupSnapshot` deja de llevar la amenaza.
- **Por qué:**
  - Sin la dirección no se puede calcular el arco del escudo.
  - Rol y amenaza son estado del grupo, no algo que el adaptador ve.
- **Impacto:** `arquitectura.md` (tabla de fotos) y WP-06 (mergeado). La amenaza la guarda el grupo en su `ThreatLedger`.

## CT-04 — Infinitos en las trazas

- **Qué cambió:** el JSON de trazas e incidentes tiene que escribir y volver a leer `Infinity`.
- **Por qué:**
  - Un jugador que se cura más rápido de lo que el grupo le pega tiene tiempo para matarlo infinito (WP-07).
  - Gson, por defecto, lanza una excepción con infinitos. Un incidente en esa situación se perdería justo cuando más se lo necesita.
- **Alternativa descartada:** sacar el infinito del dominio (opción A). Habría cambiado código ya probado, y el WP-28 igual tendría que protegerse.
- **Impacto:** WP-28, con una prueba obligatoria de ida y vuelta.

## CT-05 — El WP-10 se divide y cambian de lugar las salidas del cerebro

- **Qué cambió:**
  - `RoleAssignment` y `GroupDecision` pasan del WP-08 al WP-10.
  - El WP-10 se divide en WP-10A (piezas: fin de plan, retirada, ataque sugerido, decisión y traza; Sonnet) y WP-10B (`Brain`, que las coordina; Opus).
  - El WP-09 pasa de Haiku a Sonnet.
- **Por qué:**
  - El WP-10 pasaba las 400 líneas de producción.
  - Las salidas del cerebro las arma el cerebro.
  - La elección de flanqueadores del WP-09 usa geometría.
- **Impacto:** `README.md` (mapa del código) y `estado.md`.

## CT-06 — Máximo de 20 métodos públicos por clase y división de `Group`

- **Qué cambió:**
  - Una regla de ArchUnit falla el build si una clase de producción tiene más de 20 métodos públicos de comportamiento. No cuentan los accesores de records ni `equals`, `hashCode`, `toString`, `values` y `valueOf`.
  - `Group` (25 métodos) se divide en:
    - `GroupRoster`: miembros, líder y objetivos de araña;
    - `PlanLifecycle`: estados, plan, compromiso y reagrupamiento;
    - `PendingEvents`: eventos que esperan a la aplicación.
  - `Group` queda como fachada de 8 métodos.
- **Por qué:** pedido de Nico al cerrar el WP-08: mantener a raya la superficie pública, porque una clase que crece sin control termina haciendo de todo. La regla automática lo garantiza sin depender de la revisión.
- **Alternativa descartada:** solo vigilarlo en la revisión. Depende de que alguien se acuerde.
- **Impacto:** `ArchitectureTest`, `Group` y sus pruebas (WP-08B), `orquestacion.md` (los WPs que agregan métodos públicos lo justifican) y D28.

## CT-07 — Retirada táctica, reagrupamiento y ventana adaptativa

- **Qué cambió** (D25, D26 y D27):
  - **Retirada individual:**
    - Con 30 % de vida o menos, el mob pasa a `RETREAT`, se aleja del objetivo y se queda al margen.
    - Con 60 % o más, vuelve al rol que tenía al empezar el plan, o al básico si se sumó después.
    - Antes, la retirada duraba hasta el fin del plan y nunca se volvía.
  - **Curación:**
    - Mientras está en `RETREAT` y no hay jugadores a menos de 12 bloques, el plugin le cura 1 punto cada 50 ticks (el ritmo de Regeneración I).
    - No muestra partículas ni ícono, y dura lo que dure la retirada.
  - **Retirada del grupo:**
    - Si más de la mitad de los mobs con los que empezó el plan murieron o están en `RETREAT`, el plan cierra con `GROUP_RETREATED`, como antes.
    - Ahora el grupo pasa al estado nuevo `REGROUPING`: todos se retiran y se curan.
    - Sale cuando más de la mitad de los presentes tiene 60 % o más, o cuando vence la ventana de reagrupamiento.
  - **Ventana adaptativa y global:**
    - Empieza en 600 ticks.
    - Baja 50 si un grupo muere entero mientras se reagrupa, y sube 50 si termina de reagruparse vivo.
    - Se mantiene entre 200 y 1.200 ticks.
    - Es una sola para todo el server y se guarda con las memorias.
- **Por qué:**
  - Nico: la retirada tiene que servir para alejarse, recuperarse y volver, no para salir de la pelea.
  - El tiempo de reagrupamiento se aprende de lo que les pasa a todos los grupos.
- **Desvío respecto del pedido original:** Nico pidió aplicar Regeneración o Veneno de nivel 1 según el mob. En Minecraft, zombies y esqueletos son no-muertos y son **inmunes** a los dos efectos, y las arañas son inmunes al Veneno. Se cumple la intención (curar al ritmo de Regeneración I, sin efectos visibles y sin límite de duración) con una curación directa del plugin, igual para los tres tipos.
- **Alternativas descartadas:**
  - Sin curación en el MVP: la retirada individual sería de ida y el grupo saldría de reagrupar solo por tiempo.
  - Ventana fija: no se adapta a lo que funciona en cada server.
- **Impacto:**
  - `arquitectura.md` (sección «Retirada táctica y reagrupamiento» y tabla de nombres), `diagramas.md` (ciclo de cinco estados), `catalogo-mvp.md` (rol `RETREAT`, fin de plan y configuración) y `README.md` (D25 a D28).
  - WP-08B: estado `REGROUPING` y roles iniciales del plan.
  - WP-10A: `RetreatSettings`, regla de vuelta y de curación, `RegroupRule` y `RegroupWindow`.
  - WP-10B: el cerebro maneja el estado nuevo.
  - WP-13: avisar a la ventana cuando un grupo muere reagrupándose.
  - WP-14: guardar la ventana.
  - WP-22: moverse al margen y aplicar la curación.
- **Riesgos:**
  - La curación es un recurso nuevo de los mobs: se calibra en la puerta G1 junto con el objetivo de dificultad («un grupo completo solo lo vence un jugador muy preparado»).
  - Si la ventana se estabiliza en un extremo, puede ser señal de que las otras reglas de retirada están mal calibradas.

## CT-08 — El zombie que flanquea usa siempre el golpe de flanco

- **Qué cambió:** un zombie con rol `FLANK` ejecuta siempre `zombie.flank_strike`, sin consultar la política ni consumir azar (como la mordida de la araña). La elección entre los tres golpes queda para los zombies con rol `PRESS`.
- **Por qué:** al diseñar la simulación del WP-11 apareció una contaminación de la estadística. Un zombie que flanquea conecta desde el costado aunque use el «golpe frontal», y ese acierto se anotaba como éxito del golpe frontal contra el jugador que bloquea: el grupo aprendería lo contrario de lo que pasa.
- **Alternativa descartada:** registrar la estadística por (ataque, rol). Multiplica los registros por cuatro y hace más lento el aprendizaje, que ya es el punto más justo del MVP.
- **Impacto:** `Brain.fighterOrder` y dos pruebas de brain (WP-11).

## CT-09 — Velocidad de aprendizaje 1,0, calibrada por simulación

- **Qué cambió:** la velocidad de aprendizaje por defecto pasa de 0,7 a 1,0 (2 intentos virtuales). La vida media queda en 12.000 ticks.
- **Por qué:** la simulación del WP-11, con 300 semillas, mostró que con 0,7 el grupo prefiere el flanqueo contra el jugador que bloquea en solo el 67 % de las corridas (se exige 80 %); con 1,0 y 12.000, el 90 %. El cuello de botella es la estrategia: una observación por plan, y con 0,7 los intentos virtuales pesan más que los datos reales.
- **Alternativa descartada:** dos velocidades (1,0 para estrategias y 0,7 para ataques). Más configuración para un beneficio que la simulación no mostró: los ataques aprenden bien con cualquier velocidad.
- **Riesgo:** con 1,0 el grupo se sesga rápido con pocas peleas (RF-06). Lo mitigan el olvido y el sorteo; se revisa en la puerta G1.
- **Impacto:** `catalogo-mvp.md`, `config.yml` del WP-16. `TestSettings` queda en 0,7: es un fixture de pruebas con valores calculados a mano, no la configuración del plugin.

## CT-10 — Puerto `GroupIdSource`

**Qué cambia.** Los ids de grupo nuevos salen de un puerto del dominio, `GroupIdSource.nextGroupId()`. En las pruebas, `SequentialGroupIdSource` da ids 1, 2, 3…; en el plugin, el adaptador del WP-16 usa `UUID.randomUUID()`.

**Por qué.** `RecruitMob` (aplicación) necesita crear grupos, y ArchUnit le prohíbe `UUID.randomUUID()`. Sacar el id de `RandomSource` correría el orden fijo de tiradas (estrategia primero, después ataques), y las reproducciones de trazas dejarían de coincidir.

**Impacto.** Un puerto más, que se arma en el arranque. No cambia el dominio de grupos.

**Alternativas descartadas.**
- Id derivado del mob fundador: choca si un mob sale de su grupo y funda otro mientras el primero sigue vivo.
- Contador más hash: se reinicia con el server y puede repetir ids guardados.

**Riesgos.** Ninguno conocido: el adaptador real es una línea.

## CT-11 — Un solo camino para el resultado del plan y causa de salida

**Qué cambia.**
1. El resultado de todo plan cerrado llega a la memoria por un solo camino: el evento `PlanClosed`, publicado por `GroupEvents` y escuchado por `ClosePlan`. El adaptador no llama a `ClosePlan` con `BrainResult.closedPlan()`, que queda para las trazas.
2. `TickGroups` decide de a un grupo por llamada, para que el adaptador aísle las fallas de cada grupo.
3. `DisbandGroup` publica los eventos pendientes del grupo después de sacarlo de `ActiveGroups`.
4. `RemoveMember` recibe la causa (`RemovalCause`: `DIED` o `DESPAWNED`). Si el último miembro muere mientras el grupo reagrupa, la ventana global baja (CT-07).

**Por qué.** Salieron de la revisión del WP-12: al disolver un grupo se perdían sus eventos, y no había forma de saber si un grupo murió reagrupando (la ventana de CT-07 no aprendía de los fracasos). Con dos caminos para el resultado del plan (el evento y el resultado del cerebro), un plan se podía registrar dos veces.

**Impacto.** Cambian las firmas de `RemoveMember` y `DisbandGroup` (WP-12, sin uso todavía fuera de las pruebas). El dominio no cambia.

**Alternativas descartadas.** Llamar a `ClosePlan` directo desde `TickGroups` y desde `RecordPlayerDeath`: dos lugares que recordar, y los observadores de la fase 2 igual necesitan el evento. Contar también los despawns como fracaso: un despawn no dice nada del tiempo de reagrupamiento.

**Riesgos.** El `LeaderDied` de un miembro que sale de un grupo vivo se publica en la próxima decisión (hasta 10 ticks tarde). Hoy nadie lo escucha en tiempo real.

## CT-12 — Estado completo del grupo y división del WP-28

**Qué cambia.**
1. El dominio puede copiar y restaurar el estado completo de un grupo: `PlanLifecycle.capture`/`restore` (con `LifecycleCapture`), `ThreatLedger.capture`/`restore` (con `ThreatCapture` y `ThreatRecord`) y `GroupRoster.spiderTargets`. `GroupCapture` y `GroupCaptureMapper` (aplicación) juntan eso con lo que ya guardaba el WP-15.
2. `RecordingRandomSource` pasa del WP-29 al WP-28A, junto con `ReplayRandomSource`, `RecordedDraw` y `DrawKind`.
3. `BrainParts.standard` es el único armado del cerebro: lo usan el plugin, las pruebas y la reproducción.
4. El WP-28 se divide en WP-28A (estado completo y azar grabado, Opus) y WP-28B (incidente, JSON y `TraceReplay`, Sonnet).
5. La copia se toma **antes** de cada decisión y se guarda en RAM; a disco va solo si hay un incidente (WP-29).

**Por qué.** Reproducir un bug exige repetir la decisión con el estado exacto de antes; lo que guarda el WP-15 (lo que sobrevive a un reinicio) no alcanza: falta el plan en curso, la amenaza, los objetivos de las arañas y la ventana de reagrupamiento. Guardar una referencia al grupo no sirve: el cerebro la modifica al decidir.

**Impacto.** Métodos públicos nuevos: `PlanLifecycle` 16, `ThreatLedger` 8, `GroupRoster` 9. `GroupCapture` vive en `application` para no crear un ciclo `group` ↔ `port`.

**Alternativas descartadas.** Reconstruir el estado desde la caja negra (frágil y lento); serialización nativa de Java (acopla el formato a las clases y no es legible).

**Riesgos.** Copiar el grupo cada 10 ticks cuesta memoria y CPU; el WP-29 lo mide y, si pesa, lo limita al debug activo. Un campo de estado nuevo que alguien agregue en el futuro sin sumarlo a la copia rompería la reproducción: `DecisionRepeatTest` lo detecta.

## CT-13 — No abrir un plan con el grupo todavía en retirada

**Qué cambia.** Se eligió la opción 1 (al especificar el WP-22A). Un grupo está «en retirada» si más de la mitad de los mobs presentes tiene 30 % de vida o menos (`RetreatRule.isGroupRetreated`). Entonces:
1. **Al observar,** con objetivo y en retirada, no planifica: pasa a `REGROUPING` sin abrir plan (`PlanLifecycle.regroupWithoutPlan`).
2. **Al reagrupar,** si la ventana vence y el grupo sigue en retirada, sigue reagrupando con la ventana reiniciada (`PlanLifecycle.restartRegroupWindow`), y la ventana adaptativa no cambia: solo aprende de los reagrupamientos que terminan.
3. La traza lo muestra con `DecisionTrace.stillRetreated`.

Descartada la opción 2 (abrir el plan y cerrarlo con una causa que no llega a la memoria): igual gasta números de plan y llena las trazas de planes que no existieron.

**Por qué.** En la puerta E5 (corrida 2, `puerta-e5-registro.md`), una araña sola con poca vida abrió y cerró cinco planes seguidos con `GROUP_RETREATED` y éxito 0,00, sin atacar nunca. La memoria aprendía que `DIRECT_ASSAULT` falla contra el jugador por planes que no se jugaron. La curación del WP-22 no alcanza: solo cura sin jugadores a menos de 12 bloques, así que con el jugador cerca el ciclo se repite.

**Impacto.** Dominio: `RetreatRule`, `PlanLifecycle` (2 métodos públicos, 18 en total), `Brain`, `DecisionTrace` y `TraceDraft` (WP-22A). Sin cambios de persistencia: el estado `REGROUPING` sin plan ya era válido para `LifecycleCapture`.

**Alternativas descartadas.** Esperar a la curación del WP-22: no cubre al jugador que se queda cerca. Disolver el grupo: pierde la memoria de un grupo que puede recuperarse.

**Riesgos.** Un grupo herido con el jugador al lado no ataca nunca: huye (`RetreatGoal`, WP-22B) y no se cura mientras el jugador esté a menos de 12 bloques. Es coherente con la retirada, pero se mira en la validación. Si el jugador lo mata mientras se reagrupa, la ventana global baja 50 (CT-07), aunque el grupo nunca haya peleado.

## CT-14 — Formación de flanqueo y golpe de flanco en el WP-22

**Qué cambia.**
1. **Formación.** `CombatGeometry.flankPoint` les daba el mismo punto a todos los flanqueadores de un lado, y chocaban. `FlankFormation` (dominio) le da a cada uno su puesto: en cada lado, del más rodeado al menos rodeado, el primero a 135° de la mirada del jugador, el segundo a 165° y los demás a 180° (justo atrás). El primero de cada lado da el mismo punto que antes. Los flanqueadores se agrupan por objetivo, no por grupo: dos grupos que flanquean al mismo jugador no se pisan.
2. **Golpe de flanco.** `FlankGoal` golpea solo desde fuera del arco del escudo, que es exactamente el golpe de flanco del catálogo («se mueve hasta quedar a más de 90° del frente y recién ahí golpea»), y lo registra como `ZOMBIE_FLANK_STRIKE`. Ese comportamiento pasa del WP-23 al WP-22B; el WP-23 queda con el golpe paciente.
3. **El WP-22 se divide:** WP-22A (dominio: CT-13 y formación) y WP-22B (goals, curación y armado). Junto pasaba las 400 líneas.

**Por qué.** Lo pedía el tablero («repartir a los flanqueadores del mismo lado»). Y un flanqueador que no golpeara hasta el WP-23 rodearía al jugador sin pegarle: la memoria contaría planes de flanqueo sin daño.

**Impacto.** Dominio: `FlankFormation`, `FlankQuery`; `CombatGeometry` expone a su paquete el ángulo, el lado y la rotación. Adaptadores: `FlankGoal`, `Waypoints`. Catálogo y CT-08 sin cambios.

**Alternativas descartadas.**
- Alternar lados (izquierda, derecha, izquierda): manda mobs a cruzar por delante del jugador.
- Un desvío al azar por mob: no garantiza separación y rompe la reproducción exacta.
- Guardar el puesto en `RoleAssignment`: cambia un record que usa todo el sistema, para un dato que el goal puede calcular con las posiciones del momento.

**Riesgos.** El puesto se recalcula cada 10 ticks con las posiciones del momento: si dos flanqueadores cruzan el mismo ángulo, pueden intercambiar puestos. Con más de 3 flanqueadores de un lado, los que pasan del tercero comparten el punto de atrás.
