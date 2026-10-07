# Arquitectura por capas

El plugin se organiza en cuatro capas con una sola regla: las dependencias apuntan hacia adentro. El dominio, donde viven todas las reglas del juego, no conoce Minecraft; por eso se puede probar sin levantar el server y sobrevive a los cambios de versión.

## Vista general

> **Diagrama:** ver «Capas del plugin» en [diagramas.md](diagramas.md).

Las capas de afuera conocen a las de adentro, nunca al revés. Cuando el dominio necesita algo de afuera (guardar, saber la hora, sortear), define un puerto y una capa externa lo implementa.

## Capa de dominio

El dominio contiene todas las reglas del juego y ninguna dependencia: decide qué hace un grupo a partir de datos, sin saber que existe Minecraft.

**Responsabilidad:** representar el estado del sistema (grupos, memorias, planes) y calcular decisiones (objetivo, plan, roles, aprendizaje).

**Contenido:**

| Elemento | Qué es | Datos principales |
| --- | --- | --- |
| Grupo | Conjunto de mobs con cerebro y memoria compartidos | ID, miembros (UUID y tipo), líder, plan actual, memoria |
| Miembro | Un mob dentro del grupo | UUID, tipo de mob, rol actual, tiempo de vida restante |
| Rol | Tarea asignada a un miembro | Presionar, flanquear, cortar retirada, retirarse, apoyar |
| Estrategia | Plan contra un objetivo | Nombre, requisitos mínimos de composición, roles que reparte |
| RegistroAtaque | Historial de un ataque o estrategia contra un jugador | Éxitos, intentos, último tick de actualización |
| MemoriaGrupo | Todo lo que un grupo aprendió | Jugador → ataque → registro; jugador → estrategia → registro |
| MemoriaGlobal | Patrones por categoría de equipo | Categoría de equipo → estrategia → registro |
| CategoriaEquipo | Equipo agrupado en clases gruesas | Tier de armadura, protección alta o baja, arma principal, escudo |
| Cerebro | La lógica que decide | Recibe una foto y la memoria; devuelve una decisión |
| SelectorObjetivo | Calcula la prioridad de cada jugador | Amenaza, vida efectiva, daño esperado, tiempo de llegada |

**Reglas que viven acá:** olvido por vida media, sorteo desde la distribución Beta, puntaje final con techo, prioridad = amenaza ÷ tiempo para matarlo, bonus de compromiso, requisitos de cada estrategia, fusión de memorias al unir grupos, peso de los observadores, intentos virtuales desde el patrón de equipo y la regla de supervivencia de la memoria.

**Prohibido en el dominio:**

- Importar cualquier cosa de `org.bukkit` o `io.papermc`.
- Leer o escribir archivos, o usar el reloj del sistema.
- Usar `Math.random()` directo: el azar entra por un puerto, para que las pruebas sean reproducibles.
- Guardar referencias a entidades reales: un jugador es un UUID con datos, nunca un objeto `Player`.

## Capa de aplicación

La aplicación traduce "pasó algo" en "qué tiene que hacer el dominio": cada caso de uso recibe datos simples, llama al dominio en orden y devuelve un resultado, sin contener fórmulas propias.

| Caso de uso | Lo dispara | Recibe | Devuelve o produce |
| --- | --- | --- | --- |
| TickGrupos | Scheduler, cada 10 ticks | Una foto por grupo | Una decisión por grupo (objetivo, plan, roles) |
| RegistrarResultado | Rastreador de ataques, al resolver un intento | ResultadoAtaque (mob, objetivo, ataque, acierto/parcial/fallo/neutral) | Memoria del grupo actualizada |
| CerrarPlan | Cerebro, cuando un plan termina | Grupo, objetivo, estrategia, resultado | Memoria del grupo, de la global y de los observadores actualizada |
| ReclutarMob | Adaptador, cuando un mob se acerca a un grupo | Foto del mob y del grupo | Mob sumado, rechazado o grupo nuevo |
| UnirGrupos | Adaptador, cuando dos grupos están cerca | Dos grupos | Un grupo con memorias sumadas |
| RegistrarEscape | Adaptador, cuando un mob cumple la condición de escape (RF-08) | Mob y grupo | Memoria conservada en el grupo que corresponda |
| DisolverGrupo | Adaptador, cuando muere el último miembro | Grupo | Memoria descartada (o conservada si hubo testigos) |
| GuardarMemorias | Scheduler periódico y apagado del server | Todas las memorias | Llamada al puerto de persistencia |
| CargarMemorias | Arranque del server | Nada | Memorias en RAM |

**Regla práctica:** si aparece una cuenta, una condición de juego o un umbral dentro de un caso de uso, se mueve al dominio. La aplicación solo decide el orden de las llamadas y a quién avisar.

**Prohibido en la aplicación:** importar Paper, tocar entidades, y contener lógica de balance.

## Adaptadores de Paper

Los adaptadores son la única capa que conoce Minecraft: convierten lo que pasa en el mundo en datos para la aplicación, y las decisiones de la aplicación en acciones en el mundo.

### Entrada: del mundo hacia el sistema

