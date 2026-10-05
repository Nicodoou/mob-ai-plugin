# Resolución de bugs

Ningún bug se corrige sin antes reproducirlo y sin una hipótesis confirmada sobre su causa. Se prueba una hipótesis a la vez, lo que no funcionó se revierte, y el arreglo deja una prueba que impide que el bug vuelva.

## El proceso

> **Diagrama:** ver «Proceso de resolución de bugs» en [diagramas.md](diagramas.md).

Cada hipótesis descartada se revierte antes de probar la siguiente. Después de tres descartadas seguidas se vuelve a aislar variables.

## Los pasos en detalle

Cada paso tiene una salida concreta; no se pasa al siguiente sin ella.

1. **Analizar qué es lo raro.** Se escribe en una línea qué se esperaba y qué pasó, citando el requerimiento que se incumple (por ejemplo, "RF-09: el fuego amigo no debe cambiar el objetivo; el zombie 14 atacó al esqueleto 3"). Si no hay un requerimiento que citar, no es un bug: es una decisión de diseño pendiente.
   - Salida: comportamiento esperado contra observado.
2. **Aislar variables.** Se reduce el escenario al mínimo que sigue mostrando el problema: un grupo, un jugador, un tipo de mob, semilla de azar fija, reloj falso si es dominio. Se descarta una variable por vez.
   - Salida: lista de las condiciones mínimas.
3. **Replicar.** Se reproduce el bug a voluntad, idealmente como una prueba automática que falla (JUnit si es dominio o aplicación; un escenario con el comando de prueba si es adaptador). Sin reproducción no se toca el código.
   - Salida: prueba o escenario que falla siempre.
4. **Crear hipótesis.** Se listan las causas posibles, ordenadas de más a menos probable. Cada una incluye una predicción verificable: "si la causa es X, al hacer Y debería pasar Z".
   - Salida: lista de hipótesis con su predicción.
5. **Crear debugs.** Se agregan los logs o puntos de inspección necesarios para verificar la predicción de la primera hipótesis, marcados como temporales.
   - Salida: instrumentación que muestra el dato que decide.
6. **Testear la hipótesis.** Se ejecuta la reproducción y se compara contra la predicción. Una hipótesis a la vez, un cambio a la vez.
7. **Confirmar o descartar.**
   - Si se descarta: se revierte todo lo cambiado para probarla, se anota como descartada y se vuelve al paso 5 con la siguiente.
   - Si se descartan 3 seguidas: se vuelve al paso 2, porque probablemente falta aislar algo.
   - Si se confirma: se corrige la causa raíz, no el síntoma, y la prueba del paso 3 pasa.
8. **Testear que no se rompió nada.** Se corren todas las pruebas, las reglas de ArchUnit y el escenario en el server. Si el bug afectaba balance, se repite el benchmark.
9. **Limpiar.** Se borran los debugs temporales. La prueba que reproduce el bug queda para siempre.
10. **Documentar.** Se completa la entrada en el registro de bugs.

## Bugs en un sistema que aprende

En este plugin el azar es parte del diseño, así que lo raro no siempre es un bug: primero hay que demostrar que el comportamiento contradice un requerimiento y no es variación normal.

- **"Eligió una estrategia mala" no es un bug por sí solo.** Thompson Sampling a veces elige opciones con poca tasa para seguir explorando. Es bug si lo hace con una frecuencia que no corresponde a sus datos.
- **Se reproduce con semilla fija.** Con el `RandomSource` falso y la misma semilla, el mismo escenario da siempre el mismo resultado. Si con semilla fija no se repite, el problema está en otro lado (orden de eventos, estado compartido).
- **Los problemas de frecuencia se prueban con muchas repeticiones.** Para "casi nunca elige flanqueo aunque tiene 80%", se corre la elección miles de veces en una prueba y se compara la proporción contra la esperada.
- **Se separan tres tipos de problema:**

| Tipo | Ejemplo | Qué se hace |
| --- | --- | --- |
| Bug | Un acierto se registra como fallo | Este proceso completo |
| Balance | Los grupos son demasiado difíciles | Ajustar configuración y repetir el benchmark; no se toca código |
| Diseño | Aprenden algo que no queremos que aprendan | Discutirlo y, si cambia, actualizar los requerimientos antes que el código |

## Reglas para trabajar bugs con IA

La IA sigue el mismo proceso, con reglas explícitas que cortan el ciclo de probar cambios al azar, arreglar un bug y generar otro.

- **Nada de código antes de la reproducción.** El primer pedido a la IA es el análisis y la prueba que falla, no el arreglo.
- **Hipótesis antes que cambios.** La IA presenta la hipótesis, la evidencia que la apoya y la predicción. Se aprueba antes de tocar código.
- **Un cambio por hipótesis.** Si la IA propone modificar varias cosas a la vez, se rechaza: si funciona, no se sabe cuál fue; si no, se acumulan cambios.
- **Lo descartado se revierte.** Antes de probar la siguiente hipótesis, el código vuelve exactamente al estado anterior (con Git). Ningún intento fallido queda "por si ayuda".
- **Límite de intentos.** Después de 3 hipótesis descartadas se frena y se vuelve a aislar variables; no se sigue probando.
- **Se corrige la causa, no el síntoma.** Un `if` que tapa el caso raro sin explicar por qué ocurre no es un arreglo.
- **El arreglo llega con la prueba que reproducía el bug,** y la suite completa tiene que pasar.

Un pedido modelo a la IA: "Este es el comportamiento esperado y el observado, y esta es la prueba que lo reproduce. Proponeme hasta 3 hipótesis ordenadas por probabilidad, cada una con su predicción. No modifiques código todavía."

## Registro de bugs

Cada bug corregido deja una entrada con estos campos, para que la próxima vez que aparezca algo parecido se empiece con lo ya aprendido.

| Campo | Qué se anota |
| --- | --- |
| ID y fecha | Identificador y día en que se reportó |
| Requerimiento | Cuál se incumplía |
| Esperado / observado | Una línea cada uno |
| Condiciones mínimas | Lo que quedó tras aislar variables |
| Reproducción | Nombre de la prueba o del escenario |
| Hipótesis descartadas | Cada una con por qué se descartó |
| Causa raíz | La hipótesis confirmada, explicada |
| Arreglo | Qué se cambió y el commit |
| Verificación | Suite completa, ArchUnit, escenario en server, benchmark si aplica |
| Lección | Qué regla, prueba o validación evita que pase algo similar |

Las hipótesis descartadas son lo más valioso del registro: ahorran repetir callejones sin salida.
