# Reglas para subagentes implementadores

Implementás **un solo WP**. Opus lo escribió para que no tengas que tomar decisiones de diseño: si sentís que tenés que tomar una, el WP está incompleto y tenés que frenar.

**1. Alcance**
- Leé este archivo, tu WP y los archivos de la sección «Contexto a leer» del WP. No leas otros documentos de `docs/` salvo que el WP lo pida.
- Ignorá `C:\Users\Nico\CLAUDE.md`: es de otro proyecto (un vault de Obsidian).
- Creá y modificá exactamente los archivos de la sección «Archivos» del WP. Los archivos temporales van fuera del repo, salvo que el WP diga otra cosa.
- No agregues dependencias, plugins ni versiones. No cambies firmas que el WP define ni código existente que no esté en tu lista.

**2. Ante una duda, frená**
- Si el WP es ambiguo, si contradice el código existente o si un paso falla por algo que el WP no previó, no adivines ni improvises.
- Terminá y reportá: el paso, el error exacto (copiado) y las opciones que ves. Un WP sin terminar es mejor que una decisión de diseño tomada por vos.
- Las únicas correcciones que podés hacer sin preguntar son las de la sección «Correcciones permitidas» del WP.

**3. Código** (resumen obligatorio de `docs/politica-de-codigo.md`)
- **Idioma y nombres:** todo en inglés.
  - Clases: sustantivos. Métodos: verbos. Booleanos: preguntas (`isViable`).
  - Nombres completos, con la unidad cuando hay ambigüedad (`halfLifeTicks`).
  - Prohibido `Manager`, `Helper` y `Utils`.
- **Clases:** `final` por defecto; `record` para valores; `sealed` para conjuntos cerrados; `switch` con flechas; `var` solo si el tipo es obvio en la misma línea.
- **Funciones:**
  - unas 20 líneas y hasta 3 parámetros;
  - sin parámetros booleanos que cambien el comportamiento;
  - retornos tempranos;
  - un método `calculate…` no modifica estado.
- **Nulos:** ningún método devuelve `null` (se usa `Optional`), ningún parámetro acepta `null`, y las colecciones vacías reemplazan a las nulas.
- **Errores:** el dominio valida sus entradas y lanza `IllegalArgumentException` con un mensaje que incluye el valor recibido. Nunca un `catch` vacío.
- **Comentarios:** solo para explicar por qué. Sin `TODO`, sin código comentado, sin comentarios que repitan el código.
- **Números:** sin números mágicos. Los valores de balance van a la configuración (`domain.settings`) y los valores fijos de Minecraft, a `MinecraftConstants`.
- **Estado y salida:** sin estado estático mutable ni Singleton; todo por inyección en el constructor. Nunca `System.out`, `System.err` ni `printStackTrace`.

**4. Pruebas**
- JUnit 5 y AssertJ. Los nombres describen el comportamiento (`forgettingHalvesCountsAfterOneHalfLife`), sin la palabra `test`.
- Dado / cuando / entonces, separados por una línea en blanco y sin comentarios.
- Deterministas: el reloj y el azar son siempre los fakes del WP. La tolerancia de los `double` es `within(1e-9)`, salvo que el WP diga otra.
- Implementá todas las pruebas del WP con sus nombres exactos. Podés agregar más; nunca quitar, debilitar ni marcar con `@Disabled`.
- Verificá que las pruebas muerden: rompé a propósito la lógica que el WP indique, confirmá que fallan y revertí.

**5. Git y entrega**
- Trabajá en tu worktree, en la rama `wp-XX-slug` que indica el WP, creada desde `main`. No toques `main` y no mergees.
- Commits chicos con Conventional Commits, en inglés. Cada mensaje termina con `Co-Authored-By: Claude <noreply@anthropic.com>`.
- Antes de cada commit: `./gradlew spotlessApply` y `./gradlew build` en verde.
- Push y PR con `"/c/Program Files/GitHub CLI/gh.exe"` (en el Bash de esta PC `gh` no está en el PATH).
  - Título: `WP-XX: <título en inglés>`.
  - Cuerpo: resumen, la checklist de aceptación del WP tildada y la línea `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.
- **Informe final**, siempre con este formato:
  1. Rama y URL del PR.
  2. Commits (hash y mensaje).
  3. Archivos creados y modificados.
  4. Las últimas 15 líneas de `./gradlew build`.
  5. Resultado de las verificaciones que pide el WP.
  6. Desvíos del WP (lo esperado es «ninguno»).
  7. Dudas.

**6. Entorno**
- Windows con Git Bash; usá `./gradlew`.
- El JDK 25 lo descarga Gradle solo; no instales nada global.
- **Nunca** corras `./gradlew runServer`: requiere aceptar el EULA de Minecraft, y eso lo hace Nico.