| Adaptador | Escucha o hace | Llama a |
| --- | --- | --- |
| Scheduler de decisión | Cada 10 ticks arma una foto por grupo | TickGrupos |
| Rastreador de ataques | Abre un intento cuando un mob arranca un golpe; lo resuelve con el evento de daño o por tiempo | RegistrarResultado |
| Listener de daño | Daño entre entidades: resuelve intentos, deja pasar el daño de fuego amigo sin que provoque cambio de objetivo, acumula amenaza por jugador | Rastreador, fotos |
| Listener de proyectiles | Disparos e impactos de flechas | Rastreador |
| Listener de muertes | Muerte de un miembro o del último | DisolverGrupo, cambio de líder |
| Listener de spawns | Entidades que entran al mundo | ReclutarMob |
| Detector de cercanía | Periódico: mobs sueltos cerca de grupos y grupos cerca entre sí | ReclutarMob, UnirGrupos |
| Detector de escape | Periódico: mobs sin ser atacados por X tiempo, reunidos con otro grupo o aislados | RegistrarEscape |
| Listener de creepers | Encendido de la mecha | Alerta a los aliados en el radio |
| Listener de objetivos | Cambios de objetivo vanilla | Los cancela si contradicen el rol asignado |
| Comandos | Spawnear grupo de prueba, ver estado, resetear, recargar | El caso de uso que corresponda |

### Salida: del sistema hacia el mundo

- **Aplicador de decisiones:** recibe la decisión de cada grupo y escribe el rol de cada mob en un registro de roles (mapa UUID del mob → rol).
- **Goals (Mob Goal API):** un goal por rol. Cada goal solo pregunta "¿cuál es mi rol?" al registro y se activa si le toca. No calcula puntajes ni lee memoria.
- **Instalador de goals:** al sumarse un mob a un grupo, quita los goals vanilla de los tipos `MOVE`, `LOOK` y `TARGET` y pone los propios. Los goals propios se pierden cuando el chunk se descarga, así que se reinstalan cada vez que un miembro vuelve al mundo (`EntityAddToWorldEvent`). En el MVP un mob solo sale del grupo al morir, así que no hace falta restaurar goals vanilla.
- **Rastreador de movimiento:** guarda la posición de cada jugador tick a tick y calcula su movimiento por tick. `getVelocity()` no sirve: da 0 al caminar (ver `docs/plan/hallazgos-api.md`).

### Traductor de versión (capa anticorrupción)

Todo lo que cambia entre versiones de Minecraft o de Paper pasa por un único componente, el traductor, en el borde inferior de los adaptadores. Es el único lugar del plugin que nombra constantes de Paper; el resto de los adaptadores y todas las capas internas usan tipos propios.

| Tipo propio | Lo traduce desde |
| --- | --- |
| TipoMob | Tipos de entidad de Paper |
| Efecto | Tipos de efecto de poción |
| CategoriaEquipo | Materiales de armadura y armas, encantamientos de protección |
| Atributos (vida máxima, armadura) | Atributos de Paper |
| Goals vanilla a quitar o conservar | Claves de goals vanilla de la Mob Goal API |

- **Al actualizar de versión** se revisa y corrige solo el traductor; si compila y sus pruebas pasan, el resto sigue igual.
- **Va dentro de los adaptadores, no entre adaptadores y aplicación:** entre esas dos capas ya solo viajan fotos con tipos propios, así que ahí no queda nada de Minecraft que traducir.
- **Una sola implementación** mientras el server use una sola versión. Si algún día hay que soportar dos versiones a la vez, el traductor pasa a ser una interfaz con una implementación por versión.

**Regla:** los adaptadores no deciden nada. Si un listener empieza a tener condiciones de juego, esa lógica pertenece al dominio.

## Persistencia

La persistencia guarda la memoria en JSON y es reemplazable: implementa un puerto definido adentro, así que pasar a SQLite después no toca el dominio.

| Qué se guarda | Dónde | Cuándo |
| --- | --- | --- |
| Memoria de cada grupo vivo | Un archivo por grupo | Cada intervalo de guardado (6.000 ticks por defecto) y al apagar |
| Memoria global por categoría de equipo | Un archivo único | Cada intervalo de guardado y al apagar |
| Contador de ticks del reloj del server | Archivo de estado | Junto con las memorias |

- **Formato versionado:** cada archivo lleva un número de versión, para poder migrar datos si cambia la estructura.
- **Guardado sin trabar el server:** en el hilo principal se copia la memoria a datos simples (es rápido); la escritura del archivo corre en otro hilo.
- **Escritura segura:** se escribe en un archivo temporal y después se reemplaza el real, para que un corte de luz no deje un JSON roto.
- **Grupos y miembros no se guardan como entidades:** al reiniciar, se reconstruyen por cercanía; lo que sobrevive es la memoria.

## Puertos y fotos

Las capas se comunican sin romper la regla de dependencias de dos formas: los datos cruzan como fotos (objetos simples, sin lógica) y lo que el centro necesita de afuera se pide por puertos (interfaces definidas adentro, implementadas afuera).

### Puertos

| Puerto | Para qué lo usa el centro | Lo implementa |
| --- | --- | --- |
| RepositorioMemoria | Cargar y guardar memorias | Persistencia JSON |
| Reloj | Saber el tick actual para calcular el olvido | Contador de ticks del server |
| Aleatorio | Sortear desde la Beta y elegir entre estrategias | Generador real en el server; uno con semilla fija en las pruebas |

Solo tres. Cualquier otra interfaz tiene que justificarse con una prueba que sin ella no se pueda escribir.

### Fotos (entrada al dominio)

