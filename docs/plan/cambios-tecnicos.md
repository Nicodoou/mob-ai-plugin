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
| [CT-15](#ct-15--retirada-a-cubierto) Retirada a cubierto | 7 oct 2026 | Nico | WP-22C |
| [CT-16](#ct-16--flanqueo-fuera-de-la-vista) Flanqueo fuera de la vista | 7 oct 2026 | Nico (verificación del WP-22B, B-02) | WP-22D |
| [CT-17](#ct-17--apertura-y-abandono-del-golpe-paciente) Apertura y abandono del golpe paciente | 7 oct 2026 | Opus (especificación del WP-23) | WP-23 |
| [CT-18](#ct-18--flanqueadores-proporcionales) Flanqueadores proporcionales | 7 oct 2026 | Nico (prueba del WP-23) | WP-22E |
| [CT-19](#ct-19--golpe-esquivo) Golpe esquivo | 7 oct 2026 | Nico | WP-23B (por especificar, después del WP-24) |
| [CT-20](#ct-20--todos-los-tiros-con-nuestra-puntería-y-división-del-wp-24) Todos los tiros con nuestra puntería, y división del WP-24 | 7 oct 2026 | Opus (especificación del WP-24) | WP-24A y WP-24B |
| [CT-21](#ct-21--esqueletos-en-formación) Esqueletos en formación | 7 oct 2026 | Nico (prueba del WP-24B) | WP-24C |
| [CT-22](#ct-22--esqueletos-en-altura) Esqueletos en altura | 7 oct 2026 | Nico | WP-24D (por especificar) |
| [CT-23](#ct-23--andanada) Andanada | 7 oct 2026 | Nico | WP-24E (por especificar, antes de la puerta E6) |
| [CT-24](#ct-24--alcance-del-jugador-según-su-arma) Alcance del jugador según su arma | 8 oct 2026 | Nico | WP-23C |
| [CT-25](#ct-25--esquivo-calculado) Esquivo calculado | 8 oct 2026 | Nico | WP-23D |
| [CT-26](#ct-26--tensar-el-arco) Tensar el arco | 8 oct 2026 | Nico | WP-24G |
| [CT-27](#ct-27--éxito-con-tres-medidas) Éxito con tres medidas | 8 oct 2026 | Nico | WP-30A, WP-30B |
| [CT-28](#ct-28--retirada-aprendida) Retirada aprendida | 8 oct 2026 | Nico | WP-30C |
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
3. **El WP-22 se divide:** WP-22A (dominio: CT-13 y formación) y WP-22B (flanqueo, curación y armado). Junto pasaba las 400 líneas. Con el CT-15, la retirada pasó a un WP-22C.

**Por qué.** Lo pedía el tablero («repartir a los flanqueadores del mismo lado»). Y un flanqueador que no golpeara hasta el WP-23 rodearía al jugador sin pegarle: la memoria contaría planes de flanqueo sin daño.

**Impacto.** Dominio: `FlankFormation`, `FlankQuery`; `CombatGeometry` expone a su paquete el ángulo, el lado y la rotación. Adaptadores: `FlankGoal`, `Waypoints`. Catálogo y CT-08 sin cambios.

**Alternativas descartadas.**
- Alternar lados (izquierda, derecha, izquierda): manda mobs a cruzar por delante del jugador.
- Un desvío al azar por mob: no garantiza separación y rompe la reproducción exacta.
- Guardar el puesto en `RoleAssignment`: cambia un record que usa todo el sistema, para un dato que el goal puede calcular con las posiciones del momento.

**Riesgos.** El puesto se recalcula cada 10 ticks con las posiciones del momento: si dos flanqueadores cruzan el mismo ángulo, pueden intercambiar puestos. Con más de 3 flanqueadores de un lado, los que pasan del tercero comparten el punto de atrás.

## CT-15 — Retirada a cubierto

**Qué cambia.** El mob en `RETREAT` busca un lugar donde el jugador no lo vea antes de quedarse quieto (pedido de Nico). Cada 10 ticks elige un movimiento (`RetreatSituation`):
1. escondido y a 16 bloques o más: se queda;
2. camino a un cubierto que el jugador sigue sin ver: sigue;
3. cada 2 s: busca cubierto entre 14 candidatos (dos anillos, a 16 y 20 bloques del jugador, en abanico de hasta 90° a cada lado de la dirección que se aleja). Salta los que el jugador ve (un rayo) y pide hasta 3 caminos; acepta el primero que llega y cuyo punto final el jugador tampoco ve;
4. si no: se aleja en línea recta hasta 16 bloques.

La retirada sale del WP-22B y pasa a un WP-22C propio (`CoverFinder`, `RetreatGoal`, `RetreatSituation`, `CombatGeometry.coverCandidates`).

**Por qué.** Un mob que se cura a la vista del jugador es un blanco fácil (sobre todo para un arco): se aleja, pero el jugador lo encuentra y lo remata. Escondido, la retirada sirve de verdad para volver a pelear.

**Impacto.** Sin cambios en el dominio de decisiones ni en la curación: la curación sigue dependiendo de la distancia (CT-07), no de la vista. Paper: `hasLineOfSight`, `findPath` y `PathResult` (riesgo medio en `actualizar-paper.md`).

**Alternativas descartadas.**
- Que la curación dependa de estar escondido: cambia una regla del dominio que el dominio no puede verificar (no conoce los bloques) y deja sin curar a los grupos en campo abierto.
- Buscar el cubierto con un barrido de bloques alrededor: mucho más caro que 14 rayos y 3 caminos.

**Riesgos.** El vidrio y las hojas tapan la vista (Minecraft corta la línea de visión en los bloques con colisión): un mob detrás de un vidrio se cree escondido. Con muchos mobs en retirada, cada búsqueda cuesta hasta 3 caminos cada 2 s por mob; se mira en la verificación en el server.

## CT-16 — Flanqueo fuera de la vista

**Qué cambia.** El modelo de flanqueo de Nico. El jugador tiene un abanico de vista y alcance; el flanqueador lo evita:
1. **Vista:** el mob está a la vista si su ángulo respecto de la mirada del jugador es de 120° o menos (90° del escudo más 30° de margen para un movimiento corto del mouse).
2. **Mientras lo ven, esquiva:** cada 10 ticks camina hacia la dirección 45° más allá de la suya (como máximo hasta 135°), de su mismo lado, al pie de la perpendicular, sin acercarse a menos de `attack.flank-distance-blocks` (ahora 4 por defecto, uno más que el alcance del jugador). Las patas sucesivas arman el recorrido «triangular».
3. **Fuera de la vista, busca la espalda:** va a su puesto de la formación (CT-14) a 1,5 bloques, dentro del alcance de golpe.
4. **Golpea solo fuera de la vista** (antes, fuera del arco del escudo: más de 90°). El golpe de flanco del catálogo pasa a ser «se mueve hasta quedar fuera de la vista del jugador (más de 120° de su mirada) y recién ahí golpea».

**Por qué.** Bug B-02 (`verificacion-e6.md`): los flanqueadores nunca golpeaban. Dos causas: el punto de flanqueo giraba con la mirada del jugador (el mob daba vueltas sin salir del arco del escudo) y estaba a 3 bloques, fuera del alcance de golpe (2). El modelo de Nico resuelve las dos y además evita que el flanqueador se meta en el alcance del jugador.

**Impacto.** Dominio: `CombatGeometry.isOutOfSight` y `VISION_HALF_ANGLE_DEGREES`, `FlankManeuver`, `FlankStep`. Adaptadores: `Waypoints.flankStep` e `isOutOfSight` (reemplazan a `flankPoint` e `isOutsideTheShieldArc`), `FlankGoal`. Configuración: `flank-distance-blocks` cambia de sentido (distancia que mantiene mientras lo ven) y de valor por defecto (3 → 4). Catálogo: el golpe de flanco. CT-08 sin cambios.

**Alternativas descartadas.**
- Un punto fijo detrás del jugador sin esquivar: cruza por delante y se mete en su alcance.
- Planear el rodeo entero de una vez: se vuelve viejo apenas el jugador gira; recalcular cada 10 ticks es más simple y reacciona solo.
- Usar la línea de visión con bloques también para el flanqueo: más caro y no es lo que pide el modelo (un abanico geométrico).

**Riesgos.** Un jugador que gira constantemente hacia el flanqueador lo mantiene esquivando: el plan cierra por tiempo y la memoria aprende que flanquear a ese jugador sirve poco (es lo que tiene que aprender). Los servers que ya tienen `config.yml` conservan `flank-distance-blocks: 3.0` hasta que lo cambien.

## CT-17 — Apertura y abandono del golpe paciente

**Qué cambia.** El catálogo dice «golpea cuando el jugador baja el escudo o termina su propio ataque, con espera máxima de 3 s» y que una espera vencida no cuenta. Se precisa así:
1. **Apertura:** el jugador acaba de pegar (no bloquea y `getAttackCooldown()` es menor que 1, hallazgo 7) o acaba de bajar el escudo (estaba bloqueando y ya no). Un jugador que no bloquea ni pega no es una apertura.
2. **Abandono:** si pasan 3 s sin apertura, no se abre un intento paciente (no cuenta, como dice el catálogo) y el zombie **pega de frente**, que se registra como `ZOMBIE_FRONT_STRIKE` porque es lo que ejecutó. Después vuelve a esperar.

**Por qué.** Sin el abandono, un zombie paciente frente a un jugador que nunca baja el escudo, o que no hace nada, no pegaría nunca, y la memoria no aprendería nada de esa situación. Con el abandono, el golpe frontal suma su resultado (por ejemplo, parcial contra el escudo) y la política puede comparar.

**Impacto.** Solo adaptadores (`PatientWait`, `PressGoal`). Catálogo: sin cambios de texto; esta es la lectura oficial.

**Alternativas descartadas.** Contar la espera vencida como fallo del golpe paciente: contradice el catálogo y castiga al ataque por algo que el jugador no hizo. Considerar apertura a cualquier jugador que no bloquea: el golpe paciente sería idéntico al frontal contra quien no usa escudo.

**Riesgos.** `getAttackCooldown()` también baja al cambiar de ítem en la mano: un jugador que cambia de arma abre una apertura. Es raro y no le hace daño a nadie.

## CT-18 — Flanqueadores proporcionales

**Qué cambia.** En la estrategia de flanqueo, flanquea la mitad de las arañas (para abajo) y el resto de la mitad del cuerpo a cuerpo son zombies; un sobrante impar va a un zombie. Antes flanqueaban primero las arañas. Con el grupo de prueba (4 zombies, 2 arañas): 2 zombies y 1 araña, en vez de 2 arañas y 1 zombie.

**Por qué.** Nico vio en el juego que los zombies casi siempre atacaban de frente: aun con la estrategia de flanqueo, flanqueaba un solo zombie de cuatro.

**Impacto.** `FlankStrategy` (WP-22E) y el texto del catálogo. La frecuencia de la estrategia de flanqueo no cambia: la sigue eligiendo la memoria.

**Alternativas descartadas.** Que los zombies también flanqueen en «contener y disparar» (cambia la idea de esa estrategia) y que el flanqueo arranque con más chances (va contra el aprendizaje). Nico no las pidió.

**Riesgos.** Cambian los sorteos de las pruebas del cerebro que usan el flanqueo (un zombie más usa el golpe de flanco sin sorteo).

## CT-19 — Golpe esquivo

**Qué cambia.** Un ataque nuevo de zombie, `zombie.evasive_strike`, que la memoria aprende como los otros (opción 2 de Nico). El zombie que lo tiene asignado lee la carga del arma del jugador (`getAttackCooldown()`): si el jugador lo mira (está dentro de su vista de 120°), lo tiene a su alcance (3 bloques) y su carga está por llegar al tope (80 % o más), retrocede lo justo para quedar fuera del alcance (unos 3,5 bloques). Cuando el jugador pega al aire y su carga cae, vuelve a entrar y pega mientras el arma se recarga.

**Por qué.** Pedido de Nico: que el mob evite el golpe cargado. Como ataque aprendible (y no como comportamiento fijo), la memoria descubre contra quién conviene: rinde contra armas lentas como el hacha, poco contra quien pega rápido sin cargar.

**Impacto.** Dominio: un valor nuevo de `Attack` (su identificador se guarda en las memorias; las memorias viejas siguen sirviendo). Adaptadores: el comportamiento en `PressGoal`, con la decisión en una clase pura como `PatientWait`. Configuración: umbral de carga (0,8) y distancia de esquive (3,5). Catálogo: la fila del ataque nuevo. Se especifica como WP-23B, después del WP-24.

**Alternativas descartadas.** Un comportamiento fijo de todos los que presionan (puede volver injusto al grupo y la memoria no aprende nada) y sumarlo al golpe paciente (mezcla escudo y carga en un mismo ataque y la memoria no las distingue).

**Riesgos.** El zombie camina más lento que el jugador: contra uno que corre hacia él, el esquive no alcanza. Con espada (0,6 s de carga) la ventana es corta.

## CT-20 — Todos los tiros con nuestra puntería, y división del WP-24

**Qué cambia.**
1. El spike decidió que el tiro directo usara `rangedAttack` (la puntería vanilla) y el anticipado `launchProjectile`. Ahora los tres tiros usan `launchProjectile` con nuestra puntería (`ShotAim`): el directo apunta al centro del jugador, el anticipado y el oportuno a donde va a estar.
2. El tiro oportuno espera a que el objetivo esté distraído: el esqueleto fuera de su vista (120°, CT-16) o el jugador recién pegó (`getAttackCooldown()`). Al vencer la espera dispara el directo y lo registra como directo (igual que el abandono del golpe paciente, CT-17).
3. El WP-24 se divide: WP-24A (rastreo de flechas) y WP-24B (`ShootGoal`, `BowShooter` y los tres tiros).

**Por qué.** `rangedAttack` no devuelve la flecha, y el rastreador necesita su UUID para seguir el intento (hallazgo 5). Con una sola forma de disparar, la diferencia entre tiros es solo la puntería, que es lo que la memoria tiene que comparar. El WP-24 junto pasaba las 400 líneas.

**Impacto.** Adaptadores (`BowShooter`, `ShotAim`, `ShootGoal`, `AttackTracker` con proyectiles, `ProjectileListener`, `ProjectileResolver`). Dominio: sin cambios.

**Alternativas descartadas.** Capturar la flecha de `rangedAttack` con `ProjectileLaunchEvent`: funciona, pero acopla el disparo a un evento y complica correlacionar mob, flecha y ataque.

**Riesgos.** El daño de la flecha depende de su velocidad: con 1,6 bloques por tick ronda el de un esqueleto vanilla; se mira en la verificación.

## CT-21 — Esqueletos en formación

**Qué cambia.**
1. Los esqueletos pelean entre 20 y 30 bloques (antes 8 y 15) y se ubican en el medio, a 25.
2. Los esqueletos de un mismo objetivo se reparten parejo alrededor de él (`ShooterFormation`): 2 opuestos, 3 a 120°, 4 a 90°. El de menor id ancla el anillo donde está y los demás siguen en el orden en que ya están.
3. No disparan si un aliado está a menos de 1 bloque de la línea de tiro; prueban girar su puesto 30°, 60° y 90° a cada lado hasta tener la línea limpia.
4. El grupo de prueba les da arco (sin probabilidad de soltarlo).
5. Se borran `RangeSituation`, `RangeMove` y `Waypoints.backOffPoint`: el puesto en el anillo ya cumple acercarse y alejarse.

**Por qué.** Nico, después de probar el WP-24B: a 8–15 bloques el esqueleto no tiene tiempo de reaccionar si el jugador se acerca; se agrupaban; no tenían arco; y le pegaban todo el tiempo a los zombies que atacaban.

**Impacto.** Dominio: `ShooterFormation`, `ShooterQuery`, `CombatGeometry.isLineOfFireClear` y `clearLane`. Adaptadores: `ShootGoal`, `Waypoints`, `RoleRegistry.mobsTargeting`, `PoseReader.bodyCenterOf`, `VersionTranslator.armWithBow`, `GroupSpawner`. Configuración por defecto: 20 y 30.

**Alternativas descartadas.** Una clave nueva para la distancia preferida (el medio del rango alcanza); separación fija de 90° (con 2 o 3 esqueletos quedan amontonados de un lado).

**Riesgos.** A 25 bloques, un jugador que cambia de dirección es difícil de acertar: es lo que la memoria tiene que aprender de él. El chequeo de aliados es geométrico: un aliado detrás de un bloque cuenta igual.

## CT-22 — Esqueletos en altura

**Qué cambia.** Al ubicarse, el esqueleto revisa unos 10 lugares cerca de su puesto (±15° y ±30°, a 24 y 28 bloques) y elige el más alto con suelo firme (bloque más alto de la columna), desde donde ve al jugador y al que puede llegar (hasta 3 caminos por búsqueda). Si no hay uno más alto, se queda en su puesto.

**Por qué.** Nico: desde arriba tienen vista y distancia.

**Riesgos.** El bloque más alto de la columna falla en cuevas o bajo árboles grandes; el cálculo de camino descarta esos lugares.

## CT-23 — Andanada

**Qué cambia.** Una estrategia nueva, `VOLLEY`, que la memoria aprende como las otras tres: los zombies presionan; cada tanto, los que presionan se abren unos bloques, los esqueletos disparan juntos con la línea limpia y los zombies vuelven a entrar. Se especifica como WP-24E, antes de la puerta E6.

**Por qué.** Nico: sinergia entre zombies y esqueletos en vez de fuego amigo.

**Impacto.** Cerebro (fases dentro del plan), goals (abrirse y volver) y tirador (ventana de disparo). Es el cambio más grande de la etapa.

## CT-24 — Alcance del jugador según su arma

**Qué cambia.** El alcance del jugador ya no es fijo (3 bloques): es el `maxReach` del componente `ATTACK_RANGE` del arma en la mano (las lanzas), o el atributo `ENTITY_INTERACTION_RANGE`. La distancia del flanqueador mientras lo ven (CT-16) y la del zombie esquivo (CT-19) pasan a ser **alcance + margen**: `flank-distance-blocks` (4) se reemplaza por `flank-margin-blocks` (1) y `evasive-distance-blocks` (3,5) por `evasive-margin-blocks` (0,5). Con una espada da lo mismo que antes.

**Por qué.** Nico: con una lanza el jugador pega más lejos, y un flanqueador o un zombie esquivo a 4 o 3,5 bloques quedarían dentro de su alcance.

**Impacto.** `VersionTranslator.playerReach` (Paper detrás del traductor), `PlayerReach` en `Weapons`, `PlayerTarget`, `Waypoints.flankStep` y `evadePoint`, `FlankGoal`, `PressGoal`, configuración. El dominio no conoce el alcance del jugador.

**Riesgos.** La API de componentes de ítems es reciente en Paper. Los `config.yml` existentes tienen las claves viejas y el plugin no arranca hasta cambiarlas (el del server de prueba lo actualiza Opus).

## CT-25 — Esquivo calculado

**Qué cambia.** El zombie esquivo deja de usar un umbral fijo de carga (0,8) y de cargar de frente después de 3 s de espera. Ahora calcula dos tiempos:

- **lo que le falta al arma del jugador para estar al 100 %**, con `getCooldownPeriod`;
- **lo que tarda él en salir del alcance, o en entrar, pegar y salir**, con su velocidad real: el atributo de velocidad, que ya incluye pociones y buffs, y la física del suelo de Minecraft.

**Reglas:**
- Pega si le da el tiempo.
- Dentro del alcance, con el tiempo justo para salir: retrocede.
- Dentro del alcance, sin tiempo para salir: pega igual, porque el golpe del jugador le llega de todos modos (Nico).
- Afuera, sin tiempo para entrar, pegar y salir: espera en el borde.
- Si el jugador lo mira cubierto con el escudo y el arma al 100 %, el golpe es inevitable: se pone a un costado de la mira (el ancho del mob más un margen) y le pega al escudo para desgastarlo.
- `evasive-charge-threshold` se reemplaza por `evasive-safety-ticks` (4) y `evasive-aim-margin-degrees` (15).

**Por qué.** B-05 y Nico:
- con el escudo arriba, la carga por espera agotada hacía entrar y salir al zombie sin parar;
- la retirada tiene que estar terminada al 100 %;
- un valor fijo no sirve para un mob con Speed ni para buffs futuros.

**Impacto.** `PressGoal`, `EvasiveRules` (reemplaza a `EvasiveWait`), `EscapeTiming`, `Waypoints.sideStepPoint`, `CombatGeometry.sideStepPoint`, `VersionTranslator.movementSpeed`, `Bodies` en `Weapons` y la configuración.

**Riesgos.**
- La física del suelo está deducida del código de Minecraft, no medida. Se confirma en el juego: el zombie tiene que salir antes del 100 %.
- Con espada, el zombie casi nunca entra contra un jugador atento: es lo esperado y la memoria lo aprende.
- `getCooldownPeriod` es API de Paper.

## CT-26 — Tensar el arco

**Qué cambia.** El esqueleto ya no dispara en el acto. Antes de cada tiro:
1. se planta;
2. gira el cuerpo hacia el jugador;
3. levanta los brazos y tensa el arco 20 ticks, como en vanilla;
4. recién ahí suelta, con la puntería calculada en ese momento.

La cadencia no cambia: empieza a tensar 20 ticks antes de que le toque el tiro.

En `HOLD_FIRE` (andanada), el esqueleto ya ubicado se queda tenso y suelta en el acto cuando llega `VOLLEY`. El tiro oportuno espera con el arco tenso.

**Por qué.** Nico, corrida 4:
- los esqueletos no tensaban el arco;
- disparaban caminando o de espaldas (B-06).

**Impacto.** `ShootGoal`, `ShotRhythm.canDraw`, `BowDraw`, `BodyFacing`, `BowShooter.draw` y `lower`, y `VersionTranslator.drawBow` y `lowerBow` (`setAggressive`, `startUsingItem`, `clearActiveItem`).

**Riesgos.**
- Que el arco de un mob en uso no muestre la animación en el cliente.
- Un esqueleto plantado es un blanco más fácil: es el costo de que se vea bien, y vanilla lo compensa moviéndose de costado (fuera de alcance por ahora).

## CT-27 — Éxito con tres medidas

**Qué cambia.** El éxito de un plan deja de medir solo el daño. Combina tres medidas, cada una de 0 a 1:

- **daño:** como hasta ahora;
- **rapidez:** la parte de la vida del objetivo que se le sacó, en proporción al tiempo, contra matarlo en 600 ticks;
- **supervivencia del grupo:** ½ × aliados del inicio vivos al cierre + ½ × (1 − vida neta perdida ÷ vida del grupo al inicio). La vida neta descuenta la curación.

**Pesos** (WP-30A): 0,4, 0,4 y 0,2, configurables.

**Jugador muy bueno** (WP-30B): el grupo lleva por jugador cuánta vida pierde por cada punto de daño que le hace, con el olvido de la memoria. Cuanto más alto, más pesa la supervivencia, entre un piso y un techo configurables, y el resto se reparte entre daño y rapidez. Se detecta solo.

**Por qué.** Nico: contra jugadores muy buenos conviene reforzar las estrategias que mantienen vivo al grupo. Hasta ahora, un plan que hacía daño pero perdía a medio grupo se aprendía igual de bueno.

**Impacto.**
- WP-30A: `Plan` (vida del inicio y última vista), `PlanScoring`, `PlanScores`, `ClosedPlan`, `Brain`, `RecordPlayerDeath`, `DebugLog` y la configuración `success`.
- WP-30B: la memoria (un registro de peligro por jugador), la persistencia (versión 2 del esquema) y los pesos dinámicos.

**Riesgos.**
- Un mob que desaparece (chunk descargado) cuenta como perdido.
- La simulación del WP-11 sortea el éxito de su propio modelo: no valida esta fórmula.

**Enmienda (8 oct 2026, D-01, WP-30D).** La supervivencia multiplica al ataque en vez de sumarse: con la suma, un plan que no peleaba sacaba el mejor puntaje contra un jugador muy bueno (corrida 5, plan 3: 1,4 de daño, éxito 0,64).

## CT-28 — Retirada aprendida

**Qué cambia.** El umbral de retirada individual (`plan.retreat-health-fraction`, 30 %) deja de ser fijo y pasa a aprenderse por jugador.

- El grupo guarda un umbral entre 0 % (pelear hasta morir) y un techo configurable (70 %).
- Al abrir cada plan, prueba un umbral al azar cerca del suyo. El sorteo es más abierto cuando sabe poco de ese jugador.
- Al cerrar, compara el éxito del plan (CT-27) con el promedio contra ese jugador. Si salió mejor, el umbral se mueve hacia el que probó; si no, no se refuerza.
- El umbral y el promedio se olvidan con la vida media de la memoria.

**Por qué.** Nico: que el cerebro decida cuándo conviene retirarse. Si curarse no sirve porque el jugador persigue, que pruebe retirarse antes o pelear hasta morir.

Se usa el éxito del plan, y no la curación sola, para poder comparar con no retirarse.

**Impacto.** `Brain` (umbral por plan), la memoria y la persistencia, y la configuración. La retirada de grupo (CT-13) sigue fija al 30 %.

**Riesgos.**
- Aprende más lento que una estrategia, porque es un valor continuo con ruido.
- Hay que calibrar el paso y el ruido con la simulación.
