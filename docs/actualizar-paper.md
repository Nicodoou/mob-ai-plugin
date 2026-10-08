# Actualizar o cambiar la versión de Paper

Este documento dice **dónde toca Paper el plugin** y **qué hacer para pasar a otra versión**. Se mantiene al día: cada WP que agrega o cambia código que usa la API de Paper actualiza la tabla de la sección 3 en el mismo PR.

## 1. Cómo está aislado Paper

| Capa | ¿Usa Paper? |
| --- | --- |
| Dominio, aplicación | **No.** ArchUnit lo prohíbe en cada build. Ningún cambio de versión los toca |
| Persistencia | Solo Gson, que viene con Paper (sección 4) |
| `adapter.translate.VersionTranslator` | **Sí: es el único lugar con constantes que cambian entre versiones** (tipos de entidad, efectos, atributos, encantamientos, modificadores de daño). ArchUnit verifica que ninguna otra clase use `EntityType`, `Material`, `PotionEffectType`, `Attribute`, `Enchantment` ni `VanillaGoal` |
| Resto de los adaptadores y `bootstrap` | Usan la API estable de Paper (eventos, entidades, goals, pathfinder, comandos) directamente. Están listados en la sección 3 |

Regla de los adaptadores: lo que depende de la versión se averigua con `VersionTranslator` (por ejemplo, el tipo de mob: nunca `instanceof Zombie`). Ver `docs/plan/orquestacion.md`.

## 2. Dónde se fija la versión (tres lugares)

| Archivo | Qué cambiar | Hoy |
| --- | --- | --- |
| `gradle/libs.versions.toml` | `paper-api` | `26.3.build.151-beta` |
| `build.gradle.kts` | `tasks.runServer { minecraftVersion(...) }` | `26.3` |
| `src/main/resources/plugin.yml` | `api-version` | `'26.3'` |

Si la versión nueva pide otro Java, también `java.toolchain` y `options.release` en `build.gradle.kts`, y `java-version` en `.github/workflows/build.yml`.

## 3. Dónde se usa la API de Paper directamente

Riesgo: **alto** = API marcada como deprecada o que cambió en versiones recientes; **medio** = comportamiento que verificó el spike y podría cambiar sin que cambie la firma; **bajo** = API estable desde hace años.