| Foto | Datos |
| --- | --- |
| FotoJugador | UUID, posición y dirección hacia la que mira (horizontal), movimiento por tick (medido, no `getVelocity()`), vida, absorción, vida máxima, armadura, dureza, nivel total de Protección, nivel de cada efecto activo, si está bloqueando con escudo. La categoría de equipo se suma con RF-07 (fase 2) |
| FotoMob | UUID, tipo, posición, vida, vida máxima. El rol no va: es estado del grupo, no algo que el adaptador ve; el grupo es el de la FotoGrupo que lo contiene |
| FotoGrupo | ID del grupo, tick actual, sus FotoMob y los FotoJugador cercanos. La amenaza no va: la guarda el grupo en su `ThreatLedger` |
| ResultadoAtaque | Mob, objetivo, ataque, resultado (acierto, fallo o neutral), causa si es neutral |

### Decisiones (salida del dominio)

| Decisión | Datos |
| --- | --- |
| DecisionGrupo | Objetivo principal, estrategia elegida, rol por cada mob |
| AlertaCreeper | Centro y radio de la explosión; los aliados dentro reciben el rol "retirarse" |

**Regla de las fotos:** son inmutables y no tienen métodos con lógica. Se arman en el adaptador, se usan en ese tick y se descartan.

## Flujos completos

Todo el sistema se mueve con dos flujos: uno guiado por eventos que escribe en la memoria, y uno guiado por el reloj que la lee para decidir.

### Flujo 1: registrar el resultado de un ataque (por eventos)

1. Un zombie del grupo arranca un golpe contra un jugador estando en rango. El rastreador de ataques (adaptador) abre un intento pendiente.
2. Durante la ventana de resolución llega uno de estos casos:
   - Evento de daño con daño real: acierto.
   - Evento de daño absorbido entero por escudo: fallo causado por el objetivo.
   - Otro jugador golpeó al zombie y le cortó el ataque: neutral, causa externa.
   - No llega nada antes de que venza la ventana: fallo.
3. El rastreador arma un ResultadoAtaque y llama a RegistrarResultado (aplicación).
4. RegistrarResultado busca el grupo del zombie y le pasa el resultado a su memoria (dominio).
5. La memoria aplica el olvido pendiente hasta el tick actual y suma el intento (y el éxito, si lo hubo). Si es neutral, no cambia nada.

### Flujo 2: decidir (cada 10 ticks)

1. El scheduler (adaptador) arma una FotoGrupo por cada grupo con jugadores cerca.
2. Llama a TickGrupos (aplicación) con todas las fotos.
3. Para cada grupo, el cerebro (dominio):
   1. Calcula la prioridad de cada jugador y aplica el bonus de compromiso al objetivo actual.
   2. Filtra las estrategias que la composición del grupo puede ejecutar.
   3. Sortea una tasa desde la Beta para cada estrategia viable y elige la de mayor puntaje.
   4. Reparte roles entre los mobs según la estrategia.
4. TickGrupos devuelve una DecisionGrupo por grupo.
5. El aplicador de decisiones (adaptador) escribe los roles en el registro de roles.
6. En los ticks siguientes, cada goal ve su rol y mueve al mob. Lo que el mob hace genera nuevos eventos, y vuelve a empezar el flujo 1.

### Cierre de un plan

Cuando el cerebro detecta que un plan terminó (objetivo muerto, objetivo perdido, plan agotado, grupo en retirada), llama a CerrarPlan: el resultado se registra en la memoria del grupo con peso 1, en la memoria global por categoría de equipo y en los grupos observadores cercanos con peso 0,5.

## Trazabilidad y depuración

Requisito obligatorio: ante un bug, el modo debug tiene que decir dónde ocurrió, por qué camino pasaron los datos y cómo reproducirlo. Se apoya en que el dominio es determinista: con la misma foto, la misma memoria, la misma configuración y los mismos números al azar, decide exactamente lo mismo.

1. **IDs de correlación.** Toda traza lleva el grupo y el tick, y además el `PlanId` (grupo + número de plan dentro del grupo) y el `AttemptId` (número del intento en el rastreador) cuando corresponden. Filtrando por un ID se ve el recorrido completo de un dato: evento de Paper → rastreador → clasificador → memoria.
2. **Explicaciones como datos.** El dominio no escribe logs: devuelve la explicación junto con el resultado.
   - `DecisionTrace` (dentro de `BrainResult`): prioridad de cada jugador con sus componentes, estrategias viables y descartadas con la razón, valor sorteado y puntaje de cada opción, roles y ataques sugeridos, y la condición que cerró el plan.
   - `ClassificationTrace` (dentro del resultado del clasificador): la regla del rastreador que aplicó (1 a 8) y los hechos que usó.
   - La memoria devuelve el registro antes y después de cada cambio.
3. **Escritura.** `TraceWriter` escribe JSON Lines en `plugins/MobAI/debug/`, en otro hilo. El nivel (`TraceLevel`: `OFF`, `DECISIONS` o `FULL`) se elige por grupo con `/mobai debug <grupo|all> <nivel>`. En la consola solo salen errores y una línea por incidente.
4. **Caja negra.** `FlightRecorder` guarda en RAM los últimos eventos de traza de cada grupo (cantidad configurable, 200 por defecto), siempre, aunque el nivel sea `OFF`.
5. **Incidentes.** Ante una excepción atrapada en el borde de los adaptadores, una validación del dominio que falla o un estado imposible, `IncidentWriter` escribe `incident-<id>.json` con:
   - dónde: capa, clase, método, caso de uso y stack trace;
   - la caja negra del grupo;
   - las entradas para reproducirlo: foto, estado del grupo (memoria, plan y estado), configuración y los números al azar que se consumieron.
