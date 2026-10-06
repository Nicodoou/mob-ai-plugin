# Manual del orquestador

Este manual es para la sesión de Opus que orquesta el plan. Junto con `estado.md` contiene todo lo necesario para retomar el trabajo en una sesión nueva sin depender de la memoria de conversaciones anteriores. Si algo de lo que hacés no está acá, agregalo.

## 1. Al empezar una sesión

1. Leé este manual, `estado.md` y `README.md` (el plan maestro).
2. Revisá GitHub: `"/c/Program Files/GitHub CLI/gh.exe" pr list -R Nicodoou/mob-ai-plugin`. Un PR abierto de un WP es una entrega pendiente de revisar.
3. Revisá `git worktree list`: un worktree en `.claude/worktrees/` es de un subagente anterior. Si su PR ya está mergeado, borralo.
4. Seguí desde «Próximo paso» de `estado.md`. Los subagentes de sesiones anteriores no se pueden retomar: si un WP quedó «en curso» sin PR, se relanza desde cero en un worktree nuevo.
5. Contale a Nico en dos líneas dónde estás y qué sigue.

## 2. Quién es quién

| Rol | Quién | Hace |
| --- | --- | --- |
| Orquestador | Opus, el chat principal | Especifica cada WP, lo presenta, lanza el subagente, revisa, mergea y mantiene el tablero |
| Implementador | Un subagente por WP | Implementa un solo WP en su worktree y abre el PR |
| Dueño | Nico | Aprueba cada WP antes de lanzarlo y hace las puertas de etapa que requieren el server |

## 3. Preferencias de Nico (obligatorias)

- Todo en castellano rioplatense con voseo. El código, los commits y los PR en inglés.
- **Cada WP se presenta para aprobar antes de lanzar el subagente.** La presentación dice qué hace, resume la arquitectura y lista las decisiones que tomaste en el WP.
- **Al cerrar cada WP:** qué hizo, la arquitectura y **tu opinión del código** (lo bueno, lo flojo y los riesgos, con honestidad).
- **Mergeás vos** cada PR que aprobás (`gh pr merge --squash --delete-branch`). Nico interviene en las puertas de etapa.
- Las decisiones importantes no se dejan a los modelos económicos: el WP las toma.

## 4. Cómo especificar un WP

Plantilla: copiá la estructura de `wp/WP-03-memoria.md`, que es el mejor ejemplo: ficha, objetivo, contexto a leer, reglas de negocio, archivos, especificación, pruebas obligatorias, pruebas que muerden, procedimiento, correcciones permitidas, fuera de alcance y aceptación.

Reglas que salieron de la experiencia:

- **Todo exacto:** firmas, nombres de parámetros, mensajes de error textuales, valores esperados con su tolerancia y nombres de las pruebas. Lo que no está escrito, un modelo chico lo inventa.
- **Valores de referencia de los documentos** (por ejemplo, 1 de 1 → 67 %) como pruebas con número exacto.
- **Pruebas que muerden:** de 2 a 4 cambios temporales que tienen que hacer fallar una prueba concreta.
- **Pruebas estadísticas con parámetros asimétricos:** un caso simétrico puede pasar aunque el algoritmo esté mal. En el WP-04, Beta(0,5; 0,5) daba media 0,5 incluso sin el boost para formas menores a 1.
- **Cada rotura, calculada:** antes de escribir una prueba que muerde, calculá el valor que da el código roto con los números de la prueba. En el WP-07, 8 de daño con Debilidad 2 daba 2 con el piso antes o después de la debilidad, y la rotura no mordía.
- **Cambios técnicos:** todo cambio de diseño posterior al plan aprobado (pedido por Nico, salido de un spike o de una revisión) se registra en `cambios-tecnicos.md` y, si es una decisión, como D25 en adelante en `README.md`, en el mismo commit que actualiza los documentos de diseño.
- **Métodos públicos a raya** (pedido de Nico): un WP que agrega métodos públicos a una clase existente justifica por qué van ahí. Umbral de alerta: 20 métodos públicos por clase; pasado ese número, el WP propone dividir la clase antes de seguir agregando. `Group` quedó con 25 en el WP-08: es la primera candidata a revisar cuando el cerebro (WP-10) o los casos de uso (WP-12 y WP-13) le pidan más.
- **Contexto mínimo:** listá solo los archivos de código que el subagente necesita leer. Nunca le pidas leer `docs/` completo.
- **Nada de `switch` sobre enums de Paper fuera de `VersionTranslator`… ni adentro:** `javac` genera una clase sintética (`VersionTranslator$1`) con la tabla de ordinales, y la regla de ArchUnit la ve como otra clase (WP-17). Se usan `if` con `==`.
- **Capas antes de ubicar una clase:** `persistence` solo depende de `domain` y Gson; lo que use tipos de `application` va en un adaptador (WP-28B). Y la traza de una decisión trae `targetSelection` solo cuando el grupo observa: para probar algo de la selección de objetivo, grabá la primera decisión.
- **Versiones:** si el WP agrega o cambia una dependencia, verificá que la combinación compile y funcione (no alcanza con que cada versión exista). En el WP-00, google-java-format 1.37.0 existía pero rompía Spotless.
- **APIs de Paper:** consultá `hallazgos-api.md`; si el WP usa algo que el spike no verificó, verificalo antes (javap sobre el jar de paper-api en `~/.gradle/caches`, o el server).
- **El CI antes del informe:** el WP pide esperar el check `build` en verde antes de informar.
- **Tamaño:** unas 400 líneas de producción y 600 de pruebas como máximo; si se pasa, dividilo.
- Si el WP introduce nombres nuevos, agregalos a la tabla «Nombres en el código» de `arquitectura.md` en el mismo commit que el WP.

