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