6. **Reproducción.** `RecordingRandomSource` envuelve el puerto `RandomSource` (no es un puerto nuevo) y registra cada número sorteado. `TraceReplay`, una herramienta de prueba, lee un incidente, reconstruye el estado, entrega los mismos números al azar y vuelve a ejecutar el dominio y la aplicación: el resultado tiene que coincidir. Así un incidente se convierte en la prueba JUnit que reproduce el bug (paso 3 de `resolucion-de-bugs.md`).
7. **Límite.** Lo que hace Minecraft por su cuenta (pathfinding, física de las flechas) no se reproduce exactamente. Para esa parte, el incidente trae los hechos crudos de Paper y un guion para repetirlo en el server: posiciones, equipo, efectos, composición del grupo y política.

**Reglas:** todo flujo nuevo emite su traza; ningún error sale sin contexto (grupo, tick e IDs); las pruebas verifican también las explicaciones.

## Hilos y tiempo

Todo corre en el hilo principal del server salvo la escritura a disco; el tiempo se mide en ticks propios, no con la hora real.

- **Hilo principal:** listeners, rastreador, scheduler, cerebro y goals. Paper no permite tocar entidades desde otros hilos, y el cerebro cada 10 ticks es lo bastante barato como para no necesitarlo.
- **Hilo secundario:** solo la escritura de los JSON. La copia de los datos a guardar se hace antes, en el hilo principal.
- **Reloj del server:** un contador de ticks propio que avanza solo con el server prendido y se guarda con la memoria. El olvido se calcula con este contador, así que no corre con el server apagado.
- **Repartir la carga:** si hay muchos grupos, no todos deciden en el mismo tick. Cada grupo decide en su propio desfase dentro de la ventana de 10 ticks.
- **Presupuesto:** el tick del plugin no debería sumar más de unos pocos milisegundos al MSPT. Se mide con Spark antes de subir el tamaño máximo de grupo.

## Pruebas por capa

Cada capa se prueba de la forma más barata posible: el dominio en segundos con JUnit, y el server solo para lo que de verdad depende de Minecraft.

| Capa | Cómo se prueba | Ejemplos |
| --- | --- | --- |
| Dominio | JUnit, sin server, con Reloj y Aleatorio falsos | El olvido reduce a la mitad tras una vida media; 1 de 1 da 67% y no 100%; un tanque con veneno sube de prioridad; dos memorias fusionadas suman sus intentos |
| Aplicación | JUnit con un RepositorioMemoria en memoria | RegistrarResultado ignora los neutrales; CerrarPlan escribe en observadores con peso 0,5 |
| Adaptadores | En el server de prueba, con el comando para spawnear un grupo | El rastreador clasifica bien un golpe bloqueado con escudo; el fuego amigo no cambia objetivos |
| Persistencia | JUnit sobre una carpeta temporal | Guardar y cargar devuelve la misma memoria; un archivo de versión vieja se migra |
| Sistema completo | Peleas reales | Benchmark de balance: full diamante P4 gana gastando casi todo |

Como el dominio se puede prototipar en Python antes de pasarlo a Java, las mismas pruebas sirven dos veces: primero para validar la idea en Python, después para comprobar que la traducción a Java da los mismos números.

## Límites: cómo no sobre-diseñar

La arquitectura sirve mientras ahorre trabajo; las capas son una herramienta, no una meta.

- **Interfaces solo donde compran algo:** los tres puertos existen para poder probar el dominio sin server. Fuera de eso, clases directas.
- **Casos de uso finitos están bien:** si uno termina siendo un método que llama a otro, no hace falta inflarlo.
- **Sin capas extra:** nada de "servicios", "managers" y "handlers" encadenados para lo mismo. Si dos clases hacen pasar el dato sin transformarlo, sobra una.
- **El dominio crece con el juego, no con la arquitectura:** cada mob nuevo (creepers, brujas, illagers) suma reglas al dominio y un goal en los adaptadores; la estructura de capas no cambia.
- **Señal de alarma:** si para agregar una regla simple hay que tocar más de tres archivos, la separación está mal puesta y hay que revisarla.

## Patrones de diseño

Cada patrón entra porque resuelve un problema concreto de este diseño; los que hoy no resuelven nada quedan afuera.

| Patrón | Dónde | Problema que resuelve |
| --- | --- | --- |
| Strategy (estrategias de grupo) | Dominio | Agregar una estrategia sin tocar el cerebro |
| Strategy (comportamiento por tipo de mob) | Dominio | Araña, creeper y bruja con reglas propias bajo un mismo contrato |
| Strategy + Factory (política de selección) | Dominio; el Factory en el arranque | Cambiar el algoritmo de elección por configuración (RF-12) |
| Máquina de estados | Dominio (cerebro) | Que el grupo nunca quede en un estado inválido |
| Observer | Dominio | Avisar cierre de plan, muerte del líder y alerta de creeper sin acoplar emisor y receptores |
| Registry | Adaptadores | Que los goals consulten su rol al instante |
| Repository | Puerto en el dominio, implementación en persistencia | Cambiar JSON por SQLite sin tocar el dominio |
| Adapter (anticorrupción) | Adaptadores | Aislar los cambios entre versiones de Paper |
| Value Object | Entre capas | Fotos y decisiones inmutables |
| Inyección por constructor | Arranque del plugin | Probar con reloj y azar falsos |
| Builder (solo en pruebas) | Pruebas | Armar fotos de prueba sin repetir código |

