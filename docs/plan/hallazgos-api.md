# Hallazgos del spike (WP-01)

5 de octubre de 2026 · Paper 26.3 build 151 · Probado en el server con un jugador real (papu123). El código del spike está en la rama `spike/wp-01`, que no se mergea.

Este documento es la fuente de verdad de los WPs de adaptadores (WP-16 a WP-26). Cada hallazgo dice qué se observó y qué decide para el diseño.

## 1. Mob Goal API

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| `Bukkit.getMobGoals()` → `MobGoals`. Un goal propio implementa `Goal<T extends Mob>`: `shouldActivate()`, `shouldStayActive()`, `start()`, `stop()`, `tick()`, `getKey()` y `getTypes()` (un `EnumSet<GoalType>`). La clave es `GoalKey.of(Mob.class, new NamespacedKey(plugin, "nombre"))` | El goal arrancó al tick siguiente de agregarlo | API confirmada tal como la describe `arquitectura.md` |
| `removeAllGoals(mob, GoalType.MOVE)`, `LOOK` y `TARGET` dejan al zombie sin **ningún** goal: se van los 12 que trae, incluidos 5 de búsqueda de objetivo y `hurt_by`. El zombie no trae goal de nadar | Lista de goals antes y después en el log | `GoalInstaller` quita esos tres tipos. No hay goal de nadar que conservar en zombies |
| **Los goals propios se pierden cuando el chunk se descarga.** Al volver, el mob tiene otra vez sus goals vanilla | `/spike goals` después de alejarse 300 bloques | **Obligatorio:** reinstalar los goals de cada miembro en `EntityAddToWorldEvent` |
| Pathfinder: `mob.getPathfinder().moveTo(entity, speed)` y `stopPathfinding()`. Mirar: `mob.lookAt(entity)` | El zombie persigue bien | Confirmado |

## 2. Golpe cuerpo a cuerpo

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| `mob.attack(target)` dispara `EntityDamageByEntityEvent` **dentro de la misma llamada**, en el mismo tick | 100 % de los golpes: el evento llega entre el «antes» y el «después» de `attack()` | El rastreador resuelve un intento cuerpo a cuerpo al instante, sin plazo |
| `attack()` no controla el alcance ni el ritmo de ataque | Los golpes salen siempre que el goal lo llama | El goal controla alcance y cooldown (valores con nombre o de configuración) |
| La vida que trae el evento es la de **antes** del golpe; la de después se ve al tick siguiente | `health` del evento y del tick siguiente | El daño se toma del evento, no de la vida |
| Daño de zombie: 2,5 en fácil y 3 en normal. Un zombie spawneado por la API puede traer equipo: hubo golpes de 8 | Log | El grupo de prueba de `/mobai spawngroup` tiene que vaciarles el equipo, así las pruebas son comparables |

## 3. Escudo

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| Un golpe bloqueado **no** viene cancelado: trae `damage` completo, `finalDamage = 0` y el modificador `BLOCKING` con el daño negativo | 12 golpes bloqueados | **Regla 7: el golpe fue bloqueado si y solo si el modificador `BLOCKING` es distinto de 0** |
| `isBlocking()` **no alcanza**: con el escudo arriba y el zombie a más de 90° del frente, `isBlocking()` da `true` pero el daño entra completo | Bloqueados a 8°, 10°, 43° y 82°; no bloqueados a 98°, 107° y 111° | `isBlocking()` sirve para la intención (golpe paciente), nunca para clasificar |
| El arco del escudo es de 90° hacia cada lado del frente del jugador | Tabla anterior | Coincide con el umbral del golpe de flanco del catálogo («más de 90°») |
| `isHandRaised()` se activa al levantar el escudo; `isBlocking()`, **5 ticks después** | Transiciones en el log | Constante de Minecraft `SHIELD_WARMUP_TICKS = 5` |

## 4. Absorción, resistencia e invulnerabilidad

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| **Con absorción, `finalDamage` da 0 aunque el golpe sacó corazones de absorción.** Lo absorbido aparece en el modificador `ABSORPTION` (negativo) | 6 golpes: `finalDamage=0`, `ABSORPTION=-2,40`, absorción 16 → 13,6 | **Corrección obligatoria a la regla 6:** daño real = `finalDamage` + lo absorbido por `ABSORPTION`. Con la regla tal como está, todo golpe contra alguien que comió una manzana dorada contaría como fallo |
| La resistencia aparece como modificador `RESISTANCE` | `RESISTANCE=-0,60` con Resistencia I | Informativo |
| Si el objetivo está en la ventana de invulnerabilidad (`noDamageTicks` > la mitad del máximo) y el golpe no supera al anterior, **no llega ningún evento** | Golpes con `noDamageTicks` en 17 de 20: ningún evento | **Regla 3:** se detecta al abrir el intento. Si `noDamageTicks > max/2` y no llega evento, el golpe es neutral (`TARGET_INVULNERABLE`), no fallo |
| Si dentro de la ventana el golpe supera al anterior, llega el evento con `INVULNERABILITY_REDUCTION` y aplica solo la diferencia | `noDamageTicks` en 19: `finalDamage=2` con `INVULNERABILITY_REDUCTION=-1` | Cuenta como acierto si el daño real es mayor a 0 |
| Contra un jugador en creativo, `attack()` tampoco dispara evento | 7 golpes sin evento | Mismo tratamiento: sin evento contra un objetivo invulnerable, el golpe es neutral |
| Los modificadores se leen con `EntityDamageEvent.getDamage(DamageModifier)`, una API deprecada | Compila con `@SuppressWarnings` | La lectura vive en un solo método de `VersionTranslator`, con el motivo comentado |

