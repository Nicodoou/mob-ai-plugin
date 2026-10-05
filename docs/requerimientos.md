# Requerimientos — Plugin de IA adaptable para mobs

Oct 3, 2026 · @Nicolas Orona

## Resumen y objetivo

Un plugin para Paper que hace que los mobs hostiles peleen en grupos con roles y aprendan de cada jugador: guardan qué ataques y estrategias les funcionan, olvidan con el tiempo si no se refuerza, y ajustan su próxima decisión.

Objetivo de diseño: un grupo completo y bien proporcionado solo puede ser vencido en solitario por un jugador muy preparado (full diamante Protección IV, pociones y manzanas doradas), gastando casi todos sus recursos. Con un equipo coordinado, debe ser ganable. La dificultad sale de amenazas simultáneas que piden respuestas distintas, no de inflar vida y daño.

## Contexto

Es para la tercera edición de un server de Minecraft entre amigos, que arranca de cero. La identidad del server siempre fue tener mobs muy difíciles; en ediciones anteriores eso venía de datapacks desbalanceados. Esta edición busca mantener la dificultad pero que venga de que los mobs piensan, no de números inflados.

- **Plataforma:** Paper 26.3 con Java 25, usando la Mob Goal API y la API de Pathfinder (verificadas vigentes y sin cambios en 26.3, octubre de 2026). Se desarrolla sobre el build beta de 26.3 y la versión se congela un mes antes del lanzamiento. El plugin está activo desde el día 1; lanzamiento a mediados de diciembre de 2026.
- **Lenguaje:** Java para el plugin. La lógica de decisión es lógica pura y puede prototiparse antes en Python.
- **Referencias revisadas:** VortexMobs (Apache-2.0) aprende un perfil global del server, sin grupos, roles ni memoria por jugador; no se usa, este plugin corre solo. MobsThinkNow muestra lo que permite la Mob Goal API, pero es "todos los derechos reservados": no se reutiliza su código.
- **Datapacks:** solo se usan si suman contenido (mobs, estructuras, loot), no para subir vida y daño.

### Base técnica verificada (octubre de 2026)