### Strategy: estrategias y comportamientos

Cada estrategia de grupo es una clase que cumple el mismo contrato. El cerebro filtra las viables, consulta la memoria y le pide los roles a la elegida; para sumar una estrategia nueva se crea una clase y se registra, sin tocar el cerebro. El `id` es la clave con la que la memoria la indexa.

```java
interface GroupStrategy {
    String id();
    boolean isViable(GroupSnapshot snapshot);
    Map<UUID, Role> assignRoles(GroupSnapshot snapshot, UUID target);
}
```

El comportamiento propio de cada tipo de mob (regla simple de la araña, puntaje de explosión del creeper, reglas de pociones de la bruja) usa el mismo patrón con otra interfaz. Los illagers se suman igual.

La elección de ataque y objetivo de cada mob no es una estrategia que se construye: se calcula en cada decisión con la política de selección. Por eso no se usa Builder para las estrategias.

### Política de selección: Strategy + Factory

El algoritmo que elige entre opciones (objetivo, ataque o estrategia de grupo) es intercambiable. Un Factory crea la política que indique la configuración, y se puede asignar una distinta a cada grupo.

```java
interface SelectionPolicy {
    <T> T choose(List<T> options, Function<T, AttackRecord> history, RandomSource random);
}
```

| Política | Cómo elige |
| --- | --- |
| Thompson Sampling (por defecto) | Sortea desde la Beta de cada opción; sin datos equivale al azar y converge a medida que aprende |
| Explorar primero | Al azar durante los primeros N intentos; después, la de mejor tasa |
| Epsilon-greedy | Un porcentaje fijo al azar y el resto, la de mejor tasa |
| Azar puro | Siempre al azar; línea base para medir si el aprendizaje aporta |

La velocidad de aprendizaje actúa sobre la memoria (intentos virtuales), no sobre la política, así que funciona igual con las cuatro.

### Máquina de estados: el ciclo del grupo

El ciclo del grupo se modela como cinco estados con transiciones explícitas: observar, planificar, ejecutar, evaluar y reagrupar. Se implementa con un `enum` y un `switch` en el cerebro; si algún estado acumula mucha lógica propia, se migra a una clase por estado. El estado actual se muestra en el comando de debug.

> **Diagrama:** ver «Ciclo del grupo (máquina de estados)» en [diagramas.md](diagramas.md).

Un plan termina cuando el objetivo muere, se pierde (`TARGET_LOST`), el plan se agota o el grupo entra en retirada. «Objetivo perdido» y «escape del mob» son conceptos distintos: el primero cierra un plan; el segundo (RF-08) decide si la memoria sobrevive. Al evaluar se llama a CerrarPlan, que publica el evento PlanCerrado.

### Retirada táctica y reagrupamiento

La retirada no saca a un mob de la pelea: lo aparta para que se recupere y vuelva.

- **Individual.** Un mob con 30 % de vida o menos pasa a `RETREAT`: se aleja del objetivo y se queda al margen. Con 60 % o más vuelve al rol que tenía al empezar el plan (o al rol básico si se sumó después). El margen entre los dos umbrales evita que cambie de rol en cada decisión.
- **Curación.** Mientras está en `RETREAT` y ningún jugador está a menos de 12 bloques, el plugin le cura 1 punto cada 50 ticks, el ritmo de Regeneración I, sin partículas ni ícono y sin límite de duración. No se usa el efecto de poción: zombies y esqueletos son no-muertos y Minecraft los hace inmunes a Regeneración y Veneno.
- **Del grupo.** Si más de la mitad de los mobs con los que empezó el plan murieron o están en `RETREAT`, el plan cierra con `GROUP_RETREATED` y el grupo pasa a **reagrupar**: todos se retiran y se curan. Sale cuando más de la mitad de los mobs presentes tiene 60 % o más, o cuando vence la ventana de reagrupamiento; después vuelve a observar.
- **Ventana de reagrupamiento adaptativa y global.** Empieza en 600 ticks y la ajusta la experiencia de todos los grupos: si un grupo muere entero mientras se reagrupa, baja 50 ticks; si termina de reagruparse vivo, sube 50. Se mantiene entre 200 y 1.200 ticks y se guarda con las memorias.

### Observer: avisar sin acoplar

Un sistema de eventos propio y simple (una lista de suscriptores) dentro del dominio. No usa los eventos de Bukkit, para no meter Paper en el dominio.

| Evento | Suscriptores |
| --- | --- |
| PlanCerrado | Memoria del grupo, memoria global por categoría de equipo, grupos testigos, registro de métricas |
| LiderMuerto | Elección de un nuevo líder |
| CreeperEncendido | Asignación del rol "retirarse" a los aliados dentro del radio |
| MiembroEscapo | Conservación de la memoria en el grupo que corresponda |

### Registry: roles y pertenencia

Dos mapas en los adaptadores: `UUID del mob → rol` y `UUID del mob → ID del grupo`. El aplicador de decisiones los escribe; los goals y los listeners los leen. Los goals no conocen al cerebro, solo al registro, y la consulta en cada tick es una búsqueda en un mapa.

### Builder: solo en las pruebas

Las pruebas del dominio necesitan muchas fotos distintas. Un builder de pruebas evita repetir código: `new GroupSnapshotBuilder().withPlayer(diamondP4).withZombies(5).build()`. No se usa en el código de producción.

### Lo que no se usa

