# Estado del plan

Tablero del orquestador. Se actualiza y se commitea cada vez que un WP cambia de estado. Una sesión nueva retoma desde acá (ver `orquestacion.md`).

**Última actualización:** 5 de octubre de 2026 (puerta E3 pasada).

## Próximo paso

1. **Puerta E5 pasada** (7 oct 2026): evidencia en `docs/plan/puerta-e5-registro.md`. Un bug corregido (B-01, #31) y el CT-13 para el WP-22.
2. **WP-22A, WP-22B y WP-22C aprobados por Nico (7 oct).** WP-22A (#32), WP-22B (#33) y WP-22C (#34) mergeados. Verificación en el juego (`verificacion-e6.md`): la retirada funciona; el flanqueo no (B-02). **WP-22D mergeado (#35) y verificado en el juego por Nico: B-02 cerrado; WP-22A a 22D completos.** **WP-23 especificado, esperando la aprobación de Nico.** Después: especificar el WP-23 (golpe paciente). Después, en orden: cada uno parte del anterior mergeado.
3. Pendientes de limpieza (ver «Decisiones abiertas»): ciclos de paquetes y `hasRetreated` con miembros que se suman a mitad de plan. El orden del plan se mantiene.
4. Nico prefiere seguir en la misma sesión compactando el contexto: después de cada compactación, releer `orquestacion.md` y este tablero antes de seguir. No bajar la calidad de especificaciones, revisiones ni devoluciones para ahorrar contexto (pedido de Nico).

## WPs

Estados: `pendiente` → `especificado` (WP escrito, sin aprobar) → `aprobado` → `en curso` (subagente trabajando) → `en revisión` → `mergeado`.

| WP | Título | Etapa | Estado | Modelo | PR | Notas |
| --- | --- | --- | --- | --- | --- | --- |
| WP-00 | Andamiaje, reglas automáticas y CI | E0 | mergeado | Sonnet | [#1](https://github.com/Nicodoou/mob-ai-plugin/pull/1) | Desvío aceptado: google-java-format 1.36.1 (1.37.0 rompe Spotless 8.10.3) |
| WP-01 | Spike en el server | E1 | terminado | Opus (chat principal) | — | Hallazgos en `hallazgos-api.md`; código en la rama `spike/wp-01`, sin mergear |
| WP-02 | Tipos base, puertos y configuración | E2 | mergeado | Haiku | [#2](https://github.com/Nicodoou/mob-ai-plugin/pull/2) | Una ronda de corrección: nombres abreviados en `Vec3` |
| WP-03 | Memoria con olvido | E2 | mergeado | Sonnet | [#3](https://github.com/Nicodoou/mob-ai-plugin/pull/3) | Sin desvíos ni correcciones |
| WP-04 | Sorteo Beta y políticas de selección | E2 | mergeado | Sonnet | [#4](https://github.com/Nicodoou/mob-ai-plugin/pull/4) | Una ronda: la prueba del boost era simétrica y no mordía; ahora mide la cola de Beta(0,5; 2) |
| WP-05 | Clasificador de ataques | E2 | mergeado | Sonnet | [#6](https://github.com/Nicodoou/mob-ai-plugin/pull/6) | Sin correcciones |
| WP-06 | Fotos, amenaza y geometría | E3 | mergeado | Sonnet | [#7](https://github.com/Nicodoou/mob-ai-plugin/pull/7) | La foto del jugador lleva el movimiento por tick y la dirección hacia la que mira; la amenaza sale de la foto y la guarda el grupo |
| WP-07 | Selección de objetivo | E3 | mergeado | Sonnet | [#8](https://github.com/Nicodoou/mob-ai-plugin/pull/8) | Desvío aceptado: prueba extra `weaknessAppliesToTheThreatFloorToo`, porque una rotura del WP no mordía con sus números |
| WP-08 | Grupo, plan y eventos | E3 | mergeado | Sonnet | [#9](https://github.com/Nicodoou/mob-ai-plugin/pull/9) | Incluye `PlanId`; `RoleAssignment` y `GroupDecision` pasan al WP-10 |
| WP-09 | Estrategias | E3 | mergeado | Sonnet | [#10](https://github.com/Nicodoou/mob-ai-plugin/pull/10) | Pasa de Haiku a Sonnet: la elección de flanqueadores usa geometría |
| WP-08B | División de `Group` y estado de reagrupamiento | E3 | mergeado | Sonnet | [#11](https://github.com/Nicodoou/mob-ai-plugin/pull/11) | CT-06 y CT-07: regla de 20 métodos públicos, `GroupRoster`, `PlanLifecycle`, `REGROUPING`, roles iniciales del plan |
| WP-10A | Piezas del cerebro | E3 | mergeado | Sonnet | [#12](https://github.com/Nicodoou/mob-ai-plugin/pull/12) | El WP-10 pasaba las 400 líneas y se dividió. Fin de plan, retirada táctica y reagrupamiento (CT-07), ataque sugerido, decisión y traza. Se lanza cuando el WP-08B esté mergeado |
| WP-10B | Cerebro | E3 | mergeado | Opus | [#13](https://github.com/Nicodoou/mob-ai-plugin/pull/13) | `Brain`: coordina las piezas del WP-10A y devuelve `BrainResult` con `DecisionTrace` |
| WP-11 | Simulación de aprendizaje | E3 | mergeado | Sonnet | [#14](https://github.com/Nicodoou/mob-ai-plugin/pull/14) | Calibra velocidad de aprendizaje y vida media |
| WP-12 | Grupos activos y membresía | E4 | mergeado | Sonnet | #15 | |
| WP-13 | Casos de uso de combate | E4 | mergeado | Sonnet | #16 | Avisar a `RegroupWindow` cuando un grupo muere entero en `REGROUPING` (CT-07) |
| WP-14 | Puerto de persistencia y JSON | E4 | mergeado | Sonnet | #17 | Guardar `RegroupWindow` y el último número de plan de cada grupo (CT-07) |
| WP-15 | Guardar, cargar, resetear y consultar | E4 | mergeado | Sonnet | #18 | Agregar al dominio la restauración del número de plan (`PlanLifecycle`) y usar `RegroupWindow.restore`; si la carga falla por versión más nueva, no guardar nunca (si no, se borran esos archivos). |
| WP-28A | Estado completo y azar grabado | E4 | mergeado | Opus | #21 | |
| WP-28B | Incidente, JSON y reproducción | E4 | mergeado | Sonnet | #22 | Infinity ida y vuelta obligatorio (CT-04); cierra E4 |
| WP-16 | Runtime, configuración y mensajes | E5 | mergeado | Sonnet | #19 | Prueba que compare rangos del cargador con los records; `learningSpeed` por defecto 1,0 (CT-09) |
| WP-17 | Traductor de versión y fotos | E5 | mergeado | Sonnet | #20 | Incluye `MovementTracker` |
| WP-18 | Rastreador cuerpo a cuerpo | E5 | mergeado | Sonnet | #23 | |
| WP-19 | Roles, goals y golpe frontal | E5 | mergeado | Sonnet | #24 | `MeleeAttacker`: `openMelee` con `TargetChecks.isInvulnerable`, `mob.attack`, `closeMelee` con `TargetChecks.isValidTarget`, siempre en `try/finally` (cancelar si `attack` lanza); cancelar intentos al descargarse el chunk (WP-18). Reinstalar goals en `EntityAddToWorldEvent` |
| WP-20A | Schedulers y aplicador | E5 | mergeado | Sonnet | #26 | |
| WP-20B | Arranque del plugin | E5 | mergeado | Opus | #27 | Agendar cada tick `ServerTickCounter.advance()` y `MovementTracker.sample` de los jugadores conectados (`forget` al salir); semilla de `JdkRandomSource`; `saveDefaultConfig` y `ConfigLoader`. Aplicar `LoadReport.state()` al reloj y a `RegroupWindow.restore`, y pasar el estado a `SaveMemories.capture`; usar `GuardedMemoryRepository` (WP-15). Suscribir `ClosePlan` a `PlanClosed`, con una prueba de que la suscripción existe (CT-11); recorrer los grupos con un `catch` por grupo al llamar a `TickGroups` |
| WP-21 | Comandos | E5 | mergeado | Sonnet | #28 | `/mobai reload` atrapa cualquier excepción de `ConfigLoader` (un entero enorme sale como `ArithmeticException`, riesgo del WP-16) y conserva la configuración anterior. Los mobs de `spawngroup` con `setRemoveWhenFarAway(false)`: si no, un miembro que desaparece con el server apagado queda para siempre en su grupo guardado. Spawn del grupo de prueba sin equipo |
| WP-29A | Incidentes en el server | E5 | mergeado | Sonnet | #29 | Incluye el `DebugLog` legible (sale del WP-21): cada plan y cada ataque con estrategia, ataque y resultado, para contar en la validación (catálogo). Publicar los eventos pendientes y copiar el grupo (`GroupCaptureMapper`) antes de cada decisión, con `RecordingRandomSource.clear()`; medir el costo y limitarlo al debug si pesa (CT-12). `RecordingRandomSource` ya existe (WP-28A) |
| WP-29B | Trazas y log de debug | E5 | mergeado | Sonnet | #30 | Niveles por grupo, `TraceWriter` JSON Lines, `DebugLog` legible para contar (catálogo), `/mobai debug <grupo|all> <nivel>`; se cuelga de `TraceHub` (WP-29A) |
| WP-22A | Sin planes con el grupo en retirada y formación de flanqueo | E6 | mergeado | Sonnet | [#32](https://github.com/Nicodoou/mob-ai-plugin/pull/32) | CT-13 (opción 1) y CT-14 |
| WP-22B | Goal de flanqueo y curación | E6 | mergeado | Sonnet | [#33](https://github.com/Nicodoou/mob-ai-plugin/pull/33) | Después del WP-22A. Golpe de flanco en `FlankGoal` (CT-14). Verificación en el server por Opus. Los goals miden el tiempo con el reloj del plugin (B-01) |
| WP-22D | Flanqueo fuera de la vista | E6 | mergeado | Sonnet | [#35](https://github.com/Nicodoou/mob-ai-plugin/pull/35) | Arregla B-02 con el modelo de Nico (CT-16). Verificación en el juego por Nico |
| WP-22C | Retirada a cubierto | E6 | mergeado | Sonnet | [#34](https://github.com/Nicodoou/mob-ai-plugin/pull/34) | Después del WP-22B. CT-15 (pedido de Nico). Verificación en el server por Opus, incluido el costo con varios mobs en retirada |
| WP-23 | Golpe paciente | E6 | especificado | Sonnet | — | CT-17 (apertura y abandono). Verificación en el juego por Nico |
| WP-24 | Esqueletos y proyectiles | E6 | pendiente | Sonnet | — | Hasta acá los esqueletos conservan sus goals vanilla (WP-19): `GoalInstaller` tiene que pasar a instalarles los nuestros. Mientras no registren, un grupo de solo esqueletos cierra planes con éxito 0 (puerta E5, corrida 5): verificar que deje de pasar. Ignorar impactos que llegan después del plazo |
| WP-25 | Arañas | E6 | pendiente | Sonnet | — | |
| WP-26 | Consulta de memoria y métricas | E6 | pendiente | Sonnet | — | |
| WP-27 | Validación del MVP | E7 | pendiente | Nico + Opus | — | |

## Puertas de etapa

| Puerta | Estado | Evidencia |
| --- | --- | --- |
| E0 | pasada | Paper 26.3 build 151 levantó con el plugin habilitado y deshabilitado sin errores |
| E1 | pasada | `hallazgos-api.md`, 7 rondas de prueba con Nico en el server |
| E2 | pasada | 146 pruebas en verde; valores de RF-06 verificados en WP-03; cobertura del dominio 96 % |
| E3 | pasada | 367 pruebas; simulación del WP-11: con velocidad 1,0 y vida media 12.000 el grupo aprende a flanquear al que bloquea en el 90 % de 300 corridas (CT-09) |
| E4 | pasada | 560 pruebas, 0 fallas, cobertura total 94 % (dominio y aplicación entre 94 y 100 %). Memoria de ida y vuelta por disco (`JsonMemoryRepositoryTest`, `SaveAndLoadMemoriesTest`); todos los casos de uso con fakes; incidentes provocados (decisión en medio de un plan, cierre de plan y falla) se reproducen idénticos desde su JSON con `TraceReplay`, y los adulterados (azar, estado, números sobrantes, ventana) se detectan |
| E5 | **pasada** (7 oct) | Guion: `docs/plan/puerta-e5.md`; registro: `docs/plan/puerta-e5-registro.md`. Además del guion: tildes de los mensajes en el chat del juego (en el log de la consola de Windows salen mal, `Us�`, por la codificación de la consola); permiso `mobai.admin` no declarado en `plugin.yml` (queda solo para operadores); goals del WP-19 (el zombie persigue y golpea con ritmo de 1 s; reinstalación tras alejarse 300 bloques y volver; los esqueletos siguen con su IA vanilla); listeners del WP-18 (golpe normal, bloqueado con escudo, contra absorción, en invulnerabilidad y en creativo; muerte de un miembro y del jugador; amenaza por golpe y por flecha del jugador); verificar en el server lo que no tiene prueba JUnit (riesgo del WP-17): la foto de un grupo (`SnapshotFactory`) con jugadores en supervivencia, creativo y lejos; efectos, armadura y Protección leídos por `VersionTranslator`; daño absorbido y golpe bloqueado |
| E6 | pendiente | |
| G1 | pendiente | |

## Decisiones abiertas

| Decisión | Dónde se cierra |
| --- | --- |
| El cerebro le sugiere el golpe de flanco también a un zombie que presiona; `PressGoal` pega de frente y registra eso (lo ejecutado), así que la sugerencia se desperdicia. Propuesta: que `AttackSuggester` no ofrezca `ZOMBIE_FLANK_STRIKE` fuera del rol `FLANK` (CT-08 ya lo elige solo para los flanqueadores). Cambia sorteos de varias pruebas del cerebro | WP chico de limpieza, junto con los ciclos de paquetes |
| Ciclos entre paquetes del dominio: `group` ↔ `decision` (`ClosedPlan` usa `PlanEndReason`) y `brain` ↔ `decision` (`DecisionTrace` usa `RegroupEndReason`). No rompen nada, pero conviene una regla de ArchUnit sin ciclos y mover los enums compartidos | Después de la puerta E3, en un WP chico de limpieza |
| `PlanEndDetector.hasRetreated` con miembros que se suman a mitad de plan: «se fueron» = iniciales − roles actuales da negativo y la regla se vuelve menos sensible. Propuesta: `gone = max(0, iniciales − roles)` y comparar contra `max(iniciales, roles)` | WP de limpieza después de E3 (junto con los ciclos de paquetes); el WP-11 puede mostrar si importa |
| Brujas contra un objetivo que se cura más rápido de lo que el grupo le pega (idea de Nico). Tres jugadas: debuffear al jugador (Veneno, Daño instantáneo, Debilidad, Lentitud), buffear a los aliados (Fuerza, Velocidad) o las dos en secuencia. Propuesta: puntuar cada poción por cuánto baja el tiempo para matarlo, recalculando `KillTimeEstimator` con la foto modificada, y multiplicar por su tasa aprendida. Cuidar la salpicadura: cura y daño se invierten en no-muertos, y Fuerza o Velocidad cerca del jugador también lo buffean a él. La señal de «inmatable» ya existe: `KillTimeEstimate.damagePerSecond` negativo | Fase 2, en el WP de las brujas |
| Tope de seguridad por tipo de mob, regla para aceptar o rechazar mobs, mobs raros, bloques que rompen los zombies, memoria en SQLite | Fase 2, después de G1 |