| Clase | Qué usa de Paper | Riesgo | Qué revisar al actualizar |
| --- | --- | --- | --- |
| `adapter/translate/VersionTranslator` | `EntityType` (ZOMBIE, SKELETON, SPIDER), `PotionEffectType` (RESISTANCE, REGENERATION, POISON, WITHER, WEAKNESS, SLOWNESS), `Attribute` (MAX_HEALTH, ARMOR, ARMOR_TOUGHNESS, `Attribute.ENTITY_INTERACTION_RANGE`, `Attribute.MOVEMENT_SPEED` (`movementSpeed`)), `DataComponentTypes.ATTACK_RANGE`, `AttackRange.maxReach`, `ItemStack.hasData`/`getData`, `PlayerInventory.getItemInMainHand` (`playerReach`), `Enchantment.PROTECTION`, `EntityEquipment`, `ItemStack.getEnchantmentLevel`, `PotionEffect`, `AbstractArrow.PickupStatus.DISALLOWED` (`forbidPickup`), `Material.BOW`, `ItemStack`, `EntityEquipment.setItemInMainHand` y `setItemInMainHandDropChance` (`armWithBow`) | **Alto** | Que las constantes sigan existiendo con ese nombre (en 1.21.2 `GENERIC_MAX_HEALTH` pasó a `MAX_HEALTH`, por ejemplo). Si cambian, se corrige **solo** esta clase. `setPickupStatus` no está deprecado en 26.3 (sí `setPickupRule`); si cambia, solo cambia `forbidPickup`. Que las lanzas sigan trayendo su alcance en `ATTACK_RANGE` (la API de componentes es reciente) y que `ENTITY_INTERACTION_RANGE` siga siendo el alcance del jugador para pegar |
| `adapter/translate/VersionTranslator` (`absorbedDamage`, `wasBlocked`) | `EntityDamageEvent.DamageModifier` (ABSORPTION, BLOCKING), `isApplicable`, `getDamage(modifier)` | **Alto**: API deprecada sin reemplazo | Que sigan existiendo. Si Paper la quita, hay que encontrar otra forma de ver el daño absorbido y el bloqueo (hallazgos 3 y 4) |
| `adapter/snapshot/SnapshotFactory` | `Bukkit.getEntity(UUID)`, `Mob`, `Player` (`getGameMode`, `getHealth`, `getAbsorptionAmount`, `isBlocking`), `World.getPlayers()`, `Location.distanceSquared`, `GameMode` | Bajo | Que `isBlocking()` siga queriendo decir «escudo levantado» (hallazgo 3) |
| `adapter/tracker/TargetChecks` | `GameMode`, `getNoDamageTicks`, `getMaximumNoDamageTicks`, `isOnline`, `isDead`, `getWorld` | **Medio** | Que un golpe dentro de la ventana de invulnerabilidad siga sin disparar evento (hallazgo 4) |
| `adapter/listener/DamageListener` | `EntityDamageByEntityEvent` (`getDamager`, `getEntity`, `getFinalDamage`, `isCancelled`) | **Medio** | Que `mob.attack()` siga disparando el evento **dentro** de la misma llamada (hallazgo 2): el rastreador depende de eso |
| `adapter/listener/ProjectileListener` | `ProjectileHitEvent` (`getEntity`, `getHitEntity`), `EntityDamageByEntityEvent` (`getDamager`, `getEntity`, `getFinalDamage`, `isCancelled`), `Projectile`, `Mob`, `Player` | **Medio** | Que el impacto y el daño de una flecha sigan llegando en el mismo tick (hallazgo 5) y que el atacante del daño siga siendo la flecha y no el tirador |
| `adapter/scheduler/ProjectileResolver` | `Bukkit.getPlayer`, `Bukkit.getEntity`, `Player.isOnline`/`isDead` | Bajo | — |
| `adapter/listener/ThreatListener` | `EntityDamageByEntityEvent`, `Projectile.getShooter` | Bajo | — |
| `adapter/listener/DeathListener` | `EntityDeathEvent`, `EntityRemoveEvent` y su `Cause` (DEATH, UNLOAD) | **Medio** | El orden de eventos de muerte, quitado y descarga (hallazgo 6) y que la descarga no dispare `EntityRemoveEvent` |
| `adapter/goal/*` | Mob Goal API: `Goal`, `GoalKey`, `GoalType`, `Bukkit.getMobGoals()` (`removeAllGoals`, `addGoal`), `Pathfinder.moveTo(Entity, double)`, `Pathfinder.moveTo(Location, double)`, `stopPathfinding`, `Mob.lookAt`, `LivingEntity.attack`, `NamespacedKey`, `new Location(World, double, double, double)`, `LivingEntity.hasLineOfSight(Entity)` y `hasLineOfSight(Location)`, `LivingEntity.getEyeHeight()`, `Pathfinder.findPath(Location)`, `PathResult` (`canReachFinalPoint`, `getFinalPoint`), `Pathfinder.moveTo(PathResult, double)`, `Bukkit.getEntity`, `Bukkit.getPlayer`, `Entity.getWorld`/`isValid`, `World.getHighestBlockYAt(int, int)`, `HumanEntity.isBlocking()`, `HumanEntity.getAttackCooldown()`, `ProjectileSource.launchProjectile(Class, Vector)`, `Arrow`, `org.bukkit.util.Vector`, `LivingEntity.getEyeLocation()`, `Entity.getHeight()`, `Mob.hasLineOfSight(Entity)`, `Player.getCooldownPeriod()` y `Entity.getWidth()` | **Medio** | Que `moveTo(Location)` siga aceptando un punto dentro de un bloque (busca el más cercano alcanzable), que los goals propios se sigan perdiendo al descargar el chunk (hallazgo 1), que `attack()` no controle alcance ni ritmo (hallazgo 2), que `hasLineOfSight` siga cortándose en los bloques con colisión (vidrio y hojas incluidos), que `findPath` siga devolviendo `null` cuando no hay camino y **cada cuántos ticks llama Paper a `tick()`** de un goal activo: hoy, uno sí y uno no. Por eso `MeleeRhythm` mide con el reloj del plugin y no cuenta llamadas (B-01, puerta E5), que `getAttackCooldown()` siga bajando cerca de 0 con cada golpe del jugador y subiendo hasta 1 (hallazgo 7), y que `isBlocking()` siga queriendo decir escudo levantado (para la intención del golpe paciente y el esquivo calculado), que `getCooldownPeriod` siga dando los ticks de carga del arma en la mano, y que la física del suelo (`MOB_MOVE_INPUT_SCALE`, `GROUND_DRAG_PER_TICK`) no cambie, que una flecha lanzada con `launchProjectile` desde un esqueleto siga haciendo el daño de un tiro de esqueleto (unos 4 de daño a 1,6 bloques por tick), que `getHighestBlockYAt` sin `HeightMap` siga dando el bloque más alto que bloquea el movimiento (en cuevas y bajo árboles da el techo o la copa: el camino lo descarta) |
| `adapter/listener/EntityLifecycleListener` | `EntityAddToWorldEvent`, `EntityRemoveFromWorldEvent` (`com.destroystokyo.paper.event.entity`) | **Medio**: paquete viejo de Paper | Que sigan existiendo y con la misma semántica (hallazgo 6) |
| `adapter/listener/TargetListener` | `EntityTargetEvent` | Bajo | — |
| `adapter/config/ConfigLoader` | `ConfigurationSection` | Bajo | — |
| `adapter/config/Messages` | Adventure: `Component`, `MiniMessage`, `Placeholder`, `TagResolver` | Bajo | Que Paper siga trayendo Adventure con MiniMessage |
| `adapter/scheduler/RecoveryHealer` | `Bukkit.getEntity`, `LivingEntity.heal(double)` | **Medio** | Que `heal` siga sin pasar la vida máxima, que no cure a un mob muerto y que dispare `EntityRegainHealthEvent` (otro plugin podría cancelarlo) |
| `adapter/snapshot/PoseReader` | `Location` (`getX/Y/Z`, `getYaw`), `Player.getLocation`, `LivingEntity.getHeight()` (`bodyCenterOf`) | Bajo | Que el yaw siga midiéndose igual (0 mira al sur, crece en sentido horario) |
| `adapter/scheduler/MovementSampler` | `Bukkit.getOnlinePlayers()`, `Player.getLocation()`, `PlayerQuitEvent` | Bajo | — |
| `adapter/scheduler/GroupDecider`, `PersistenceScheduler` | `org.slf4j.Logger` (viene con Paper) | Bajo | Que Paper siga exponiendo SLF4J |
| `adapter/debug/*` | Sin Paper: solo Gson y `org.slf4j.Logger`, que vienen con Paper | Bajo | Que Paper siga trayendo Gson y SLF4J |
| `adapter/command/MobAiCommand` | `BasicCommand` (`execute`, `suggest`, `permission`), `CommandSourceStack.getSender` | **Medio**: API de Paper reciente | Que `BasicCommand` conserve esas firmas y que `JavaPlugin.registerCommand` siga registrando sin `plugin.yml` (hallazgo 9) |
| `adapter/command/GroupSpawner` | `World.spawnEntity(Location, EntityType)`, `Mob`, `EntityEquipment.clear`, `LivingEntity.setCanPickupItems`, `setRemoveWhenFarAway`, `Player.getLocation`, `Location` | Bajo | Que el mob spawneado salga sin equipo (hallazgo 2) |
| `adapter/command/SpawnGroupCommand`, `StatusCommand`, `ResetCommand`, `ReloadCommand`, `DebugCommand` | `CommandSender.sendMessage(Component)`, `Player`, `Bukkit.getPlayer`, `Bukkit.getPlayerExact`, `Bukkit.getOnlinePlayers`, `Plugin.reloadConfig`, `Plugin.getConfig` | Bajo | — |
| `bootstrap/PluginRuntime` | `JavaPlugin` (`saveDefaultConfig`, `saveResource`, `reloadConfig`, `getConfig`, `getDataFolder`, `getSLF4JLogger`, `registerCommand`), `YamlConfiguration.loadConfiguration`, `BukkitScheduler.runTaskTimer`, `BukkitTask`, `PluginManager.registerEvents`, `Bukkit.getWorlds`, `World.getEntitiesByClass` | Bajo | — |
| `bootstrap/AdapterServices` | `Plugin`, `Listener` | Bajo | — |
| `bootstrap/MobAiPlugin` | `JavaPlugin` (`getPluginMeta`, `disablePlugin`) | Bajo | — |