- **Singleton y estado estático global:** rompe las pruebas porque no se puede reemplazar nada. Lo reemplaza la inyección por constructor.
- **Behavior trees:** sería un segundo sistema de decisión compitiendo con los puntajes, el sorteo y la máquina de estados.
- **Frameworks de inyección de dependencias:** para unas 30 clases, armar todo a mano en el arranque es más claro.
- **Patrones por las dudas** (Abstract Factory, Visitor, Command): no resuelven ningún problema actual. Si aparece el problema, se agregan.

## Diseño del rastreador de ataques

El rastreador abre un intento cuando uno de nuestros goals ataca, junta los hechos de lo que pasó y se los pasa al dominio, que es quien decide si fue acierto, fallo o neutral. Todo intento termina exactamente una vez.

### Principio: el adaptador junta hechos, el dominio clasifica

- **`AttackTracker`** (adaptador): abre intentos, escucha eventos de Paper y arma un `AttackFacts` con datos crudos.
- **`AttackClassifier`** (dominio): función pura que recibe `AttackFacts` y devuelve un `AttackOutcome` (`Hit`, `Partial` si el golpe conectó pero se bloqueó, `Miss`, o `Neutral` con su causa).
- Las reglas de clasificación, que son lo más delicado, se prueban con JUnit sin server.

### Quién abre el intento

Paper no tiene un evento de "el mob empezó a atacar". Como los ataques los ejecutan nuestros goals, el goal abre el intento en el mismo momento en que ataca. El goal verifica el alcance antes de atacar; no se depende de que la API lo haga.

### Cuerpo a cuerpo y proyectiles

| Tipo | Ejemplos | Cuándo se resuelve | Con qué |
| --- | --- | --- | --- |
| Cuerpo a cuerpo | Zombie, araña, vindicator | En el mismo tick del ataque | El evento de daño que dispara el propio golpe |
| Proyectil | Flecha de esqueleto o pillager | Cuando el proyectil impacta o vence el plazo | El evento de impacto del proyectil, asociado por el UUID del proyectil |
| Explosión (fase 2) | Creeper | Al explotar | Daño a los jugadores dentro del radio |

### Hechos que se registran (`AttackFacts`)

Mob, objetivo, ataque, tick de apertura, daño real al objetivo (daño final más lo que absorbió la absorción, porque con absorción el daño final es 0), si el modificador de bloqueo (`BLOCKING`) estuvo presente, si el objetivo estaba bloqueando con escudo y de frente, si estaba en invulnerabilidad post-golpe, qué tocó el proyectil (el objetivo, un aliado, otra entidad o un bloque), si el mob recibió daño de un tercero antes de resolver, si el evento fue cancelado por otro plugin, y si el objetivo sigue válido (vivo, conectado, mismo mundo).

### Reglas de clasificación

Se evalúan en este orden; gana la primera que aplica.

| Prioridad | Condición | Resultado |
| --- | --- | --- |
| 1 | El objetivo murió, se desconectó o cambió de mundo antes de resolver | Neutral |
| 2 | El daño fue cancelado por otro plugin o una protección de región | Neutral |
| 3 | El objetivo era invulnerable al abrir el intento (`noDamageTicks` mayor a la mitad del máximo, o modo creativo o espectador) y no llegó evento de daño | Neutral |
| 4 | El proyectil impactó a un aliado | Neutral (el daño al aliado se aplica, pero no cuenta para la memoria ni provoca cambio de objetivo) |
| 5 | Un tercero golpeó al mob e interrumpió el ataque | Neutral |
| 6 | Daño real mayor a 0 sobre el objetivo (daño final más lo absorbido) | Acierto |
| 7 | El evento trae el modificador `BLOCKING` (el escudo bloqueó; `isBlocking()` no alcanza, porque da `true` aunque el golpe venga por la espalda) | Parcial (peso configurable, 0,5 por defecto); si el golpe fue de hacha y deshabilitó el escudo, acierto |
| 8 | El proyectil tocó un bloque u otra entidad, o venció el plazo | Fallo |

### Ciclo de vida de un intento

| Estado | Se entra cuando | Se sale hacia |
| --- | --- | --- |
| Abierto | El goal ataca | Resuelto, vencido o cancelado |
| Resuelto | Llega el evento que lo decide | Se registra el resultado y se descarta |
| Vencido | Pasa el plazo sin evento (proyectiles: 60 ticks propuestos) | Se registra como fallo |
| Cancelado | El mob muere, se descarga su chunk o sale del grupo | Se descarta sin registrar (neutral) |

### Invariantes

- **Cada intento termina exactamente una vez**: no queda abierto ni se registra dos veces.
- **Sin fugas de memoria**: los intentos abiertos se limpian cuando muere el mob, se descarga el chunk o vence el plazo.
- **Un mob tiene como máximo un intento cuerpo a cuerpo abierto**; los proyectiles pueden tener varios en vuelo.

### Verificado en el spike

Los tres puntos que había que verificar se probaron en el server (detalle en `docs/plan/hallazgos-api.md`):

- **Bloqueo con escudo:** se detecta con el modificador `BLOCKING` del evento de daño. La lectura de modificadores usa una API deprecada y vive en un solo método de `VersionTranslator`.
- **Invulnerabilidad:** un golpe que cae en la ventana de invulnerabilidad no dispara ningún evento; se detecta al abrir el intento.
- **Mismo tick:** el evento de daño llega dentro de la llamada a `attack()`, así que el cuerpo a cuerpo se resuelve sin plazo.
- **Flechas que llegan tarde:** un impacto que llega después de que venció el plazo del intento se ignora.

