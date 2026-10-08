# Política de código

El código se explica solo: nombres que dicen qué hace cada cosa, funciones cortas con una sola responsabilidad y comentarios solo para explicar por qué. Las reglas que se pueden verificar con herramientas se verifican automáticamente, no de memoria.

## Nombres

Un nombre bien elegido reemplaza al comentario: si hace falta explicar qué es una variable, el nombre está mal.

**Idioma:** todo el código en inglés (clases, métodos, variables, comentarios, logs y mensajes de commit), para leerse parejo con Java y Paper. La documentación queda en español. Los mensajes que ven los jugadores van en español, en un archivo de mensajes separado del código. La equivalencia entre los conceptos del documento y los nombres en el código está en la pestaña de Arquitectura.

| Elemento | Regla | Bien | Mal |
| --- | --- | --- | --- |
| Clase | Sustantivo que dice qué es | `TargetSelector` | `Manager`, `Helper`, `Utils` |
| Método | Verbo que dice qué hace | `calculatePriority()` | `process()`, `doStuff()` |
| Booleano | Pregunta que se responde con sí o no | `isViable`, `isBlocking` | `viable`, `flag` |
| Variable | Completa, sin abreviaturas | `effectiveHealth` | `eh`, `tmp`, `x` |
| Unidades | En el nombre cuando hay ambigüedad | `halfLifeTicks` | `halfLife` |
| Colección | En plural | `members` | `memberList` |
| Constante | Mayúsculas con guion bajo | `TICKS_BETWEEN_DECISIONS` | `10` suelto en el código |

- **Un mismo concepto, un solo nombre en todo el código:** si es "target" en el dominio, no es "victim" en un adaptador y "enemy" en otro.
- **Los nombres siguen el glosario** de la pestaña de requerimientos.
- **Los nombres de las pruebas describen el comportamiento:** `forgettingHalvesCountsAfterOneHalfLife`, no `test1`.

## Funciones y clases

Cada función hace una sola cosa y cada clase tiene un solo motivo para cambiar; si cuesta ponerle nombre, está haciendo de más.

**Funciones**

- **Cortas:** si no entra en la pantalla sin hacer scroll, se divide. Guía: unas 20 líneas.
- **Hasta 3 parámetros.** Con más, se agrupan en un objeto (por ejemplo, una foto). El constructor canónico de un `record` queda exento: el record es ese objeto. ArchUnit lo verifica.
- **Sin parámetros booleanos que cambian el comportamiento:** `attack(true)` no dice nada; se separa en dos funciones con nombre propio.
- **Retornos tempranos** en vez de `if` anidados: primero se descartan los casos que no aplican.
- **Sin efectos ocultos:** una función que se llama `calculate...` no modifica estado.
- **Una sola tarea, sin excepciones:** si una función hace dos cálculos distintos (por ejemplo, suma y saca el promedio), se divide aunque sea corta. Una función que solo coordina llamadas a otras cumple la regla.
- **Bucles con límite:** todo bucle que se repite hasta que se cumpla una condición (reintentos, muestreo por rechazo) tiene un máximo de iteraciones con nombre y, al alcanzarlo, falla con un error claro. Un bucle infinito cuelga el server; un error queda registrado y se puede reproducir.

**Clases**

- **Una responsabilidad:** `TargetSelector` elige objetivos; no guarda memoria ni mueve mobs.
- **`final` por defecto:** se abre a herencia solo si hace falta.
- **Inmutables cuando se puede:** las fotos, decisiones y configuración son `record`. El estado que cambia (memoria, grupos) queda encapsulado y solo se modifica con métodos con nombre (`recordHit()`), nunca con setters sueltos.
- **Composición antes que herencia:** los comportamientos por tipo de mob se combinan con Strategy, no con jerarquías de clases.

**Java moderno** (Java 25, el mínimo que exige Paper 26.1 en adelante)