## 5. Flechas

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| `skeleton.launchProjectile(Arrow.class, velocidad)` dispara con dirección propia; `rangedAttack(target, 1f)` usa la puntería vanilla | Ambos funcionaron | `BowShooter` usa `launchProjectile` para el tiro anticipado y `rangedAttack` para el directo |
| **Tiro anticipado: 8 de 9 impactos; directo: 1 de 5**, contra un jugador caminando de costado | Log de impactos | Los dos disparos del catálogo tienen sentido. El anticipado se queda |
| **`player.getVelocity()` no sirve**: caminando da 0 en horizontal. La velocidad real sale de la diferencia de posición tick a tick | `serverVelocity=0,-0.078,0` contra `movementPerTick≈0,2` | La foto del jugador lleva el movimiento por tick, medido por un rastreador de posiciones |
| Lanzamiento, impacto y daño se correlacionan por el UUID de la flecha. `ProjectileHitEvent` y `EntityDamageByEntityEvent` llegan en el mismo tick. El atacante del daño es la flecha (`getDamager()`) y el tirador sale de `getShooter()` | 9 flechas | El rastreador indexa los intentos de proyectil por el UUID de la flecha |
| Una flecha que falla dispara `ProjectileHitEvent` con el bloque | 4 flechas a pasto o grava | Regla 8: fallo |
| Una flecha tardó **69 ticks** en caer, más que el plazo de 60 | Log | El rastreador ignora los impactos de intentos ya cerrados por plazo |
| Vuelo típico a 12 bloques: de 5 a 7 ticks | Log | El plazo de 60 ticks sobra para los impactos reales |

## 6. Ciclo de vida de las entidades

| Situación | Eventos, en orden | Decisión |
| --- | --- | --- |
| Muerte | `EntityDeathEvent` → 20 ticks después `EntityRemoveEvent(DEATH)` → `EntityRemoveFromWorldEvent` (con `isDead=true`) | Baja del grupo: con `EntityDeathEvent`, que llega primero |
| Quitado por el plugin o un comando | `EntityRemoveEvent(PLUGIN)` → `EntityRemoveFromWorldEvent` (con `isDead=true`) | Baja del grupo |
| **Descarga del chunk** | Solo `EntityRemoveFromWorldEvent`, con `isDead=false`. **`EntityRemoveEvent` no se dispara** | El mob sigue en el grupo; se cancelan sus intentos abiertos |
| Vuelve a cargarse | `EntityAddToWorldEvent`, con el **mismo UUID** | Se reinstalan los goals (sección 1) |

Regla para los adaptadores: la baja de un miembro llega por `EntityDeathEvent`, o por `EntityRemoveEvent` con cualquier causa. La descarga llega solo por `EntityRemoveFromWorldEvent` con `isDead=false`.

## 7. Jugador

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| Cada golpe del jugador dispara `PlayerArmSwingEvent` y deja `getAttackCooldown()` cerca de 0, que después sube hasta 1 | Swings en el log | Golpe paciente: «terminó su golpe» = hubo swing y el cooldown se está recuperando |
| La muerte del jugador dispara `EntityDeathEvent` | Muerte por flecha | `RecordPlayerDeath` se engancha a ese evento (o a `PlayerDeathEvent`, que es su subclase) |
| `hasLineOfSight(player)` funciona | `/spike los` | Confirmado para «objetivo perdido» |

## 8. Fuego amigo

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| `zombie.attack(otroZombie)` aplica daño normal y el evento no viene cancelado | `finalDamage=2,94` | RF-09 se cumple sin hacer nada |
| Con sus goals vanilla, el zombie golpeado no cambió de objetivo en 5 ticks | Objetivo antes y después | Nuestros miembros no tienen goals de objetivo; `TargetListener` queda como red de seguridad |

## 9. Comandos y utilidades

| Hallazgo | Evidencia | Decisión |
| --- | --- | --- |
| `JavaPlugin.registerCommand(nombre, descripción, BasicCommand)` registra el comando sin declararlo en `plugin.yml` | `/spike` con autocompletado | `MobAiCommand` implementa `BasicCommand` y se registra así en el arranque |
| `Bukkit.getCurrentTick()` existe | Usado en el log | Útil para los logs; el reloj del dominio sigue siendo el contador propio |

## Cambios que esto pide al diseño

1. **Regla 6 del rastreador** (`arquitectura.md`): «daño final mayor a 0» pasa a ser «daño real mayor a 0», donde daño real = daño final + lo absorbido. `AttackFacts` lleva el daño real, calculado en el adaptador.
2. **Regla 7:** el bloqueo se detecta con el modificador `BLOCKING`, no con `isBlocking()`.
3. **Regla 3:** la invulnerabilidad se detecta al abrir el intento (`noDamageTicks` o modo de juego) y se confirma porque no llega evento.
4. **Ciclo de vida:** se reinstalan los goals al volver a cargar el mob; la baja y la descarga usan los eventos de la sección 6.
5. **Foto del jugador:** lleva el movimiento por tick medido, no `getVelocity()`.
6. **Grupo de prueba:** los mobs se spawnean sin equipo.