### Decisión: golpes bloqueados cuentan como parciales

Un golpe que conecta pero se bloquea con escudo cuenta como parcial: gasta durabilidad del escudo y obliga al jugador a defenderse. Su peso es configurable (0,5 por defecto) y se suma como fracción de éxito. Si en las pruebas los mobs insisten en atacar de frente contra jugadores que bloquean, se baja el peso (por ejemplo a 0,25) para que el flanqueo gane. Un golpe de hacha que deshabilita el escudo cuenta como acierto completo aunque haga 0 de daño.

## Nombres en el código

El código está en inglés y la documentación en español; esta tabla traduce cada concepto del documento al nombre que tiene en el código.

| Concepto en el documento | Nombre en el código | Capa |
| --- | --- | --- |
| Grupo, miembro, rol | `Group`, `Member`, `Role` | Dominio |
| Estrategia de grupo, composición del grupo | `GroupStrategy`, `GroupComposition` | Dominio |
| Registro de ataque | `AttackRecord` | Dominio |
| Memoria del grupo, memoria global | `GroupMemory`, `GlobalMemory` | Dominio |
| Categoría de equipo | `GearCategory` | Dominio |
| Cerebro | `Brain` | Dominio |
| Selector de objetivo | `TargetSelector` | Dominio |
| Política de selección | `SelectionPolicy` | Dominio |
| Estados del grupo | `GroupState`: `OBSERVING`, `PLANNING`, `EXECUTING`, `EVALUATING`, `REGROUPING` | Dominio |
| Miembros y líder de un grupo, ciclo del plan, eventos pendientes | `GroupRoster`, `PlanLifecycle`, `PendingEvents` | Dominio |
| Ventana de reagrupamiento, regla de reagrupamiento, configuración de retirada | `RegroupWindow`, `RegroupRule`, `RetreatSettings` | Dominio |
| Roles | `Role`: `PRESS`, `FLANK`, `SHOOT`, `RETREAT` (MVP); `CUT_OFF`, `SUPPORT` (posteriores) | Dominio |
| Resultado de ataque | `AttackOutcome` (interfaz `sealed`): `Hit`, `Partial`, `Miss`, `Neutral` | Dominio |
| Motivo de cierre de un plan | `PlanEndReason`: `TARGET_DIED`, `TARGET_LOST`, `TIMED_OUT`, `GROUP_RETREATED` | Dominio |
| Eventos | `PlanClosed`, `LeaderDied`, `CreeperIgnited`, `MemberEscaped` (escape del mob, RF-08) | Dominio |
| Fotos | `PlayerSnapshot`, `MobSnapshot`, `GroupSnapshot` | Entre capas |
| Decisiones | `GroupDecision`, `CreeperAlert` | Entre capas |
| Puertos | `MemoryRepository`, `ServerClock`, `RandomSource`, `GroupIdSource` | Dominio (interfaz) |
| Casos de uso | `TickGroups`, `RecordOutcome`, `ClosePlan`, `RecruitMob`, `MergeGroups`, `RecordEscape`, `DisbandGroup`, `SaveMemories`, `LoadMemories` | Aplicación |
| Rastreador de ataques | `AttackTracker` | Adaptadores |
| Registro de roles | `RoleRegistry` | Adaptadores |
| Traductor de versión | `VersionTranslator` | Adaptadores |
| Ataque (los 7 del catálogo) | `Attack` | Dominio |
| Tipo de mob, efecto de poción | `MobKind`, `EffectKind` | Dominio |
| Identificadores | `MobId`, `PlayerId`, `GroupId`, `StrategyId`, `PlanId`, `AttemptId` | Dominio |
| Vector o posición | `Vec3` | Dominio |
| Valores fijos de Minecraft | `MinecraftConstants` | Dominio |
| Configuración | `MobAiSettings` (un record por sección) | Dominio |
| Intentos virtuales de la velocidad de aprendizaje | `LearningPrior` | Dominio |
| Estimación de éxito (los parámetros de la Beta) | `SuccessEstimate` | Dominio |
| Sorteo desde la Beta | `BetaSampler` | Dominio |
| Opción a elegir, con su puntaje base | `SelectionCandidate` | Dominio |
| Resultado de una elección, puntaje de cada opción, multiplicador de memoria | `SelectionResult`, `CandidateScore`, `MemoryMultiplier` | Dominio |
| Hechos de un intento, clasificador, causa neutral, contacto de la flecha, clasificación | `AttackFacts`, `AttackClassifier`, `NeutralCause`, `ProjectileContact`, `Classification` | Dominio |
| Registro de amenaza | `ThreatLedger` | Dominio |
| Geometría de combate, posición y frente del jugador | `CombatGeometry`, `PlayerPose` | Dominio |
| Tiempo para matarlo y su desglose | `KillTimeEstimator`, `KillTimeEstimate` | Dominio |
| Consulta, puntaje y resultado de la selección de objetivo | `TargetQuery`, `TargetScore`, `TargetSelection` | Dominio |
| Regla de objetivo de la araña | `SpiderTargetRule` | Dominio |
| Plan en curso, lo que se decide al empezarlo | `Plan`, `PlanStart` | Dominio |
| Memoria y amenaza de un grupo, juntas | `GroupKnowledge` | Dominio |
| Orden para un mob (rol, objetivo y ataque sugerido) | `RoleAssignment` | Entre capas |
| Plan cerrado | `ClosedPlan` | Entre capas |
| Explicación de una decisión, de una clasificación | `DecisionTrace`, `ClassificationTrace` | Dominio |
| Requisito de estrategia evaluado, ataque sugerido a un mob, lo que necesita el sugeridor | `StrategyCheck`, `AttackChoice`, `AttackContext` | Dominio |
| Detector de fin de plan, regla de retirada, sugeridor de ataques | `PlanEndDetector`, `RetreatRule`, `AttackSuggester` | Dominio |
| Resultado del cerebro, piezas que coordina | `BrainResult`, `BrainParts` | Dominio |
| Nivel de traza | `TraceLevel`: `OFF`, `DECISIONS`, `FULL` | Dominio |
| Grupos activos e índice mob → grupo | `ActiveGroups` | Aplicación |
| Configuración vigente | `SettingsHolder` | Aplicación |
| Pedido y resultado de reclutar, resultado de sacar un miembro | `RecruitRequest`, `RecruitResult`, `RemovalOutcome` | Aplicación |
| Resultado de un intento, daño recibido, causa de salida, publicador de eventos de un grupo | `AttackResolution`, `DamageTaken`, `RemovalCause`, `GroupEvents` | Aplicación |
| Guardar, cargar y consultar: conversión, candado de guardado, informe de carga, vistas | `StoredMemoriesMapper`, `GuardedMemoryRepository`, `LoadReport`, `GroupStatusView`, `PlayerMemoryView` | Aplicación |
| Rastreo cuerpo a cuerpo: apertura, golpe, intento abierto, condiciones del objetivo | `MeleeOpening`, `MeleeHit`, `OpenAttempt`, `TargetChecks` | Adaptadores |
| Ritmo del cuerpo a cuerpo y lo que comparten los goals | `MeleeRhythm`, `GoalContext` | Adaptadores |
| Reparto de decisiones, decidir un grupo, muestreo de movimiento | `DecisionCadence`, `DecisionParts`, `GroupDecider`, `MovementSampler` | Adaptadores |
| Comandos de administración y grupo de prueba | `MobAiCommand`, `Subcommand`, `SpawnGroupCommand`, `StatusCommand`, `ResetCommand`, `ReloadCommand`, `GroupSpawner`, `SpawnRing` | Adaptadores |
| Armado y ciclo de vida del plugin | `CoreServices`, `AdapterServices`, `PluginRuntime` | Arranque |
| Listeners de daño, amenaza y muertes | `DamageListener`, `ThreatListener`, `DeathListener` | Adaptadores |
| Reloj propio, azar e ids de grupo reales | `ServerTickCounter`, `JdkRandomSource`, `RandomGroupIdSource` | Adaptadores |
| Configuración y mensajes | `ConfigLoader`, `InvalidConfigException`, `Messages`, `MessageKey` | Adaptadores |
| Movimiento real del jugador, lecturas puras de entidades | `MovementTracker`, `EntityReadings` | Adaptadores |
| Datos guardados: carga, memorias, estado, grupo y registros | `MemoryLoad`, `StoredMemories`, `StoredState`, `StoredGroup`, `StoredAttackRecord`, `StoredStrategyRecord` | Dominio (puerto) |
| Persistencia JSON: repositorio, archivos, escritor seguro, versiones y formato | `JsonMemoryRepository`, `MemoryFiles`, `AtomicFileWriter`, `SchemaMigrator`, `GroupFileMapper`, `GroupFile`, `StateFile`, `MemberEntry`, `RecordEntry` | Persistencia |
| Casos de uso nuevos | `RemoveMember`, `RecordDamageTaken`, `RecordPlayerDeath`, `ResetMemories`, `DescribeGroup`, `DescribePlayerMemory` | Aplicación |
| Datos guardados | `StoredMemories`, `StoredGroup`, `StoredMember`, `StoredRecord` | Dominio (puerto) |
| Scheduler de decisión, aplicador de decisiones | `DecisionScheduler`, `DecisionApplier` | Adaptadores |
| Armado de fotos, instalador de goals, rastreador de movimiento | `SnapshotFactory`, `GoalInstaller`, `MovementTracker` | Adaptadores |
| Escritor de trazas, caja negra, escritor de incidentes | `TraceWriter`, `FlightRecorder`, `IncidentWriter` | Adaptadores |
| Reproducción de un incidente | `TraceReplay`, `IncidentFixture` | Pruebas |
| Copia completa de un grupo | `GroupCapture`, `GroupCaptureMapper` (aplicación), `LifecycleCapture` (dominio), `ThreatCapture`, `ThreatRecord` (dominio) | Dominio y aplicación |
| Azar grabado y repetido | `RecordingRandomSource`, `ReplayRandomSource`, `RecordedDraw`, `DrawKind` | Aplicación |
| Incidente y su JSON | `IncidentReport`, `IncidentLocation`, `IncidentFailure` (aplicación), `IncidentJson`, `IncidentFile`, `OptionalTypeAdapterFactory` (adaptadores, `adapter.debug`) | Aplicación y adaptadores |

«Escape» se reserva para el mob (RF-08: `MemberEscaped`, `RecordEscape`). Cuando el que se va es el jugador, el plan cierra con `TARGET_LOST`; nunca se lo llama escape en el código.

El reloj se llama `ServerClock` y no `Clock` para no chocar con `java.time.Clock`, que es otra cosa.