- `record` para objetos de valor.
- Interfaces `sealed` para conjuntos cerrados, como el resultado de un ataque (`Hit`, `Partial`, `Miss`, `Neutral`), así el compilador avisa si falta un caso.
- `switch` con flechas y expresiones en lugar de cadenas de `if`.
- `var` solo cuando el tipo es obvio por la misma línea.

## Comentarios

No se comenta qué hace el código, porque eso lo dicen los nombres; sí se comenta por qué hace algo que no es obvio.

**Sí se comenta:**

- **Mecánicas de Minecraft que no se deducen del código:** la bruja usa daño instantáneo con no-muertos porque se curan con él.
- **Decisiones de diseño con alternativas descartadas:** por qué el valor previo de la Beta es de 2 intentos y no 0.
- **Workarounds de Paper:** qué problema esquiva y, si existe, el enlace al issue. Cuando el problema se arregla, el workaround y su comentario se borran.
- **Contratos de los puertos:** las tres interfaces (RepositorioMemoria, Reloj, Aleatorio) llevan una línea de Javadoc con lo que garantizan, porque se implementan en otra capa.

**No se comenta:**

- Lo que el nombre ya dice: `// calculates the priority` arriba de `calculatePriority()`.
- Código comentado "por si acaso": se borra. Para recuperarlo está Git.
- Historial de cambios o autores: eso también lo guarda Git.
- `TODO` sin dueño: las tareas pendientes van a la lista de tareas del proyecto, no al código.

**Prueba rápida:** antes de escribir un comentario, intentar renombrar o extraer una función para que no haga falta. Si después de eso sigue haciendo falta, va.

## Errores, nulos y logs

Un error en el plugin nunca debe tirar abajo el server, pero tampoco puede pasar en silencio.

**Errores**

- **El dominio falla rápido:** valida sus entradas y lanza una excepción clara ante datos imposibles (vida negativa, grupo sin miembros). Un error temprano es más fácil de encontrar que un comportamiento raro tres peleas después.
- **Los adaptadores son el borde de seguridad:** cada listener y cada tarea del scheduler atrapa las excepciones, las registra con contexto y sigue. Un grupo con un error se descarta; los demás siguen funcionando.
- **Nunca un `catch` vacío.** Si se atrapa una excepción, se registra o se resuelve.

**Nulos**

- **Ningún método devuelve `null`:** si algo puede no existir, devuelve `Optional` (por ejemplo, el objetivo de un grupo sin jugadores cerca).
- **Ningún parámetro acepta `null`.** Lo que llega de Paper y puede ser nulo se resuelve en el adaptador, antes de armar la foto.
- **Colecciones vacías en vez de nulas.**

**Logs**

| Nivel | Cuándo | Ejemplo |
| --- | --- | --- |
| Error | Algo falló y se descartó trabajo | "No se pudo guardar la memoria del grupo 7" |
| Advertencia | Algo raro que no rompe nada | "Memoria en versión vieja, migrada" |
| Info | Arranque, apagado y eventos raros | "Cargados 12 grupos" |
| Debug | Decisiones del cerebro, solo con la opción de debug activada | "Grupo 7: flanqueo contra Juan, tasa sorteada 0,64" |

- **Nada se registra en cada tick** con la opción de debug apagada: con 20 ticks por segundo, llena la consola en minutos.
- **Las trazas no son logs:** el detalle de las decisiones va a los archivos de traza (`TraceWriter`), no a la consola. El dominio no escribe nada: devuelve la explicación como datos (ver «Trazabilidad y depuración» en `arquitectura.md`).
- **Los mensajes llevan contexto:** qué grupo, qué mob, qué jugador.

## Configuración y números mágicos

Ningún número de balance vive en el código: todo lo que se pueda querer ajustar está en la configuración, y todo lo que es fijo tiene un nombre.