Commit del WP a `main` **antes** de lanzar el subagente (`docs: add WP-XX ...`), con push: el worktree parte de `main`.

## 5. Elección de modelo y lanzamiento

| Modelo | Cuándo |
| --- | --- |
| Haiku | Transcripción casi mecánica (records, enums, configuración) |
| Sonnet | El caso general |
| Opus | Lógica delicada (cerebro) o terreno sin documentar |

Si un subagente no pasa la revisión después de dos rondas de correcciones, se relanza con el modelo siguiente. Si el problema es una ambigüedad del WP, primero se corrige el WP.

Lanzamiento: herramienta `Agent` con `subagent_type: general-purpose`, el `model` elegido, `isolation: worktree` y `run_in_background: true`. Prompt:

> Sos el implementador de un único paquete de trabajo (WP) del proyecto MobAI, un plugin de Paper. Trabajás en un worktree del repo (git, remoto origin = github.com/Nicodoou/mob-ai-plugin, rama base main).
> 1. Leé `docs/plan/reglas-para-agentes.md` y seguilas en todo momento.
> 2. Implementá `docs/plan/wp/WP-XX-nombre.md` completo, en el orden de su sección «Procedimiento».
> 3. No leas otros archivos de `docs/` salvo que el WP lo pida.
> 4. Si algo no está previsto en el WP o en sus «Correcciones permitidas», frená y reportá; no improvises.
> 5. Terminá con el informe final, en el formato de las reglas. El check `build` del PR tiene que estar terminado (verde) antes de escribir el informe.
>
> Datos del entorno: Windows 10 con Git Bash. El proyecto compila con `./gradlew` (Gradle descarga el JDK 25 solo). `gh` está autenticado y se invoca con la ruta completa "/c/Program Files/GitHub CLI/gh.exe".

Si un subagente no puede correr `gh` desde Bash dentro del worktree (le pasó al del WP-05), puede usar PowerShell con el mismo ejecutable.

Las correcciones se le piden **al mismo subagente** con `SendMessage`, mientras siga en esta sesión: conserva su contexto.

Actualizá `estado.md` al lanzar (`en curso`), al recibir el informe (`en revisión`) y al mergear (`mergeado`).

## 6. Checklist de revisión

En un worktree de revisión propio, nunca en el del subagente:

```bash
cd /c/Users/Nico/Documents/Plugin && git fetch -q origin
git worktree add -q --detach "$TEMP/rvXX" origin/wp-XX-nombre
cd "$TEMP/rvXX" && git log --format='%h %s' origin/main..HEAD && git diff --name-only origin/main...HEAD
./gradlew build jacocoTestReport jacocoTestCoverageVerification --no-daemon -q
"/c/Program Files/GitHub CLI/gh.exe" pr checks <número> -R Nicodoou/mob-ai-plugin
```

