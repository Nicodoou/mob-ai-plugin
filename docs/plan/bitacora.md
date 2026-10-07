# Bitácora de WPs

Una entrada por WP cerrado: qué hizo, cómo quedó armado, la opinión del orquestador sobre el código y lo que quedó pendiente. Es la versión escrita de los cierres que se le presentan a Nico; se agrega al mergear cada WP (ver `orquestacion.md`, sección 7).

Las entradas de WP-00 a WP-05 se reconstruyeron después, a partir de los cierres de la primera sesión.

## WP-00 — Andamiaje, reglas automáticas y CI

- **PR:** [#1](https://github.com/Nicodoou/mob-ai-plugin/pull/1) · Sonnet · sin rondas de corrección.
- **Qué hizo:** proyecto Gradle (Kotlin DSL, wrapper 9.8.0 con sha256) que compila un plugin vacío para Paper 26.3 con Java 25, corre JUnit, Spotless y JaCoCo, y verifica la arquitectura con ArchUnit, local y en GitHub Actions.
- **Arquitectura:** `ArchitectureTest` con 10 reglas (15 ejecuciones): capas, solo `VersionTranslator` usa constantes de Paper, campos estáticos `final`, sin azar ni reloj directo en dominio y aplicación, sin archivos ni logging ahí, sin `System.out`, sin nombres `Manager`/`Helper`/`Util(s)`. Compilación con `-Werror`.
- **Opinión:** bueno: las reglas se probaron rompiéndolas a propósito (7 fallas exactas). Flojo: ninguno relevante. Riesgo: Paper 26.3 está en beta; un build nuevo puede cambiar la API.
- **Desvío aceptado:** google-java-format 1.36.1, porque 1.37.0 rompe Spotless 8.10.3.

## WP-01 — Spike en el server

- **Sin PR:** lo hizo el orquestador con Nico en el server; el código queda en la rama `spike/wp-01`, sin mergear.
- **Qué hizo:** verificó en Paper 26.3 el Mob Goal API, el golpe cuerpo a cuerpo, el escudo, la absorción, la invulnerabilidad, las flechas y el ciclo de vida de las entidades. Todo en `hallazgos-api.md`.
- **Cambió el diseño en tres reglas del rastreador:** daño real = daño final + absorbido; bloqueo por el modificador `BLOCKING` (no `isBlocking()`); invulnerabilidad detectada al abrir el intento. Además: reinstalar goals al volver a cargar el chunk, y medir el movimiento del jugador tick a tick porque `getVelocity()` da 0 al caminar.
- **Opinión:** el WP más valioso hasta ahora: sin él, todo golpe contra alguien con manzana dorada contaba como fallo.

## WP-02 — Tipos base, puertos y configuración

- **PR:** [#2](https://github.com/Nicodoou/mob-ai-plugin/pull/2) · Haiku · una ronda de corrección.
- **Qué hizo:** IDs (`MobId`, `PlayerId`, `GroupId`, `StrategyId`), `MobKind`, `Attack`, `EffectKind`, `Vec3`, `MinecraftConstants`, los puertos `ServerClock` y `RandomSource`, los 10 records de configuración con validación y mensajes exactos, y los fakes de prueba.
- **Arquitectura:** records inmutables que validan en el constructor; la configuración es un record por sección dentro de `MobAiSettings`.
- **Opinión:** bueno: transcripción fiel y mensajes exactos. Flojo: nombres abreviados en `Vec3` (`len`, `len1`) y un `String.valueOf` de más; los corrigió el mismo subagente.

## WP-03 — Memoria con olvido

- **PR:** [#3](https://github.com/Nicodoou/mob-ai-plugin/pull/3) · Sonnet · sin correcciones.
- **Qué hizo:** `AttackRecord` con olvido perezoso por vida media, `LearningPrior` (intentos virtuales = 50 − 48 × velocidad, sumados al leer y nunca guardados), `SuccessEstimate` y `GroupMemory` por jugador, ataque y estrategia.
- **Opinión:** bueno: los valores de RF-06 (por ejemplo 10 de 10 → 92 % con velocidad 1) están como pruebas con número exacto. Riesgo: la vida media y la velocidad de aprendizaje se calibran juntas en el WP-11.

## WP-04 — Sorteo Beta y políticas de selección

- **PR:** [#4](https://github.com/Nicodoou/mob-ai-plugin/pull/4) · Sonnet · una ronda de corrección. Arreglo posterior en [#5](https://github.com/Nicodoou/mob-ai-plugin/pull/5).
- **Qué hizo:** `BetaSampler` (gamma de Marsaglia–Tsang, con el ajuste para formas menores a 1), las cuatro políticas (Thompson, explorar primero, épsilon-greedy, azar), `MemoryMultiplier` y `SelectionResult` con el puntaje de cada opción.
- **Opinión:** la prueba del ajuste era simétrica (Beta(0,5; 0,5)) y pasaba aunque el ajuste estuviera mal; se reemplazó por una que mide la cola de Beta(0,5; 2). De ahí salió la lección de usar parámetros asimétricos.
- **#5, pedido por Nico:** los bucles del sorteo quedaron con un límite de 1000 intentos (antes, una forma rota colgaba el server), y se separaron las funciones que hacían más de una tarea (`BetaSampler`, `GroupMemory.kindEstimate`). Desde entonces es regla: una tarea por función y bucles con límite.

## WP-05 — Clasificador de ataques

- **PR:** [#6](https://github.com/Nicodoou/mob-ai-plugin/pull/6) · Sonnet · sin correcciones.
- **Qué hizo:** `AttackFacts` (los hechos crudos de un intento), `AttackOutcome` sellado (`Hit`, `Partial`, `Miss`, `Neutral`), `AttackClassifier` con las 8 reglas en orden y `ClassificationTrace`, que dice qué regla decidió.
- **Arquitectura:** el adaptador junta hechos y el dominio clasifica; `classify` coordina `classifyNeutral` (reglas 1 a 5) y `classifyContact` (6 a 8).
- **Opinión:** bueno: cada prueba verifica el resultado y el número de regla, así un bug de orden se detecta. Riesgo: depende de que el rastreador (WP-18) arme bien los hechos; esa es la parte que solo se ve en el server.

## Puerta E2

146 pruebas en verde y cobertura del dominio del 96 % (459 de 478 líneas; el objetivo era 80 %). Las líneas sin cubrir son casos defensivos.

## WP-06 — Fotos, amenaza y geometría

- **PR:** [#7](https://github.com/Nicodoou/mob-ai-plugin/pull/7) · Sonnet · sin correcciones ni desvíos. 56 pruebas nuevas (202 en total); cobertura de los tres paquetes nuevos, 100 %.
- **Qué hizo:** las fotos (`PlayerSnapshot`, `MobSnapshot`, `GroupSnapshot`), el registro de amenaza con ventana deslizante (`ThreatLedger`) y la geometría de combate (`PlayerPose`, `CombatGeometry`: arco del escudo, punto de flanqueo a 135°, punto de retirada y tiro anticipado con la fórmula del spike).
- **Arquitectura:** las fotos son records inmutables con copias en orden determinista (`EnumMap`, `List.copyOf`) y solo búsquedas como métodos. La amenaza y el rol no viajan en las fotos: son estado del grupo. La geometría recibe vectores para que los goals la llamen con datos frescos en cada tick.
- **Opinión:** bueno: el código es la especificación al pie de la letra, una tarea por función, sin bucles propios en el registro (`removeIf`), y las 5 roturas del WP más 4 propias (ángulo con altura, tiempo de vuelo multiplicado, lista sin copiar, tiro sin elevar) fueron detectadas. Flojo: `prune` recorre las colas dos veces (barrer y después borrar las vacías); es claro y el tamaño es chico, no vale la pena optimizarlo. Riesgos: los flanqueadores del mismo lado reciben el mismo punto (anotado para WP-22); el tiro anticipado supone movimiento recto y no recalcula el tiempo de vuelo con el punto predicho; la validación estricta de las fotos (vida mayor a la máxima, por ejemplo) va a lanzar excepciones si el adaptador del WP-17 lee mal un dato, lo que es bueno para encontrar el bug pero obliga a que ese WP las atrape y escriba el incidente.

## WP-07 — Selección de objetivo

- **PR:** [#8](https://github.com/Nicodoou/mob-ai-plugin/pull/8) · Sonnet · sin rondas de corrección. 32 pruebas nuevas (234 en total); paquete `target` con 127 de 128 líneas cubiertas.
- **Qué hizo:** `KillTimeEstimator` (vida efectiva, daño por segundo con las fórmulas de armadura, dureza, Protección y Resistencia de Minecraft, efectos de pociones y tiempo de llegada), `TargetSelector` (prioridad = amenaza ÷ tiempo para matarlo, con piso, debilidad y compromiso) y `SpiderTargetRule` (el más cercano, con compromiso).
- **Arquitectura:** el selector devuelve el objetivo y el puntaje desglosado de cada candidato (`TargetScore` con su `KillTimeEstimate`), listo para la `DecisionTrace` del WP-10. Daños e intervalos de ataque son constantes con nombre que los goals del WP-19 y el WP-24 tienen que usar.
- **Opinión:** bueno: transcripción exacta, una tarea por función, y el implementador detectó un error del WP: la rotura del piso de amenaza no mordía con los números elegidos (con 8 de daño y Debilidad 2, `max(1, 8) × 0,25` y `max(1, 8 × 0,25)` dan lo mismo). Agregó una prueba que sí muerde, sin tocar las demás. Flojo: Spotless partió un comentario de `MinecraftConstants`; lo reescribí en el cierre. Riesgos: los daños de la araña y la flecha no se midieron en el server; el tope de 20 de Protección no tiene prueba que lo ejerza (nadie pasa de 16 en el MVP); un tiempo infinito va a necesitar un formato especial cuando las trazas se escriban en JSON (WP-28).

## WP-08 — Grupo, plan y eventos

- **PR:** [#9](https://github.com/Nicodoou/mob-ai-plugin/pull/9) · Sonnet · sin correcciones. 44 pruebas nuevas (278 en total).
- **Qué hizo:** `Group` (miembros con número de ingreso, líder = el más antiguo, ciclo de cuatro estados con transiciones validadas, plan en curso, objetivo comprometido, objetivos de araña y eventos pendientes), `Plan` inmutable con `PlanId`, `ClosedPlan` con éxito fraccional, y `DomainEventPublisher` con límite de 8 publicaciones anidadas.
- **Arquitectura:** el grupo junta sus eventos y la aplicación los retira con `drainEvents()`; el dominio no conoce a los suscriptores. `GroupKnowledge` junta memoria y amenaza para respetar el máximo de 3 parámetros.
- **Opinión:** bueno: fiel a la especificación; el implementador renombró los parámetros a `mobId` para no tapar el campo `id`, un buen criterio. Las 5 roturas del WP y 4 propias (sin compromiso al cerrar, `drainEvents` sin vaciar, rol que queda al sacar un miembro, daño a cualquier jugador) fueron detectadas. Flojo: `restoreMember` valida, agrega, ordena y actualiza el contador en un mismo método (así lo pedía el WP); se puede separar la validación del número de ingreso si crece. Riesgos: el número de plan vuelve a 1 al reiniciar (anotado para WP-14); `Group` ya tiene 25 métodos públicos: si el cerebro le pide más, conviene revisar si alguno pertenece a otra clase.

## WP-09 — Estrategias

- **PR:** [#10](https://github.com/Nicodoou/mob-ai-plugin/pull/10) · Sonnet (el plan decía Haiku; se subió porque elegir flanqueadores usa geometría) · sin correcciones. 20 pruebas nuevas (298 en total).
- **Qué hizo:** `GroupStrategy` y las tres estrategias del catálogo (`DIRECT_ASSAULT`, `FLANK`, `PIN_AND_SHOOT`), `GroupComposition` y `StrategyCatalog` con orden fijo.
- **Arquitectura:** patrón Strategy; cada estrategia declara su requisito en palabras para la traza. En `FLANK` flanquean primero las arañas y después los zombies más de costado respecto del frente del objetivo.
- **Opinión:** bueno: código corto y legible, las 4 roturas del WP y 4 propias (esqueletos que flanquean, requisito de 4 en vez de 3, estrategia por defecto equivocada, ignorar la pose del objetivo) fueron detectadas. Flojo: `DirectAssaultStrategy` y `PinAndShootStrategy` repiten el armado del mapa de roles; con tres estrategias no justifica una abstracción. Riesgo: el ángulo usa la pose de la foto (hasta 10 ticks vieja); para elegir quién flanquea alcanza.

## WP-08B — División de `Group` y estado de reagrupamiento

- **PR:** [#11](https://github.com/Nicodoou/mob-ai-plugin/pull/11) · Sonnet · sin correcciones. 5 pruebas nuevas (303 en total); las existentes conservan nombres y verificaciones.
- **Qué hizo:** regla de ArchUnit de 20 métodos públicos (falló con `Group has 25 public methods` antes de dividir); `Group` pasó a fachada de 8 métodos sobre `GroupRoster` (miembros, líder, arañas), `PlanLifecycle` (estados, plan, compromiso, reagrupamiento) y `PendingEvents`; estado `REGROUPING`; `Plan` guarda sus roles iniciales (CT-06 y CT-07).
- **Opinión:** bueno: refactorización limpia, el código se movió sin cambios y las validaciones quedaron separadas (`requireFreeJoinOrder`). Las 4 roturas del WP y 3 propias (`drain` sin vaciar, inicio de reagrupamiento que no se borra, tick de cierre perdido) fueron detectadas. Flojo: el mensaje `"Group.spiderTarget"` de `GroupRoster` quedó con el prefijo viejo (se movió sin cambios, como pedía el WP). Riesgo: la exclusión de accesores de records en la regla nueva no tiene prueba que la ejercite (ningún record pasa de 20); si se rompe, la regla sería más estricta, no más laxa.

## WP-10A — Piezas del cerebro

- **PR:** [#12](https://github.com/Nicodoou/mob-ai-plugin/pull/12) · Sonnet · sin correcciones. 34 pruebas nuevas (337 en total).
- **Qué hizo:** `PlanEndDetector` (perdido, tiempo, retirada; objetivo a la vista), `RetreatRule` (retirada, vuelta con 60 % y curación lejos de jugadores), `RegroupRule` y `RegroupWindow` (ventana global adaptativa, CT-07), `AttackSuggester`, `RetreatSettings` con la validación cruzada contra el umbral de retirada, y los records de salida del cerebro (`GroupDecision`, `RoleAssignment`, `DecisionTrace`, `BrainResult`, `StrategyCheck`, `AttackChoice`).
- **Opinión:** bueno: fiel y prolijo; las 10 roturas del WP y 5 propias (jugadores muertos que impiden curarse, objetivo muerto «a la vista», ventana que sube con una derrota, estimación en el tick equivocado, umbrales iguales aceptados) fueron detectadas. Desvío aceptado: `RegroupEndReason` entró en el commit de los records porque `DecisionTrace` lo necesita para compilar (error de orden del WP). Flojo: mi especificación dejó un ciclo de paquetes `brain` ↔ `decision`, que se suma al de `group` ↔ `decision`; anotado para limpiar después de E3. Riesgo: ninguno nuevo.

## WP-10B — Cerebro

- **PR:** [#13](https://github.com/Nicodoou/mob-ai-plugin/pull/13) · Opus · sin correcciones. 30 pruebas nuevas (367 en total); `Brain` con 199 de 201 líneas cubiertas, dominio 98,7 %.
- **Qué hizo:** `Brain` (pasos 0 a 6 del WP: validar, observar, ejecutar, evaluar, reagrupar, órdenes), `BrainParts` y `TraceDraft`. Pruebas de caminos con azar guionado e invariantes sobre una pelea simulada de 500 decisiones con semillas fijas, que cubre las tres estrategias, todos los motivos de cierre, reagrupamientos, 20 vueltas de retirada y 2 `LeaderDied`.
- **Arquitectura:** `Brain` solo coordina; cada regla vive en una pieza ya probada. Un record privado `Turn` lleva grupo, foto y borrador de traza para respetar los 3 parámetros. El azar se consume en orden fijo: estrategia, después ataques en el orden de la foto.
- **Opinión:** revisado línea por línea; es el mejor código del proyecto hasta ahora: cada método hace una cosa, los comentarios explican el porqué y el algoritmo se lee igual que el WP. Las 6 roturas del WP y 6 propias (nunca marcar al objetivo visto, curar siempre, planificar sin mobs, no guardar el objetivo de la araña, reagrupar sin objetivo del que huir, detectar el fin antes de actualizar roles) fueron detectadas, varias por más de una prueba. Las dudas del implementador se resolvieron bien: `plan` de la traza se llena cuando hay un plan en juego, y el motivo de un cierre por muerte viaja en el evento. Flojo: cinco `orElseThrow()` sin mensaje en lugares que no deberían fallar; si alguno falla, el incidente igual trae el stack. Riesgos: el guion de invariantes produce un solo `TIMED_OUT`, frágil si se tocan sus parámetros; y encontré en la revisión que la regla de retirada del grupo pierde sensibilidad con miembros que se suman a mitad de plan (anotado en el tablero).

## WP-11 — Simulación de aprendizaje y calibración

- **PR:** [#14](https://github.com/Nicodoou/mob-ai-plugin/pull/14) · Sonnet, más una corrección del orquestador en el PR. Incluye CT-08.
- **Qué hizo:** CT-08 en `Brain` (el zombie con rol `FLANK` usa siempre `flank_strike`); un simulador en `src/test` (`simulation`) que pone a pelear al cerebro real contra un jugador que bloquea y uno que no, con tablas de probabilidades; la tarea `./gradlew simulationReport`, que corre la grilla de calibración aparte del build.
- **Resultados (repetidos por el orquestador con 300 semillas, porque con 30 el margen de ruido era de ±8 puntos):** la configuración del catálogo (velocidad 0,7, vida media 12.000) aprueba el criterio del jugador que bloquea en el 67 % de las corridas, contra el 80 % exigido. Aprueban: velocidad 1,0 con cualquier vida media (85 %, 90 % y 89 %) y 0,9 con 24.000 (82 %). El jugador que no bloquea aprueba siempre (99-100 %), y el golpe frontal contra el que bloquea desaparece en todas las configuraciones: el cuello de botella es la elección de estrategia, que recibe una sola observación por plan.
- **Opinión:** bueno: el simulador es claro, las tablas son datos validados (una fila que no suma 1 frena el arranque) y la grilla corre en un minuto con 300 semillas. Flojo: el método nuevo de `Brain` tenía 4 parámetros; lo corregí en el PR antes de mergear. Riesgo: los números dependen de un modelo del mundo inventado por el orquestador; la validación real es la puerta G1 con Nico en el server.

## WP-12 — Grupos activos y membresía (PR #15, Sonnet)

**Qué hizo.** Abrió la capa de aplicación: `SettingsHolder` (configuración vigente y suppliers por sección, D17), `ActiveGroups` (grupos vivos y el único índice mob → grupo, D15) y los casos de uso `RecruitMob`, `RemoveMember` y `DisbandGroup`. Puerto nuevo `GroupIdSource` (CT-10, D31) con el fake `SequentialGroupIdSource`.

**Arquitectura.** Toda alta y baja de miembros pasa por `ActiveGroups`, que valida antes de escribir; así el índice y los rosters no se desincronizan. Reclutar: ya en grupo → rechazo; sin grupo cercano o grupo lleno → funda; grupo desconocido → rechazo; si no, se suma. Un grupo que queda vacío se disuelve en el momento.

**Revisión.** Código de producción idéntico al WP, 28 pruebas, CI verde, 4 roturas del WP que mordieron y 2 roturas propias (sin disolver al vaciar; sin el chequeo de «ya en grupo») que hicieron fallar 2 pruebas.

**Opinión del código.** Lo bueno: clases chicas, una tarea por función, `ActiveGroups` con 8 métodos públicos y atómico ante errores. Lo flojo: `DisbandGroup` hoy es un pasamanos de `ActiveGroups.remove`; se justifica porque el WP-13 le agrega la memoria y `recordWiped`. Riesgos: al disolver por vaciado se pierden los eventos pendientes del grupo (por ejemplo, el `LeaderDied` del último miembro); el WP-13 tiene que decidir si drena eventos antes de disolver. `RemoveMember` no devuelve el grupo disuelto: el WP-13 necesita mirar el estado (REGROUPING) antes de sacar al último miembro para llamar a `recordWiped`.

## WP-13 — Casos de uso de combate (PR #16, Sonnet)

**Qué hizo.** `TickGroups` (una decisión por grupo y publicación de sus eventos), `RecordOutcome` (intento → memoria del grupo y daño al plan), `RecordDamageTaken` (amenaza), `RecordPlayerDeath` (cierra con `TARGET_DIED`), `ClosePlan` (suscriptor de `PlanClosed`, peso 1) y `GroupEvents`. `DisbandGroup` publica los eventos pendientes después de sacar el grupo; `RemoveMember` recibe `RemovalCause` y avisa a `RegroupWindow.recordWiped()` si el último miembro muere reagrupando (CT-11, D32).

**Arquitectura.** El resultado de un plan llega a la memoria por un solo camino: `PlanClosed` → `ClosePlan`. `BrainResult.closedPlan()` queda para las trazas. `TickGroups` va de a un grupo para que el adaptador aísle fallas.

**Revisión.** Código de producción idéntico al WP; 37 pruebas nuevas y 6 adaptadas (2+3+8+3+5+2+9+3 ejecuciones en las clases tocadas); CI verde; las 5 roturas del WP mordieron, y 2 roturas propias (`ClosePlan` con éxito fijo 1.0; `RecordPlayerDeath` sin filtrar por objetivo) hicieron fallar 2 pruebas.

**Opinión del código.** Lo bueno: casos de uso finos, sin lógica de balance, un camino único para el resultado del plan y los dos riesgos del WP-12 cerrados con pruebas de control (despawn, fuera de reagrupamiento, con sobrevivientes). Lo flojo: `TickGroupsTest` depende de la semilla 7 del cerebro; si cambia el orden de tiradas del dominio, puede requerir otra semilla. Riesgos: un grupo que muere entero ejecutando no cierra su plan (aceptado en el MVP; en la fase 2 los testigos lo necesitan); el `LeaderDied` de un grupo vivo se publica hasta 10 ticks tarde; el bootstrap tiene que acordarse de suscribir `ClosePlan` a `PlanClosed` (si se olvida, el aprendizaje de estrategias se apaga en silencio: el WP de arranque necesita una prueba de que la suscripción existe).

## WP-14 — Puerto de persistencia y JSON (PR #17, Sonnet + arreglo de Opus)

**Qué hizo.** Puerto `MemoryRepository` con los datos guardados (`StoredState`, `StoredGroup`, `StoredAttackRecord`, `StoredStrategyRecord`, `StoredMemories`, `MemoryLoad`) y su implementación JSON: `state.json` y `groups/<uuid>.json`, versión de formato, escritura temporal + reemplazo, cuarentena `.corrupt` y rechazo de versiones más nuevas. `InMemoryMemoryRepository` para las pruebas. Sin dependencias nuevas (Gson 2.14.0 viene con Paper).

**Arquitectura.** El dominio solo ve el puerto y records validados (reusa `Member` y `AttackRecord`); `persistence` tiene los records de archivo package-private y un mapper que convierte todo error en `IllegalArgumentException`, que es la señal de cuarentena.

**Revisión.** Código idéntico al WP, 27 pruebas, 4 roturas del WP que mordieron con la prueba nombrada, y 2 roturas propias (borrado invertido y sin chequeo de nombre de archivo) con 12 fallas. El subagente señaló que un archivo con bytes UTF-8 inválidos hacía fallar toda la carga: lo arreglé en la rama (la `CharacterCodingException` pasa a ser cuarentena) con la prueba `loadQuarantinesAGroupFileWithInvalidUtf8`, verificada sin el arreglo.

**Opinión del código.** Lo bueno: chico, legible, con la frontera de errores bien marcada (corrupto → cuarentena; disco o versión nueva → falla) y cuarentena por archivo. Lo flojo: un campo numérico ausente en el JSON se lee como 0 sin aviso (Gson con records), así que un archivo editado a mano puede perder un contador sin quedar en cuarentena; es poco probable con archivos escritos por el plugin. Riesgo: si el WP-20 guarda después de una carga fallida, se borran grupos; quedó anotado en el WP-15.

## WP-15 — Guardar, cargar, resetear y consultar (PR #18, Sonnet)

**Qué hizo.** `StoredMemoriesMapper` (grupo vivo ↔ datos guardados, con orden determinista de registros), `SaveMemories` (captura en el hilo principal y escritura aparte), `LoadMemories` (reconstruye grupos y saltea los que no se pueden restaurar), `GuardedMemoryRepository` (candado: sin carga exitosa no se guarda), `ResetMemories`, `DescribeGroup` y `DescribePlayerMemory` con sus vistas. En el dominio, `PlanLifecycle.restorePlanSequence` (14 métodos públicos).

**Arquitectura.** El estado global (reloj y ventana) no lo aplican los casos de uso: `LoadMemories` lo devuelve y `SaveMemories` lo recibe; el arranque (WP-20) es el dueño.

**Revisión.** Código conforme al WP (dos comparadores con nombre agregados, aceptados), 26 pruebas, CI verde, las 4 roturas del WP mordieron (la 4, la del orden de jugadores, de forma intermitente como estaba previsto), y 2 roturas propias (reset que cuenta todos los grupos; estado sin el objetivo de reagrupamiento) hicieron fallar 2 pruebas.

**Opinión del código.** Lo bueno: casos de uso finos, el candado cierra el riesgo del WP-14 y la carga tolera grupos rotos sin perder el resto. Lo flojo: `toStoredOrdersRecordsByPlayerThenAttack` muerde de forma intermitente si se rompe el orden (depende del orden de `Map.copyOf`); con el código correcto es determinista. Riesgo: un miembro guardado cuya entidad ya no existe (por ejemplo, un mob que desapareció con el server apagado) queda para siempre en su grupo; anotado en el WP-21 que los mobs del grupo de prueba no se descarten por distancia.

## WP-16 — Runtime, configuración y mensajes (PR #19, Sonnet)

**Qué hizo.** `ServerTickCounter` (reloj propio que sobrevive a reinicios), `JdkRandomSource`, `RandomGroupIdSource`, `config.yml` con los valores del catálogo (velocidad de aprendizaje 1,0), `ConfigLoader` con `InvalidConfigException`, y `messages.yml` con `MessageKey` y `Messages` (MiniMessage, valores insertados como texto literal).

**Arquitectura.** El cargador solo valida presencia y tipo; los rangos los validan los records del dominio y su mensaje llega tal cual, así cada límite vive en un solo lugar.

**Revisión.** YAML idéntico al WP, 19 pruebas, CI verde, 4 roturas del WP con su prueba, 2 roturas propias (enum que acepta cualquier texto; mensajes sin chequear faltantes) con 2 fallas.

**Opinión del código.** Lo bueno: chico, mensajes de error claros y probados uno por uno, el caso `OFF` sin comillas cubierto. Lo flojo: un entero enorme en `config.yml` (por ejemplo `max-size: 99999999999`) sale como `ArithmeticException` de `Math.toIntExact` en vez de `InvalidConfigException`; el WP-21 (`/mobai reload`) tiene que atrapar cualquier excepción del cargador, no solo la propia.

## WP-17 — Traductor de versión y fotos (PR #20, Sonnet + arreglo de Opus)

**Qué hizo.** `VersionTranslator` (tipos de mob, efectos, atributos, Protección, daño absorbido y bloqueo), `MovementTracker` (movimiento real por tick, con límite de teleport), `EntityReadings` (mirada desde el yaw, vida acotada) y `SnapshotFactory` (foto del grupo con mobs cargados y jugadores cercanos en supervivencia o aventura).

**Arquitectura.** Toda constante de Paper sensible a la versión vive en `VersionTranslator`; la foto no conoce constantes.

**Revisión.** El subagente frenó bien: el `switch` sobre `EntityType` que puse en el WP generaba una clase sintética que viola la regla de ArchUnit; se cambió por `if` con `==` y quedó la lección en el manual. Agregué en la rama los `requireNonNull` que faltaban en el constructor de `SnapshotFactory`. 13 pruebas, CI verde, 4 roturas del WP con su prueba, 2 roturas propias (mismo tick sin filtrar; mapeo de esqueleto cambiado) con 2 fallas.

**Opinión del código.** Lo bueno: el traductor es la única puerta a las constantes de versión, y lo puro está probado. Lo flojo y el riesgo principal: `SnapshotFactory` y la mitad de `VersionTranslator` no tienen prueba automática (sin MockBukkit por decisión D20); se verifican en la puerta E5. `EntityType` sí se pudo cargar en JUnit sin server.

## WP-28A — Estado completo del grupo y azar grabado (PR #21, Opus)

**Qué hizo.** Copia y restauración del estado completo de un grupo (`LifecycleCapture`, `ThreatCapture`/`ThreatRecord`, `GroupRoster.spiderTargets`, `GroupCapture` y `GroupCaptureMapper`), azar grabado y repetido (`RecordingRandomSource`, `ReplayRandomSource`, `RecordedDraw`, `DrawKind`) y un único armado del cerebro (`BrainParts.standard`, que ya usa `BrainFixture`) (CT-12).

**Arquitectura.** El dominio expone copiar y restaurar solo en grupos recién armados; la aplicación junta esa copia con la del WP-15. `GroupCapture` en `application` para no crear un ciclo de paquetes.

**Revisión.** El subagente frenó por un error mío en el WP: la repetición de una decisión que cierra el plan exigía consumir azar, y cerrar un plan no sortea nada; se corrigió a «cero números» (documenta el comportamiento). También corregí el conteo de `ThreatLedger` (7, no 8). 23 pruebas nuevas, toda la suite verde con el armado único, 4 roturas del WP con su prueba y 2 roturas propias (sin restaurar `lastEndTick`; sin restaurar objetivos de araña) con 3 fallas. CI verde.

**Opinión del código.** Lo bueno: la prueba central repite una decisión en medio de un plan y otra que lo cierra, y exige igualdad de resultado, traza, estado final, eventos y consumo exacto del azar; cualquier estado nuevo que no se copie la rompe. Lo flojo: en esa prueba la memoria del grupo está vacía, así que la restauración de memoria con datos la cubre solo el WP-15 por separado. `LearningSimulation` sigue armando su propio cerebro (no afecta al plugin). Riesgo: el costo de copiar cada 10 ticks, que mide el WP-29.

## WP-28B — Incidente, JSON y reproducción (PR #22, Sonnet + pruebas de Opus)

**Qué hizo.** `IncidentReport` (con `IncidentLocation` e `IncidentFailure`), su JSON (`IncidentJson`, `IncidentFile`, `OptionalTypeAdapterFactory`) con versión de formato e infinitos (CT-04), y en pruebas `TraceReplay` e `IncidentFixture`.

**Arquitectura.** El JSON vive en `adapter.debug` (no en `persistence`): `persistence` no puede depender de `application`, y quien escribe incidentes es el adaptador del WP-29. `TraceReplay` repite la decisión con `BrainParts.standard`, el azar grabado y la ventana de antes, y compara resultado o falla, estado después, ventana después y números sobrantes.

**Revisión.** El subagente frenó por dos errores míos del WP (paquete del JSON y el jugador inmortal grabado en medio de un plan, donde la traza no trae selección de objetivo); los dos quedaron como lecciones en el manual. Mis roturas propias mostraron dos huecos de pruebas (no restaurar la ventana de reagrupamiento y no exigir que se consuman todos los números pasaban inadvertidos): agregué `leftoverDrawIsDetected` y `regroupWindowIsRestoredBeforeReplaying`, verificadas contra cada rotura. La rotura 2 del WP (`setStrictness`) no muerde: Gson 2.14 relee `Infinity` con `serializeSpecialFloatingPointValues`; se dejó por intención. CI verde (hubo que esperar una corrida que GitHub no disparó al abrir el PR).

**Opinión del código.** Lo bueno: la reproducción es exacta y desconfiada (detecta cinco tipos de adulteración). Lo flojo: `IncidentJson.read("")` da `NullPointerException` en vez de un mensaje claro (lo lee una persona con un archivo a mano; menor). Riesgo: el armado de incidentes en el plugin (WP-29) tiene que copiar antes de decidir y publicar eventos antes de copiar; si no, la reproducción falla aunque el cerebro esté bien.

## Puerta E4 — pasada (6 oct 2026)

560 pruebas en verde, cobertura total 94 %. Memoria de ida y vuelta por disco, casos de uso probados con fakes y reproducción exacta de incidentes provocados desde su JSON.

## WP-18 — Rastreador cuerpo a cuerpo (PR #23, Sonnet)

**Qué hizo.** `AttackTracker` (Java puro: abrir, juntar el primer golpe contra el objetivo, cerrar, clasificar con `AttackClassifier` y registrar con `RecordOutcome`), `MeleeOpening`, `MeleeHit`, `OpenAttempt`, `TargetChecks` y los listeners `DamageListener`, `ThreatListener` y `DeathListener`.

**Arquitectura.** El rastreador no conoce Paper: los listeners traducen eventos a valores simples (daño real = final + absorbido; bloqueo por el modificador `BLOCKING`). `ThreatListener` se separó del de daño para que cada listener tenga una tarea.

**Revisión.** Código idéntico al WP, 13 pruebas, CI verde, sin desvíos; las 4 roturas del WP mordieron y mis 2 roturas propias (perder la invulnerabilidad al abrir; no guardar el golpe) hicieron fallar 6 pruebas.

**Opinión del código.** Lo bueno: toda la lógica de intentos está probada sin server, y el invariante «cada intento termina una vez» tiene prueba propia. Lo flojo: `DeathListener.onRemove` llama a `RemoveMember` para cualquier entidad que se borre (flechas, ítems); es barato (una búsqueda en un mapa) pero ruidoso si algún día se loguea. Riesgo: los listeners y `TargetChecks` solo se verifican en el server (checklist de la puerta E5).

## WP-19 — Roles, goals y golpe frontal (PR #24, Sonnet)

**Qué hizo.** `RoleRegistry`, `MeleeRhythm` (Java puro), `MeleeAttacker`, `GoalContext`, `PressGoal`, `GoalInstaller`, `EntityLifecycleListener` y `TargetListener`. Zombies y arañas reciben nuestro goal; los esqueletos conservan su IA vanilla hasta el WP-24.

**Arquitectura.** El goal solo lee su orden; `MeleeAttacker` es el único que golpea y envuelve `attack()` con el rastreador. El tipo de mob llega siempre desde `VersionTranslator` (condición de Nico al aprobar; regla en el manual). A pedido de Nico se creó `docs/actualizar-paper.md`, el mapa de todo uso directo de Paper y el procedimiento para cambiar de versión, que cada WP que toque Paper mantiene.

**Revisión.** Código conforme al WP, 10 pruebas, CI verde, 4 roturas del WP con su prueba y 2 propias (sin chequeo de alcance; registro que no reemplaza órdenes) que fallaron.

**Opinión del código.** Lo bueno: chico y legible; ningún intento queda abierto aunque `attack()` falle. Lo flojo: `executedAttack` mapea `SKELETON` a golpe frontal para cubrir el `switch`, aunque nunca pasa (el instalador no le da el goal); es inalcanzable pero confuso. Riesgo: nada de esto se ve sin server; la puerta E5 lo verifica.

## WP-20A — Schedulers y aplicador de decisiones (PR #26, Sonnet)

**Qué hizo.** `DecisionCadence` (cada grupo decide una vez por ventana, con un desfase fijo que reparte la carga), `DecisionApplier`, `DecisionParts`, `GroupDecider` (una falla de un grupo no frena a los demás), `DecisionScheduler`, `PersistenceScheduler` (copia en el hilo principal, escritura en un hilo propio, último guardado al apagar) y `MovementSampler`; `RoleRegistry.retainOnly`.

**Revisión.** El trabajo quedó cortado por un apagado de la PC con los commits ya pusheados; el subagente retomó desde las roturas. Código conforme al WP; 13 pruebas; las 4 roturas del WP y 2 propias (aplicador que no indexa las órdenes; escritura que relanza el error) fallaron. `groupsAreSpreadOverTheWindow` quedó en 10 ticks distintos con los UUID reales (corrección permitida). CI verde.

**Opinión del código.** Lo bueno: el guardado es seguro entre hilos (la copia son records inmutables) y nunca puede tumbar el server. Lo flojo: `GroupDecider`, `DecisionScheduler` y `MovementSampler` no tienen prueba sin server. Riesgo: el reparto usa `hashCode` del UUID; con muchos grupos de UUID parecidos podría agruparse, pero con UUID aleatorios (v4) se reparte bien.

## WP-20B — Arranque del plugin (PR #27, Opus)

**Qué hizo.** `CoreServices` (dominio y aplicación armados una vez, Java puro, con `ClosePlan` suscripto y el azar del cerebro grabado), `AdapterServices`, `PluginRuntime` (configuración, memorias con candado, estado restaurado, listeners, goals de los miembros ya cargados y un único tick ordenado) y `MobAiPlugin`.

**Arquitectura.** El tick se agenda desde el constructor con un `Function<Runnable, BukkitTask>`, así los campos son `final` y nunca nulos. Para respetar los 3 parámetros, el armado agrupa piezas en records privados anidados.

**Revisión.** 6 pruebas; 3 roturas mordieron (el subagente agregó una aserción para que la rotura de la suscripción falle con un mensaje claro). **Verificación en el server (Opus):** arranca y registra semilla e informe de carga; al apagar escribe `state.json` (tick 160); con `learning-speed: 2.0` se deshabilita con el mensaje del dominio sin tocar las memorias; al reiniciar, el reloj sigue de 160 a 349. CI verde.

**Opinión del código.** Lo bueno: el armado está en un solo lugar, el núcleo se prueba sin server y la prueba de la suscripción cierra el riesgo del WP-13. Lo flojo: con la carga de memorias fallida, cada ciclo de guardado deja un error con stack trace cada 5 minutos (ruidoso, pero hace visible el candado). Riesgo: un error de arranque que no sea de configuración no se atrapa y deshabilita el plugin con el stack trace de Paper.