- **Valores de balance van a la configuración:** tamaño máximo de grupo, tiempo de vida, vida media del olvido, velocidad de aprendizaje, política de selección, peso de observadores, radios, umbrales, bonus de compromiso, techo de la memoria.
- **Valores fijos de Minecraft van a constantes con nombre:** `TICKS_PER_SECOND = 20`, `CREEPER_FUSE_TICKS = 30`. Un `20` suelto en el código está prohibido.
- **La configuración se lee una vez y se convierte en un `record`:** el dominio recibe ese objeto, nunca lee el archivo ni consulta claves sueltas.
- **Se valida al cargar:** valores fuera de rango (velocidad de aprendizaje mayor a 1, tamaño de grupo negativo) se rechazan con un mensaje claro y se usa el valor por defecto.
- **Recarga sin reiniciar:** el comando de recarga arma un nuevo `record` y lo reemplaza entero; nunca se modifican valores a mitad de un tick.
- **Cada valor tiene un comentario en el archivo de configuración** que explica qué hace y su rango. Es el único lugar donde se documenta para quien administra el server.

## Pruebas y reglas automáticas

Lo que una herramienta puede verificar no se deja a la memoria: el formato, las reglas de capas y las pruebas corren solas en cada compilación.

**Herramientas**

| Herramienta | Qué verifica | Cuándo corre |
| --- | --- | --- |
| Spotless (plugin de Gradle) | Formato del código con google-java-format | Al compilar; `spotlessApply` lo corrige solo |
| ArchUnit | Reglas de capas: el dominio no importa Paper, la aplicación no importa adaptadores | Como una prueba más, en cada compilación |
| JUnit 5 | Comportamiento del dominio, la aplicación y la persistencia | En cada compilación |
| JaCoCo | Qué parte del código cubren las pruebas | A pedido |

Con ArchUnit, la regla más importante de la arquitectura deja de depender de acordarse: si alguien importa `org.bukkit` en el dominio, la compilación falla.

**Reglas de las pruebas**

- **Toda regla del dominio tiene al menos una prueba:** olvido, sorteo, prioridad, fusión de memorias, requisitos de estrategias, velocidad de aprendizaje.
- **Pruebas deterministas:** usan el reloj y el azar falsos; la misma prueba da siempre el mismo resultado.
- **Estructura dado / cuando / entonces:** se arma el escenario, se ejecuta una acción y se verifica un resultado.
- **Una prueba verifica una cosa:** si falla, el nombre ya dice qué se rompió.
- **Un bug corregido deja una prueba:** antes de arreglarlo se escribe la prueba que lo reproduce.
- **Objetivo de cobertura:** alta en el dominio (80% o más) y sin objetivo en adaptadores, que se prueban en el server.

## Git y código generado con IA

La rama principal siempre compila y pasa las pruebas; el código escrito con ayuda de IA cumple exactamente las mismas reglas que el escrito a mano.

**Git**

- **Commits chicos y con un solo propósito.** Si el mensaje necesita un "y", son dos commits.
- **Mensajes con formato Conventional Commits:** `feat: attack tracker resolves shield blocks`, `fix: ...`, `test: ...`, `refactor: ...`.
- **Una rama por funcionalidad**, que se une a la principal cuando compila y las pruebas pasan.
- **Nada de configuración del server ni memorias reales en el repositorio**; solo ejemplos.

**Código generado con IA**

- **Se entiende antes de commitear:** si no se puede explicar línea por línea qué hace y por qué, no entra.
- **Cumple esta política:** nombres, tamaño de funciones, sin comentarios obvios, sin números mágicos. La IA tiende a agregar comentarios que repiten el código y nombres genéricos; se corrigen.
- **Llega con pruebas:** el código de dominio generado viene con sus pruebas, y se verifica que esas pruebas fallen si se rompe la lógica.
- **Respeta las capas:** ArchUnit lo verifica igual que al resto.

## Decisiones pendientes

- [x] Idioma del código: inglés. Documentación en español; mensajes para jugadores en español, en un archivo aparte.
- [x] Versión de Java: 25, el mínimo que exige Paper 26.1 en adelante.
- [x] Estilo de formato de Spotless: google-java-format. Sin discusiones de estilo: lo que el formateador decide, queda.
