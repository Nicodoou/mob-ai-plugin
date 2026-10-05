# Estado del plan

Tablero del orquestador. Se actualiza y se commitea cada vez que un WP cambia de estado. Una sesión nueva retoma desde acá (ver `orquestacion.md`).

**Última actualización:** 5 de octubre de 2026 (WP-09 mergeado).

## Próximo paso

1. WP-09 mergeado. WP-10A especificado en `wp/WP-10A-piezas-del-cerebro.md`, esperando la aprobación de Nico; después, especificar el WP-10B. WP-07 y WP-08 pueden ir en paralelo (los dos dependen solo de WP-06). El orden del plan se mantiene (Nico decidió no reordenar).
2. Nico prefiere seguir en la misma sesión compactando el contexto en vez de abrir una nueva: después de cada compactación, releer `orquestacion.md` y este tablero antes de seguir.

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
| WP-10A | Piezas del cerebro | E3 | especificado | Sonnet | — | El WP-10 pasaba las 400 líneas y se dividió. Fin de plan, retirada, ataque sugerido, decisión y traza; `Plan` suma `startingMembers` |
| WP-10B | Cerebro | E3 | pendiente | Opus | — | `Brain`: coordina las piezas del WP-10A y devuelve `BrainResult` con `DecisionTrace` |
| WP-11 | Simulación de aprendizaje | E3 | pendiente | Sonnet | — | Calibra velocidad de aprendizaje y vida media |
| WP-12 | Grupos activos y membresía | E4 | pendiente | Sonnet | — | |
| WP-13 | Casos de uso de combate | E4 | pendiente | Sonnet | — | |
| WP-14 | Puerto de persistencia y JSON | E4 | pendiente | Sonnet | — | |
| WP-15 | Guardar, cargar, resetear y consultar | E4 | pendiente | Sonnet | — | |
| WP-28 | Trazas: incidentes y reproducción | E4 | pendiente | Sonnet | — | **Obligatorio:** el JSON de trazas e incidentes tiene que escribir y volver a leer `Infinity` (tiempo para matar de un jugador inmatable, WP-07), con prueba de ida y vuelta; Gson por defecto lanza una excepción con infinitos |
| WP-16 | Runtime, configuración y mensajes | E5 | pendiente | Sonnet | — | Prueba que compare rangos del cargador con los records |
| WP-17 | Traductor de versión y fotos | E5 | pendiente | Sonnet | — | Incluye `MovementTracker` |
| WP-18 | Rastreador cuerpo a cuerpo | E5 | pendiente | Sonnet | — | |
| WP-19 | Roles, goals y golpe frontal | E5 | pendiente | Sonnet | — | Reinstalar goals en `EntityAddToWorldEvent` |
| WP-20 | Scheduler, guardado y arranque | E5 | pendiente | Sonnet | — | |
| WP-21 | Comandos y log de debug | E5 | pendiente | Sonnet | — | Spawn del grupo de prueba sin equipo |
| WP-29 | Trazas en el server | E5 | pendiente | Sonnet | — | |
| WP-22 | Flanqueo y retirada | E6 | pendiente | Sonnet | — | Repartir a los flanqueadores del mismo lado: `CombatGeometry.flankPoint` les da el mismo punto |
| WP-23 | Golpes de flanco y paciente | E6 | pendiente | Sonnet | — | |
| WP-24 | Esqueletos y proyectiles | E6 | pendiente | Sonnet | — | Ignorar impactos que llegan después del plazo |
| WP-25 | Arañas | E6 | pendiente | Sonnet | — | |
| WP-26 | Consulta de memoria y métricas | E6 | pendiente | Sonnet | — | |
| WP-27 | Validación del MVP | E7 | pendiente | Nico + Opus | — | |

## Puertas de etapa

| Puerta | Estado | Evidencia |
| --- | --- | --- |
| E0 | pasada | Paper 26.3 build 151 levantó con el plugin habilitado y deshabilitado sin errores |
| E1 | pasada | `hallazgos-api.md`, 7 rondas de prueba con Nico en el server |
| E2 | pasada | 146 pruebas en verde; valores de RF-06 verificados en WP-03; cobertura del dominio 96 % |
| E3 | pendiente | |
| E4 | pendiente | |
| E5 | pendiente | |
| E6 | pendiente | |
| G1 | pendiente | |

## Decisiones abiertas

| Decisión | Dónde se cierra |
| --- | --- |
| Valores por defecto de la velocidad de aprendizaje y de la vida media, calibrados juntos | Simulación del WP-11 (puerta E3) |
| `Group` tiene 25 métodos públicos (umbral de alerta: 20). Antes de sumarle más, evaluar dividirlo, por ejemplo membresía por un lado y ciclo del plan por otro | WP-10, WP-12 o WP-13, el primero que le agregue métodos |
| Brujas contra un objetivo que se cura más rápido de lo que el grupo le pega (idea de Nico). Tres jugadas: debuffear al jugador (Veneno, Daño instantáneo, Debilidad, Lentitud), buffear a los aliados (Fuerza, Velocidad) o las dos en secuencia. Propuesta: puntuar cada poción por cuánto baja el tiempo para matarlo, recalculando `KillTimeEstimator` con la foto modificada, y multiplicar por su tasa aprendida. Cuidar la salpicadura: cura y daño se invierten en no-muertos, y Fuerza o Velocidad cerca del jugador también lo buffean a él. La señal de «inmatable» ya existe: `KillTimeEstimate.damagePerSecond` negativo | Fase 2, en el WP de las brujas |
| Tope de seguridad por tipo de mob, regla para aceptar o rechazar mobs, mobs raros, bloques que rompen los zombies, memoria en SQLite | Fase 2, después de G1 |