| Punto | Resultado | Fuente |
| --- | --- | --- |
| Versión de Java | Paper 26.1 en adelante exige Java 25 como mínimo | [PaperMC Docs: Getting started](https://docs.papermc.io/paper/getting-started/) |
| Mob Goal API | Vigente y sin cambios: goals propios, tipos MOVE/LOOK/JUMP/TARGET, goals vanilla y quitar goals por tipo. Guía actualizada el 18/09/2026 | [PaperMC Docs: Mob Goal API](https://docs.papermc.io/paper/dev/mob-goals/) |
| MobGoals en 26.3 | Interfaz completa en el Javadoc de 26.3, sin marca de deprecada ni experimental; `getGoal` puede devolver nulo | [Javadoc paper-api 26.3](https://jd.papermc.io/paper/com/destroystokyo/paper/entity/ai/MobGoals.html) |
| Estado de 26.3 | Minecraft 26.3 salió el 15/09/2026; Paper 26.3 pasó de builds experimentales a beta | [mc-node.net](https://mc-node.net/blog/en/minecraft-26-3-server-breaking-changes/) |

**Decisión:** se desarrolla contra Paper 26.3 con Java 25 y la versión se congela un mes antes del lanzamiento. Si sale una versión nueva de Minecraft cerca de diciembre, se actualiza después del lanzamiento.

## Alcance

El MVP prueba la idea central (mobs que aprenden de cada jugador) con tres tipos de mob; el resto se suma por fases sobre esa base.

| Etapa | Incluye |
| --- | --- |
| MVP (mes 1) | Zombies, esqueletos y arañas · grupo con cerebro · 3 acciones de zombie · memoria por jugador con olvido · selección de objetivo · fuego amigo activado; sin cambiar de objetivo · comando de prueba · persistencia en JSON |
| Fase 2 (mes 2) | Creepers · brujas · ciclo completo de planes · crecimiento por acercamiento y unificación de grupos · observadores · patrones por equipo |
| Posterior | Zombies que ponen y rompen bloques · mobs raros (a definir) · composición adaptable · perfiles de composición · pathfinding líder y seguidores |

**Fuera de alcance:** machine learning entrenado o modelos externos (Jev y similares quedan como experimento futuro), cambios a mobs pasivos o neutrales, y mobs del Nether.

## Glosario

| Término | Significado |
| --- | --- |
| Grupo | Conjunto de mobs con un cerebro y una memoria compartidos. Vive en el plugin, no en las entidades |
| Cerebro | Lógica del grupo que corre cada 10 ticks: elige objetivo, plan y roles |
| Rol | Tarea asignada a un mob (presionar, flanquear, cortar retirada, retirarse, apoyar) |
| Plan / estrategia | Combinación de roles contra un objetivo principal durante una pelea |
| Ataque | Acción concreta de un mob contra un objetivo (golpe de hacha, flecha, mordida) |
| Intento | Un ataque que arrancó estando en rango |
| Acierto | Un intento que hizo daño real al objetivo |
| Causa externa | Fallo no provocado por el objetivo; no modifica la estadística |
| Memoria | Éxitos e intentos con olvido gradual, por (jugador, ataque) y (jugador, estrategia) |
| Patrón de equipo | Memoria global por categoría de equipamiento; punto de partida para jugadores nuevos |
| Observador | Grupo cercano que registra el resultado de una pelea ajena con menos peso |
| Escapar | Un mob pasa X tiempo sin ser atacado por el objetivo, se reúne con otro grupo o forma un grupo nuevo |

## Requerimientos funcionales

### RF-01 Grupos

1. Los mobs hostiles se organizan en grupos con un ID, un cerebro y una memoria compartida.
2. La memoria vive en el plugin (mapa por ID de grupo), así sobrevive a la descarga de chunks.
3. Los grupos crecen por acercamiento: un mob cercano se suma al grupo. Tamaño máximo configurable; los que no entran forman otro grupo.
4. Cada mob tiene un tiempo de vida configurable que solo se cumple fuera de combate y sin jugadores mirando.
5. Dos grupos cercanos pueden unificarse; sus memorias se suman (éxitos e intentos).
6. Si el líder muere, se elige otro de inmediato.

**Composición adaptable (posterior):** la proporción de cada tipo de mob la deciden los datos. Para eso un grupo tiene que poder aceptar o rechazar a los mobs que se acercan, según qué composición le dio mejores resultados. Propuesta: un tope configurable por tipo de mob como válvula de seguridad contra composiciones imbatibles (por ejemplo, 20 brujas curándose entre sí), desactivable.

### RF-02 Roles por tipo de mob

| Mob | Rol | Decide con |
| --- | --- | --- |
| Araña | Hostigador: rápida, aplica lentitud al morder | Regla simple: objetivo más cercano, con compromiso |
| Zombie | Primera línea cuerpo a cuerpo, utilitario | Estadística |
| Esqueleto | Distancia, se cubre | Estadística |
| Creeper | Abre brechas, castiga jugadores agrupados | Puntaje de situación |
| Bruja | Soporte: cura, potencia aliados, debuffea jugadores | Reglas según composición |
| Vindicator (posterior) | Rompe escudos, no pone ni rompe bloques | Estadística |
| Pillager (posterior) | Tirador pesado, no se cubre; el capitán puede ser líder | Estadística |
| Ravager (posterior) | Tanque y montura | Aprende solo "¿me bloquean?" |
| Evoker (posterior) | Control de zona; vexes contra pilares y encierros | Puntaje de situación |

- **Arañas:** la lentitud no se acumula ni se renueva si el jugador ya la tiene, o hay una ventana de inmunidad.
- **Brujas:** zombies y esqueletos son no-muertos (curación los daña, daño instantáneo los cura, inmunes a regeneración y veneno). La bruja elige la poción según quién está en el radio de salpicadura y se ubica detrás de la primera línea.
- **Creepers:** explotan cuando (jugadores en radio × daño) − aliados en radio supera un umbral. Avisan antes para que el grupo se aleje durante la mecha de 1,5 s. Rompen bloques según el gamerule mobGriefing.

### RF-03 Cerebro y ciclo de planes

1. El cerebro corre cada 10 ticks, de forma asíncrona respecto a las decisiones caras, sin bloquear el hilo principal.
2. Ciclo: observar → planificar → sortear → ejecutar → evaluar → reforzar o penalizar → volver a observar con el historial.
3. La observación ocurre también durante el combate, a partir de cada interacción.
4. Cada estrategia declara requisitos mínimos de composición; primero se filtran las viables y después se elige entre ellas.
5. El sorteo elige qué plan ejecutar (no si ejecutar): siempre hay un plan activo. Existe un plan por defecto (ataque directo) para cuando no hay datos.
6. El cerebro asigna roles a los mobs; los goals solo consultan su rol, no calculan puntajes.
7. Cada plan tiene un objetivo principal; con varios jugadores el grupo puede dividirse en sub-escuadrones que escriben en la misma memoria.

### RF-04 Selección de objetivo

1. Prioridad = amenaza ÷ tiempo para matarlo.
2. Amenaza = daño que ese jugador hizo al grupo en los últimos 30 s.
3. Tiempo para matarlo = (vida efectiva + tiempo de llegada) ÷ daño esperado por segundo (tasa de éxito × daño del ataque).
4. Cada efecto de poción modifica la variable que le corresponde: resistencia, absorción y regeneración suben la vida efectiva; veneno y wither la bajan; debilidad baja la amenaza; lentitud baja el tiempo de llegada.
5. El objetivo actual tiene un bonus de compromiso (+20% inicial) para no cambiar con diferencias mínimas. Foco total o reparto entre objetivos se elige por estadística, como cualquier otra estrategia.

### RF-05 Estadísticas de ataques

1. La estadística es por (ataque, objetivo) y vive en la memoria del grupo, no en el mob.
2. Intento = el mob arranca el golpe estando en rango.
3. Acierto = daño real. Golpe que conecta pero se bloquea con escudo = parcial (peso configurable, 0,5 por defecto). Golpe de hacha que deshabilita el escudo = acierto.
4. Si el fallo lo causó el objetivo (esquive, pilar, ender pearl) cuenta como fallo.
5. Si lo causó otra cosa, es neutral. Lista cerrada: otro jugador interrumpió, un aliado se cruzó, el terreno trabó al mob, daño ambiental, cambio de objetivo. Cada causa se detecta con un evento concreto.

### RF-06 Memoria

1. Se guardan éxitos e intentos con olvido por vida media (10 min inicial, configurable).
2. Al decidir se sortea la tasa desde una distribución Beta(éxitos + 1, fallos + 1) (Thompson Sampling): memoria fresca y abundante da valores estables; vieja o escasa, valores dispersos hasta 0–100%.
3. Puntaje final = puntaje base × (0,5 + tasa sorteada).
4. El efecto de la memoria tiene techo (multiplicador entre 0,5 y 1,5).
5. Contexto del jugador: solo o acompañado (se agrega solo si los datos lo justifican).

**Velocidad de aprendizaje (configurable, de 0 a 1):** controla cuánto pesa cada resultado frente al valor inicial de 50%. Se implementa como intentos virtuales al crear cada registro: intentos virtuales = 50 − 48 × velocidad. Con 1 (2 intentos virtuales, la Beta(1,1)), diez aciertos de diez dan 92%: aprende rápido pero se sesga. Con 0 (50 intentos virtuales), dan 58%: aprende de forma muy gradual y necesita muchas peleas. El 0 nunca apaga el aprendizaje. Aplica igual a todas las políticas del RF-12, puede asignarse por grupo y debe ajustarse junto con la vida media del olvido: aprendizaje lento con olvido rápido borra la memoria antes de juntar datos.

### RF-07 Aprendizaje cruzado

1. **Observadores:** al terminar un plan, los grupos a menos de N bloques registran el resultado con peso 0,5.
2. **Patrones por equipo:** memoria global por categoría (armadura y nivel de protección, arma principal, escudo sí/no). Todo resultado se registra en el jugador y en su patrón.
3. Un jugador nuevo arranca con hasta 5–10 intentos virtuales tomados de su patrón; con datos propios, manda su historial.
4. Foto del equipo al arrancar cada plan.
5. **Perfiles de composición** (posterior): cuerpo a cuerpo dominante, distancia dominante o balanceado, más marcas "con soporte" y "con brechas".

### RF-08 Supervivencia de la memoria

1. Si al menos un mob escapa (X tiempo sin ser atacado por el objetivo, reunión con otro grupo o formación de un grupo nuevo), la memoria se conserva y viaja con él.
2. Si el grupo muere entero y no hay testigos, se pierde lo aprendido en esa pelea.
3. La memoria global solo recibe datos de peleas con sobrevivientes o testigos.

### RF-09 Fuego amigo

1. No se cancela el daño entre miembros del mismo grupo (golpes y flechas) pero no provoca cambio de objetivo.
2. Daño de explosiones de creepers a aliados: activado; al dispararse la alerta, los aliados salen de la zona.

### RF-10 Comandos

- Spawnear un grupo de prueba junto al jugador.
- Ver el estado de un grupo y la memoria de un jugador.
- Resetear memorias.
- Recargar la configuración.

### RF-11 Persistencia

1. Memorias en JSON por jugador o grupo, guardadas al apagar y cada cierto tiempo.
2. El olvido usa el reloj del server (ticks de actividad), no timestamps reales: no corre con el server apagado.

**Base de datos:** la memoria se guarda en JSON en el MVP. Las métricas de peleas del RF-12 se guardan en SQLite (base relacional de un solo archivo, sin servidor) desde la fase 2, para poder consultarlas con SQL. Si conviene, la memoria también migra a SQLite en ese momento; como está detrás del puerto RepositorioMemoria, solo cambia el adaptador de persistencia.

### RF-12 Política de selección intercambiable

1. La forma de elegir objetivo, ataque y estrategia de grupo es un componente intercambiable: una interfaz con varias implementaciones, creadas por un Factory según la configuración.
2. Implementaciones iniciales: Thompson Sampling (por defecto), explorar primero (azar durante N intentos y después la mejor tasa), epsilon-greedy (un porcentaje fijo al azar y el resto la mejor) y azar puro (línea base).
3. La política se elige por configuración, sin recompilar, y se puede asignar una distinta a cada grupo para compararlas en paralelo.
4. Cada pelea registra la política usada, el daño hecho, el daño recibido, el tiempo para matar y el resultado.
5. Criterio de éxito: una política con aprendizaje tiene que superar claramente al azar puro; si no, el aprendizaje no está aportando.

## Requerimientos no funcionales

### Rendimiento

- Decisiones de grupo cada 10 ticks, nunca por mob en cada tick.
- Nada que bloquee el hilo principal (cálculos pesados y guardado a disco fuera de él).
- Si se mide lag: pathfinding largo solo para el líder de cada rol, seguidores con caminos cortos.
- Medición con `/mspt` y Spark con grupos de 20, 40 y 80 mobs para fijar el tamaño máximo real.

### Compatibilidad

- Paper 26.3 con Java 25 (ver «Base técnica verificada»).
- Solo API pública de Paper (Mob Goal API, Pathfinder, eventos); evitar NMS porque se rompe en cada versión.
- Convivir con los goals vanilla necesarios (nadar) y quitar solo los de movimiento y combate.

### Configurabilidad

Todo valor de balance va a un archivo de configuración y se recarga sin reiniciar: tamaño máximo de grupo, tiempo de vida, vida media del olvido, peso de observadores, radio de observación, bonus de compromiso, techo de la memoria, umbral de explosión y probabilidades.

### Balance

| Jugador solo contra un grupo completo | Resultado esperado |
| --- | --- |
| Hierro, sin consumibles | No gana; tiene que poder escapar |
| Diamante sin encantar o pocos consumibles | Pierde o escapa con esfuerzo |
| Diamante Protección IV + pociones + manzanas doradas | Gana, pero gasta casi todo |
| Netherite completo bien encantado | Gana con margen |
| 2-3 jugadores medianamente equipados | Ganan si se coordinan |

El benchmark aplica a un grupo que no conoce al jugador; un grupo con memoria es más difícil, dentro del techo de RF-06.

### Jugabilidad

- Un grupo completo se reconoce de lejos (estandarte del capitán, tamaño o efecto visual).
- Un jugador solo siempre puede evitarlo o escapar: los grupos grandes son más lentos que un jugador huyendo o defienden territorio.
- Cada mob tiene una debilidad clara que algún rol de jugador puede explotar.
- No despawnear mobs en pleno combate.

## Decisiones abiertas

- [x] Versión: Paper 26.x.
- [x] Plugin desde el día 1; lanzamiento a mediados de diciembre de 2026.
- [x] Tamaño máximo de grupo y tiempo de vida: configurables.
- [x] Proporción de mobs: la deciden los datos.
- [x] Crecimiento: por acercamiento, con unificación de grupos.
- [x] Escapar: X tiempo sin ser atacado por el objetivo, reunirse con otro grupo o formar uno nuevo.
- [x] Foco total o reparto: por estadística.
- [x] Creepers: daño a aliados activado con alerta de escape; rompen bloques según mobGriefing.
- [x] El olvido corre solo con el server prendido.
- [x] Se usa solo este plugin, sin VortexMobs.
- [x] Disponibilidad: 8 horas por día.
- [x] Valor de X para escapar y de los demás parámetros iniciales: resuelto en la pestaña Catálogo del MVP (X = 10 s).
- [ ] Tope de seguridad por tipo de mob en la composición adaptable: sí o no.
- [ ] Regla para que un grupo acepte o rechace a un mob que se acerca.
- [ ] Mobs raros: cuáles y cómo entran (fuera del MVP).
- [ ] Qué bloques pueden romper los zombies constructores (fuera del MVP).

- [ ] Valor por defecto de la velocidad de aprendizaje y de la vida media del olvido, calibrados juntos.
- [ ] Migrar o no la memoria a SQLite cuando lleguen las métricas.

## Plan por fases

> **Diagrama:** ver «Plan por fases» en [diagramas.md](diagramas.md).

Cada fase cierra con una puerta: no se suman mobs nuevos hasta que lo anterior funciona en peleas reales. Con 8 horas por día, el MVP y la fase 2 entran antes del lanzamiento de mediados de diciembre; las semanas restantes quedan para pruebas con amigos y ajuste de balance.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
| --- | --- |
| Curva de Java y del entorno de plugins frena la semana 1 | Empezar por un solo goal en un zombie antes de cualquier sistema; apoyarse en IA para el boilerplate |
| Conflictos entre goals propios y vanilla (tipos MOVE, LOOK, TARGET) | Quitar solo los goals de movimiento y combate; probar cada goal aislado |
| El sistema aprende mal por definiciones flojas de acierto o causa externa | Lista cerrada de causas externas, cada una atada a un evento; revisar estadísticas con el comando de estado |
| Datos fragmentados que nunca alcanzan para aprender | Perfiles y categorías gruesas, valor previo en la Beta, patrones por equipo |
| Grupos imbatibles para quien juega solo | Benchmark de balance, grupos evitables y lentos al perseguir, techo de la memoria |
| Fuego amigo destruye al grupo | Cancelar daño entre miembros desde el MVP |
| Lag con grupos grandes | Tope de tamaño, cerebro cada 10 ticks, medición con Spark |
| Griefing de bases por creepers y zombies | Lista de bloques rompibles, bloques puestos que desaparecen, acuerdo previo sobre `mobGriefing` |
| Alcance excesivo para el tiempo disponible | Cerrar el MVP antes de sumar mobs; cada fase se apoya en la anterior sin rehacerla |

Arquitectura por capas: [arquitectura.md](arquitectura.md)

Reglas de código: [politica-de-codigo.md](politica-de-codigo.md)

Proceso de bugs: [resolucion-de-bugs.md](resolucion-de-bugs.md)

Contenido concreto del MVP: [catalogo-mvp.md](catalogo-mvp.md)