## 4. Bibliotecas que vienen con Paper

El plugin no las empaqueta: usa las que trae el server. Si una versión de Paper cambia la versión de alguna, hay que correr las pruebas.

| Biblioteca | Hoy | La usan |
| --- | --- | --- |
| Gson | 2.14.0 | `persistence` (memorias en JSON), `adapter/debug` (incidentes) |
| SnakeYAML | 2.2 | `ConfigLoader` y `Messages`, a través de `YamlConfiguration` |
| Adventure + MiniMessage | 5.2.0 | `Messages` |

## 5. Procedimiento para cambiar de versión

1. **Rama nueva** (`chore/paper-<versión>`).
2. **Cambiar la versión** en los tres lugares de la sección 2.
3. **`./gradlew build`.** Con `-Werror`, cualquier API deprecada nueva rompe la compilación y muestra exactamente qué clase la usa. Las constantes renombradas también fallan acá. Arreglar **solo** en las clases de la sección 3 (casi siempre `VersionTranslator`).
4. **Verificar los nombres** con `javap` sobre el jar nuevo de `paper-api` (en `~/.gradle/caches/modules-2/files-2.1/io.papermc.paper/paper-api/`), para las clases de riesgo alto.
5. **Repetir en el server los hallazgos del spike** que la sección 3 marca como riesgo medio o alto (`docs/plan/hallazgos-api.md`, secciones 1 a 6). Son comportamientos: compilan igual aunque cambien.
6. **Guion de la puerta E5** (y E6 cuando exista) completo en el server.
7. Actualizar este documento con la versión nueva y lo que cambió.

## 6. Historial

| Fecha | Versión | Notas |
| --- | --- | --- |
| 5 oct 2026 | Paper 26.3 build 151 (beta) | Versión inicial; spike WP-01 |
