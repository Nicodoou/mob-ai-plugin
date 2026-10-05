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