1. **Archivos:** exactamente los de la tabla del WP.
2. **Contenido:** firmas, nombres y mensajes contra el WP. Leé el código de producción completo; buscá nombres abreviados, comentarios que repiten el código, números mágicos, `TODO`, `System.out`, lógica de más, **funciones que hacen más de una tarea** (cada función, también las privadas) y **bucles sin límite de iteraciones**, y **cuántos métodos públicos** tiene cada clase tocada (alerta desde 20).
3. **Pruebas:** que existan con su nombre exacto (contá las ejecuciones en `build/test-results/test/*.xml`).
4. **Roturas propias:** hacé al menos dos cambios temporales **distintos** de los del WP y verificá que alguna prueba falle. Revertí con `git checkout`.
5. **CI** en verde.
6. **Informe:** los desvíos se aceptan o se devuelven al subagente.

Al terminar: `git worktree remove --force "$TEMP/rvXX"` y `git worktree prune`.

## 7. Merge y limpieza

```bash
"/c/Program Files/GitHub CLI/gh.exe" pr merge <número> -R Nicodoou/mob-ai-plugin --squash --delete-branch --subject "WP-XX: <título> (#<número>)"
cd /c/Users/Nico/Documents/Plugin && git pull -q origin main
git worktree remove --force .claude/worktrees/<carpeta-del-subagente>; git worktree prune
git branch -D wp-XX-nombre worktree-<carpeta-del-subagente>
```

Windows a veces no deja borrar la carpeta del worktree («Permission denied» o «Filename too long»): con `git worktree prune` alcanza; la carpeta se borra más tarde. `.claude/worktrees/` está en `.git/info/exclude`.

Después: actualizá `estado.md`, agregá la entrada del WP a `bitacora.md` (qué hizo, arquitectura, tu opinión del código y lo pendiente), commiteá y presentale a Nico el cierre del WP.

## 8. Cambio de sesión

Preferencia de Nico: mientras la ventana de contexto lo permita, en vez de abrir una sesión nueva se compacta la conversación (`/compact`) y después se releen este manual y `estado.md`. La sesión nueva queda para cuando compactar ya no alcance.

Una sesión por etapa. Al pasar cada puerta:

1. Dejá `estado.md` al día, con un «Próximo paso» concreto.
2. Commit y push.
3. Decile a Nico: «Abrí una sesión nueva y corré `/orquestar`».

Si la sesión se está haciendo larga antes de terminar una etapa, hacé lo mismo al cerrar el WP en curso.

## 9. Entorno

| Dato | Valor |
| --- | --- |
| Repo | `C:\Users\Nico\Documents\Plugin`, GitHub `Nicodoou/mob-ai-plugin` (privado) |
| `gh` | `"/c/Program Files/GitHub CLI/gh.exe"` (no está en el PATH de Git Bash) |
| Java | JDK 21 instalado; Gradle descarga el JDK 25 en `~/.gradle/jdks` |
| javap para inspeccionar la API | `~/.gradle/jdks/eclipse_adoptium-25-amd64-windows.2/bin/javap` sobre el jar de paper-api en `~/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/` |
| Server de prueba | Carpeta `run/` (ignorada por Git). EULA aceptado, `online-mode=false`, dificultad normal |
| Jugador | `papu123` (cuenta no premium), en la whitelist y operador |
| IP | `localhost` desde esta PC; `10.10.10.105` en la red local |

Levantar el server desde Bash, con la consola por un archivo (para poder mandarle comandos):

```bash
cd /c/Users/Nico/Documents/Plugin && : > run/console-input.txt
(tail -f run/console-input.txt | ./gradlew runServer --no-daemon > run/server.log 2>&1 &)
echo "<comando>" >> run/console-input.txt        # por ejemplo: op papu123, gamemode survival papu123
echo stop >> run/console-input.txt               # apagar
taskkill //F //IM tail.exe                       # después de apagar
```

## 10. Documentos

| Documento | Para qué |
| --- | --- |
| `README.md` | Plan maestro: etapas, WPs, mapa del código, decisiones D1 a D24 |
| `estado.md` | Tablero |
| `cambios-tecnicos.md` | Cada cambio de diseño posterior al plan aprobado: qué, por qué, impacto y alternativas descartadas |
| `bitacora.md` | Cierre de cada WP: qué hizo, arquitectura, opinión del código y pendientes |
| `reglas-para-agentes.md` | Reglas de los subagentes |
| `hallazgos-api.md` | Comportamiento verificado de Paper 26.3 |
| `wp/WP-XX-*.md` | Los WPs especificados |
| `docs/*.md` | Diseño: requerimientos, arquitectura, política de código, bugs, catálogo, diagramas |
