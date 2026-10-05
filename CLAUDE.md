# Plugin de IA adaptable para mobs

Plugin para Paper 26.3 (Java 25) que hace que los mobs hostiles peleen en grupos con roles y aprendan de cada jugador. La documentación está en español; el código, en inglés.

## Antes de cualquier tarea

Leé los documentos de `docs/` que correspondan a la tarea:

- `docs/requerimientos.md`: qué tiene que hacer el sistema (RF-01 a RF-12) y las decisiones tomadas.
- `docs/arquitectura.md`: capas, puertos, flujos, patrones, diseño del rastreador de ataques y la tabla de nombres en el código.
- `docs/politica-de-codigo.md`: reglas de código. Son obligatorias.
- `docs/resolucion-de-bugs.md`: proceso obligatorio para cualquier bug.
- `docs/catalogo-mvp.md`: estrategias, ataques, configuración inicial y definición de terminado del MVP.
- `docs/diagramas.md`: los diagramas en Mermaid.

Si una tarea contradice estos documentos, frená y preguntá antes de implementar.

Si estás implementando un WP de `docs/plan/`, leé solo lo que indica el WP y `docs/plan/reglas-para-agentes.md`: el WP ya trae lo que necesita de estos documentos.

Si sos el orquestador del plan (el chat principal, no un subagente), empezá por `docs/plan/orquestacion.md` y `docs/plan/estado.md`, o corré `/orquestar`.

## Reglas que no se negocian

- **Capas:** el dominio no importa nada de `org.bukkit` ni `io.papermc`. Las constantes de Paper solo aparecen en `VersionTranslator`. ArchUnit lo verifica.
- **Nombres:** usá los de la tabla "Nombres en el código" de `docs/arquitectura.md`. Un concepto, un nombre.
- **Código:** en inglés, autodescriptivo, sin comentarios que repitan el código; comentarios solo para explicar por qué. Sin números mágicos: valores de balance a la configuración, valores fijos de Minecraft a constantes con nombre.
- **Pruebas:** toda regla del dominio llega con su prueba JUnit, determinista (reloj y azar falsos).
- **Sin Singleton ni estado estático global.** Inyección por constructor, armada en el arranque del plugin.

## Bugs

Seguí `docs/resolucion-de-bugs.md` al pie de la letra:

1. No modifiques código antes de tener una prueba que reproduzca el bug.
2. Presentá hasta 3 hipótesis, cada una con su predicción, y esperá aprobación antes de cambiar código.
3. Un cambio por hipótesis. Si se descarta, revertí todo antes de probar la siguiente.
4. Después de 3 hipótesis descartadas, volvé a aislar variables.
5. Corregí la causa, no el síntoma. El arreglo llega con su prueba y la suite completa tiene que pasar.

## Comandos

- `./gradlew build`: compila, corre pruebas, ArchUnit y Spotless.
- `./gradlew spotlessApply`: corrige el formato.
- `./gradlew runServer`: levanta un server Paper de prueba con el plugin.
